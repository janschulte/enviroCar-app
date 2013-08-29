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

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.protocol.HTTP;
import org.envirocar.app.R;
import org.envirocar.app.application.ECApplication;
import org.envirocar.app.application.User;
import org.envirocar.app.application.UserManager;
import org.envirocar.app.logging.Logger;
import org.envirocar.app.network.HTTPClient;
import org.envirocar.app.views.TypefaceEC;
import org.json.JSONException;
import org.json.JSONObject;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragment;

import de.keyboardsurfer.android.widget.crouton.Crouton;
import de.keyboardsurfer.android.widget.crouton.Style;

/**
 * Activity which displays a register screen to the user, offering registration
 * as well.
 */
public class RegisterFragment extends SherlockFragment {
	
	private static final Logger logger = Logger.getLogger(RegisterFragment.class);

	private static final int ERROR_GENERAL = 1;
	private static final int ERROR_NET = 2;

	/**
	 * Keep track of the register task to ensure we can cancel it if requested.
	 */
	private UserRegisterTask mAuthTask = null;

	// Values for email and password at the time of the register attempt.
	private String mUsername;
	private String mEmail;
	private String mPassword;
	private String mPasswordConfirm;

	// UI references.
	private EditText mUsernameView;
	private EditText mEmailView;
	private EditText mPasswordView;
	private EditText mPasswordConfirmView;
	private View mRegisterFormView;
	private View mRegisterStatusView;
	private TextView mRegisterStatusMessageView;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.register_layout, null);

		mUsernameView = (EditText) view.findViewById(R.id.register_username);

		mEmailView = (EditText) view.findViewById(R.id.register_email);

		mPasswordView = (EditText) view.findViewById(R.id.register_password);
		mPasswordConfirmView = (EditText) view
				.findViewById(R.id.register_password_second);
		mPasswordConfirmView
				.setOnEditorActionListener(new TextView.OnEditorActionListener() {
					@Override
					public boolean onEditorAction(TextView textView, int id,
							KeyEvent keyEvent) {
						if (id == R.id.register || id == EditorInfo.IME_NULL) {
							attemptRegister();
							return true;
						}
						return false;
					}
				});
		mRegisterFormView = view.findViewById(R.id.register_form);
		mRegisterStatusView = view.findViewById(R.id.register_status);
		mRegisterStatusMessageView = (TextView) view
				.findViewById(R.id.register_status_message);

		view.findViewById(R.id.register_button).setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						attemptRegister();
					}
				});
		return view;
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		TypefaceEC.applyCustomFont((ViewGroup) view,
				TypefaceEC.Raleway(getActivity()));
		mUsernameView.requestFocus();
	}

	/**
	 * Attempts to sign in or register the account specified by the register
	 * form. If there are form errors (invalid email, missing fields, etc.), the
	 * errors are presented and no actual register attempt is made.
	 */
	public void attemptRegister() {
		// Reset errors.
		mUsernameView.setError(null);
		mEmailView.setError(null);
		mPasswordView.setError(null);
		mPasswordConfirmView.setError(null);

		if (mAuthTask != null) {
			return;
		}

		// Store values at the time of the register attempt.
		mUsername = mUsernameView.getText().toString();
		mEmail = mEmailView.getText().toString();
		mPassword = mPasswordView.getText().toString();
		mPasswordConfirm = mPasswordConfirmView.getText().toString();

		boolean cancel = false;
		View focusView = null;

		// TODO fiddle around with order of checks

		// Check for a valid password.
		if (TextUtils.isEmpty(mPassword)) {
			mPasswordView.setError(getString(R.string.error_field_required));
			focusView = mPasswordView;
			cancel = true;
		} else if (mPassword.length() < 6) {
			mPasswordView.setError(getString(R.string.error_invalid_password));
			focusView = mPasswordView;
			cancel = true;
		}

		// check if the password confirm is empty
		if (TextUtils.isEmpty(mPasswordConfirm)) {
			mPasswordConfirmView
					.setError(getString(R.string.error_field_required));
			focusView = mPasswordConfirmView;
			cancel = true;
		}

		// Check for a valid email address.
		if (TextUtils.isEmpty(mEmail)) {
			mEmailView.setError(getString(R.string.error_field_required));
			focusView = mEmailView;
			cancel = true;
		} else if (!mEmail.matches("^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$")){
			mEmailView.setError(getString(R.string.error_invalid_email));
			focusView = mEmailView;
			cancel = true;
		}

		// check for valid username
		if (TextUtils.isEmpty(mUsername)) {
			mUsernameView.setError(getString(R.string.error_field_required));
			focusView = mUsernameView;
			cancel = true;
		} else if (mUsername.length() < 6) {
			mUsernameView.setError(getString(R.string.error_invalid_username));
			focusView = mUsernameView;
			cancel = true;
		}

		// check if passwords match
		if (!mPassword.equals(mPasswordConfirm)) {
			mPasswordConfirmView
					.setError(getString(R.string.error_passwords_not_matching));
			focusView = mPasswordConfirmView;
			cancel = true;
		}

		if (cancel) {
			// There was an error; don't attempt register and focus the first
			// form field with an error.
			focusView.requestFocus();
		} else {
			//hide the keyboard
			InputMethodManager imm = (InputMethodManager)getActivity().getSystemService(
				      Context.INPUT_METHOD_SERVICE);
				imm.hideSoftInputFromWindow(mPasswordView.getWindowToken(), 0);

			// Show a progress spinner, and kick off a background task to
			// perform the user register attempt.
			mRegisterStatusMessageView
					.setText(R.string.register_progress_signing_in);
			showProgress(true);
			mAuthTask = new UserRegisterTask();
			mAuthTask.execute((Void) null);
		}
	}

	/**
	 * Shows the progress UI and hides the register form.
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
	private void showProgress(final boolean show) {
		// On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
		// for very easy animations. If available, use these APIs to fade-in
		// the progress spinner.
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
			int shortAnimTime = getResources().getInteger(
					android.R.integer.config_shortAnimTime);

			mRegisterStatusView.setVisibility(View.VISIBLE);
			mRegisterStatusView.animate().setDuration(shortAnimTime)
					.alpha(show ? 1 : 0)
					.setListener(new AnimatorListenerAdapter() {
						@Override
						public void onAnimationEnd(Animator animation) {
							mRegisterStatusView
									.setVisibility(show ? View.VISIBLE
											: View.GONE);
						}
					});

			mRegisterFormView.setVisibility(View.VISIBLE);
			mRegisterFormView.animate().setDuration(shortAnimTime)
					.alpha(show ? 0 : 1)
					.setListener(new AnimatorListenerAdapter() {
						@Override
						public void onAnimationEnd(Animator animation) {
							mRegisterFormView.setVisibility(show ? View.GONE
									: View.VISIBLE);
						}
					});
		} else {
			// The ViewPropertyAnimator APIs are not available, so simply show
			// and hide the relevant UI components.
			mRegisterStatusView.setVisibility(show ? View.VISIBLE : View.GONE);
			mRegisterFormView.setVisibility(show ? View.GONE : View.VISIBLE);
		}
	}

	/**
	 * Represents an asynchronous register/registration task used to
	 * authenticate the user.
	 */
	public class UserRegisterTask extends AsyncTask<Void, Void, Integer> {
		@Override
		protected Integer doInBackground(Void... params) {

			return createUser(mUsername, mPassword, mEmail);

		}

		@Override
		protected void onPostExecute(final Integer httpStatus) {
			mAuthTask = null;
			showProgress(false);

			if (httpStatus == HttpStatus.SC_CREATED) {
				Crouton.makeText(getActivity(), getResources().getString(R.string.welcome_message)+mUsername, Style.CONFIRM).show();
				UserManager.instance().setUser(new User(mUsername, mPassword));
				
				//open the Garage
	        	MyGarage garageFragment = new MyGarage();
	            getActivity().getSupportFragmentManager().beginTransaction().replace(R.id.content_frame, garageFragment).commit();
			} else if (httpStatus == HttpStatus.SC_FORBIDDEN) {
                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());
                alertDialogBuilder.setTitle("Sorry");
                alertDialogBuilder
                        .setMessage(R.string.error_email_not_in_beta)
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog,
                                            int id) {
                                        dialog.cancel();
                                    }
                                });
                AlertDialog alertDialog = alertDialogBuilder.create();
                alertDialog.show();
			} else if (httpStatus == HttpStatus.SC_CONFLICT) {
				// TODO look out for server changes..
				mUsernameView.setError(getString(R.string.error_username_already_in_use));
				mEmailView.setError(getString(R.string.error_email_already_in_use));
				mUsernameView.requestFocus();				
			} else if (httpStatus==ERROR_NET){
				mUsernameView.setError(getString(R.string.error_host_not_found));
				mUsernameView.requestFocus();
			}
		}

		@Override
		protected void onCancelled() {
			mAuthTask = null;
			showProgress(false);
		}
	}

	/*
	 * Use this method to sign up a new user
	 */
	public int createUser(String user, String token, String mail) {

		JSONObject requestJson = new JSONObject();
		try {
			requestJson.put("name", user);
			requestJson.put("token", token);
			requestJson.put("mail", mail);
		} catch (JSONException e) {
			logger.warn(e.getMessage(), e);
		}


		try {
			HttpPost postRequest = new HttpPost(
					ECApplication.BASE_URL+"/users");
					

			StringEntity input = new StringEntity(requestJson.toString(),
					HTTP.UTF_8);
			input.setContentType("application/json");

			postRequest.setEntity(input);
			return HTTPClient.execute(postRequest).getStatusLine()
					.getStatusCode();

		} catch (UnsupportedEncodingException e) {
			// Shouldn't occur hopefully..
			logger.warn(e.getMessage(), e);
			return ERROR_GENERAL;
		} catch (IOException e) {
			logger.warn(e.getMessage(), e);
			// probably something with the Internet..
			return ERROR_NET;
		}
	}

}
