/* 
 * enviroCar 2013
 * Copyright (C) 2013  
 * Martin Dueren, Jakob Moellers, Gerald Pape, Christopher Stephan
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
 * 
 */

package org.envirocar.app.activity;

import java.io.File;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.TimeZone;

import org.envirocar.app.R;
import org.envirocar.app.application.ECApplication;
import org.envirocar.app.application.RestClient;
import org.envirocar.app.application.UploadManager;
import org.envirocar.app.application.User;
import org.envirocar.app.application.UserManager;
import org.envirocar.app.logging.Logger;
import org.envirocar.app.storage.DbAdapter;
import org.envirocar.app.storage.DbAdapterRemote;
import org.envirocar.app.storage.Measurement;
import org.envirocar.app.storage.Track;
import org.envirocar.app.views.TypefaceEC;
import org.envirocar.app.views.Utils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.BaseExpandableListAdapter;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.view.Menu;
import com.loopj.android.http.JsonHttpResponseHandler;

import de.keyboardsurfer.android.widget.crouton.Crouton;
import de.keyboardsurfer.android.widget.crouton.Style;
/**
 * List Fragement that displays local and remote tracks.
 * @author jakob
 * @author gerald
 *
 */
public class ListMeasurementsFragment extends SherlockFragment {
	
	ECApplication application;

	// Measurements and tracks
	
	private ArrayList<Track> tracksList;
	private TracksListAdapter elvAdapter;
	private DbAdapter dbAdapterRemote;
	private DbAdapter dbAdapterLocal;
	
	// UI Elements
	
	private ExpandableListView elv;
	private ProgressBar progress;
	private int itemSelect;
	
	private boolean isDownloading = false;
	
	private com.actionbarsherlock.view.MenuItem upload;

	public View onCreateView(android.view.LayoutInflater inflater,
			android.view.ViewGroup container,
			android.os.Bundle savedInstanceState) {
		
		application = ((ECApplication) getActivity().getApplication()); 
		
		setHasOptionsMenu(true);

		dbAdapterRemote = ((ECApplication) getActivity().getApplication()).getDbAdapterRemote();
		dbAdapterLocal = ((ECApplication) getActivity().getApplication()).getDbAdapterLocal();

		View v = inflater.inflate(R.layout.list_tracks_layout, null);
		elv = (ExpandableListView) v.findViewById(R.id.list);
		progress = (ProgressBar) v.findViewById(R.id.listprogress);
		elv.setEmptyView(v.findViewById(android.R.id.empty));
		
		registerForContextMenu(elv);

		elv.setOnItemLongClickListener(new OnItemLongClickListener() {

			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				itemSelect = ExpandableListView.getPackedPositionGroup(id);
				logger.info(String.valueOf("Selected item: " + itemSelect));
				return false;
			}

		});
		
		
		return v;
	};
	
	@Override
	public void onCreateOptionsMenu(Menu menu, com.actionbarsherlock.view.MenuInflater inflater) {
    	inflater.inflate(R.menu.menu_tracks, (com.actionbarsherlock.view.Menu) menu);
    	super.onCreateOptionsMenu(menu, inflater);
	}
	
	private com.actionbarsherlock.view.MenuItem delete_btn;

	protected static final Logger logger = Logger.getLogger(ListMeasurementsFragment.class);
	
	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		delete_btn = menu.findItem(R.id.menu_delete_all);
		if (((ECApplication) getActivity().getApplication()).getDbAdapterLocal().getAllTracks().size() > 0 && !isDownloading) {
			menu.findItem(R.id.menu_delete_all).setEnabled(true);
			if(UserManager.instance().isLoggedIn())
				menu.findItem(R.id.menu_upload).setEnabled(true);
		} else {
			menu.findItem(R.id.menu_upload).setEnabled(false);
			menu.findItem(R.id.menu_delete_all).setEnabled(false);
		}
		upload = menu.findItem(R.id.menu_upload);
		
	}
	
	/**
	 * Method to remove all tracks of the logged in user from the listview and from the internal database.
	 * Tracks which are locally on the device, are not removed.
	 */
	public void clearRemoteTracks(){
		try{
			for(Track t : tracksList){
				if(!t.isLocalTrack())
					tracksList.remove(t);
			}
		} catch (ConcurrentModificationException e) {
			logger.warn(e.getMessage(), e);
			clearRemoteTracks();
		}
		dbAdapterRemote.deleteAllTracks();
		elvAdapter.notifyDataSetChanged();
	}
	
	public void notifyDataSetChanged(){
		tracksList.clear();
		downloadTracks();
		//elvAdapter.notifyDataSetChanged();
	}
	
	/**
	 * Edit all tracks
	 */
	@Override
	public boolean onOptionsItemSelected(
			com.actionbarsherlock.view.MenuItem item) {
		switch(item.getItemId()){
		
		//Upload all tracks
		
		case R.id.menu_upload:
			((ECApplication) getActivity().getApplicationContext()).createNotification("start");
			UploadManager uploadManager = new UploadManager(((ECApplication) getActivity().getApplication()));
			uploadManager.uploadAllTracks();
			upload.setEnabled(false);
			return true;
			
		//Delete all tracks

		case R.id.menu_delete_all:
			((ECApplication) getActivity().getApplication()).getDbAdapterLocal().deleteAllTracks();
			((ECApplication) getActivity().getApplication()).setTrack(null);
			tracksList.clear();
			downloadTracks();
			Crouton.makeText(getActivity(), R.string.all_local_tracks_deleted,Style.CONFIRM).show();
			return true;
			
		}
		return false;
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = getSherlockActivity().getMenuInflater();
		final Track track = tracksList.get(itemSelect);
		if(track.isLocalTrack()){
			inflater.inflate(R.menu.context_item, menu);
		}else{
			inflater.inflate(R.menu.context_item_remote, menu);
		}
	}
	
	/**
	 * Change one item
	 */
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		final Track track = tracksList.get(itemSelect);
		switch (item.getItemId()) {
		
		//Edit the trackname

		case R.id.editName:
			if(track.isLocalTrack()){
				logger.info("editing track: " + itemSelect);
				final EditText input = new EditText(getActivity());
				new AlertDialog.Builder(getActivity()).setTitle(getString(R.string.editTrack)).setMessage(getString(R.string.enterTrackName)).setView(input).setPositiveButton("Ok", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						String value = input.getText().toString();
						logger.info("New name: " + value.toString());
						track.setName(value);
						track.setDatabaseAdapter(dbAdapterLocal);
						track.commitTrackToDatabase();
						tracksList.get(itemSelect).setName(value);
						elvAdapter.notifyDataSetChanged();
						Crouton.showText(getActivity(), getString(R.string.nameChanged), Style.INFO);
					}
				}).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						// Do nothing.
					}
				}).show();
			} else {
				Crouton.showText(getActivity(), R.string.not_possible_for_remote, Style.INFO);
			}
			return true;
			
		//Edit the track description

		case R.id.editDescription:
			if(track.isLocalTrack()){
				logger.info("editing track: " + itemSelect);
				final EditText input2 = new EditText(getActivity());
				new AlertDialog.Builder(getActivity()).setTitle(getString(R.string.editTrack)).setMessage(getString(R.string.enterTrackDescription)).setView(input2).setPositiveButton("Ok", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						String value = input2.getText().toString();
						logger.info("New description: " + value.toString());
						track.setDescription(value);
						track.setDatabaseAdapter(dbAdapterLocal);
						track.commitTrackToDatabase();
						elv.collapseGroup(itemSelect);
						tracksList.get(itemSelect).setDescription(value);
						elvAdapter.notifyDataSetChanged();
						// TODO Bug: update the description when it is changed.
						Crouton.showText(getActivity(), getString(R.string.descriptionChanged), Style.INFO);
					}
				}).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						// Do nothing.
					}
				}).show();
			} else {
				Crouton.showText(getActivity(), R.string.not_possible_for_remote, Style.INFO);
			}
			return true;
			
		// Show that track in the map

		case R.id.startMap:
			logger.info("Show in Map");
			logger.info(Environment.getExternalStorageDirectory().toString());
			File f = new File(Environment.getExternalStorageDirectory() + "/Android");
			if (f.isDirectory()) {
				ArrayList<Measurement> measurements = track.getMeasurements();
				logger.info("Count of measurements in the track: " + String.valueOf(measurements.size()));
				String[] trackCoordinates = extractCoordinates(measurements);
				
				if (trackCoordinates.length != 0){
					logger.info(String.valueOf(trackCoordinates.length));
					Intent intent = new Intent(getActivity().getApplicationContext(), Map.class);
					Bundle bundle = new Bundle();
					bundle.putStringArray("coordinates", trackCoordinates);
					intent.putExtras(bundle);
					startActivity(intent);
				} else {
					Crouton.showText(getActivity(), getString(R.string.trackContainsNoCoordinates), Style.INFO);
				}
				
			} else {
				Crouton.showText(getActivity(), getString(R.string.noSdCard), Style.INFO);
			}

			return true;
			
		// Delete only this track

		case R.id.deleteTrack:
			if(track.isLocalTrack()){
				logger.info("deleting item: " + itemSelect);
				dbAdapterLocal.deleteTrack(track.getId());
				Crouton.showText(getActivity(), getString(R.string.trackDeleted), Style.INFO);
				tracksList.remove(itemSelect);
				elvAdapter.notifyDataSetChanged();
			} else {
				createDeleteDialog(track);
			}
			return true;
			
		case R.id.shareTrack:
			try{
				Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
				sharingIntent.setType("application/json");
				Uri shareBody = Uri.fromFile(new UploadManager(getActivity().getApplication()).saveTrackAndReturnUri(track));
				sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "EnviroCar Track "+track.getName());
				sharingIntent.putExtra(android.content.Intent.EXTRA_STREAM,shareBody);
				startActivity(Intent.createChooser(sharingIntent, "Share via"));
			}catch (JSONException e){
				logger.warn(e.getMessage(), e);
				Crouton.showText(getActivity(), R.string.error_json, Style.ALERT);
			}
			return true;
			
		case R.id.uploadTrack:
			new UploadManager(((ECApplication) getActivity().getApplication())).uploadSingleTrack(track);
			return true;
		
		default:
			return super.onContextItemSelected(item);
		}
	}

	private void createDeleteDialog(final Track track) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setMessage(R.string.deleteRemoteTrackQuestion)
				.setPositiveButton(R.string.yes,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								User user = UserManager.instance().getUser();
								final String username = user.getUsername();
								final String token = user.getToken();
								RestClient.deleteRemoteTrack(username, token,
										track.getId(),
										new JsonHttpResponseHandler() {
											@Override
											protected void handleMessage(
													Message msg) {
												if (dbAdapterRemote
														.hasTrack(track.getId())) {
													dbAdapterRemote
															.deleteTrack(track
																	.getId());
													tracksList
															.remove(itemSelect);
													elvAdapter
															.notifyDataSetChanged();
													Crouton.showText(
															getActivity(),
															getString(R.string.remoteTrackDeleted),
															Style.INFO);
												}
											}
										});
							}
						})
				.setNegativeButton(R.string.no,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								// do nothing
							}
						});
		builder.create().show();
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		logger.info("Create view ListMeasurementsFragment");
		super.onViewCreated(view, savedInstanceState);
		elv.setGroupIndicator(getResources().getDrawable(
				R.drawable.list_indicator));
		elv.setChildDivider(getResources().getDrawable(
				android.R.color.transparent));
		
		//fetch local tracks
		this.tracksList = dbAdapterLocal.getAllTracks();
		logger.info("Number of tracks in the List: " + tracksList.size());
		if (elvAdapter == null)
			elvAdapter = new TracksListAdapter();
		elv.setAdapter(elvAdapter);
		elvAdapter.notifyDataSetChanged();

		//if logged in, download tracks from server
		if(UserManager.instance().isLoggedIn()){
			downloadTracks();
		}

	}
	
	/**
	 * Returns an StringArray of coordinates for the mpa
	 * 
	 * @param measurements
	 *            arraylist with all measurements
	 * @return string array with coordinates
	 */
	private String[] extractCoordinates(ArrayList<Measurement> measurements) {
		ArrayList<String> coordinates = new ArrayList<String>();

		for (Measurement measurement : measurements) {
			String lat = String.valueOf(measurement.getLatitude());
			String lon = String.valueOf(measurement.getLongitude());
			coordinates.add(lat);
			coordinates.add(lon);
		}
		return coordinates.toArray(new String[coordinates.size()]);
	}

	/**
	 * Download remote tracks from the server and include them in the track list
	 */
	private void downloadTracks() {
		
		isDownloading = true;
		
		User user = UserManager.instance().getUser();
		final String username = user.getUsername();
		final String token = user.getToken();
		RestClient.downloadTracks(username,token, new JsonHttpResponseHandler() {
			
			
			@Override
			public void onFailure(Throwable e, JSONObject errorResponse) {
				super.onFailure(e, errorResponse);
				logger.warn(e.getMessage(), e);
			}
			
			@Override
			public void onFailure(Throwable error, String content) {
				super.onFailure(error, content);
				logger.warn(content, error);
			}
			
			// Variable that holds the number of trackdl requests
			private int ct = 0;
			
			class AsyncOnSuccessTask extends AsyncTask<JSONObject, Void, Track>{
				
				@Override
				protected Track doInBackground(JSONObject... trackJson) {
					Track t;
					try {

						JSONObject trackProperties = trackJson[0].getJSONObject("properties");
						t = new Track(trackProperties.getString("id"));
						t.setDatabaseAdapter(dbAdapterRemote);
						String trackName = "unnamed Track #"+ct;
						try{
							trackName = trackProperties.getString("name");
						}catch (JSONException e){
							logger.warn(e.getMessage(), e);
						}
						t.setName(trackName);
						String description = "";
						try{
							description = trackProperties.getString("description");
						}catch (JSONException e){
							logger.warn(e.getMessage(), e);
						}
						t.setDescription(description);
						String manufacturer = "unknown";
						JSONObject sensorProperties = trackProperties.getJSONObject("sensor").getJSONObject("properties");
						try{
							manufacturer = sensorProperties.getString("manufacturer");
						}catch (JSONException e){
							logger.warn(e.getMessage(), e);
						}
						t.setCarManufacturer(manufacturer);
						String carModel = "unknown";
						try{
							carModel = sensorProperties.getString("model");
						}catch (JSONException e){
							logger.warn(e.getMessage(), e);
						}
						t.setCarModel(carModel);
						String sensorId = "undefined";
						try{
							sensorId = sensorProperties.getString("id");
						}catch (JSONException e) {
							logger.warn(e.getMessage(), e);
						}
						t.setSensorID(sensorId);
						String fuelType = "undefined";
						try{
							fuelType = sensorProperties.getString("fuelType");
						}catch (JSONException e) {
							logger.warn(e.getMessage(), e);
						}
						t.setFuelType(fuelType);
						//include server properties tracks created, modified?
						
						t.commitTrackToDatabase();
						//Log.i("track_id",t.getId()+" "+((DbAdapterRemote) dbAdapter).trackExistsInDatabase(t.getId())+" "+dbAdapter.getNumberOfStoredTracks());
						
						Measurement recycleMeasurement;
						
						for (int j = 0; j < trackJson[0].getJSONArray("features").length(); j++) {
							
							JSONObject measurementJsonObject = trackJson[0].getJSONArray("features").getJSONObject(j);
							recycleMeasurement = new Measurement(
									Float.valueOf(measurementJsonObject.getJSONObject("geometry").getJSONArray("coordinates").getString(1)),
									Float.valueOf(measurementJsonObject.getJSONObject("geometry").getJSONArray("coordinates").getString(0)));
							JSONObject properties = measurementJsonObject.getJSONObject("properties");
							JSONObject phenomenons = properties.getJSONObject("phenomenons");
							if (phenomenons.has("MAF")) {
								recycleMeasurement.setMaf((phenomenons.getJSONObject("MAF").getDouble("value")));
							}
							if (phenomenons.has("Calculated MAF")) {
								recycleMeasurement.setCalculatedMaf((phenomenons.getJSONObject("Calculated MAF").getDouble("value")));
							}
							recycleMeasurement.setSpeed((phenomenons.getJSONObject("Speed").getInt("value")));
							recycleMeasurement.setMeasurementTime(Utils.isoDateToLong((properties.getString("time"))));
							recycleMeasurement.setTrack(t);
							t.addMeasurement(recycleMeasurement);
						}

						return t;
					} catch (JSONException e) {
						logger.warn(e.getMessage(), e);
					} catch (NumberFormatException e) {
						logger.warn(e.getMessage(), e);
					} catch (ParseException e) {
						logger.warn(e.getMessage(), e);
					}
					return null;
				}

				@Override
				protected void onPostExecute(
						Track t) {
					super.onPostExecute(t);
					if(t != null){
						t.setLocalTrack(false);
						tracksList.add(t);
						elvAdapter.notifyDataSetChanged();
					}
					ct--;
					if (ct == 0) {
						progress.setVisibility(View.GONE);
					}
				}
			}
			
			
			private void afterOneTrack(){
				getView().findViewById(android.R.id.empty).setVisibility(View.GONE);
				ct--;
				if (ct == 0) {
					progress.setVisibility(View.GONE);
					//sort the tracks bubblesort ?
					Collections.sort(tracksList);
					elvAdapter.notifyDataSetChanged();
					isDownloading = false;
					if (((ECApplication) getActivity().getApplication()).getDbAdapterLocal().getAllTracks().size() > 0)
						delete_btn.setEnabled(true);
				}
				if (elv.getAdapter() == null || (elv.getAdapter() != null && !elv.getAdapter().equals(elvAdapter))) {
					elv.setAdapter(elvAdapter);
				}
			}

			@Override
			public void onStart() {
				super.onStart();
				if (tracksList == null)
					tracksList = new ArrayList<Track>();
				if (elvAdapter == null)
					elvAdapter = new TracksListAdapter();
				progress.setVisibility(View.VISIBLE);
			}

			@Override
			public void onSuccess(int httpStatus, JSONObject json) {
				super.onSuccess(httpStatus, json);

				try {
					JSONArray tracks = json.getJSONArray("tracks");
					if(tracks.length()==0) progress.setVisibility(View.GONE);
					ct = tracks.length();
					for (int i = 0; i < tracks.length(); i++) {

						// skip tracks already in the ArrayList
						for (Track t : tracksList) {
							if (t.getId().equals(((JSONObject) tracks.get(i)).getString("id"))) {
								afterOneTrack();
								continue;
							}
						}
						//AsyncTask to retrieve a Track from the database
						class RetrieveTrackfromDbAsyncTask extends AsyncTask<String, Void, Track>{
							
							@Override
							protected Track doInBackground(String... params) {
								return dbAdapterRemote.getTrack(params[0]);
							}
							
							protected void onPostExecute(Track result) {
								tracksList.add(result);
								elvAdapter.notifyDataSetChanged();
								afterOneTrack();
							}
							
						}
						if (((DbAdapterRemote) dbAdapterRemote).trackExistsInDatabase(((JSONObject) tracks.get(i)).getString("id"))) {
							// if the track already exists in the db, skip and load from db.
							new RetrieveTrackfromDbAsyncTask().execute(((JSONObject) tracks.get(i)).getString("id"));
							continue;
						}

						// else
						// download the track
						RestClient.downloadTrack(username, token, ((JSONObject) tracks.get(i)).getString("id"),
								new JsonHttpResponseHandler() {
									
									@Override
									public void onFinish() {
										super.onFinish();
										if (elv.getAdapter() == null || (elv.getAdapter() != null && !elv.getAdapter().equals(elvAdapter))) {
											elv.setAdapter(elvAdapter);
										}
										elvAdapter.notifyDataSetChanged();
									}

									@Override
									public void onSuccess(JSONObject trackJson) {
										super.onSuccess(trackJson);

										// start the AsyncTask to handle the downloaded trackjson
										new AsyncOnSuccessTask().execute(trackJson);

									}

									public void onFailure(Throwable arg0,
											String arg1) {
										logger.warn(arg1,arg0);
									};
								});

					}
				} catch (JSONException e) {
					logger.warn(e.getMessage(), e);
				}
			}
		});
		
		
		

	}

	private class TracksListAdapter extends BaseExpandableListAdapter {

		@Override
		public int getGroupCount() {
			return tracksList.size();
		}

		@Override
		public int getChildrenCount(int i) {
			return 1;
		}

		@Override
		public Object getGroup(int i) {
			return tracksList.get(i);
		}

		@Override
		public Object getChild(int i, int i1) {
			return tracksList.get(i);
		}

		@Override
		public long getGroupId(int i) {
			return i;
		}

		@Override
		public long getChildId(int i, int i1) {
			return i;
		}

		@Override
		public boolean hasStableIds() {
			return true;
		}

		@Override
		public View getGroupView(int i, boolean b, View view,
				ViewGroup viewGroup) {
			if (view == null || view.getId() != 10000000 + i) {
				Track currTrack = (Track) getGroup(i);
				View groupRow = ViewGroup.inflate(getActivity(), R.layout.list_tracks_group_layout, null);
				TextView textView = (TextView) groupRow.findViewById(R.id.track_name_textview);
				textView.setText((currTrack.isLocalTrack() ? "L" : "R")+" "+currTrack.getName());
				groupRow.setId(10000000 + i);
				TypefaceEC.applyCustomFont((ViewGroup) groupRow,
						TypefaceEC.Newscycle(getActivity()));
				return groupRow;
			}
			return view;
		}

		@Override
		public View getChildView(int i, int i1, boolean b, View view, ViewGroup viewGroup) {
			logger.info("Selects a track");
			//if (view == null || view.getId() != 10000100 + i + i1) {
				Track currTrack = (Track) getChild(i, i1);
				View row = ViewGroup.inflate(getActivity(),
						R.layout.list_tracks_item_layout, null);
				TextView start = (TextView) row
						.findViewById(R.id.track_details_start_textview);
				TextView end = (TextView) row
						.findViewById(R.id.track_details_end_textview);
				TextView length = (TextView) row
						.findViewById(R.id.track_details_length_textview);
				TextView car = (TextView) row
						.findViewById(R.id.track_details_car_textview);
				TextView duration = (TextView) row
						.findViewById(R.id.track_details_duration_textview);
				TextView co2 = (TextView) row
						.findViewById(R.id.track_details_co2_textview);
				TextView consumptionTextView = (TextView) row
						.findViewById(R.id.track_details_consumption_textview);
				TextView description = (TextView) row.findViewById(R.id.track_details_description_textview);

				try {
					DateFormat sdf = DateFormat.getDateTimeInstance();
					DecimalFormat twoDForm = new DecimalFormat("#.##");
					DateFormat dfDuration = new SimpleDateFormat("HH:mm:ss");
					dfDuration.setTimeZone(TimeZone.getTimeZone("UTC"));
					start.setText(sdf.format(currTrack.getStartTime()) + "");
					end.setText(sdf.format(currTrack.getEndTime()) + "");
					Date durationMillis = new Date(currTrack.getDurationInMillis());
					duration.setText(dfDuration.format(durationMillis) + "");
					if (!application.isImperialUnits()) {
						length.setText(twoDForm.format(currTrack.getLengthOfTrack()) + " km");
					} else {
						length.setText(twoDForm.format(currTrack.getLengthOfTrack()/1.6) + " miles");
					}
					car.setText(currTrack.getCarManufacturer() + " "
							+ currTrack.getCarModel());
					description.setText(currTrack.getDescription());
					double consumption = currTrack.getFuelConsumptionPerHour();
					double literOn100km = currTrack.getLiterPerHundredKm();
					co2.setText(twoDForm.format(currTrack.getGramsPerKm()) + "g/km");
					consumptionTextView.setText(twoDForm.format(consumption) + " l/h (" + twoDForm.format(literOn100km) + " l/100 km)");
				} catch (Exception e) {
					logger.warn(e.getMessage(), e);
				}

				row.setId(10000100 + i + i1);
				TypefaceEC.applyCustomFont((ViewGroup) row,
						TypefaceEC.Newscycle(getActivity()));
				return row;
			//}
			//return view;
		}

		@Override
		public boolean isChildSelectable(int i, int i1) {
			return false;
		}

	}

}
