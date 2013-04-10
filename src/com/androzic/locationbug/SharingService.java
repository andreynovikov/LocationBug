package com.androzic.locationbug;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.location.GpsStatus.Listener;
import android.location.GpsStatus.NmeaListener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

public class SharingService extends Service implements OnSharedPreferenceChangeListener, LocationListener, NmeaListener, Listener
{
	private static final String TAG = "LocationBug";
	private static final int NOTIFICATION_ID = 24165;

	private Notification notification;
	private PendingIntent contentIntent;

	private ThreadPoolExecutor executorThread = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(1));
	private Timer timer;

	private LocationManager locationManager = null;
	private Location currentLocation = new Location("unknown");
	private float nmeaGeoidHeight = Float.NaN;
	private String session;
	private String user;
	private int updateInterval = 10000; // 10 seconds (default)
	private boolean useNetwork = true;

	@Override
	public void onCreate()
	{
		super.onCreate();

		// Prepare notification components
		notification = new Notification();
		contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, Preferences.class), 0);

		// Inintialize preferences
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_sharing_session));
		onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_sharing_user));
		onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_sharing_updateinterval));
		onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_loc_usenetwork));
		sharedPreferences.registerOnSharedPreferenceChangeListener(this);

		// Connect to location service
		prepareNormalNotification();
		connect();
		startForeground(NOTIFICATION_ID, notification);
		startTimer();

		Log.i(TAG, "Service started");
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();

		// Disconnect from location service
		disconnect();
		stopForeground(true);
		stopTimer();

		notification = null;
		contentIntent = null;

		Log.i(TAG, "Service stopped");
	}

	protected void sendLocation()
	{
		if ("unknown".equals(currentLocation.getProvider()))
			return;

		notification.icon = R.drawable.ic_stat_sharing_out;
		final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		nm.notify(NOTIFICATION_ID, notification);

		executorThread.getQueue().poll();
		executorThread.execute(new Runnable() {
			public void run()
			{
				URI URL;
				try
				{
					String query = null;
					synchronized (currentLocation)
					{
						query = "silent=1;" +
								"session=" + URLEncoder.encode(session) +
								";user=" + URLEncoder.encode(user) +
								";lat=" + currentLocation.getLatitude() +
								";lon=" + currentLocation.getLongitude() +
								";track=" + currentLocation.getBearing() +
								";speed=" + currentLocation.getSpeed() +
								";accuracy=" + currentLocation.getAccuracy() +
								";ftime=" + currentLocation.getTime();
					}
					URL = new URI("http", null, "androzic.com", 80, "/cgi-bin/loc.cgi", query, null);

					HttpClient httpclient = new DefaultHttpClient();
					httpclient.execute(new HttpGet(URL));
				}
				catch (URISyntaxException e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				catch (ClientProtocolException e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				catch (IOException e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				if (notification != null)
				{
					notification.icon = R.drawable.ic_stat_sharing;
					nm.notify(NOTIFICATION_ID, notification);
				}
			}
		});
	}

	private void connect()
	{
		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		if (locationManager != null)
		{
			if (useNetwork)
			{
				try
				{
					locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, updateInterval, 0, this);
					Log.d(TAG, "Network provider set");
				}
				catch (IllegalArgumentException e)
				{
					Log.d(TAG, "Cannot set network provider, likely no mobile service on device");
				}
			}
			try
			{
				long minTime = updateInterval > 120000 ? updateInterval : 0;
				locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTime, 0, this);
				locationManager.addNmeaListener(this);
				Log.d(TAG, "Gps provider set");
			}
			catch (IllegalArgumentException e)
			{
				Log.d(TAG, "Cannot set gps provider, likely no gps on device");
			}
		}
	}

	private void disconnect()
	{
		if (locationManager != null)
		{
			locationManager.removeNmeaListener(this);
			locationManager.removeUpdates(this);
			locationManager.removeGpsStatusListener(this);
			locationManager = null;
		}
	}

	private void startTimer()
	{
		if (timer != null)
			stopTimer();

		timer = new Timer();
		TimerTask updateTask = new UpdatelocationTask();
		timer.scheduleAtFixedRate(updateTask, 0, updateInterval);
	}

	private void stopTimer()
	{
		timer.cancel();
		timer = null;
	}

	private void prepareNormalNotification()
	{
		notification.when = 0;
		notification.icon = R.drawable.ic_stat_sharing;
		notification.setLatestEventInfo(getApplicationContext(), getText(R.string.app_name), getText(R.string.notif_sharing), contentIntent);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		if (getString(R.string.pref_sharing_session).equals(key))
		{
			session = sharedPreferences.getString(key, "");
		}
		else if (getString(R.string.pref_sharing_user).equals(key))
		{
			user = sharedPreferences.getString(key, "");
		}
		else if (getString(R.string.pref_sharing_updateinterval).equals(key))
		{
			updateInterval = sharedPreferences.getInt(key, getResources().getInteger(R.integer.def_updateinterval)) * 1000;
			if (timer != null)
			{
				stopTimer();
				startTimer();
			}
		}
		else if (getString(R.string.pref_loc_usenetwork).equals(key))
		{
			useNetwork = sharedPreferences.getBoolean(key, getResources().getBoolean(R.bool.def_loc_usenetwork));
			disconnect();
			connect();
		}

		if ((session != null && session.trim().equals("")) || (user != null && user.trim().equals("")))
			stopSelf();
	}

	private final IBinder binder = new LocalBinder();

	public class LocalBinder extends Binder
	{
		public SharingService getService()
		{
			return SharingService.this;
		}
	}

	@Override
	public IBinder onBind(Intent intent)
	{
		return binder;
	}

	class UpdatelocationTask extends TimerTask
	{
		public void run()
		{
			sendLocation();
		}
	}

	@Override
	public void onNmeaReceived(long timestamp, String nmea)
	{
		if (nmea.indexOf('\n') == 0)
			return;
		if (nmea.indexOf('\n') > 0)
		{
			nmea = nmea.substring(0, nmea.indexOf('\n') - 1);
		}
		int len = nmea.length();
		if (len < 9)
		{
			return;
		}
		if (nmea.charAt(len - 3) == '*')
		{
			nmea = nmea.substring(0, len - 3);
		}
		String[] tokens = nmea.split(",");
		String sentenceId = tokens[0].length() > 5 ? tokens[0].substring(3, 6) : "";

		try
		{
			if (sentenceId.equals("GGA") && tokens.length > 11)
			{
				String heightOfGeoid = tokens[11];
				if (!"".equals(heightOfGeoid))
					nmeaGeoidHeight = Float.parseFloat(heightOfGeoid);
			}
		}
		catch (NumberFormatException e)
		{
			Log.e(TAG, "NFE", e);
		}
		catch (ArrayIndexOutOfBoundsException e)
		{
			Log.e(TAG, "AIOOBE", e);
		}
	}

	@Override
	public void onLocationChanged(Location location)
	{
		Log.e(TAG, "Location arrived from " + location.getProvider());
		if (!Float.isNaN(nmeaGeoidHeight))
			location.setAltitude(location.getAltitude() + nmeaGeoidHeight);
		synchronized (currentLocation)
		{
			currentLocation.set(location);
		}
	}

	@Override
	public void onProviderDisabled(String provider)
	{
	}

	@Override
	public void onProviderEnabled(String provider)
	{
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras)
	{
	}

	@Override
	public void onGpsStatusChanged(int event)
	{
		// TODO Send lost location status
	}
}
