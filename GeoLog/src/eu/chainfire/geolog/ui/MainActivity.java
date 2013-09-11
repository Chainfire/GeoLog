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

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

import eu.chainfire.geolog.R;
import eu.chainfire.geolog.data.Database;
import eu.chainfire.geolog.service.BackgroundService;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.Html;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;

public class MainActivity extends FragmentActivity implements
		ActionBar.TabListener {
	
	private ProfilesFragment tabProfiles = null;
	private LogsFragment tabLogs = null;
	private SettingsFragment tabSettings = null;

	private SectionsPagerAdapter mSectionsPagerAdapter;
	private ViewPager mViewPager;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		final ActionBar actionBar = getActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

		mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());
		mViewPager = (ViewPager) findViewById(R.id.pager);
		mViewPager.setAdapter(mSectionsPagerAdapter);

		mViewPager
				.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
					@Override
					public void onPageSelected(int position) {
						actionBar.setSelectedNavigationItem(position);
						invalidateOptionsMenu();
					}
				});
		mViewPager.setOffscreenPageLimit(3);
				
		for (int i = 0; i < mSectionsPagerAdapter.getCount(); i++) {
			actionBar.addTab(actionBar.newTab()
					.setText(mSectionsPagerAdapter.getPageTitle(i))
					.setTabListener(this));
		}
		
		int play = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
		if (play == ConnectionResult.SUCCESS) {
			// Force database creation
			Database.Helper helper = Database.Helper.getInstance(this);
			helper.getReadableDatabase();
			
			// Force Off profile as default
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
			long id = prefs.getLong(SettingsFragment.PREF_CURRENT_PROFILE, 0);
			Database.Profile profile = Database.Profile.getById(helper, id, null);
			if (profile == null) id = 0;
			if (id == 0) {
				profile = Database.Profile.getOffProfile(helper);
				id = profile.getId();
				prefs.edit().putLong(SettingsFragment.PREF_CURRENT_PROFILE, id).commit();
			}
			
			// Start background service
			if (profile.getType() != Database.Profile.Type.OFF) BackgroundService.startService(this);
		} else {
			GooglePlayServicesUtil.getErrorDialog(play, this, 0).show();
			finish();
		}
	}
	
	private void addProfile() {		
		Cursor list = Database.Profile.list(Database.Helper.getInstance(this));
		
		final CharSequence[] items = new CharSequence[list.getCount() + 1];
		final Long[] ids = new Long[list.getCount() + 1];
		
		items[0] = Html.fromHtml(getString(R.string.profile_add_name));
		ids[0] = 0L;

		int i = 1;
		int idxID = list.getColumnIndex(Database.Profile._ID);
		int idxName = list.getColumnIndex(Database.Profile.COLUMN_NAME_NAME);
		if (list.moveToFirst()) {
			while (true) {
				ids[i] = list.getLong(idxID);
				items[i] = Html.fromHtml(String.format(Locale.ENGLISH, getString(R.string.profile_add_copy), list.getString(idxName)));
				i++;
				if (!list.moveToNext()) break;
			}
		}
		
		(new AlertDialog.Builder(this)).
			setTitle(R.string.profile_add_title).
			setItems(items, new OnClickListener() {				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					Database.Helper h = Database.Helper.getInstance(MainActivity.this);
					if (which == 0) {
						Database.Profile p = new Database.Profile();
						p.setName(getString(R.string.profile_add_name));
						p.setType(Database.Profile.Type.USER);
						p.saveToDatabase(h);
						ProfileActivity.launchActivity(MainActivity.this, p.getId());						
					} else {
						ProfileActivity.launchActivity(MainActivity.this, Database.Profile.copy(h, Database.Profile.getById(h, ids[which], null), getString(R.string.profile_add_name)).getId());						
					}
				}
			}).
			setCancelable(true).
			setNegativeButton(getString(R.string.generic_cancel), null).
			show();					
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		int tab = getActionBar().getSelectedNavigationIndex();
		
		if (tab == 0) {
			menu.
				add(R.string.menu_add).
				setOnMenuItemClickListener(new OnMenuItemClickListener() {				
					@Override
					public boolean onMenuItemClick(MenuItem item) {
						addProfile();
						return true;
					}
				}).
				setIcon(R.drawable.ic_action_add).
				setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		} else if (tab == 1) {
			menu.
				add(R.string.menu_export).
				setOnMenuItemClickListener(new OnMenuItemClickListener() {				
					@Override
					public boolean onMenuItemClick(MenuItem item) {
						ExportActivity.launchActivity(MainActivity.this, 0);
						return true;
					}
				}).
				setIcon(R.drawable.ic_action_export).
				setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
			menu.
				add(R.string.menu_clear).
				setOnMenuItemClickListener(new OnMenuItemClickListener() {				
					@Override
					public boolean onMenuItemClick(MenuItem item) {
						(new AlertDialog.Builder(MainActivity.this)).
							setTitle(R.string.generic_clear).
							setMessage(Html.fromHtml(getString(R.string.logs_clear_confirm))).
							setPositiveButton(getString(R.string.generic_clear), new DialogInterface.OnClickListener() {							
								@Override
								public void onClick(DialogInterface dialog, int which) {									
									(new ClearLogsAsync()).execute();									
								}
							}).
							setNegativeButton(getString(R.string.generic_cancel), null).
							show();
							
						return true;
					}
				}).
				setIcon(R.drawable.ic_action_delete).
				setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
			
		} else if (tab == 2) {
			
		}
		
		return true;
	}

	@Override
	public void onTabSelected(ActionBar.Tab tab,
			FragmentTransaction fragmentTransaction) {
		mViewPager.setCurrentItem(tab.getPosition());
	}

	@Override
	public void onTabUnselected(ActionBar.Tab tab,
			FragmentTransaction fragmentTransaction) {
	}

	@Override
	public void onTabReselected(ActionBar.Tab tab,
			FragmentTransaction fragmentTransaction) {
	}

	public class SectionsPagerAdapter extends FragmentPagerAdapter {

		public SectionsPagerAdapter(FragmentManager fm) {
			super(fm);
		}

		@Override
		public Fragment getItem(int position) {
			if (position == 0) {
				if (tabProfiles == null) tabProfiles = new ProfilesFragment();
				return tabProfiles;
			} else if (position == 1) {
				if (tabLogs == null) tabLogs = new LogsFragment();
				return tabLogs;
			} else if (position == 2) {
				if (tabSettings == null) tabSettings = new SettingsFragment();
				return tabSettings;				
			}
			return null;
		}

		@Override
		public int getCount() {
			return 3;
		}

		@Override
		public CharSequence getPageTitle(int position) {
			Locale l = Locale.getDefault();
			switch (position) {
			case 0: return getString(R.string.section_profiles).toUpperCase(l);
			case 1:	return getString(R.string.section_logs).toUpperCase(l);
			case 2:	return getString(R.string.section_settings).toUpperCase(l);
			}
			return null;
		}
	}
	
	private class ClearLogsAsync extends AsyncTask<Void, Void, Void> {
		private ProgressDialog dialog = null;

		@Override
		protected void onPreExecute() {
			dialog = new ProgressDialog(MainActivity.this);
			dialog.setMessage(getString(R.string.logs_clear_clearing));
			dialog.setIndeterminate(true);
			dialog.setCancelable(false);
			dialog.show();
		}
		
		@Override
		protected Void doInBackground(Void... params) {
			Database.Location.deleteAll(Database.Helper.getInstance(MainActivity.this));
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			dialog.dismiss();
		}		
	}
}
