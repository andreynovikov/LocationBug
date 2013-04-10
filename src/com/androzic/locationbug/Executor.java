package com.androzic.locationbug;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class Executor extends BroadcastReceiver
{
	@Override
	public void onReceive(Context context, Intent intent)
	{
		String action = intent.getAction();
		if (action.equals(Intent.ACTION_BOOT_COMPLETED))
		{
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
			boolean enabled = prefs.getBoolean(context.getString(R.string.pref_enabled), false);
			boolean startAtBoot = prefs.getBoolean(context.getString(R.string.pref_startatboot), context.getResources().getBoolean(R.bool.def_startatboot));
			enabled &= startAtBoot;
			if (enabled)
				context.startService(new Intent(context, SharingService.class));
		}
	}
}
