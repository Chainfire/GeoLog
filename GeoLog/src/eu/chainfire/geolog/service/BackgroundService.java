/*
 * Copyright (C) 2013 Jorrit "Chainfire" Jongma
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.chainfire.geolog.service;

import java.util.Locale;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient.ConnectionCallbacks;
import com.google.android.gms.common.GooglePlayServicesClient.OnConnectionFailedListener;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;

import eu.chainfire.geolog.Debug;
import eu.chainfire.geolog.R;
import eu.chainfire.geolog.data.Database;
import eu.chainfire.geolog.data.Database.Accuracy;
import eu.chainfire.geolog.data.Database.Activity;
import eu.chainfire.geolog.data.Database.Profile.Type;
import eu.chainfire.geolog.ui.MainActivity;
import eu.chainfire.geolog.ui.SettingsFragment;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.location.Location;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;

public class BackgroundService extends Service {
	public static void startService(Context context) {
		context.startService(new Intent(context.getApplicationContext(), BackgroundService.class));		
	}
	
	private static String EXTRA_ALARM_CALLBACK = "eu.chainfire.geolog.EXTRA.ALARM_CALLBACK";
	
	private volatile ServiceThread thread = null;
	private volatile PowerManager.WakeLock wakelock = null;

	private volatile NotificationManager notificationManager;
	private volatile PendingIntent notificationIntent;
	private volatile Notification.Builder notificationBuilder;
	
	@SuppressLint("NewApi")
	@Override
	public void onCreate() {
		super.onCreate();
		Debug.log("Service created");
		
		if (thread == null) {
			Debug.log("Launching thread");
			thread = new ServiceThread();
			thread.setContext(getApplicationContext());
			thread.start();
		}
		
		PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
		wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GeoLog Wakelock");
		
		Intent i = new Intent();
		i.setAction(Intent.ACTION_MAIN);
		i.addCategory(Intent.CATEGORY_LAUNCHER);
		i.setClass(this, MainActivity.class);
		i.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_NEW_TASK);		
		notificationIntent = PendingIntent.getActivity(this, 0, i, 0);

		notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		notificationBuilder = (new Notification.Builder(this)).
			setSmallIcon(R.drawable.ic_stat_service).
			setContentIntent(notificationIntent).
			setWhen(System.currentTimeMillis()).
			setAutoCancel(false).
			setOngoing(true).
			setContentTitle(getString(R.string.service_title)).
			setContentText(getString(R.string.service_waiting));
		
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
			notificationBuilder.setShowWhen(false);
		}
		
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
			/* quick turn off, maybe ? if added, make sure to add a button to preferences to disable these buttons
			notificationBuilder.
				setPriority(Notification.PRIORITY_MAX).
				addAction(0, "A", notificationIntent).
				addAction(0, "B", notificationIntent).
				addAction(0, "C", notificationIntent);
			*/
		}
		
		updateNotification();		
	}
	
	@SuppressWarnings("deprecation")
	@SuppressLint("NewApi")
	private void updateNotification() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
			notificationManager.notify(1, notificationBuilder.build()); 					
		} else {
			notificationManager.notify(1, notificationBuilder.getNotification()); 								
		}
	}
	
	@Override
	public void onDestroy() {		
		Debug.log("Stopping thread");
		thread.signalStop();
		try { thread.join(); } catch (Exception e) { }
		thread = null;
		
		((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancelAll();
		
		Debug.log("Service destroyed");
		super.onDestroy();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		thread.processIntent(intent);
		return START_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	private class ServiceThread extends Thread {
		private static final int FLAG_SETUP = 1;
		private static final int FLAG_ACTIVITY_UPDATE = 2;
		private static final int FLAG_LOCATION_UPDATE = 4;
		private static final int FLAG_PROFILE = 8;

		private volatile Context context = null;
		private volatile Handler handler = null;
		
		private volatile AlarmManager alarm = null;
		private volatile PendingIntent alarmCallback = null;
		
		private volatile ActivityRecognitionClient activityClient = null;
		private volatile boolean activityConnected = false;
		private volatile PendingIntent activityIntent = null;
		
		private volatile LocationClient locationClient = null;
		private volatile boolean locationConnected = false;
		
		private volatile Database.Helper databaseHelper = null;
		
		private volatile boolean metric = true;
				
		private volatile Database.Activity lastActivity = Database.Activity.UNKNOWN;
		private volatile int lastConfidence = 0;
		private volatile Location lastLocLoc = null; 
		private volatile Database.Location lastLocation = null;
		private volatile long lastLocationDuplicates = 0;
		private volatile long lastNonUnknown = 0;
		
		private volatile Database.Accuracy lastLocationAccuracy = Database.Accuracy.NONE;
		private volatile int lastLocationInterval = -1;
		private volatile int lastActivityInterval = -1;
		private volatile int lastBatteryLevel = 0;
		private volatile boolean isSegmentStart = true;
		private volatile long lastProfileUpdate = SystemClock.elapsedRealtime();
		
		private volatile long scheduledReduceAccuracyTime = 0;
		
		private volatile SharedPreferences prefs = null;		
		private volatile Database.Profile currentProfile = null;
		
		// Main thread
		
		public void setContext(Context context) {
			this.context = context;			
		}
		
		public void signalStop() {
			if (handler != null) {
				handler.post(new Runnable() {					
					@Override
					public void run() {
						Looper.myLooper().quit();
					}
				});
			}
		}
		
		public boolean processIntent(Intent intent) {
			if (intent == null) return false;
			
			if (ActivityRecognitionResult.hasResult(intent)) {
				processActivity(intent);
				return true;
			} else if (intent.hasExtra(EXTRA_ALARM_CALLBACK)) {
				processAlarmCallback(intent);
				return true;
			}			
			
			return false;
		}
		
		private void processActivity(Intent intent) {
			if (handler != null) {
				final DetectedActivity activity = ActivityRecognitionResult.extractResult(intent).getMostProbableActivity();
				if (Database.isDetectedActivityValid(activity)) {
					wakelock.acquire();
					handler.post(new Runnable() {					
						@Override
						public void run() {
							setActivity(Database.activityFromDetectedActivity(activity), activity.getConfidence());
							wakelock.release();
						}
					});
				}
			}			
		}
		
		private void processAlarmCallback(Intent intent) {
			if (handler != null) {
				wakelock.acquire();
				handler.post(new Runnable() {					
					@Override
					public void run() {
						updateListeners(0);
						wakelock.release();
					}
				});
			}						
		}
		
		// Service thread
						
		private void setActivity(Database.Activity activity, int confidence) {
			if ((activity == Activity.UNKNOWN) && (SystemClock.elapsedRealtime() < lastNonUnknown + (2 * 60 * 1000)) && (SystemClock.elapsedRealtime() > lastNonUnknown)) {
				return;
			}
			if (activity != Activity.UNKNOWN) {
				lastNonUnknown = SystemClock.elapsedRealtime();
			}
			
			Debug.log(String.format(Locale.ENGLISH, "A: %s (%d%%)", Database.activityToString(activity), confidence));
			
			lastActivity = activity;
			lastConfidence = confidence;
			updateListeners(FLAG_ACTIVITY_UPDATE);
		}
		
		private void setLocation(Location location) {
			Debug.log(String.format(Locale.ENGLISH, "L: lat=%.8f long=%.5f alt=%.5f bearing=%.4f speed=%.4f accuracy=%.2f", location.getLatitude(), location.getLongitude(), location.getAltitude(), location.getBearing(), location.getSpeed(), location.getAccuracy()));

			lastLocLoc = location;
			updateListeners(FLAG_LOCATION_UPDATE);
		}
		
		@SuppressLint("NewApi")
		private void updateListeners(int flags) {
			if (!(activityConnected && locationConnected && (currentProfile != null))) return;			

			if (currentProfile.getType() == Type.OFF) {
				stopSelf();
			}
			
			if ((flags & FLAG_PROFILE) == FLAG_PROFILE) {
				Debug.log("Profile update");
				
				lastActivity = Activity.UNKNOWN;
				lastConfidence = 0;
				
				scheduledReduceAccuracyTime = 0L;
				lastProfileUpdate = SystemClock.elapsedRealtime();
			}
			
			Accuracy originalAccuracy = lastLocationAccuracy;
			
			Database.Profile.ActivitySettings wanted = currentProfile.getActivitySettings(lastActivity);
			Database.Accuracy wantedAccuracy = wanted.getAccuracy();
			int wantedLocationInterval = wanted.getLocationInterval();
			int wantedActivityInterval = wanted.getActivityInterval();
			
			boolean allowUpdateActivityInterval = true;
			boolean allowUpdateLocationInterval = true;
			boolean allowUpdateLocationAccuracy = true;
			
			if (
					(
							(SystemClock.elapsedRealtime() > lastProfileUpdate + (90 * 1000)) || 
							(SystemClock.elapsedRealtime() < lastProfileUpdate)
					) &&
					(currentProfile.getReduceAccuracyDelay() > 0) && 
					(
							(wantedActivityInterval > lastActivityInterval) ||
							(wantedLocationInterval > lastLocationInterval) || 
							(Database.accuracyToInt(wantedAccuracy) < Database.accuracyToInt(lastLocationAccuracy))
					)
			) {
				long left = 0;
				
				if (scheduledReduceAccuracyTime == 0) {
					left = currentProfile.getReduceAccuracyDelay() * 1000;
					scheduledReduceAccuracyTime = SystemClock.elapsedRealtime() + left;					
					alarm.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, scheduledReduceAccuracyTime + 1000, alarmCallback);
				} else {
					left = scheduledReduceAccuracyTime - SystemClock.elapsedRealtime();
				}
				
				if (left <= 0) scheduledReduceAccuracyTime = 0L;

				allowUpdateActivityInterval = ((left <= 0) || (wantedActivityInterval < lastActivityInterval) || (lastActivityInterval == -1));
				allowUpdateLocationInterval = ((left <= 0) || (wantedLocationInterval < lastLocationInterval) || (lastLocationInterval == -1));
				allowUpdateLocationAccuracy = ((left <= 0) || (Database.accuracyToInt(wantedAccuracy) > Database.accuracyToInt(lastLocationAccuracy)));				
				
				if (!allowUpdateActivityInterval) wantedActivityInterval = lastActivityInterval;
				if (!allowUpdateLocationInterval) wantedLocationInterval = lastLocationInterval;
				if (!allowUpdateLocationAccuracy) wantedAccuracy = lastLocationAccuracy;

				if (!allowUpdateActivityInterval) Debug.log(String.format(Locale.ENGLISH, "ActivityInterval --> Delay (%ds remaining)", (left / 1000)));
				if (!allowUpdateLocationInterval) Debug.log(String.format(Locale.ENGLISH, "LocationInterval --> Delay (%ds remaining)", (left / 1000)));
				if (!allowUpdateLocationAccuracy) Debug.log(String.format(Locale.ENGLISH, "LocationAccuracy --> Delay (%ds remaining)", (left / 1000)));
			} else {
				scheduledReduceAccuracyTime = 0;
				alarm.cancel(alarmCallback);
			}
			
			if ((wantedAccuracy != lastLocationAccuracy) || (wantedLocationInterval != lastLocationInterval)) {
				String s = "NONE";
				if (wantedAccuracy == Accuracy.LOW) s = "LOW";
				if (wantedAccuracy == Accuracy.HIGH) s = "HIGH";
				Debug.log(String.format(Locale.ENGLISH, "Location --> %s %ds", s, wantedLocationInterval));
				
				locationClient.removeLocationUpdates(locationListener);
				
				if ((wantedAccuracy != Accuracy.NONE) && (wantedLocationInterval > 0)) {
					if ((lastLocationAccuracy == Accuracy.NONE) || (lastLocationInterval == 0)) isSegmentStart = true;
					
					LocationRequest req = new LocationRequest();
					req.setFastestInterval(wantedLocationInterval * 250);
					req.setInterval(wantedLocationInterval * 1000);
					if (lastLocationAccuracy == Accuracy.NONE) req.setPriority(LocationRequest.PRIORITY_NO_POWER);
					if (lastLocationAccuracy == Accuracy.LOW) req.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
					if (lastLocationAccuracy == Accuracy.HIGH) req.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
					locationClient.requestLocationUpdates(req, locationListener);
				}
				
				lastLocationAccuracy = wantedAccuracy;
				lastLocationInterval = wantedLocationInterval;
			}
			
			if (wantedActivityInterval != lastActivityInterval) {
				Debug.log(String.format(Locale.ENGLISH, "Activity --> %ds", wantedActivityInterval));

				activityClient.removeActivityUpdates(activityIntent);
				
				if ((wantedActivityInterval == 0) && (lastActivityInterval != 0)) {
					lastActivity = Activity.UNKNOWN;
					lastConfidence = 0;					
				}
				
				if (wantedActivityInterval != 0) {
					activityClient.requestActivityUpdates(wantedActivityInterval * 1000, activityIntent);
				}
				
				lastActivityInterval = wantedActivityInterval;
			}
						
			if ((flags & FLAG_ACTIVITY_UPDATE) == FLAG_ACTIVITY_UPDATE) {
				if (
						(lastLocation == null) || 
						(lastLocation.getActivity() != lastActivity) || 
						(lastLocation.getConfidence() != lastConfidence)
				) {
					Debug.log("Activity update");
					if (lastLocation == null) {
						lastLocationDuplicates = 0;
						
						lastLocation = new Database.Location();
						lastLocation.setTime(System.currentTimeMillis());
					}
					lastLocation.setActivity(lastActivity);
					lastLocation.setConfidence(lastConfidence);
				}
			} else if ((flags & FLAG_LOCATION_UPDATE) == FLAG_LOCATION_UPDATE) {
				if (
						(lastLocation == null) ||
						(lastLocLoc == null) ||
						(lastLocation.getLatitude() != lastLocLoc.getLatitude()) || 
						(lastLocation.getLongitude() != lastLocLoc.getLongitude()) ||
						(lastLocation.getAccuracyDistance() > lastLocLoc.getAccuracy()) ||
						(lastLocation.getActivity() != lastActivity) ||
						(lastLocation.getConfidence() != lastConfidence) ||
						(lastLocation.getAccuracySetting() != originalAccuracy) ||
						(isSegmentStart)
				) {				
					Debug.log("Location update");
					
					if (lastLocationDuplicates > 0) {
						Debug.log("Saving last duplicate (out of " + String.valueOf(lastLocationDuplicates) + ")");
						Database.Location.copy(databaseHelper, lastLocation);
					}

					lastLocationDuplicates = 0;
					
					Database.Location loc = new Database.Location();
					loc.setActivity(lastActivity);
					loc.setConfidence(lastConfidence);
					loc.setBattery(lastBatteryLevel);
					loc.setAccuracySetting(originalAccuracy);
					loc.isSegmentStart(isSegmentStart);
					loc.loadFromLocation(lastLocLoc);
					Debug.log("Saved to database: " + String.valueOf(loc.saveToDatabase(databaseHelper)));
				
					lastLocation = loc;
					isSegmentStart = false;
				} else if (
						(lastLocation != null) &&
						(lastLocLoc != null)
				) {
					lastLocationDuplicates++;

					lastLocation.setActivity(lastActivity);
					lastLocation.setConfidence(lastConfidence);
					lastLocation.setBattery(lastBatteryLevel);
					lastLocation.setAccuracySetting(originalAccuracy);
					lastLocation.isSegmentStart(isSegmentStart);
					lastLocation.loadFromLocation(lastLocLoc);
				}
			}
				
			if (lastLocation != null) {
				notificationBuilder.
					setWhen(lastLocation.getTime()).
					setContentText(String.format(Locale.ENGLISH, "%s ~ %d%% / %.5f, %.5f ~ %.0f%s", Database.activityToString(lastLocation.getActivity()), lastLocation.getConfidence(), lastLocation.getLatitude(), lastLocation.getLongitude(), metric ? lastLocation.getAccuracyDistance() : lastLocation.getAccuracyDistance() * SettingsFragment.METER_FEET_RATIO, metric ? "m" : "ft"));
				
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
					notificationBuilder.setShowWhen(true);
				}
					
				updateNotification();
			}
		}
		
		private ConnectionCallbacks activityConnectionCallbacks = new ConnectionCallbacks() {
			@Override
			public void onConnected(Bundle arg0) {
				Debug.log("ActivityRecognitionClient connected");

				activityConnected = true;
				
				updateListeners(FLAG_SETUP);
			}
			
			@Override
			public void onDisconnected() {
				Debug.log("ActivityRecognitionClient disconnected");

				activityConnected = false;
			}				
		};
		
		private OnConnectionFailedListener activityConnectionFailed = new OnConnectionFailedListener() {			
			@Override
			public void onConnectionFailed(ConnectionResult arg0) {
				Debug.log("ActivityRecognitionClient connection failed");

				activityConnected = false;				
				signalStop();
			}
		};
		
		private LocationListener locationListener = new LocationListener() {			
			@Override
			public void onLocationChanged(Location arg0) {
				wakelock.acquire();
				try {
					setLocation(arg0);
				} finally {
					wakelock.release();
				}
			}
		};

		private ConnectionCallbacks locationConnectionCallbacks = new ConnectionCallbacks() {
			@Override
			public void onConnected(Bundle arg0) {
				Debug.log("LocationClient connected");
				
				locationConnected = true;
				
				updateListeners(FLAG_SETUP);				
			}
			
			@Override
			public void onDisconnected() {
				Debug.log("LocationClient disconnected");
				
				locationConnected = false;
			}				
		};
		
		private OnConnectionFailedListener locationConnectionFailed = new OnConnectionFailedListener() {			
			@Override
			public void onConnectionFailed(ConnectionResult arg0) {
				Debug.log("LocationClient connection failed");

				locationConnected = false;			
				signalStop();
			}
		};
		
		private BroadcastReceiver databaseUpdated = new BroadcastReceiver() {			
			@Override
			public void onReceive(Context context, Intent intent) {
				if (
						(currentProfile != null) &&
						(intent != null) &&
						intent.hasExtra(Database.Helper.EXTRA_TABLE) &&
						intent.getStringExtra(Database.Helper.EXTRA_TABLE).equals(Database.Profile.TABLE_NAME) &&
						intent.hasExtra(Database.Helper.EXTRA_ID) &&
						(intent.getLongExtra(Database.Helper.EXTRA_ID, 0) == currentProfile.getId()) 
				) {
					currentProfile = Database.Profile.getById(databaseHelper, intent.getLongExtra(Database.Helper.EXTRA_ID, 0), currentProfile);
					updateListeners(FLAG_PROFILE);
				}
			}
		};
		
		private OnSharedPreferenceChangeListener preferencesUpdated = new OnSharedPreferenceChangeListener() {			
			@Override
			public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
				if (key.equals(SettingsFragment.PREF_UNITS)) {
					metric = !prefs.getString(SettingsFragment.PREF_UNITS, SettingsFragment.PREF_UNITS_DEFAULT).equals(SettingsFragment.VALUE_UNITS_IMPERIAL);					
					updateListeners(0);
				}
				if (key.equals(SettingsFragment.PREF_CURRENT_PROFILE)) {
					currentProfile = Database.Profile.getById(databaseHelper, sharedPreferences.getLong(key, 0), currentProfile);
					updateListeners(FLAG_PROFILE);
				}
			}
		};
		
		private BroadcastReceiver batteryReceiver = new BroadcastReceiver() {			
			@Override
			public void onReceive(Context context, Intent intent) {
				int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
				boolean plugged = (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0);
				
				boolean charging = ( 
						(status == BatteryManager.BATTERY_STATUS_CHARGING) ||
	                    ((status == BatteryManager.BATTERY_STATUS_FULL) && plugged)
	            );
				
				int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
				if (charging) level += 100;
				
				lastBatteryLevel = level;
			}
		};

		@Override
		public void run() {
			Debug.log("Thread init");
						
			databaseHelper = Database.Helper.getInstance(context);
			
			alarm = (AlarmManager)context.getSystemService(ALARM_SERVICE);
			{
				Intent i = new Intent(context.getApplicationContext(), BackgroundService.class);
				i.putExtra(EXTRA_ALARM_CALLBACK, 1);
				alarmCallback =	PendingIntent.getService(BackgroundService.this, 0, i, 0);
			}

			Looper.prepare();
			handler = new Handler();
					
			Debug.log("Registering for updates");			
			prefs = PreferenceManager.getDefaultSharedPreferences(context);					
			long id = prefs.getLong(SettingsFragment.PREF_CURRENT_PROFILE, 0);
			if (id > 0) currentProfile = Database.Profile.getById(databaseHelper, id, null);
			if (currentProfile == null) currentProfile = Database.Profile.getOffProfile(databaseHelper);
			metric = !prefs.getString(SettingsFragment.PREF_UNITS, SettingsFragment.PREF_UNITS_DEFAULT).equals(SettingsFragment.VALUE_UNITS_IMPERIAL);
			prefs.registerOnSharedPreferenceChangeListener(preferencesUpdated);
			LocalBroadcastManager.getInstance(context).registerReceiver(databaseUpdated, new IntentFilter(Database.Helper.NOTIFY_BROADCAST));
			
			Debug.log("Registering for power levels");
			context.registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
			
			Debug.log("Connecting ActivityRecognitionClient");
			activityIntent = PendingIntent.getService(context, 1, new Intent(context, BackgroundService.class), 0);
			activityClient = new ActivityRecognitionClient(context, activityConnectionCallbacks, activityConnectionFailed);
			activityClient.connect();

			Debug.log("Connecting LocationClient");
			locationClient = new LocationClient(context, locationConnectionCallbacks, locationConnectionFailed);
			locationClient.connect();
			
			Debug.log("Entering loop");
			handler.post(new Runnable() {				
				@Override
				public void run() {
					updateListeners(FLAG_SETUP);
				}
			});						
			Looper.loop();			
			Debug.log("Exiting loop");
			
			context.unregisterReceiver(batteryReceiver);
			
			LocalBroadcastManager.getInstance(context).unregisterReceiver(databaseUpdated);
			prefs.unregisterOnSharedPreferenceChangeListener(preferencesUpdated);			
			
			if (activityConnected) {
				activityClient.removeActivityUpdates(activityIntent);
				activityClient.disconnect();
			}
			if (locationConnected) {
				locationClient.removeLocationUpdates(locationListener);
				locationClient.disconnect();
			}			
		}		
	}
}
