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

import java.util.Locale;

import eu.chainfire.geolog.R;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.Preference.OnPreferenceClickListener;
import android.support.v4.content.LocalBroadcastManager;
import android.view.View;

@SuppressLint("NewApi")
public class 
	SettingsFragment 
extends 
	PreferenceListFragment
implements
	OnSharedPreferenceChangeListener
{
    public static final String APP_TITLE = "GeoLog";
    public static final String APP_COPYRIGHT = "Copyright (C) 2013 - Chainfire";
    public static final String APP_WEBSITE_URL = "http://forum.xda-developers.com/showthread.php?t=2386317";

	public static final String NOTIFY_BROADCAST = "eu.chainfire.geolog.PREFERENCES.UPDATED";
	public static final String EXTRA_KEY = "eu.chainfire.geolog.EXTRA.KEY";

	public static final String PREF_FOLLOW_SHOWN = "follow_shown";
    public static final String PREF_CURRENT_PROFILE = "current_profile";
    
    public static final float METER_FEET_RATIO = 3.28084f;
    public static final String PREF_UNITS = "pref_units";
    public static final String VALUE_UNITS_METRIC = "metric";
    public static final String VALUE_UNITS_IMPERIAL = "imperial";
    public static final String PREF_UNITS_DEFAULT = VALUE_UNITS_METRIC;
    
    private SharedPreferences prefs = null;
       
    private Resources resources = null;
    
    private String S(int id) { return resources.getString(id); }
    @SuppressWarnings("unused")
	private String[] SA(int id) { return resources.getStringArray(id); }   
    
    private ListPreference prefUnits = null;
    
	@Override
	protected PreferenceScreen createPreferenceHierarchy(PreferenceManager prefMan) {
		PreferenceScreen root = prefMan.createPreferenceScreen(getActivity());
		
		resources = getActivity().getResources();

		prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
		
		String app = APP_TITLE;
    	PackageManager pm = getActivity().getPackageManager();
    	if (pm != null) {
    		try {
    			PackageInfo pi = pm.getPackageInfo(getActivity().getPackageName(), 0);
    			if (pi != null) {
    				app = app + " v" + pi.versionName;
    			}
    		} catch (Exception e) {
    		}
    	}	    
        
		Preference copyright = new Preference(getActivity());
		copyright.setTitle(app);
		copyright.setSummary(
				APP_COPYRIGHT + (char)10 + 
				"Twitter: @ChainfireXDA" + (char)10 + 
				"G+: http://google.com/+Chainfire" + (char)10 + 
				S(R.string.settings_tap_xda) 
		);
		copyright.setKey("copyright");
		copyright.setEnabled(true);
		copyright.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			public boolean onPreferenceClick(Preference preference) {
				try {
					Intent i = new Intent(Intent.ACTION_VIEW);
					i.setData(Uri.parse(APP_WEBSITE_URL));
					startActivity(i);
				} catch (Exception e) {
					// no http handler installed (wtf, it happens)
				}
				return false;
			}
		}); 			
		root.addPreference(copyright);
		
		/* maybe one day
		if (!proPresent) {
			Preference upgrade = new Preference(getActivity());
			upgrade.setTitle(R.string.settings_upgrade);
			upgrade.setSummary(R.string.settings_upgrade_description);
			upgrade.setOnPreferenceClickListener(new OnPreferenceClickListener() {
				public boolean onPreferenceClick(Preference preference) {
					try {
						Intent i = new Intent(Intent.ACTION_VIEW);
						i.setData(Uri.parse("market://details?id=eu.chainfire.geolog.pro"));
						startActivity(i);		
					} catch (Exception e) {
						// no market installed
					}
					return false;
				}
			});    					
			root.addPreference(upgrade);
		}
		*/
		
		PreferenceCategory catUnits = Pref.Category(getActivity(), root, R.string.settings_category_units);
		prefUnits = Pref.List(
				getActivity(), 
				catUnits, 
				getString(R.string.settings_units_caption), 
				"", 
				getString(R.string.settings_units_popup), 
				PREF_UNITS, 
				PREF_UNITS_DEFAULT, 
				new String[] { 
					getString(R.string.settings_units_metric), 
					getString(R.string.settings_units_imperial) 
				}, 
				new String[] { 
					VALUE_UNITS_METRIC, 
					VALUE_UNITS_IMPERIAL 
				}
		);
		
		PreferenceCategory catMarket = Pref.Category(getActivity(), root, R.string.settings_category_market);    	
		Pref.Preference(getActivity(), catMarket, R.string.settings_market, R.string.settings_market_description, true, new OnPreferenceClickListener() {
			public boolean onPreferenceClick(Preference preference) {
				try {
					Intent i = new Intent(Intent.ACTION_VIEW);
					i.setData(Uri.parse("market://search?q=pub:Chainfire"));
					startActivity(i);		
				} catch (Exception e) {
					// market not installed
				}
				return false;
			}
		});    			
		
		Pref.Preference(getActivity(), catMarket, R.string.follow_pref_title, R.string.follow_pref_desc, true, new OnPreferenceClickListener() {			
			@Override
			public boolean onPreferenceClick(Preference preference) {
				showFollow(false);
				return false;
			}
		});
		
		int shown_follow = prefs.getInt(PREF_FOLLOW_SHOWN, 0);
		if (shown_follow == 0) {
			prefs.edit().putInt(PREF_FOLLOW_SHOWN, 1).commit();
			showFollow(true);
		}    					
		
		updatePrefs(null);
		return root;				
	}
	
    private void showFollow(boolean startup) {
    	if (startup) {
    		AlertDialog.Builder builder = (new AlertDialog.Builder(getActivity())).
				setTitle(R.string.follow_popup_title).
				setMessage(R.string.follow_popup_desc).
				setPositiveButton(R.string.follow_twitter, new OnClickListener() {					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						Intent i = new Intent(Intent.ACTION_VIEW);
						i.setData(Uri.parse("http://www.twitter.com/ChainfireXDA"));
						startActivity(i);								
					}
				}).
				setNeutralButton(R.string.follow_gplus, new OnClickListener() {					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						Intent i = new Intent(Intent.ACTION_VIEW);
						i.setData(Uri.parse("http://google.com/+Chainfire"));
						startActivity(i);								
					}
				}).
				setNegativeButton(R.string.follow_nothanks, new OnClickListener() {					
					@Override
					public void onClick(DialogInterface dialog, int which) {
					}
				});
    		try {
    			builder.show();
    		} catch (Exception e) {									
    		}    		
    	} else {
    		AlertDialog.Builder builder = (new AlertDialog.Builder(getActivity())).
				setTitle(R.string.follow_popup_title).
				setItems(new CharSequence[] { S(R.string.follow_twitter), S(R.string.follow_gplus) }, new OnClickListener() {						
					@Override
					public void onClick(DialogInterface dialog, int which) {
						if (which == 0) {
							Intent i = new Intent(Intent.ACTION_VIEW);
							i.setData(Uri.parse("http://www.twitter.com/ChainfireXDA"));
							startActivity(i);								
						} else if (which == 1) {
							Intent i = new Intent(Intent.ACTION_VIEW);
							i.setData(Uri.parse("http://plus.google.com/b/113517319477420052449/"));
							startActivity(i);								
						}							
					}
				}).					
				setNegativeButton(R.string.generic_close, null);
    		try {
    			builder.show();
    		} catch (Exception e) {									
    		}
    	}
    }	

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		prefs.registerOnSharedPreferenceChangeListener(this);
	}

	
	@Override
	public void onDestroyView() {
		prefs.unregisterOnSharedPreferenceChangeListener(this);
		super.onDestroyView();
	}
	
	public void updatePrefs(String key) {
		try {
			if ((key == null) || (key.equals(PREF_UNITS))) {
				int id = R.string.settings_units_metric;
				if (prefs.getString(PREF_UNITS, PREF_UNITS_DEFAULT).equals(VALUE_UNITS_IMPERIAL)) id = R.string.settings_units_imperial;
				prefUnits.setSummary(String.format(Locale.ENGLISH, "[ %s ]", getString(id)));
			}
			
			Intent i = new Intent(NOTIFY_BROADCAST);
			i.putExtra(EXTRA_KEY, key);
			LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(i);
		} catch (Exception e) {
		}
	}
	
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		updatePrefs(key);
	}	
}