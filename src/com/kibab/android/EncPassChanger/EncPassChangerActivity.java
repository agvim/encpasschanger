package com.kibab.android.EncPassChanger;

import java.io.IOException;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;


import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

public class EncPassChangerActivity extends Activity {

	private EditText output;
	private ProgressBar progress;
	private CheckBox isVerbose;
	
	DevicePolicyManager mDPM;
	ComponentName mAdminName;
	int newattempts = 0;
	static final int REQUEST_ENABLE = 15;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		output = (EditText) findViewById(R.id.output);
		progress = (ProgressBar) findViewById(R.id.progress);
		isVerbose = (CheckBox) findViewById(R.id.isDebug);
		
		mDPM = (DevicePolicyManager)getSystemService(Context.DEVICE_POLICY_SERVICE);
		mAdminName = new ComponentName(this, Rebooter.class);
	}

	/* 'Go' button handler */
	public void beginWork(View view) {
		
		hideSoftKeyboard();
		output.setText("");

		EditText oldPassInp = (EditText) findViewById(R.id.oldpass);
		EditText newPassInp = (EditText) findViewById(R.id.newpass);
		EditText newPassInp2 = (EditText) findViewById(R.id.newpass_again);
		
		String oldPass = oldPassInp.getText().toString().trim();
		String newPass = newPassInp.getText().toString().trim();

		if (! newPass.equals(newPassInp2.getText().toString().trim())) {
			output.setText(R.string.newpass_not_the_same);
			return;			
		}

		if (newPass.length() < 1) {
			output.setText(R.string.pass_empty);
			return;
		}
		ChangePasswordTask tsk = new ChangePasswordTask();
		ChangePassParams params = new ChangePassParams(oldPass, newPass, this);
		progress.setVisibility(View.VISIBLE);
		tsk.execute(params);
	}

	
	/**
	 * Used to deliver progress from background thread.
	 * Called in UI thread.
	 * @param RID If not 0, this is considered a final update,
	 * the progress bar will be shut down and localized message displayed.
	 * @param desc This is a verbose-mode message. Displayed only if "Verbose"
	 * flag is set in UI.
	 */
	public void updateResultDisplay(int RID, String desc) {
		String msg = new String();

		if (RID > 0)
			msg = getString(RID);
		if (desc.length() > 0 && isVerbose.isChecked())
			msg += (msg.length() > 0 ? ": " : "") + desc;
		
		if (msg.length() > 0)
			output.append(msg + "\n");
		if(RID > 0)
			progress.setVisibility(View.INVISIBLE);
	}
	
	/**
	 * Force soft keyboard to hide away
	 */
	private void hideSoftKeyboard() {
	    InputMethodManager inputMethodManager =
	    		(InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
	    inputMethodManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
	}
	
	/**
	 * PIN go handler
	 */
	public void beginRebooter(View view){
		String attemptsstring = ((EditText) findViewById(R.id.allowed_fails)).getText().toString();
		if (attemptsstring.length() < 1) {
			output.setText("");
			if (mDPM.isAdminActive(mAdminName)) {
				output.append("Disabled Rebooter administration\n" );
				mDPM.removeActiveAdmin(mAdminName);
			}
		}
		else{
			output.setText("");
			newattempts = Integer.parseInt(attemptsstring);
			if (newattempts <= 0){
				if (mDPM.isAdminActive(mAdminName)) {
					output.append("Disabled Rebooter administration\n");
					mDPM.removeActiveAdmin(mAdminName);
				}	
			}
			else{
				// try to get administration rights
				Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
				intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, mAdminName);
				intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Device administration is required to be able to reboot on unlock failures");
				startActivityForResult(intent, REQUEST_ENABLE);
			}
		}
	}
	
	/**
	 * Checks whether administration has been accepted for Rebooter
	 */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	if (REQUEST_ENABLE == requestCode){
	    	if (resultCode == Activity.RESULT_OK) {
	    		// Has become the device administrator.
	    		Rebooter.maxattempts = newattempts;
	    		output.setText("Got admin and configured the unlock attempts to " + newattempts + "\n");
	    	} else {
	    		//Canceled or failed.
	    		output.setText("Failed to get admin, unable to provide reset on unlock failures\n");
	    	}
    	}
    }
	
	public static class Rebooter extends DeviceAdminReceiver {
		public static int maxattempts;
		/*
		@Override
		public void onDisabled(Context context, Intent intent){
			//Called prior to the administrator being disabled, as a result of receiving ACTION_DEVICE_ADMIN_DISABLED.
		}
		@Override
		public void onEnabled(Context context, Intent intent){
			//Called after the administrator is first enabled, as a result of receiving ACTION_DEVICE_ADMIN_ENABLED.
		}
		@Override
		public void onPasswordChanged(Context context, Intent intent){
			//Called after the user has changed their password, as a result of receiving ACTION_PASSWORD_CHANGED.
		}
		@Override
		public void onPasswordExpiring(Context context, Intent intent){
			//Called periodically when the password is about to expire or has expired.
		}
		*/
		@Override
		public void onPasswordFailed(Context context, Intent intent){
			//Called after the user has failed at entering their current password, as a result of receiving ACTION_PASSWORD_FAILED.
	        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(
	                Context.DEVICE_POLICY_SERVICE);
			int currentattempts = dpm.getCurrentFailedPasswordAttempts();
			if (currentattempts >= maxattempts){
				try {
					Runtime.getRuntime().exec("su -c reboot");
				} catch (IOException e) {
					//cry!
				}
			}
		}
		/*
		@Override
		public void onPasswordSucceeded(Context context, Intent intent){
			//Called after the user has succeeded at entering their current password, as a result of receiving ACTION_PASSWORD_SUCCEEDED.
		}
		@Override
		public void onReceive(Context context, Intent intent){
			//Intercept standard device administrator broadcasts.
		}
		*/
	}
}