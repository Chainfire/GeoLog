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

package eu.chainfire.geolog.data;

import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;

import com.google.android.gms.location.DetectedActivity;

import eu.chainfire.geolog.R;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.BaseColumns;
import android.support.v4.content.LocalBroadcastManager;

public class Database {
	private static final String TYPE_TEXT = " TEXT";
	private static final String TYPE_INTEGER = " INTEGER";		
	private static final String COMMA_SEP = ",";
	
	public static final int INTERVAL_OFF = 0;
	
	// +- 25m max distance between intervals, 6 km/h, 20 km/h, 100 km/h
	public static final int INTERVAL_NAVIGATION_HIGH_ACCURACY_FOOT = 15;
	public static final int INTERVAL_NAVIGATION_HIGH_ACCURACY_BICYCLE = 5;
	public static final int INTERVAL_NAVIGATION_HIGH_ACCURACY_VEHICLE = 1;
	public static final int INTERVAL_NAVIGATION_HIGH_ACCURACY_FIXED = 90;
	
	// += 150m max distance between intervals
	public static final int INTERVAL_NAVIGATION_LOW_POWER_FOOT = 90;
	public static final int INTERVAL_NAVIGATION_LOW_POWER_BICYCLE = 30;
	public static final int INTERVAL_NAVIGATION_LOW_POWER_VEHICLE = 6;
	public static final int INTERVAL_NAVIGATION_LOW_POWER_FIXED = 180;

	public static final int INTERVAL_VERY_FAST = 30;
	public static final int INTERVAL_FASTER = 60;
	public static final int INTERVAL_FAST = 2 * 60;
	public static final int INTERVAL_MEDIUM_FAST = 3 * 60;
	public static final int INTERVAL_MEDIUM = 5 * 60;
	public static final int INTERVAL_SLOW = 10 * 60;
	public static final int INTERVAL_SLOWER = 15 * 60;
	
	public static enum Activity { UNKNOWN, STILL, FOOT, BICYCLE, VEHICLE };
	public static enum Accuracy { NONE, LOW, HIGH };
	
	public static Activity activityFromInt(int activity) {
		switch (activity) {
		case 0: return Activity.UNKNOWN;
		case 1: return Activity.STILL;
		case 2: return Activity.FOOT;
		case 3: return Activity.BICYCLE;
		case 4: return Activity.VEHICLE;
		}
		return Activity.UNKNOWN;
	}
	
	public static int activityToInt(Activity activity) {
		switch (activity) {
		case UNKNOWN: return 0;
		case STILL: return 1;
		case FOOT: return 2;
		case BICYCLE: return 3;
		case VEHICLE: return 4;
		}
		return 0;
	}
	
	public static String activityToString(Activity activity) {
		switch (activity) {
		case UNKNOWN: return "Unknown";
		case STILL: return "Still";
		case FOOT: return "Foot";
		case BICYCLE: return "Bicycle";
		case VEHICLE: return "Vehicle";
		}
		return "Unknown";		
	}
	
	public static Activity activityFromDetectedActivity(DetectedActivity activity) {
		switch (activity.getType()) {
		case DetectedActivity.UNKNOWN: return Activity.UNKNOWN;
		case DetectedActivity.STILL: return Activity.STILL;
		case DetectedActivity.ON_FOOT: return Activity.FOOT;
		case DetectedActivity.ON_BICYCLE: return Activity.BICYCLE;
		case DetectedActivity.IN_VEHICLE: return Activity.VEHICLE;
		}
		return Activity.UNKNOWN;
	}
	
	public static boolean isDetectedActivityValid(DetectedActivity activity) {
		// conversion succeeds and is not unknown (default result), unless original is actually unknown 
		return (
			(activity.getType() == DetectedActivity.UNKNOWN) || 
			(activityFromDetectedActivity(activity) != Activity.UNKNOWN)
		);
	}	
	
	public static Accuracy accuracyFromInt(int accuracy) {
		if (accuracy == 0) return Accuracy.NONE;
		if (accuracy == 1) return Accuracy.LOW;
		return Accuracy.HIGH;
	}
	
	public static int accuracyToInt(Accuracy accuracy) {
		if (accuracy == Accuracy.NONE) return 0;
		if (accuracy == Accuracy.LOW) return 1;
		return 2;
	}		

	public static class Helper extends SQLiteOpenHelper {
		public static final int DATABASE_VERSION = 1;
		public static final String DATABASE_NAME = "geolog.db";
		
		public static final String NOTIFY_BROADCAST = "eu.chainfire.geolog.DATABASE.UPDATED";
		public static final String EXTRA_TABLE = "eu.chainfire.geolog.EXTRA.TABLE";
		public static final String EXTRA_ID = "eu.chainfire.geolog.EXTRA.ID";
		
		private static Helper instance = null;
						
		public static Helper getInstance(Context context) {
			if (instance == null) instance = new Helper(context.getApplicationContext());
			return instance;
		}
		
		private ReentrantLock lock = new ReentrantLock(true);
		private final Context context;
		
		private Helper(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
			this.context = context;
			getReadableDatabase();
			createDefaultEntries(getWritableDatabase());
		}
		
		public void acquireLock() {
			lock.lock();
		}
		
		public void releaseLock() {
			lock.unlock();
		}
		
		public void notifyUri(Uri uri) {
			context.getContentResolver().notifyChange(uri, null);
		}
		
		public void notifyBroadcast(String table, long id) {
			Intent i = new Intent(NOTIFY_BROADCAST);
			i.putExtra(EXTRA_TABLE, table);
			i.putExtra(EXTRA_ID, id);
			LocalBroadcastManager.getInstance(context).sendBroadcast(i);
		}
				
		@Override
		public void onCreate(SQLiteDatabase db) {
			// Profiles
			
			db.execSQL(Profile.SQL_CREATE_TABLE);
			for (String index : Profile.SQL_CREATE_INDICES) {
				db.execSQL(index);
			}
			
			// Locations

			db.execSQL(Location.SQL_CREATE_TABLE);
			for (String index : Location.SQL_CREATE_INDICES) {
				db.execSQL(index);
			}
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			db.execSQL(Profile.SQL_DROP_TABLE);
			db.execSQL(Location.SQL_DROP_TABLE);
			onCreate(db);
		}
		
		private void createDefaultEntries(SQLiteDatabase db) {
			//db.execSQL("DELETE FROM " + Profile.TABLE_NAME); //TODO
			
			//db.execSQL("ALTER TABLE " + Location.TABLE_NAME + " ADD COLUMN " + Location.COLUMN_NAME_LOG_ID + " " + TYPE_INTEGER);
			//db.execSQL("UPDATE " + Location.TABLE_NAME + " SET " + Location.COLUMN_NAME_LOG_ID + " = 0");
			
			//db.execSQL("ALTER TABLE " + Profile.TABLE_NAME + " ADD COLUMN " + Profile.COLUMN_NAME_REDUCE_ACCURACY_DELAY + " " + TYPE_INTEGER);
			//db.execSQL("UPDATE " + Profile.TABLE_NAME + " SET " + Profile.COLUMN_NAME_REDUCE_ACCURACY_DELAY + " = 300");

			if (DatabaseUtils.queryNumEntries(db, Profile.TABLE_NAME) == 0) {
				{
					Profile p = new Profile();
					p.setType(Profile.Type.OFF);
					p.setName(context.getString(R.string.profile_name_off));
					p.saveToDatabase(this);
				}
				
				{
					Profile p = new Profile();
					p.setType(Profile.Type.PRESET);
					p.setName(context.getString(R.string.profile_name_low_power_slow));
					p.setReduceAccuracyDelay(300);
					p.getUnknown().setAccuracy(Accuracy.LOW).setActivityInterval(INTERVAL_NAVIGATION_LOW_POWER_VEHICLE * 12).setLocationInterval(INTERVAL_NAVIGATION_LOW_POWER_VEHICLE * 12);
					p.getStill().setAccuracy(Accuracy.NONE).setActivityInterval(INTERVAL_NAVIGATION_LOW_POWER_FOOT * 4).setLocationInterval(INTERVAL_NAVIGATION_LOW_POWER_FOOT * 4);
					p.getFoot().setAccuracy(Accuracy.LOW).setActivityInterval(INTERVAL_NAVIGATION_LOW_POWER_FOOT * 4).setLocationInterval(INTERVAL_NAVIGATION_LOW_POWER_FOOT * 4);
					p.getBicycle().setAccuracy(Accuracy.LOW).setActivityInterval(INTERVAL_NAVIGATION_LOW_POWER_BICYCLE * 8).setLocationInterval(INTERVAL_NAVIGATION_LOW_POWER_BICYCLE * 8);
					p.getVehicle().setAccuracy(Accuracy.LOW).setActivityInterval(INTERVAL_NAVIGATION_LOW_POWER_VEHICLE * 12).setLocationInterval(INTERVAL_NAVIGATION_LOW_POWER_VEHICLE * 12);
					p.saveToDatabase(this);
				}

				{
					Profile p = new Profile();
					p.setType(Profile.Type.PRESET);
					p.setName(context.getString(R.string.profile_name_low_power_fast));
					p.setReduceAccuracyDelay(300);
					p.getUnknown().setAccuracy(Accuracy.LOW).setActivityInterval(INTERVAL_NAVIGATION_LOW_POWER_VEHICLE).setLocationInterval(INTERVAL_NAVIGATION_LOW_POWER_VEHICLE);
					p.getStill().setAccuracy(Accuracy.NONE).setActivityInterval(INTERVAL_NAVIGATION_LOW_POWER_FOOT).setLocationInterval(INTERVAL_NAVIGATION_LOW_POWER_FOOT);
					p.getFoot().setAccuracy(Accuracy.LOW).setActivityInterval(INTERVAL_NAVIGATION_LOW_POWER_FOOT).setLocationInterval(INTERVAL_NAVIGATION_LOW_POWER_FOOT);
					p.getBicycle().setAccuracy(Accuracy.LOW).setActivityInterval(INTERVAL_NAVIGATION_LOW_POWER_BICYCLE).setLocationInterval(INTERVAL_NAVIGATION_LOW_POWER_BICYCLE);
					p.getVehicle().setAccuracy(Accuracy.LOW).setActivityInterval(INTERVAL_NAVIGATION_LOW_POWER_VEHICLE).setLocationInterval(INTERVAL_NAVIGATION_LOW_POWER_VEHICLE);
					p.saveToDatabase(this);
				}

				{
					Profile p = new Profile();
					p.setType(Profile.Type.PRESET);
					p.setName(context.getString(R.string.profile_name_low_power_fixed));
					p.setReduceAccuracyDelay(300);
					p.getUnknown().setAccuracy(Accuracy.LOW).setActivityInterval(INTERVAL_OFF).setLocationInterval(INTERVAL_NAVIGATION_LOW_POWER_FIXED);
					p.getStill().setAccuracy(Accuracy.LOW).setActivityInterval(INTERVAL_NAVIGATION_LOW_POWER_FIXED).setLocationInterval(INTERVAL_NAVIGATION_LOW_POWER_FIXED);
					p.getFoot().setAccuracy(Accuracy.LOW).setActivityInterval(INTERVAL_NAVIGATION_LOW_POWER_FIXED).setLocationInterval(INTERVAL_NAVIGATION_LOW_POWER_FIXED);
					p.getBicycle().setAccuracy(Accuracy.LOW).setActivityInterval(INTERVAL_NAVIGATION_LOW_POWER_FIXED).setLocationInterval(INTERVAL_NAVIGATION_LOW_POWER_FIXED);
					p.getVehicle().setAccuracy(Accuracy.LOW).setActivityInterval(INTERVAL_NAVIGATION_LOW_POWER_FIXED).setLocationInterval(INTERVAL_NAVIGATION_LOW_POWER_FIXED);
					p.saveToDatabase(this);
				}

				{
					Profile p = new Profile();
					p.setType(Profile.Type.PRESET);
					p.setName(context.getString(R.string.profile_name_high_accuracy_slow));
					p.setReduceAccuracyDelay(300);
					p.getUnknown().setAccuracy(Accuracy.HIGH).setActivityInterval(INTERVAL_NAVIGATION_HIGH_ACCURACY_VEHICLE * 36).setLocationInterval(INTERVAL_NAVIGATION_HIGH_ACCURACY_VEHICLE * 36);
					p.getStill().setAccuracy(Accuracy.NONE).setActivityInterval(INTERVAL_NAVIGATION_HIGH_ACCURACY_FOOT * 8).setLocationInterval(INTERVAL_NAVIGATION_HIGH_ACCURACY_FOOT * 8);
					p.getFoot().setAccuracy(Accuracy.HIGH).setActivityInterval(INTERVAL_NAVIGATION_HIGH_ACCURACY_FOOT * 8).setLocationInterval(INTERVAL_NAVIGATION_HIGH_ACCURACY_FOOT * 8);
					p.getBicycle().setAccuracy(Accuracy.HIGH).setActivityInterval(INTERVAL_NAVIGATION_HIGH_ACCURACY_BICYCLE * 16).setLocationInterval(INTERVAL_NAVIGATION_HIGH_ACCURACY_BICYCLE * 16);
					p.getVehicle().setAccuracy(Accuracy.HIGH).setActivityInterval(INTERVAL_NAVIGATION_HIGH_ACCURACY_VEHICLE * 36).setLocationInterval(INTERVAL_NAVIGATION_HIGH_ACCURACY_VEHICLE * 36);
					p.saveToDatabase(this);
				}

				{
					Profile p = new Profile();
					p.setType(Profile.Type.PRESET);
					p.setName(context.getString(R.string.profile_name_high_accuracy_fast));
					p.setReduceAccuracyDelay(300);
					p.getUnknown().setAccuracy(Accuracy.HIGH).setActivityInterval(INTERVAL_NAVIGATION_HIGH_ACCURACY_VEHICLE).setLocationInterval(INTERVAL_NAVIGATION_HIGH_ACCURACY_VEHICLE);
					p.getStill().setAccuracy(Accuracy.NONE).setActivityInterval(INTERVAL_NAVIGATION_HIGH_ACCURACY_FOOT).setLocationInterval(INTERVAL_NAVIGATION_HIGH_ACCURACY_FOOT);
					p.getFoot().setAccuracy(Accuracy.HIGH).setActivityInterval(INTERVAL_NAVIGATION_HIGH_ACCURACY_FOOT).setLocationInterval(INTERVAL_NAVIGATION_HIGH_ACCURACY_FOOT);
					p.getBicycle().setAccuracy(Accuracy.HIGH).setActivityInterval(INTERVAL_NAVIGATION_HIGH_ACCURACY_BICYCLE).setLocationInterval(INTERVAL_NAVIGATION_HIGH_ACCURACY_BICYCLE);
					p.getVehicle().setAccuracy(Accuracy.HIGH).setActivityInterval(INTERVAL_NAVIGATION_HIGH_ACCURACY_VEHICLE).setLocationInterval(INTERVAL_NAVIGATION_HIGH_ACCURACY_VEHICLE);
					p.saveToDatabase(this);
				}
				
				{
					Profile p = new Profile();
					p.setType(Profile.Type.PRESET);
					p.setName(context.getString(R.string.profile_name_high_accuracy_fixed));
					p.setReduceAccuracyDelay(300);
					p.getUnknown().setAccuracy(Accuracy.HIGH).setActivityInterval(INTERVAL_OFF).setLocationInterval(INTERVAL_NAVIGATION_HIGH_ACCURACY_FIXED);
					p.getStill().setAccuracy(Accuracy.HIGH).setActivityInterval(INTERVAL_NAVIGATION_HIGH_ACCURACY_FIXED).setLocationInterval(INTERVAL_NAVIGATION_HIGH_ACCURACY_FIXED);
					p.getFoot().setAccuracy(Accuracy.HIGH).setActivityInterval(INTERVAL_NAVIGATION_HIGH_ACCURACY_FIXED).setLocationInterval(INTERVAL_NAVIGATION_HIGH_ACCURACY_FIXED);
					p.getBicycle().setAccuracy(Accuracy.HIGH).setActivityInterval(INTERVAL_NAVIGATION_HIGH_ACCURACY_FIXED).setLocationInterval(INTERVAL_NAVIGATION_HIGH_ACCURACY_FIXED);
					p.getVehicle().setAccuracy(Accuracy.HIGH).setActivityInterval(INTERVAL_NAVIGATION_HIGH_ACCURACY_FIXED).setLocationInterval(INTERVAL_NAVIGATION_HIGH_ACCURACY_FIXED);
					p.saveToDatabase(this);
				}
				
				{
					Profile p = new Profile();
					p.setType(Profile.Type.PRESET);
					p.setName(context.getString(R.string.profile_name_photo_walk_low_power));
					p.setReduceAccuracyDelay(300);
					p.getUnknown().setAccuracy(Accuracy.LOW).setActivityInterval(INTERVAL_VERY_FAST).setLocationInterval(INTERVAL_MEDIUM_FAST);
					p.getStill().setAccuracy(Accuracy.NONE).setActivityInterval(INTERVAL_FASTER).setLocationInterval(INTERVAL_MEDIUM_FAST);
					p.getFoot().setAccuracy(Accuracy.LOW).setActivityInterval(INTERVAL_MEDIUM_FAST).setLocationInterval(INTERVAL_MEDIUM_FAST);
					p.getBicycle().setAccuracy(Accuracy.NONE).setActivityInterval(INTERVAL_MEDIUM_FAST).setLocationInterval(INTERVAL_MEDIUM_FAST);
					p.getVehicle().setAccuracy(Accuracy.NONE).setActivityInterval(INTERVAL_MEDIUM_FAST).setLocationInterval(INTERVAL_MEDIUM_FAST);
					p.saveToDatabase(this);
				}				

				{
					Profile p = new Profile();
					p.setType(Profile.Type.PRESET);
					p.setName(context.getString(R.string.profile_name_photo_walk_high_accuracy));
					p.setReduceAccuracyDelay(300);
					p.getUnknown().setAccuracy(Accuracy.HIGH).setActivityInterval(INTERVAL_VERY_FAST).setLocationInterval(INTERVAL_MEDIUM_FAST);
					p.getStill().setAccuracy(Accuracy.NONE).setActivityInterval(INTERVAL_FASTER).setLocationInterval(INTERVAL_MEDIUM_FAST);
					p.getFoot().setAccuracy(Accuracy.HIGH).setActivityInterval(INTERVAL_MEDIUM_FAST).setLocationInterval(INTERVAL_MEDIUM_FAST);
					p.getBicycle().setAccuracy(Accuracy.NONE).setActivityInterval(INTERVAL_MEDIUM_FAST).setLocationInterval(INTERVAL_MEDIUM_FAST);
					p.getVehicle().setAccuracy(Accuracy.NONE).setActivityInterval(INTERVAL_MEDIUM_FAST).setLocationInterval(INTERVAL_MEDIUM_FAST);
					p.saveToDatabase(this);
				}				
			}			
		}
	}

	public static class Profile implements BaseColumns {
		public static final String BASE_INTERVAL_ACTIVITY = "%s_interval_activity";
		public static final String BASE_INTERVAL_LOCATION = "%s_interval_location";
		public static final String BASE_ACCURACY = "%s_accuracy";
		public static final String BASE_UNKNOWN = "unknown";
		public static final String BASE_STILL = "still";
		public static final String BASE_FOOT = "foot";
		public static final String BASE_BICYCLE = "bicycle";
		public static final String BASE_VEHICLE = "vehicle";

		public static final String TABLE_NAME = "profiles";
		
		public static final String COLUMN_NAME_NAME = "name";
		public static final String COLUMN_NAME_TYPE = "type";
		
		public static final String COLUMN_NAME_REDUCE_ACCURACY_DELAY = "reduce_accuracy_delay";
				
		public static final String COLUMN_NAME_UNKNOWN_INTERVAL_ACTIVITY = String.format(Locale.ENGLISH, BASE_INTERVAL_ACTIVITY, BASE_UNKNOWN);
		public static final String COLUMN_NAME_UNKNOWN_INTERVAL_LOCATION = String.format(Locale.ENGLISH, BASE_INTERVAL_LOCATION, BASE_UNKNOWN);
		public static final String COLUMN_NAME_UNKNOWN_ACCURACY = String.format(Locale.ENGLISH, BASE_ACCURACY, BASE_UNKNOWN);
		
		public static final String COLUMN_NAME_STILL_INTERVAL_ACTIVITY = String.format(Locale.ENGLISH, BASE_INTERVAL_ACTIVITY, BASE_STILL);
		public static final String COLUMN_NAME_STILL_INTERVAL_LOCATION = String.format(Locale.ENGLISH, BASE_INTERVAL_LOCATION, BASE_STILL);
		public static final String COLUMN_NAME_STILL_ACCURACY = String.format(Locale.ENGLISH, BASE_ACCURACY, BASE_STILL);

		public static final String COLUMN_NAME_FOOT_INTERVAL_ACTIVITY = String.format(Locale.ENGLISH, BASE_INTERVAL_ACTIVITY, BASE_FOOT);
		public static final String COLUMN_NAME_FOOT_INTERVAL_LOCATION = String.format(Locale.ENGLISH, BASE_INTERVAL_LOCATION, BASE_FOOT);
		public static final String COLUMN_NAME_FOOT_ACCURACY = String.format(Locale.ENGLISH, BASE_ACCURACY, BASE_FOOT);

		public static final String COLUMN_NAME_BICYCLE_INTERVAL_ACTIVITY = String.format(Locale.ENGLISH, BASE_INTERVAL_ACTIVITY, BASE_BICYCLE);
		public static final String COLUMN_NAME_BICYCLE_INTERVAL_LOCATION = String.format(Locale.ENGLISH, BASE_INTERVAL_LOCATION, BASE_BICYCLE);
		public static final String COLUMN_NAME_BICYCLE_ACCURACY = String.format(Locale.ENGLISH, BASE_ACCURACY, BASE_BICYCLE);

		public static final String COLUMN_NAME_VEHICLE_INTERVAL_ACTIVITY = String.format(Locale.ENGLISH, BASE_INTERVAL_ACTIVITY, BASE_VEHICLE);
		public static final String COLUMN_NAME_VEHICLE_INTERVAL_LOCATION = String.format(Locale.ENGLISH, BASE_INTERVAL_LOCATION, BASE_VEHICLE);
		public static final String COLUMN_NAME_VEHICLE_ACCURACY = String.format(Locale.ENGLISH, BASE_ACCURACY, BASE_VEHICLE);

		public static final String SQL_CREATE_TABLE =
				"CREATE TABLE " + TABLE_NAME + " (" +
						_ID + " INTEGER PRIMARY KEY AUTOINCREMENT" + COMMA_SEP +
						
						COLUMN_NAME_NAME + TYPE_TEXT + COMMA_SEP +
						COLUMN_NAME_TYPE + TYPE_INTEGER + COMMA_SEP +
						
						COLUMN_NAME_REDUCE_ACCURACY_DELAY + TYPE_INTEGER + COMMA_SEP +
						
						COLUMN_NAME_UNKNOWN_INTERVAL_ACTIVITY + TYPE_INTEGER + COMMA_SEP +
						COLUMN_NAME_UNKNOWN_INTERVAL_LOCATION + TYPE_INTEGER + COMMA_SEP +
						COLUMN_NAME_UNKNOWN_ACCURACY + TYPE_INTEGER + COMMA_SEP +
						
						COLUMN_NAME_STILL_INTERVAL_ACTIVITY + TYPE_INTEGER + COMMA_SEP +
						COLUMN_NAME_STILL_INTERVAL_LOCATION + TYPE_INTEGER + COMMA_SEP +
						COLUMN_NAME_STILL_ACCURACY + TYPE_INTEGER + COMMA_SEP +

						COLUMN_NAME_FOOT_INTERVAL_ACTIVITY + TYPE_INTEGER + COMMA_SEP +
						COLUMN_NAME_FOOT_INTERVAL_LOCATION + TYPE_INTEGER + COMMA_SEP +
						COLUMN_NAME_FOOT_ACCURACY + TYPE_INTEGER + COMMA_SEP +

						COLUMN_NAME_BICYCLE_INTERVAL_ACTIVITY + TYPE_INTEGER + COMMA_SEP +
						COLUMN_NAME_BICYCLE_INTERVAL_LOCATION + TYPE_INTEGER + COMMA_SEP +
						COLUMN_NAME_BICYCLE_ACCURACY + TYPE_INTEGER + COMMA_SEP +

						COLUMN_NAME_VEHICLE_INTERVAL_ACTIVITY + TYPE_INTEGER + COMMA_SEP +
						COLUMN_NAME_VEHICLE_INTERVAL_LOCATION + TYPE_INTEGER + COMMA_SEP +
						COLUMN_NAME_VEHICLE_ACCURACY + TYPE_INTEGER +
				")";
		
		public static final String[] SQL_CREATE_INDICES = new String[] { 
		};

		public static final String SQL_DROP_TABLE =
			    "DROP TABLE IF EXISTS " + TABLE_NAME;	
		
		public static enum Type { OFF, PRESET, USER };
				
		private static Type typeFromInt(int type) {
			if (type == 0) return Type.OFF;
			if (type == 1) return Type.PRESET;
			return Type.USER;
		}
		
		private static int typeToInt(Type type) {
			if (type == Type.OFF) return 0;
			if (type == Type.PRESET) return 1;
			return 2;
		}		
		
		public class ActivitySettings {
			private int activityInterval = 0;
			private int locationInterval = 0;
			private Accuracy accuracy = Accuracy.NONE;
			
			public int getActivityInterval() { return activityInterval; }
			public int getLocationInterval() { return locationInterval;	}
			public Accuracy getAccuracy() {	return accuracy; }

			public ActivitySettings setActivityInterval(int activityInterval) { this.activityInterval = activityInterval; return this; }
			public ActivitySettings setLocationInterval(int locationInterval) { this.locationInterval = locationInterval; return this; }
			public ActivitySettings setAccuracy(Accuracy accuracy) { this.accuracy = accuracy; return this; }

			public void loadFromCursor(Cursor cursor, String base) {
				activityInterval = cursor.getInt(cursor.getColumnIndex(String.format(Locale.ENGLISH, BASE_INTERVAL_ACTIVITY, base)));
				locationInterval = cursor.getInt(cursor.getColumnIndex(String.format(Locale.ENGLISH, BASE_INTERVAL_LOCATION, base)));
				accuracy = accuracyFromInt(cursor.getInt(cursor.getColumnIndex(String.format(Locale.ENGLISH, BASE_ACCURACY, base))));
			}		
			
			public void saveToContentValues(ContentValues values, String base) {
				values.put(String.format(Locale.ENGLISH, BASE_INTERVAL_ACTIVITY, base), activityInterval);
				values.put(String.format(Locale.ENGLISH, BASE_INTERVAL_LOCATION, base), locationInterval);
				values.put(String.format(Locale.ENGLISH, BASE_ACCURACY, base), accuracyToInt(accuracy));
			}
		}
		
		private long id = -1;
		private String name = "OFF";
		private Type type = Type.OFF;
		private int reduceAccuracyDelay = 0;
		
		private final ActivitySettings unknown = new ActivitySettings(); 
		private final ActivitySettings still = new ActivitySettings(); 
		private final ActivitySettings foot = new ActivitySettings(); 
		private final ActivitySettings bicycle = new ActivitySettings(); 
		private final ActivitySettings vehicle = new ActivitySettings();
		
		public long getId() { return id; }
		
		public String getName() { return name; }
		public Type getType() {	return type; }
		public int getReduceAccuracyDelay() { return reduceAccuracyDelay; }
		public ActivitySettings getUnknown() { return unknown; }
		public ActivitySettings getStill() { return still; }
		public ActivitySettings getFoot() { return foot; }
		public ActivitySettings getBicycle() { return bicycle; }
		public ActivitySettings getVehicle() { return vehicle; }
		
		public ActivitySettings getActivitySettings(Activity activity) {
			switch (activity) {
			case UNKNOWN: return getUnknown();
			case STILL: return getStill();
			case FOOT: return getFoot();
			case BICYCLE: return getBicycle();
			case VEHICLE: return getVehicle();
			}
			return getUnknown();
		}
		
		public Profile setType(Type type) { this.type = type; return this; }
		public Profile setName(String name) { this.name = name; return this; }	
		public Profile setReduceAccuracyDelay(int reduceAccuracyDelay) { this.reduceAccuracyDelay = reduceAccuracyDelay; return this; }

		public void loadFromCursor(Cursor cursor) {
			id = cursor.getLong(cursor.getColumnIndex(_ID));
			
			name = cursor.getString(cursor.getColumnIndex(COLUMN_NAME_NAME));
			type = typeFromInt(cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_TYPE)));
			reduceAccuracyDelay = cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_REDUCE_ACCURACY_DELAY));
			
			unknown.loadFromCursor(cursor, BASE_UNKNOWN);
			still.loadFromCursor(cursor, BASE_STILL);
			foot.loadFromCursor(cursor, BASE_FOOT);
			bicycle.loadFromCursor(cursor, BASE_BICYCLE);
			vehicle.loadFromCursor(cursor, BASE_VEHICLE);
		}	
		
		public long saveToDatabase(Helper helper) {
			SQLiteDatabase db = helper.getWritableDatabase();
			
			ContentValues values = new ContentValues();
			
			values.put(COLUMN_NAME_NAME, name);
			values.put(COLUMN_NAME_TYPE, typeToInt(type));
			values.put(COLUMN_NAME_REDUCE_ACCURACY_DELAY, reduceAccuracyDelay);
			
			unknown.saveToContentValues(values, BASE_UNKNOWN);
			still.saveToContentValues(values, BASE_STILL);
			foot.saveToContentValues(values, BASE_FOOT);
			bicycle.saveToContentValues(values, BASE_BICYCLE);
			vehicle.saveToContentValues(values, BASE_VEHICLE);
			
			if (id < 0) {
				id = db.insert(TABLE_NAME, null, values);							
			} else {
				db.update(TABLE_NAME, values, _ID + " = ?", new String[] { String.valueOf(id) });
			}
			if (id >= 0) {
				helper.notifyUri(ProfilesProvider.URILocations());
				helper.notifyUri(ProfilesProvider.URILocation(id));
				helper.notifyBroadcast(TABLE_NAME, id);
			}
			return id;
		}
		
		public static Profile getById(Helper helper, long id, Profile into) {
			Cursor cursor = helper.getReadableDatabase().rawQuery("SELECT * FROM " + TABLE_NAME + " WHERE " + _ID + " = ?", new String[] { String.valueOf(id) });
			if (cursor != null) {
				try {
					if (cursor.moveToFirst()) {
						if (into == null) into = new Profile();
						into.loadFromCursor(cursor);
						return into;
					}
				} finally {
					cursor.close();
				}
			}
			return null;
		}
		
		public static void delete(Helper helper, long id) {
			helper.getWritableDatabase().delete(TABLE_NAME, _ID + " = ?", new String[] { String.valueOf(id) });
			helper.notifyUri(ProfilesProvider.URILocations());
			helper.notifyUri(ProfilesProvider.URILocation(id));
			helper.notifyBroadcast(TABLE_NAME, id);
		}	
		
		private static void copyActivitySettings(ActivitySettings source, ActivitySettings destination) {
			destination.setAccuracy(source.getAccuracy());
			destination.setLocationInterval(source.getLocationInterval());
			destination.setActivityInterval(source.getActivityInterval());
		}
		
		public static Profile copy(Helper helper, Profile profile, String name) {
			Profile ret = new Profile();
			ret.setName(name);
			ret.setType(Type.USER);
			ret.setReduceAccuracyDelay(profile.getReduceAccuracyDelay());
			copyActivitySettings(profile.getUnknown()	, ret.getUnknown()	);
			copyActivitySettings(profile.getStill()		, ret.getStill()	);
			copyActivitySettings(profile.getFoot()		, ret.getFoot()		);
			copyActivitySettings(profile.getBicycle()	, ret.getBicycle()	);
			copyActivitySettings(profile.getVehicle()	, ret.getVehicle()	);
			ret.saveToDatabase(helper);
			return ret;
		}
		
		public static Cursor list(Helper helper) {
			return helper.getReadableDatabase().query(TABLE_NAME, null, COLUMN_NAME_TYPE + " <> ?", new String[] { String.valueOf(typeToInt(Type.OFF)) }, null, null, _ID);
		}
		
		public static Profile getOffProfile(Helper helper) {
			Cursor cursor = helper.getReadableDatabase().rawQuery("SELECT * FROM " + TABLE_NAME + " WHERE " + COLUMN_NAME_TYPE + " = ?", new String[] { String.valueOf(typeToInt(Type.OFF)) });
			if (cursor != null) {
				try {
					if (cursor.moveToFirst()) {
						Profile into = new Profile();
						into.loadFromCursor(cursor);
						return into;
					}
				} finally {
					cursor.close();
				}
			}
			return null;			
		}
	}
	
	public static class Location implements BaseColumns {
		public static final String TABLE_NAME = "locations";
		
		public static final String COLUMN_NAME_LOG_ID = "log_id";
		
		public static final String COLUMN_NAME_ACTIVITY = "activity";
		public static final String COLUMN_NAME_CONFIDENCE = "confidence";
		
		public static final String COLUMN_NAME_TIME = "time";
		public static final String COLUMN_NAME_LATITUDE = "latitude";
		public static final String COLUMN_NAME_LONGITUDE = "longitude";
		public static final String COLUMN_NAME_ALTITUDE = "altitude";
		public static final String COLUMN_NAME_HAS_ALTITUDE = "has_altitude";
		public static final String COLUMN_NAME_BEARING = "bearing";
		public static final String COLUMN_NAME_HAS_BEARING = "has_bearing";
		public static final String COLUMN_NAME_SPEED = "speed";
		public static final String COLUMN_NAME_HAS_SPEED = "has_speed";
		public static final String COLUMN_NAME_ACCURACY_DISTANCE = "location_accuracy";
		public static final String COLUMN_NAME_HAS_ACCURACY_DISTANCE = "has_location_accuracy";	
		
		public static final String COLUMN_NAME_BATTERY = "battery";
		public static final String COLUMN_NAME_ACCURACY_SETTING = "accuracy_setting";
		public static final String COLUMN_NAME_IS_SEGMENT_START = "is_segment_start";	

		public static final String SQL_CREATE_TABLE =
				"CREATE TABLE " + TABLE_NAME + " (" +
						_ID + " INTEGER PRIMARY KEY AUTOINCREMENT" + COMMA_SEP +
						
						COLUMN_NAME_LOG_ID + TYPE_INTEGER + COMMA_SEP +
						
						COLUMN_NAME_ACTIVITY + TYPE_INTEGER + COMMA_SEP +
						COLUMN_NAME_CONFIDENCE + TYPE_INTEGER + COMMA_SEP +
						
						COLUMN_NAME_TIME + TYPE_INTEGER + COMMA_SEP +
						COLUMN_NAME_LATITUDE + TYPE_TEXT + COMMA_SEP +
						COLUMN_NAME_LONGITUDE + TYPE_TEXT + COMMA_SEP +
						COLUMN_NAME_ALTITUDE + TYPE_TEXT + COMMA_SEP +
						COLUMN_NAME_HAS_ALTITUDE + TYPE_INTEGER + COMMA_SEP +
						COLUMN_NAME_BEARING + TYPE_TEXT + COMMA_SEP +
						COLUMN_NAME_HAS_BEARING + TYPE_INTEGER + COMMA_SEP +
						COLUMN_NAME_SPEED + TYPE_TEXT + COMMA_SEP +
						COLUMN_NAME_HAS_SPEED + TYPE_INTEGER + COMMA_SEP +
						COLUMN_NAME_ACCURACY_DISTANCE + TYPE_TEXT + COMMA_SEP +
						COLUMN_NAME_HAS_ACCURACY_DISTANCE + TYPE_INTEGER + COMMA_SEP +
						
						COLUMN_NAME_BATTERY + TYPE_INTEGER + COMMA_SEP +
						COLUMN_NAME_ACCURACY_SETTING + TYPE_INTEGER + COMMA_SEP +
						COLUMN_NAME_IS_SEGMENT_START + TYPE_INTEGER +
				")";
		
		public static final String[] SQL_CREATE_INDICES = new String[] { 
		};

		public static final String SQL_DROP_TABLE =
			    "DROP TABLE IF EXISTS " + TABLE_NAME;
		
		private long id = -1;
		private long logid = 0;
		private Activity activity = Activity.UNKNOWN;
		private int confidence = 0;
		private long time = 0;
		private double latitude = 0;
		private double longitude = 0;
		private double altitude = 0;
		private boolean hasAltitude = false;
		private float bearing = 0;
		private boolean hasBearing = false;
		private float speed = 0;
		private boolean hasSpeed = false;
		private float accuracyDistance = 0;
		private boolean hasAccuracyDistance = false;
		private int battery = 0;
		private Accuracy accuracySetting = Accuracy.NONE;
		private boolean isSegmentStart = false;
		
		public long getId() { return id; }
		
		public long getLogId() { return logid; }
		public Activity getActivity() { return activity; }
		public int getConfidence() { return confidence; }
		public long getTime() { return time; }
		public double getLatitude() { return latitude; }
		public double getLongitude() { return longitude; }
		public double getAltitude() { return altitude; }
		public boolean hasAltitude() { return hasAltitude; }
		public float getBearing() { return bearing; }
		public boolean hasBearing() { return hasBearing; }
		public float getSpeed() { return speed; }
		public boolean hasSpeed() { return hasSpeed; }
		public float getAccuracyDistance() { return accuracyDistance; }
		public boolean hasAccuracyDistance() { return hasAccuracyDistance; }
		public int getBattery() { return battery; }
		public Accuracy getAccuracySetting() { return accuracySetting; }
		public boolean isSegmentStart() { return isSegmentStart; } 
		
		public Location setLogId(long logid) { this.logid = logid; return this; }
		public Location setActivity(Activity activity) { this.activity = activity; return this; }
		public Location setConfidence(int confidence) { this.confidence = confidence; return this; }
		public Location setTime(long time) { this.time = time; return this; }
		public Location setLatitude(double latitude) { this.latitude = latitude; return this; }
		public Location setLongitude(double longitude) { this.longitude = longitude; return this; }
		public Location setAltitude(double altitude) { this.altitude = altitude; return this; }
		public Location hasAltitude(boolean hasAltitude) { this.hasAltitude = hasAltitude; return this; }
		public Location setBearing(float bearing) { this.bearing = bearing; return this; }
		public Location hasBearing(boolean hasBearing) { this.hasBearing = hasBearing; return this; }
		public Location setSpeed(float speed) { this.speed = speed; return this; }
		public Location hasSpeed(boolean hasSpeed) { this.hasSpeed = hasSpeed; return this; }
		public Location setAccuracyDistance(float accuracyDistance) { this.accuracyDistance = accuracyDistance; return this; }
		public Location hasAccuracyDistance(boolean hasAccuracyDistance) { this.hasAccuracyDistance = hasAccuracyDistance; return this; }
		public Location setBattery(int battery) { this.battery = battery; return this; }
		public Location setAccuracySetting(Accuracy accuracySetting) { this.accuracySetting = accuracySetting; return this; }
		public Location isSegmentStart(boolean isSegmentStart) { this.isSegmentStart = isSegmentStart; return this; }

		public void loadFromCursor(Cursor cursor) {
			id = cursor.getLong(cursor.getColumnIndex(_ID));
			logid = cursor.getLong(cursor.getColumnIndex(COLUMN_NAME_LOG_ID));
			
			activity = activityFromInt(cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_ACTIVITY)));
			confidence = cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_CONFIDENCE));
			time = cursor.getLong(cursor.getColumnIndex(COLUMN_NAME_TIME));
			latitude = Double.valueOf(cursor.getString(cursor.getColumnIndex(COLUMN_NAME_LATITUDE)));
			longitude = Double.valueOf(cursor.getString(cursor.getColumnIndex(COLUMN_NAME_LONGITUDE)));
			altitude = Double.valueOf(cursor.getString(cursor.getColumnIndex(COLUMN_NAME_ALTITUDE)));
			hasAltitude = (cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_HAS_ALTITUDE)) == 1);
			bearing = Float.valueOf(cursor.getString(cursor.getColumnIndex(COLUMN_NAME_BEARING)));
			hasBearing = (cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_HAS_BEARING)) == 1);
			speed = Float.valueOf(cursor.getString(cursor.getColumnIndex(COLUMN_NAME_SPEED)));
			hasSpeed = (cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_HAS_SPEED)) == 1);
			accuracyDistance = Float.valueOf(cursor.getString(cursor.getColumnIndex(COLUMN_NAME_ACCURACY_DISTANCE)));
			hasAccuracyDistance = (cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_HAS_ACCURACY_DISTANCE)) == 1);
			battery = cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_BATTERY));
			accuracySetting = accuracyFromInt(cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_ACCURACY_SETTING)));
			isSegmentStart = (cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_IS_SEGMENT_START)) == 1);
		}	
		
		public void loadFromDetectedActivity(DetectedActivity detectedActivity) {
			activity = activityFromDetectedActivity(detectedActivity);
			confidence = detectedActivity.getConfidence();
		}

		public void loadFromLocation(android.location.Location location) {
			time = location.getTime();
			latitude = location.getLatitude();
			longitude = location.getLongitude();
			altitude = location.getAltitude();
			hasAltitude = location.hasAltitude();
			bearing = location.getBearing();
			hasBearing = location.hasBearing();
			speed = location.getSpeed();
			hasSpeed = location.hasSpeed();
			accuracyDistance = location.getAccuracy();
			hasAccuracyDistance = location.hasAccuracy();
		}
		
		public long saveToDatabase(Helper helper) {
			SQLiteDatabase db = helper.getWritableDatabase();
			
			ContentValues values = new ContentValues();
			
			values.put(COLUMN_NAME_LOG_ID, logid);
			values.put(COLUMN_NAME_ACTIVITY, activityToInt(activity));			
			values.put(COLUMN_NAME_CONFIDENCE, confidence);
			values.put(COLUMN_NAME_TIME, time);
			values.put(COLUMN_NAME_LATITUDE, String.valueOf(latitude));
			values.put(COLUMN_NAME_LONGITUDE, String.valueOf(longitude));
			values.put(COLUMN_NAME_ALTITUDE, String.valueOf(altitude));
			values.put(COLUMN_NAME_HAS_ALTITUDE, hasAltitude ? 1 : 0);
			values.put(COLUMN_NAME_BEARING, String.valueOf(bearing));
			values.put(COLUMN_NAME_HAS_BEARING, hasBearing ? 1 : 0);
			values.put(COLUMN_NAME_SPEED, String.valueOf(speed));
			values.put(COLUMN_NAME_HAS_SPEED, hasSpeed ? 1 : 0);
			values.put(COLUMN_NAME_ACCURACY_DISTANCE, String.valueOf(accuracyDistance));
			values.put(COLUMN_NAME_HAS_ACCURACY_DISTANCE, hasAccuracyDistance ? 1 : 0);	
			values.put(COLUMN_NAME_BATTERY, battery);
			values.put(COLUMN_NAME_ACCURACY_SETTING, accuracyToInt(accuracySetting));
			values.put(COLUMN_NAME_IS_SEGMENT_START, isSegmentStart ? 1 : 0);
			
			if (id < 0) {
				id = db.insert(TABLE_NAME, null, values);							
			} else {
				db.update(TABLE_NAME, values, _ID + " = ?", new String[] { String.valueOf(id) });
			}
			if (id >= 0) {
				helper.notifyUri(LogsProvider.URILocations());
				helper.notifyUri(LogsProvider.URILocation(id));
				helper.notifyBroadcast(TABLE_NAME, id);
			}
			return id;
		}
		
		public static Location getById(Helper helper, long id, Location into) {
			Cursor cursor = helper.getReadableDatabase().rawQuery("SELECT * FROM " + TABLE_NAME + " WHERE " + _ID + " = ?", new String[] { String.valueOf(id) });
			if (cursor != null) {
				try {
					if (cursor.moveToFirst()) {
						if (into == null) into = new Location();
						into.loadFromCursor(cursor);
						return into;
					}
				} finally {
					cursor.close();
				}
			}
			return null;
		}
		
		public static void delete(Helper helper, long id) {
			helper.getWritableDatabase().delete(TABLE_NAME, _ID + " = ?", new String[] { String.valueOf(id) });
			helper.notifyUri(LogsProvider.URILocations());
			helper.notifyUri(LogsProvider.URILocation(id));
			helper.notifyBroadcast(TABLE_NAME, id);
		}
		
		public static void deleteAll(Helper helper) {
			helper.getWritableDatabase().delete(TABLE_NAME, null, null);
			helper.notifyUri(LogsProvider.URILocations());
			helper.notifyUri(LogsProvider.URILocation(0));
			helper.notifyBroadcast(TABLE_NAME, 0);
		}

		public static Location copy(Helper helper, Location location) {
			Location ret = new Location();
			ret.setLogId(location.getLogId());
			ret.setActivity(location.getActivity());
			ret.setConfidence(location.getConfidence());
			ret.setTime(location.getTime());
			ret.setLatitude(location.getLatitude());
			ret.setLongitude(location.getLongitude());
			ret.setAltitude(location.getAltitude());
			ret.hasAltitude(location.hasAltitude());
			ret.setBearing(location.getBearing());
			ret.hasBearing(location.hasBearing());
			ret.setSpeed(location.getSpeed());
			ret.hasSpeed(location.hasSpeed());
			ret.setAccuracyDistance(location.getAccuracyDistance());
			ret.hasAccuracyDistance(location.hasAccuracyDistance());
			ret.setBattery(location.getBattery());
			ret.setAccuracySetting(location.getAccuracySetting());
			ret.isSegmentStart(location.isSegmentStart());
			ret.saveToDatabase(helper);
			return ret;
		}

		public static Cursor list(Helper helper) {
			return helper.getReadableDatabase().query(TABLE_NAME, null, null, null, null, null, _ID);			
		}		
	}
}
