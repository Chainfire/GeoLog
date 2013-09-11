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

package eu.chainfire.geolog.ui;

import java.sql.Date;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

import eu.chainfire.geolog.R;
import eu.chainfire.geolog.data.Database;
import eu.chainfire.geolog.data.Exporter;
import eu.chainfire.geolog.data.Exporter.Format;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.text.InputType;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.widget.DatePicker;
import android.widget.LinearLayout;
import android.widget.TimePicker;

@SuppressWarnings("deprecation")
public class ExportActivity extends PreferenceActivity {
	public static final String EXTRA_LOG_ID = "eu.chainfire.geolog.ProfileActivity.EXTRA.LOG_ID";
	
	public static final String PREF_FORMAT = "format";
	public static final String VALUE_FORMAT_GPX = "gpx";
	public static final String VALUE_FORMAT_KML = "kml";
	public static final String PREF_FORMAT_DEFAULT = VALUE_FORMAT_GPX;
	
	public static final String PREF_TRACK_MERGE_GAP = "track_merge_gap";
	public static final int PREF_TRACK_MERGE_GAP_DEFAULT = 900;
	
	public static final String PREF_DATETIME_START = "datetime_start";
	public static final long PREF_DATETIME_START_DEFAULT = -1;
	
	public static final String PREF_DATETIME_END = "datetime_end";
	public static final long PREF_DATETIME_END_DEFAULT = -1;
	
	public static final String PREF_TRACK_MIN_POINTS = "track_min_points";
	public static final String PREF_TRACK_MIN_TIME = "track_min_time";
	public static final String PREF_TRACK_MIN_DISTANCE = "track_min_distance";
	public static final int PREF_TRACK_MIN_POINTS_DEFAULT = 5;
	public static final int PREF_TRACK_MIN_TIME_DEFAULT = 60;
	public static final int PREF_TRACK_MIN_DISTANCE_DEFAULT = 1000;

	public static final String PREF_ACC_LP_UNKNOWN = "acc_lp_unknown";
	public static final String PREF_ACC_LP_STILL = "acc_lp_still";
	public static final String PREF_ACC_LP_FOOT = "acc_lp_foot";
	public static final String PREF_ACC_LP_BICYCLE = "acc_lp_bicycle";
	public static final String PREF_ACC_LP_VEHICLE = "acc_lp_vehicle";
	public static final String PREF_ACC_HA_UNKNOWN = "acc_ha_unknown";
	public static final String PREF_ACC_HA_STILL = "acc_ha_still";
	public static final String PREF_ACC_HA_FOOT = "acc_ha_foot";
	public static final String PREF_ACC_HA_BICYCLE = "acc_ha_bicycle";
	public static final String PREF_ACC_HA_VEHICLE = "acc_ha_vehicle";

	public static final long PREF_ACC_LP_UNKNOWN_DEFAULT = 1600;
	public static final long PREF_ACC_LP_STILL_DEFAULT = 200;
	public static final long PREF_ACC_LP_FOOT_DEFAULT = 400;
	public static final long PREF_ACC_LP_BICYCLE_DEFAULT = 800;
	public static final long PREF_ACC_LP_VEHICLE_DEFAULT = 1600;
	public static final long PREF_ACC_HA_UNKNOWN_DEFAULT = 800;
	public static final long PREF_ACC_HA_STILL_DEFAULT = 100;
	public static final long PREF_ACC_HA_FOOT_DEFAULT = 200;
	public static final long PREF_ACC_HA_BICYCLE_DEFAULT = 400;
	public static final long PREF_ACC_HA_VEHICLE_DEFAULT = 800;

	public static void launchActivity(Activity activity, long id) {
		Intent i = new Intent(activity, ExportActivity.class);
		i.putExtra(ExportActivity.EXTRA_LOG_ID, id);
		activity.startActivityForResult(i, 0);		
	}
	
	private SharedPreferences prefs = null;
	private ListPreference prefFormat = null;
	private EditTextPreference prefMergeTrackGap = null;
	private DateTimePickerPreference prefDateStart = null;
	private DateTimePickerPreference prefDateEnd = null;
	private EditTextPreference prefTrackMinPoints = null;
	private EditTextPreference prefTrackMinTime = null;
	private DistanceEditTextPreference prefTrackMinDistance = null;
	private DistanceEditTextPreference prefAccLPUnknown = null;
	private DistanceEditTextPreference prefAccLPStill = null;
	private DistanceEditTextPreference prefAccLPFoot = null;
	private DistanceEditTextPreference prefAccLPBicycle = null;
	private DistanceEditTextPreference prefAccLPVehicle = null;
	private DistanceEditTextPreference prefAccHAUnknown = null;
	private DistanceEditTextPreference prefAccHAStill = null;
	private DistanceEditTextPreference prefAccHAFoot = null;
	private DistanceEditTextPreference prefAccHABicycle = null;
	private DistanceEditTextPreference prefAccHAVehicle = null;
	
	private volatile long logid = 0;
	
	private volatile long dateFirst = -1;
	private volatile long dateLast = -1;
	
	private volatile boolean metric = true;
	
	private volatile boolean doneLoading = false;
	private volatile View progressBar = null;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setResult(RESULT_OK);
		
		logid = 0;
		if (getIntent() != null) {
			logid = getIntent().getLongExtra(EXTRA_LOG_ID, 0);
		} else if (savedInstanceState != null) {
			logid = savedInstanceState.getLong(EXTRA_LOG_ID, 0);
		}
		
		setTitle(getString(R.string.export_preference_title));		
		getListView().setVisibility(View.GONE);
		
		LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		if (getListView().getParent() instanceof LinearLayout) {
			progressBar = inflater.inflate(R.layout.progressbar, null);
			((LinearLayout)(getListView().getParent())).addView(progressBar);
		}		

		prefs = PreferenceManager.getDefaultSharedPreferences(ExportActivity.this);
		updateRecordCount(true);
	}

	@Override
	protected void onDestroy() {
		prefs.unregisterOnSharedPreferenceChangeListener(preferencesListener);
		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.
			add(R.string.menu_export).
			setOnMenuItemClickListener(new OnMenuItemClickListener() {				
				@Override
				public boolean onMenuItemClick(MenuItem item) {
					if (!doneLoading) return false;
					
					Exporter exporter = new Exporter(ExportActivity.this);
					exporter.setFormat(prefs.getString(PREF_FORMAT, PREF_FORMAT_DEFAULT).equals(VALUE_FORMAT_GPX) ? Format.GPX : Format.KML);
					exporter.setTrackMergeGap(Long.parseLong(prefs.getString(PREF_TRACK_MERGE_GAP, String.valueOf(PREF_TRACK_MERGE_GAP_DEFAULT)), 10));
					exporter.setDateStart(prefs.getLong(PREF_DATETIME_START, PREF_DATETIME_START_DEFAULT));
					exporter.setDateEnd(prefs.getLong(PREF_DATETIME_END, PREF_DATETIME_END_DEFAULT));
					exporter.setTrackMinPoints(Long.parseLong(prefs.getString(PREF_TRACK_MIN_POINTS, String.valueOf(PREF_TRACK_MIN_POINTS_DEFAULT)), 10));
					exporter.setTrackMinTime(Long.parseLong(prefs.getString(PREF_TRACK_MIN_TIME, String.valueOf(PREF_TRACK_MIN_TIME_DEFAULT)), 10));
					exporter.setTrackMinDistance(Long.parseLong(prefs.getString(PREF_TRACK_MIN_DISTANCE, String.valueOf(PREF_TRACK_MIN_DISTANCE_DEFAULT)), 10));
					exporter.setAccuracyLowPowerUnknown(Long.parseLong(prefs.getString(PREF_ACC_LP_UNKNOWN, String.valueOf(PREF_ACC_LP_UNKNOWN_DEFAULT)), 10));
					exporter.setAccuracyLowPowerStill(Long.parseLong(prefs.getString(PREF_ACC_LP_STILL, String.valueOf(PREF_ACC_LP_STILL_DEFAULT)), 10));
					exporter.setAccuracyLowPowerFoot(Long.parseLong(prefs.getString(PREF_ACC_LP_FOOT, String.valueOf(PREF_ACC_LP_FOOT_DEFAULT)), 10));
					exporter.setAccuracyLowPowerBicycle(Long.parseLong(prefs.getString(PREF_ACC_LP_BICYCLE, String.valueOf(PREF_ACC_LP_BICYCLE_DEFAULT)), 10));
					exporter.setAccuracyLowPowerVehicle(Long.parseLong(prefs.getString(PREF_ACC_LP_VEHICLE, String.valueOf(PREF_ACC_LP_VEHICLE_DEFAULT)), 10));
					exporter.setAccuracyHighAccuracyUnknown(Long.parseLong(prefs.getString(PREF_ACC_HA_UNKNOWN, String.valueOf(PREF_ACC_HA_UNKNOWN_DEFAULT)), 10));
					exporter.setAccuracyHighAccuracyStill(Long.parseLong(prefs.getString(PREF_ACC_HA_STILL, String.valueOf(PREF_ACC_HA_STILL_DEFAULT)), 10));
					exporter.setAccuracyHighAccuracyFoot(Long.parseLong(prefs.getString(PREF_ACC_HA_FOOT, String.valueOf(PREF_ACC_HA_FOOT_DEFAULT)), 10));
					exporter.setAccuracyHighAccuracyBicycle(Long.parseLong(prefs.getString(PREF_ACC_HA_BICYCLE, String.valueOf(PREF_ACC_HA_BICYCLE_DEFAULT)), 10));
					exporter.setAccuracyHighAccuracyVehicle(Long.parseLong(prefs.getString(PREF_ACC_HA_VEHICLE, String.valueOf(PREF_ACC_HA_VEHICLE_DEFAULT)), 10));
					exporter.export(getQuery(false));					
					return true;
				}
			}).
			setIcon(R.drawable.ic_action_export).
			setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		menu.
			add(R.string.menu_cancel).
			setOnMenuItemClickListener(new OnMenuItemClickListener() {				
				@Override
				public boolean onMenuItemClick(MenuItem item) {
					finish();
					return false;
				}
			}).
			setIcon(R.drawable.ic_action_cancel).
			setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
			return true;
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putLong(EXTRA_LOG_ID, logid);
		super.onSaveInstanceState(outState);
	}

	@Override
	protected void onUserLeaveHint() {
		finish();
		super.onUserLeaveHint();
	}

	@Override
	public void onBackPressed() {
		finish();
		super.onBackPressed();
	}	
	
	private String formatValue(String value) {
		return String.format(Locale.ENGLISH, "[ %s ]", value);
	}
	
	private String formatValue(int interval) {
		if (interval == 0) return formatValue(getString(R.string.profile_preference_format_disabled));
			
		int hours = interval / 3600;
		interval %= 3600;
		int minutes = interval / 60;
		interval %= 60;
		int seconds = interval;
		
		StringBuilder b = new StringBuilder();
		if (hours > 0) {
			b.append(String.format(Locale.ENGLISH, "%d:%02d:%02d", hours, minutes, seconds));
		} else {
			b.append(String.format(Locale.ENGLISH, "%d:%02d", minutes, seconds));			
		}
		b.append(" - ");
		if (hours > 0) {
			b.append(String.format(Locale.ENGLISH, getString(R.string.profile_preference_format_interval_hours), hours, minutes, seconds));
		} else {
			b.append(String.format(Locale.ENGLISH, getString(R.string.profile_preference_format_interval), minutes, seconds));			
		}		

		return formatValue(b.toString());
	}
	
    private DistanceEditTextPreference editDistance(Context context, PreferenceCategory category, int caption, int summary, int dialogCaption, String key, Object defaultValue, boolean enabled) {
    	DistanceEditTextPreference retval = new DistanceEditTextPreference(context);
    	retval.setMetric(metric);
    	if (caption > 0) retval.setTitle(caption);
    	if (summary > 0) retval.setSummary(summary);
    	retval.setEnabled(enabled);
    	retval.setKey(key);
    	retval.setDefaultValue(defaultValue);
    	if (dialogCaption > 0) retval.setDialogTitle(dialogCaption);
   		retval.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
    	if (category != null) category.addPreference(retval);
    	return retval;
    }    	

	private PreferenceScreen createPreferenceHierarchy() {
		metric = !prefs.getString(SettingsFragment.PREF_UNITS, SettingsFragment.PREF_UNITS_DEFAULT).equals(SettingsFragment.VALUE_UNITS_IMPERIAL);
		
		PreferenceScreen root = getPreferenceManager().createPreferenceScreen(this);
		
		prefFormat = Pref.List(
				this, 
				null, 
				R.string.export_preference_format_title, 
				0, 
				R.string.export_preference_format_popup, 
				PREF_FORMAT, 
				PREF_FORMAT_DEFAULT, 
				new String[] {
					getString(R.string.export_preference_format_gpx),
					getString(R.string.export_preference_format_kml)
				},
				new String[] {
					VALUE_FORMAT_GPX,
					VALUE_FORMAT_KML
				},
				true
		);
		root.addPreference(prefFormat);
		
		prefMergeTrackGap = Pref.Edit(
				this, 
				null,
				R.string.export_preference_track_merge_gap_title,
				0, 
				R.string.export_preference_track_merge_gap_popup,
				PREF_TRACK_MERGE_GAP, 
				String.valueOf(PREF_TRACK_MERGE_GAP_DEFAULT), 
				true,
				InputType.TYPE_CLASS_NUMBER
		);
		root.addPreference(prefMergeTrackGap);
		
		PreferenceCategory catDateFilter = Pref.Category(this, root, R.string.export_preference_datefilter_category);
		
		prefDateStart = new DateTimePickerPreference(this, prefs, PREF_DATETIME_START, PREF_DATETIME_START_DEFAULT, dateFirst, dateLast, false);
		prefDateStart.setTitle(getString(R.string.export_preference_date_start_title));
		prefDateStart.setSummary("");
		prefDateStart.setPopupTitle(getString(R.string.export_preference_date_start_popup));
		catDateFilter.addPreference(prefDateStart.getPreference());
		
		prefDateEnd = new DateTimePickerPreference(this, prefs, PREF_DATETIME_END, PREF_DATETIME_END_DEFAULT, dateFirst, dateLast, true);
		prefDateEnd.setTitle(getString(R.string.export_preference_date_end_title));
		prefDateEnd.setSummary("");
		prefDateEnd.setPopupTitle(getString(R.string.export_preference_date_end_popup));
		catDateFilter.addPreference(prefDateEnd.getPreference());
		
		int popup = metric ? R.string.export_preference_accuracy_popup_meters : R.string.export_preference_accuracy_popup_feet;
		
		PreferenceCategory catTrack = Pref.Category(this, root, R.string.export_preference_trackfilter_category);
		prefTrackMinPoints = Pref.Edit(this, catTrack, R.string.export_preference_track_min_points_title, 0, R.string.export_preference_track_min_points_popup, PREF_TRACK_MIN_POINTS, String.valueOf(PREF_TRACK_MIN_POINTS_DEFAULT), true, InputType.TYPE_CLASS_NUMBER);
		prefTrackMinTime = Pref.Edit(this, catTrack, R.string.export_preference_track_min_time_title, 0, R.string.export_preference_track_min_time_popup, PREF_TRACK_MIN_TIME, String.valueOf(PREF_TRACK_MIN_TIME_DEFAULT), true, InputType.TYPE_CLASS_NUMBER);
		prefTrackMinDistance = editDistance(this, catTrack, R.string.export_preference_track_min_distance_title, 0, popup, PREF_TRACK_MIN_DISTANCE, String.valueOf(PREF_TRACK_MIN_DISTANCE_DEFAULT), true);
		
		PreferenceCategory catAccLP = Pref.Category(this, root, R.string.export_preference_lowpowerfilter_category);		
		prefAccLPUnknown = editDistance(this, catAccLP, R.string.profile_preference_caption_unknown, 0, popup, PREF_ACC_LP_UNKNOWN, String.valueOf(PREF_ACC_LP_UNKNOWN_DEFAULT), true);
		prefAccLPStill = editDistance(this, catAccLP, R.string.profile_preference_caption_still, 0, popup, PREF_ACC_LP_STILL, String.valueOf(PREF_ACC_LP_STILL_DEFAULT), true);
		prefAccLPFoot = editDistance(this, catAccLP, R.string.profile_preference_caption_foot, 0, popup, PREF_ACC_LP_FOOT, String.valueOf(PREF_ACC_LP_FOOT_DEFAULT), true);
		prefAccLPBicycle = editDistance(this, catAccLP, R.string.profile_preference_caption_bicycle, 0, popup, PREF_ACC_LP_BICYCLE, String.valueOf(PREF_ACC_LP_BICYCLE_DEFAULT), true);
		prefAccLPVehicle = editDistance(this, catAccLP, R.string.profile_preference_caption_vehicle, 0, popup, PREF_ACC_LP_VEHICLE, String.valueOf(PREF_ACC_LP_VEHICLE_DEFAULT), true);

		PreferenceCategory catAccHA = Pref.Category(this, root, R.string.export_preference_highaccuracyfilter_category);
		prefAccHAUnknown = editDistance(this, catAccHA, R.string.profile_preference_caption_unknown, 0, popup, PREF_ACC_HA_UNKNOWN, String.valueOf(PREF_ACC_HA_UNKNOWN_DEFAULT), true);
		prefAccHAStill = editDistance(this, catAccHA, R.string.profile_preference_caption_still, 0, popup, PREF_ACC_HA_STILL, String.valueOf(PREF_ACC_HA_STILL_DEFAULT), true);
		prefAccHAFoot = editDistance(this, catAccHA, R.string.profile_preference_caption_foot, 0, popup, PREF_ACC_HA_FOOT, String.valueOf(PREF_ACC_HA_FOOT_DEFAULT), true);
		prefAccHABicycle = editDistance(this, catAccHA, R.string.profile_preference_caption_bicycle, 0, popup, PREF_ACC_HA_BICYCLE, String.valueOf(PREF_ACC_HA_BICYCLE_DEFAULT), true);
		prefAccHAVehicle = editDistance(this, catAccHA, R.string.profile_preference_caption_vehicle, 0, popup, PREF_ACC_HA_VEHICLE, String.valueOf(PREF_ACC_HA_VEHICLE_DEFAULT), true);

		updatePrefs(null);
		
		doneLoading = true;
		if (progressBar != null) progressBar.setVisibility(View.GONE);
		getListView().setVisibility(View.VISIBLE);		
				
		return root;				
	}	
	
	private SharedPreferences.OnSharedPreferenceChangeListener preferencesListener = new SharedPreferences.OnSharedPreferenceChangeListener() {		
		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
			updatePrefs(key);
		}
	};
	
	private Cursor getQuery(boolean all) {
		ArrayList<String> conditions = new ArrayList<String>();
		ArrayList<String> parameters = new ArrayList<String>();
		
		long startDate = -1;
		long endDate = -1;
		
		if (!all) {
			startDate = prefs.getLong(PREF_DATETIME_START, PREF_DATETIME_START_DEFAULT);
			endDate = prefs.getLong(PREF_DATETIME_END, PREF_DATETIME_END_DEFAULT);
		}
		
		if (startDate >= 0) {
			conditions.add(Database.Location.COLUMN_NAME_TIME + " >= ?");
			parameters.add(String.valueOf(startDate));
		}
		
		if (endDate >= 0) {
			conditions.add(Database.Location.COLUMN_NAME_TIME + " <= ?");
			parameters.add(String.valueOf(endDate));
		}
		
		String selection = "";
		for (int i = 0; i < conditions.size(); i++) {
			selection += conditions.get(i);
			if (i < conditions.size() - 1) selection += " AND ";
		}
		
		return Database.Helper.getInstance(this).getReadableDatabase().query(
				Database.Location.TABLE_NAME, 
				null, 
				selection, 
				parameters.toArray(new String[parameters.size()]),
				null, 
				null, 
				null
		);
	}
	
	private void updateRecordCount(boolean startup) {
		if (startup) {
			(new RecordCountAsync()).execute(0);
		} else {
			(new RecordCountAsync()).execute();			
		}
	}
	
	private void updatePrefs(String key) {
		if ((key == null) || (key.equals(PREF_FORMAT))) {
			String val = prefs.getString(PREF_FORMAT, PREF_FORMAT_DEFAULT);
			if (val.equals(VALUE_FORMAT_GPX)) prefFormat.setSummary(formatValue(getString(R.string.export_preference_format_gpx)));
			if (val.equals(VALUE_FORMAT_KML)) prefFormat.setSummary(formatValue(getString(R.string.export_preference_format_kml)));
		}
		
		if ((key == null) || (key.equals(PREF_TRACK_MERGE_GAP))) {
			prefMergeTrackGap.setSummary(formatValue(Integer.parseInt(prefs.getString(PREF_TRACK_MERGE_GAP, String.valueOf(PREF_TRACK_MERGE_GAP_DEFAULT)), 10)));
		}
		
		if ((key == null) || (key.equals(PREF_DATETIME_START))) {
			long date = prefs.getLong(PREF_DATETIME_START, PREF_DATETIME_START_DEFAULT);
			if (date == -1) {
				prefDateStart.setSummary(formatValue(getString(R.string.export_preference_date_disabled)));
			} else {
				prefDateStart.setSummary(formatValue(
						DateFormat.getMediumDateFormat(this).format(new Date(date)) + 
						", " +
						DateFormat.getTimeFormat(this).format(new Date(date))
				));
			}
		}		
		
		if ((key == null) || (key.equals(PREF_DATETIME_END))) {
			long date = prefs.getLong(PREF_DATETIME_END, PREF_DATETIME_END_DEFAULT);
			if (date == -1) {
				prefDateEnd.setSummary(formatValue(getString(R.string.export_preference_date_disabled)));
			} else {
				prefDateEnd.setSummary(formatValue(
						DateFormat.getMediumDateFormat(this).format(new Date(date)) + 
						", " +
						DateFormat.getTimeFormat(this).format(new Date(date))
				));
			}
		}	
		
		if ((key == null) || (key.equals(PREF_TRACK_MIN_POINTS))) {
			prefTrackMinPoints.setSummary(formatValue(String.valueOf(prefs.getString(PREF_TRACK_MIN_POINTS, String.valueOf(PREF_TRACK_MIN_POINTS_DEFAULT)))));
		}

		if ((key == null) || (key.equals(PREF_TRACK_MIN_TIME))) {
			prefTrackMinTime.setSummary(formatValue(Integer.parseInt(prefs.getString(PREF_TRACK_MIN_TIME, String.valueOf(PREF_TRACK_MIN_TIME_DEFAULT)), 10)));
		}
		
		for (EditTextPreference pref : new EditTextPreference[] {
			prefAccLPUnknown, prefAccLPStill, prefAccLPFoot, prefAccLPBicycle, prefAccLPVehicle,	
			prefAccHAUnknown, prefAccHAStill, prefAccHAFoot, prefAccHABicycle, prefAccHAVehicle,
			prefTrackMinDistance
		}) {
			if ((key == null) || (key.equals(pref.getKey()))) {
				long val = Long.parseLong(prefs.getString(pref.getKey(), pref.getText()), 10);
				if (val > 0) {
					if (metric) {
						pref.setSummary(formatValue(String.format(getString(R.string.export_preference_accuracy_format_meters), val)));
					} else {
						pref.setSummary(formatValue(String.format(getString(R.string.export_preference_accuracy_format_feet), (int)((float)val * SettingsFragment.METER_FEET_RATIO))));
					}
				} else {
					pref.setSummary(R.string.profile_preference_format_disabled);
				}
			}			
		}
		
		//if (key != null) updateRecordCount(false); we're not doing this
	}
	
	private class RecordCountAsync extends AsyncTask<Integer, Void, Integer> {
		private volatile boolean getDates = false;

		@Override
		protected Integer doInBackground(Integer... arg0) {
			getDates = (arg0.length > 0);
			
			Cursor c = getQuery(getDates);
			try {
				int count = c.getCount();
				
				if (getDates && (count > 0)) {
					Database.Location loc = new Database.Location();

					c.moveToFirst();
					loc.loadFromCursor(c);
					dateFirst = loc.getTime();
					
					c.moveToLast();
					loc.loadFromCursor(c);
					dateLast = loc.getTime();					
				}
				
				return count;
			} finally {
				c.close();
			}
		}

		@Override
		protected void onPostExecute(Integer result) {
			if (getDates) {
				setPreferenceScreen(createPreferenceHierarchy());
				prefs.registerOnSharedPreferenceChangeListener(preferencesListener);				
			}
		}		
	}
	
	@SuppressWarnings("unused")
	private class DateTimePickerPreference implements OnPreferenceClickListener {
		private Context context = null;
		private long datetime = -1;
		private String key = null;
		private Preference preference = null;
		private SharedPreferences prefs = null;
		private CharSequence popuptitle = "";
		private boolean is24hour = false;
		private boolean isEnd = false;
		private long minValue = -1;
		private long maxValue = -1;

		public DateTimePickerPreference(Context context, SharedPreferences prefs, String key, long defaultValue, long minValue, long maxValue, boolean isEnd) {
			this.context = context;
			this.prefs = prefs;
			this.key = key;
			if (key != null) {
				this.datetime = prefs.getLong(key, defaultValue);
			} else {
				this.datetime = defaultValue;
			}
			this.preference = new Preference(context);
			preference.setOnPreferenceClickListener(this);
			is24hour = DateFormat.is24HourFormat(context);
			this.isEnd = isEnd;
			this.minValue = minValue;
			this.maxValue = maxValue;
		}
		
		public void setDate(long datetime) {
			this.datetime = datetime;
			if (key != null) prefs.edit().putLong(key, datetime).commit();
		}
		
		public long getDate() {
			return this.datetime;
		}
		
		public Preference getPreference() {
			return this.preference;
		}
		
		public void setTitle(CharSequence title) {
			this.preference.setTitle(title);
		}

		public void setSummary(CharSequence summary) {
			this.preference.setSummary(summary);
		}
		
		public void setPopupTitle(CharSequence popuptitle) {
			this.popuptitle = popuptitle;
		}

		@Override
		public boolean onPreferenceClick(Preference preference) {
			LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			View v = inflater.inflate(R.layout.dialog_datetime, null);
							
			Calendar cal = Calendar.getInstance();
			if (datetime >= 0) {
				cal.setTimeInMillis(datetime);
			} else if (!isEnd && (minValue >= 0)) {
				cal.setTimeInMillis(minValue);
			} else if (isEnd && (maxValue >= 0)) {
				cal.setTimeInMillis(maxValue);
			}

			final DatePicker datePicker = (DatePicker)v.findViewById(R.id.datePicker);
			if (minValue >= 0) datePicker.setMinDate(minValue);
			if (maxValue >= 0) datePicker.setMaxDate(maxValue);			
			datePicker.updateDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
			
			final TimePicker timePicker = (TimePicker)v.findViewById(R.id.timePicker);
			timePicker.setIs24HourView(is24hour);
			timePicker.setCurrentHour(cal.get(Calendar.HOUR_OF_DAY));
			timePicker.setCurrentMinute(cal.get(Calendar.MINUTE));
				
			(new AlertDialog.Builder(context)).
				setTitle(popuptitle).
				setView(v).
				setPositiveButton(R.string.generic_ok, new OnClickListener() {					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						Calendar cal = Calendar.getInstance();
						cal.set(Calendar.YEAR, datePicker.getYear());
						cal.set(Calendar.MONTH, datePicker.getMonth());
						cal.set(Calendar.DAY_OF_MONTH, datePicker.getDayOfMonth());
						cal.set(Calendar.HOUR_OF_DAY, timePicker.getCurrentHour());
						cal.set(Calendar.MINUTE, timePicker.getCurrentMinute());
						if (!isEnd) {
							cal.set(Calendar.SECOND, 0);
							cal.set(Calendar.MILLISECOND, 0);
						} else {
							cal.set(Calendar.SECOND, 59);
							cal.set(Calendar.MILLISECOND, 999);							
						}
						setDate(cal.getTimeInMillis());
					}
				}).
				setNeutralButton(R.string.generic_disable, new OnClickListener() {					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						setDate(-1);
					}
				}).
				setNegativeButton(R.string.generic_cancel, null).
				setCancelable(true).				
				show();

			return true;
		}
	}
	
	@SuppressWarnings("unused")
	private class DistanceEditTextPreference extends EditTextPreference {
		private boolean isMetric = true;

		public DistanceEditTextPreference(Context context) {
			super(context);
		}
		
		public void setMetric(boolean isMetric) {
			this.isMetric = isMetric;
		}
		
		public boolean getMetric() {
			return isMetric;
		}
		
		private String toFeet(String value) {
			if (isMetric) return value;
			
			int val = Integer.parseInt(value, 10);
			val = (int)((float)val * SettingsFragment.METER_FEET_RATIO);
			return String.valueOf(val);			
		}

		private String fromFeet(String value) {
			if (isMetric) return value;
			
			int val = Integer.parseInt(value, 10);
			val = (int)((float)val / SettingsFragment.METER_FEET_RATIO);
			return String.valueOf(val);
		}
		
		@Override
		protected Object onGetDefaultValue(TypedArray a, int index) {
			return toFeet(a.getString(index));
		}
					
		@Override
		protected boolean persistString(String value) {
			return super.persistString(fromFeet(value));
		}
		
		@Override
		protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
			setText(toFeet(restoreValue ? getPersistedString(getText()) : (String) defaultValue));
		}		
	}
}
