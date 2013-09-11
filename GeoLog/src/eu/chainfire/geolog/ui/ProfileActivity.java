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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import eu.chainfire.geolog.R;
import eu.chainfire.geolog.data.Database;
import eu.chainfire.geolog.data.Database.Profile.ActivitySettings;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;

@SuppressWarnings("deprecation")
public class ProfileActivity extends PreferenceActivity {
	public static final String EXTRA_PROFILE_ID = "eu.chainfire.geolog.ProfileActivity.EXTRA.PROFILE_ID";
	
	public static void launchActivity(Activity activity, long id) {
		Intent i = new Intent(activity, ProfileActivity.class);
		i.putExtra(ProfileActivity.EXTRA_PROFILE_ID, id);
		activity.startActivityForResult(i, 0);		
	}
	
	private Database.Helper helper = null;
	private Database.Profile profile = null;
	private List<Preference> dependents = new ArrayList<Preference>();
	private EditTextPreference prefActivityIntervalStill = null;
	private EditTextPreference prefActivityIntervalFoot = null;
	private EditTextPreference prefActivityIntervalBicycle = null;
	private EditTextPreference prefActivityIntervalVehicle = null;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setResult(RESULT_OK);
		
		long id = 0;
		if (getIntent() != null) {
			id = getIntent().getLongExtra(EXTRA_PROFILE_ID, 0);
		} else if (savedInstanceState != null) {
			id = savedInstanceState.getLong(EXTRA_PROFILE_ID, 0);
		}		
		if (id == 0) {
			finish();
		} else {		
			helper = Database.Helper.getInstance(this);
			profile = Database.Profile.getById(Database.Helper.getInstance(this), id, null);
			setTitle(profile.getName());
			setPreferenceScreen(createPreferenceHierarchy());
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.
			add(R.string.menu_done).
			setOnMenuItemClickListener(new OnMenuItemClickListener() {				
				@Override
				public boolean onMenuItemClick(MenuItem item) {
					finish();
					return false;
				}
			}).
			setIcon(R.drawable.ic_action_done).
			setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
			return true;
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putLong(EXTRA_PROFILE_ID, profile.getId());
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
	
	private int getActivityCaption(Database.Activity activity) {
		switch (activity) {
		case UNKNOWN	: return R.string.profile_preference_caption_unknown;
		case STILL		: return R.string.profile_preference_caption_still;
		case FOOT		: return R.string.profile_preference_caption_foot;
		case BICYCLE	: return R.string.profile_preference_caption_bicycle;
		case VEHICLE	: return R.string.profile_preference_caption_vehicle;
		}					
		return R.string.profile_preference_caption_unknown;
	}
	
	private String accuracyToDescription(Database.Accuracy accuracy) {
		switch (accuracy) {
		case NONE		: return getString(R.string.profile_preference_accuracy_none); 
		case LOW		: return getString(R.string.profile_preference_accuracy_low);
		case HIGH		: return getString(R.string.profile_preference_accuracy_high);
		}
		return getString(R.string.profile_preference_accuracy_none);
	}
	
	private String accuracyToValue(Database.Accuracy accuracy) {
		switch (accuracy) {
		case NONE		: return "none"; 
		case LOW		: return "low";
		case HIGH		: return "high";
		}
		return "none";
	}

	private Database.Accuracy accuracyFromValue(String accuracy) {
		if ("none".equals(accuracy)) return Database.Accuracy.NONE;
		if ("low".equals(accuracy)) return Database.Accuracy.LOW;
		if ("high".equals(accuracy)) return Database.Accuracy.HIGH;
		return Database.Accuracy.NONE;
	}
	
	private void updateActivitySettings(Database.Profile.ActivitySettings settings, Preference prefAccuracy, Preference prefLocationInterval, Preference prefActivityInterval) {
		if (prefAccuracy != null) prefAccuracy.setSummary(formatValue(accuracyToDescription(settings.getAccuracy())));		
		if (prefLocationInterval != null) prefLocationInterval.setSummary(formatValue(settings.getAccuracy() == Database.Accuracy.NONE ? 0 : settings.getLocationInterval()));		
		if (prefActivityInterval != null) prefActivityInterval.setSummary(formatValue(settings.getActivityInterval()));		
	}

	private void addActivitySettings(PreferenceScreen root, Database.Activity activity) {				
		final Database.Profile.ActivitySettings settings = profile.getActivitySettings(activity);
		
		final boolean isUnknown = (activity == Database.Activity.UNKNOWN); 
		final boolean enabled = (
				isUnknown ||
				(profile.getUnknown().getActivityInterval() != 0)				
		);		
			
		final PreferenceCategory catActivity = Pref.Category(this, root, getActivityCaption(activity));				
		final ListPreference prefAccuracy = Pref.List(
				this, 
				catActivity, 
				getString(R.string.profile_preference_accuracy_title),
				"",
				getString(R.string.profile_preference_accuracy_popup),
				null, 
				accuracyToValue(settings.getAccuracy()), 
				new String[] {
					accuracyToDescription(Database.Accuracy.NONE),
					accuracyToDescription(Database.Accuracy.LOW),
					accuracyToDescription(Database.Accuracy.HIGH)						
				},
				new String[] {
					accuracyToValue(Database.Accuracy.NONE),
					accuracyToValue(Database.Accuracy.LOW),
					accuracyToValue(Database.Accuracy.HIGH)			
				},
				enabled
		); 
		final EditTextPreference prefLocationInterval = Pref.Edit(
				this, 
				catActivity, 
				getString(R.string.profile_preference_location_interval_title), 
				"", 
				getString(R.string.profile_preference_location_interval_popup), 
				null, 
				String.valueOf(settings.getLocationInterval()), 
				enabled,
				InputType.TYPE_CLASS_NUMBER
		);
		final EditTextPreference prefActivityInterval = Pref.Edit(
				this, 
				catActivity, 
				getString(R.string.profile_preference_activity_interval_title), 
				"", 
				getString(R.string.profile_preference_activity_interval_popup), 
				null, 
				String.valueOf(settings.getActivityInterval()), 
				enabled,
				InputType.TYPE_CLASS_NUMBER
		);
		
		prefAccuracy.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {			
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				if ((newValue instanceof String) && (((String)newValue).length() > 0)) {
					Database.Accuracy val = accuracyFromValue((String)newValue);
					settings.setAccuracy(val);
					profile.saveToDatabase(helper);
					updateActivitySettings(settings, prefAccuracy, prefLocationInterval, prefActivityInterval);					
					return true;
				}
				return false;
			}
		});
		
		prefLocationInterval.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {			
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				if ((newValue instanceof String) && (((String)newValue).length() > 0)) {
					int val = Integer.parseInt((String)newValue, 10);
					settings.setLocationInterval(val);
					profile.saveToDatabase(helper);
					updateActivitySettings(settings, prefAccuracy, prefLocationInterval, prefActivityInterval);					
					return true;
				}
				return false;
			}
		});

		prefActivityInterval.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {			
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				if ((newValue instanceof String) && (((String)newValue).length() > 0)) {
					int val = Integer.parseInt((String)newValue, 10);
					settings.setActivityInterval(val);
					profile.saveToDatabase(helper);
					updateActivitySettings(settings, prefAccuracy, prefLocationInterval, prefActivityInterval);		
					
					if (isUnknown) {
						boolean enabled = (val != 0);
						for (Preference p : dependents) {
							p.setEnabled(enabled);
						}
						if (enabled) {
							for (ActivitySettings a : new ActivitySettings[] { 
								profile.getStill(),
								profile.getFoot(),
								profile.getBicycle(),
								profile.getVehicle()
							}) {
								if (a.getActivityInterval() == 0) a.setActivityInterval(val);
							}							
							profile.saveToDatabase(helper);
							
							prefActivityIntervalStill.setText(String.valueOf(profile.getStill().getActivityInterval()));
							prefActivityIntervalFoot.setText(String.valueOf(profile.getFoot().getActivityInterval()));
							prefActivityIntervalBicycle.setText(String.valueOf(profile.getBicycle().getActivityInterval()));
							prefActivityIntervalVehicle.setText(String.valueOf(profile.getVehicle().getActivityInterval()));
							
							updateActivitySettings(profile.getStill(), null, null, prefActivityIntervalStill);
							updateActivitySettings(profile.getFoot(), null, null, prefActivityIntervalFoot);
							updateActivitySettings(profile.getBicycle(), null, null, prefActivityIntervalBicycle);
							updateActivitySettings(profile.getVehicle(), null, null, prefActivityIntervalVehicle);
						}
					}
					return true;
				}
				return false;
			}
		});
				
		updateActivitySettings(settings, prefAccuracy, prefLocationInterval, prefActivityInterval);
		
		if (activity != Database.Activity.UNKNOWN) {
			dependents.add(prefAccuracy);
			dependents.add(prefLocationInterval);
			dependents.add(prefActivityInterval);
		}
		
		switch (activity) {
		case STILL: prefActivityIntervalStill = prefActivityInterval; break;
		case FOOT: prefActivityIntervalFoot = prefActivityInterval; break;
		case BICYCLE: prefActivityIntervalBicycle = prefActivityInterval; break;
		case VEHICLE: prefActivityIntervalVehicle = prefActivityInterval; break;
		}
	}
	
	private PreferenceScreen createPreferenceHierarchy() {
		PreferenceScreen root = getPreferenceManager().createPreferenceScreen(this);
				
		EditTextPreference prefName = new EditTextPreference(this);
    	prefName.setTitle(R.string.profile_preference_name_title);
    	prefName.setSummary(formatValue(profile.getName()));
    	prefName.setDefaultValue(profile.getName());
    	prefName.setDialogTitle(R.string.profile_preference_name_popup);			
		prefName.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {			
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				if ((newValue instanceof String) && (((String)newValue).length() > 0)) {
					String val = ((String)newValue).trim();
					setTitle(val);
					preference.setSummary(formatValue(val));
					profile.setName(val);
					profile.saveToDatabase(helper);
					return true;
				}
				return false;
			}
		});
    	root.addPreference(prefName);
    	
		EditTextPreference prefReduceAccuracyDelay = Pref.Edit(
				this, 
				null, 
				getString(R.string.profile_preference_reduce_accuracy_delay_title), 
				formatValue(profile.getReduceAccuracyDelay()), 
				getString(R.string.profile_preference_reduce_accuracy_delay_popup), 
				null, 
				String.valueOf(profile.getReduceAccuracyDelay()), 
				true,
				InputType.TYPE_CLASS_NUMBER
		);
		root.addPreference(prefReduceAccuracyDelay);
		prefReduceAccuracyDelay.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {			
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				if ((newValue instanceof String) && (((String)newValue).length() > 0)) {
					int val = Integer.parseInt((String)newValue, 10);
					profile.setReduceAccuracyDelay(val);
					profile.saveToDatabase(helper);
					preference.setSummary(formatValue(profile.getReduceAccuracyDelay()));
					return true;
				}
				return false;
			}
		});

    	addActivitySettings(root, Database.Activity.UNKNOWN);
    	addActivitySettings(root, Database.Activity.STILL);
    	addActivitySettings(root, Database.Activity.FOOT);
    	addActivitySettings(root, Database.Activity.BICYCLE);
    	addActivitySettings(root, Database.Activity.VEHICLE);
		
		return root;				
	}	
}
