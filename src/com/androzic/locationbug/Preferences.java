package com.androzic.locationbug;

import com.androzic.ui.SeekbarPreference;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;

public class Preferences extends PreferenceActivity implements OnSharedPreferenceChangeListener
{
	private static final String TAG = "Preferences";

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		PreferenceManager.setDefaultValues(this, R.xml.preferences, true);
		addPreferencesFromResource(R.xml.preferences);
	}

	@Override
	public void onResume()
	{
		super.onResume();
		initSummaries(getPreferenceScreen());
		getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		updateStates(prefs);
		boolean enabled = prefs.getBoolean(getString(R.string.pref_enabled), false);
		setSharingEnabled(enabled);
	}

	@Override
	public void onPause()
	{
		super.onPause();
		getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);    
	}

	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		Preference pref = findPreference(key);
		setPrefSummary(pref);
		if (getString(R.string.pref_enabled).equals(key))
		{
			boolean enabled = sharedPreferences.getBoolean(key, false);
			setSharingEnabled(enabled);
		}
		updateStates(sharedPreferences);
	}

    private void setPrefSummary(Preference pref)
	{
        if (pref instanceof ListPreference)
        {
	        CharSequence summary = ((ListPreference) pref).getEntry();
	        if (summary != null)
	        {
	        	pref.setSummary(summary);
	        }
        }
        else if (pref instanceof EditTextPreference)
        {
	        CharSequence summary = ((EditTextPreference) pref).getText();
	        if (summary != null)
	        {
	        	pref.setSummary(summary);
	        }
        }
        else if (pref instanceof SeekbarPreference)
        {
	        CharSequence summary = ((SeekbarPreference) pref).getText();
	        if (summary != null)
	        {
	        	pref.setSummary(summary);
	        }
        }
	}

	private void initSummaries(PreferenceGroup preference)
    {
    	for (int i=preference.getPreferenceCount()-1; i>=0; i--)
    	{
    		Preference pref = preference.getPreference(i);
           	setPrefSummary(pref);

    		if (pref instanceof PreferenceGroup || pref instanceof PreferenceScreen)
            {
    			initSummaries((PreferenceGroup) pref);
            }
    	}
    }

	private void updateStates(SharedPreferences sharedPreferences)
	{
		String session = sharedPreferences.getString(getString(R.string.pref_sharing_session), "");
		String user = sharedPreferences.getString(getString(R.string.pref_sharing_user), "");
		boolean invalid = (session == null || session.trim().equals("") || user == null || user.trim().equals(""));
		findPreference(getString(R.string.pref_enabled)).setEnabled(!invalid);
		boolean enabled = sharedPreferences.getBoolean(getString(R.string.pref_enabled), false);
		findPreference(getString(R.string.pref_sharing_session)).setEnabled(!enabled);
		findPreference(getString(R.string.pref_sharing_user)).setEnabled(!enabled);
		findPreference(getString(R.string.pref_sharing_updateinterval)).setEnabled(!enabled);
	}

	@SuppressLint("NewApi")
	private void setSharingEnabled(boolean enabled)
	{
		SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
		editor.putBoolean(getString(R.string.pref_enabled), enabled);
		editor.commit();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
			((SwitchPreference) findPreference(getString(R.string.pref_enabled))).setChecked(enabled);
		else
			((CheckBoxPreference) findPreference(getString(R.string.pref_enabled))).setChecked(enabled);
		if (enabled && !isServiceRunning())
			startService(new Intent(this, SharingService.class));
		else if (!enabled && isServiceRunning())
			stopService(new Intent(this, SharingService.class));
	}

	public boolean isServiceRunning()
	{
		ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
		// Actually it returns not only running services, so extra check is required
		for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE))
		{
			if (service.service.getClassName().equals(SharingService.class.getCanonicalName()) && service.pid > 0)
				return true;
		}
		return false;
	}
}
