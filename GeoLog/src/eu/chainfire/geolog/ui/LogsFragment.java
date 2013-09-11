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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import eu.chainfire.geolog.Debug;
import eu.chainfire.geolog.R;
import eu.chainfire.geolog.data.Database;
import eu.chainfire.geolog.data.LogsProvider;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.CursorAdapter;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class LogsFragment extends ListFragment implements LoaderCallbacks<Cursor> {
    static final int INTERNAL_EMPTY_ID = 0x00ff0001;
    static final int INTERNAL_PROGRESS_CONTAINER_ID = 0x00ff0002;
    static final int INTERNAL_LIST_CONTAINER_ID = 0x00ff0003;

    private class ViewHolder {
		public View container = null;
		public TextView time = null;
		public TextView activity = null;
		public TextView location = null;
	}

	private static final int LIST_LOADER = 1;
	private CursorAdapter adapter;
	private volatile boolean metric = true;
	private SharedPreferences prefs;

	private View mainView;
	private View progressContainer;
	private View listContainer;
	private View internalEmpty;
	private boolean isShown = false;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		mainView = inflater.inflate(R.layout.fragment_logs, container, false);
		progressContainer = mainView.findViewById(R.id.progressContainer);
		//progressContainer.setId(INTERNAL_PROGRESS_CONTAINER_ID);
		listContainer = mainView.findViewById(R.id.listContainer);
		listContainer.setId(INTERNAL_LIST_CONTAINER_ID);
		internalEmpty =	mainView.findViewById(R.id.internalEmpty);
		internalEmpty.setId(INTERNAL_EMPTY_ID);
		return mainView;
	}
	
	private BroadcastReceiver preferenceReceiver = new BroadcastReceiver() {		
		@Override
		public void onReceive(Context context, Intent intent) {
			Debug.log("Preferences updated !");
			metric = !prefs.getString(SettingsFragment.PREF_UNITS, SettingsFragment.PREF_UNITS_DEFAULT).equals(SettingsFragment.VALUE_UNITS_IMPERIAL);
			try { getListView().invalidateViews(); } catch (Exception e) { }
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
		metric = !prefs.getString(SettingsFragment.PREF_UNITS, SettingsFragment.PREF_UNITS_DEFAULT).equals(SettingsFragment.VALUE_UNITS_IMPERIAL);		
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		LocalBroadcastManager.getInstance(getActivity()).registerReceiver(preferenceReceiver, new IntentFilter(SettingsFragment.NOTIFY_BROADCAST));	

		getListView().setSelector(android.R.color.transparent);
	    
		setEmptyText(getResources().getString(R.string.logs_empty));	    
		setListShown(false);
		
	    getLoaderManager().initLoader(LIST_LOADER, null, this);
	    adapter = new CursorAdapter(getActivity(), null, CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER) {
	    	private LayoutInflater inflater = null;
	    	private Database.Location location = null;	    	
	    	
	    	private String formatTime = null;
	    	private String formatActivity = null;
	    	private String formatLocation = null;
	    	private String formatBattery = null;
	    	private boolean lastMetric = true;
	    	
	    	private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ", Locale.ENGLISH);
	    	
			@Override
			public View newView(Context context, Cursor cursor, ViewGroup root) {
				if (inflater == null) inflater = LayoutInflater.from(context);							
				return inflater.inflate(R.layout.row_logs, null);
			}
			
			@SuppressWarnings("deprecation")
			@Override
			public void bindView(View view, Context context, Cursor cursor) {				
				ViewHolder holder = (ViewHolder)view.getTag();
				if (holder == null) { 
					holder = new ViewHolder();
					holder.container = view.findViewById(R.id.container);
					holder.time = (TextView)view.findViewById(R.id.time);
					holder.activity = (TextView)view.findViewById(R.id.activity);
					holder.location = (TextView)view.findViewById(R.id.location);
					view.setTag(holder);
				}

				if (location == null) location = new Database.Location();
				location.loadFromCursor(cursor);
				
				if (formatTime == null) formatTime = context.getString(R.string.row_logs_time);
				if (formatActivity == null) formatActivity = context.getString(R.string.row_logs_activity);
				if (formatBattery == null) formatBattery = context.getString(R.string.row_logs_battery);
				if ((formatLocation == null) || (metric != lastMetric)) {
					if (metric) {
						formatLocation = context.getString(R.string.row_logs_location_metric);
					} else {
						formatLocation = context.getString(R.string.row_logs_location_imperial);						
					}
					lastMetric = metric;
				}
								
				float accuracy = location.getAccuracyDistance(); 
				if (!metric) accuracy *= SettingsFragment.METER_FEET_RATIO;
				
				if (location.isSegmentStart()) {
					holder.container.setBackgroundColor(0xFFa8dff4);
				} else {
					holder.container.setBackgroundDrawable(null);
				}
				
				holder.time.setText(Html.fromHtml(String.format(Locale.ENGLISH, formatTime, simpleDateFormat.format(new Date(location.getTime())))));
				holder.activity.setText(Html.fromHtml(
						String.format(Locale.ENGLISH, formatActivity, Database.activityToString(location.getActivity()), location.getConfidence()) + " " + 
						String.format(Locale.ENGLISH, formatBattery, (location.getBattery() > 100) ? location.getBattery() - 100 : location.getBattery(), (location.getBattery() > 100) ? "+" : "")
				));
				holder.location.setText(Html.fromHtml(String.format(Locale.ENGLISH, formatLocation, location.getLatitude(), location.getLongitude(), accuracy)));
			}
		};
	    setListAdapter(adapter);	    
	}
	
	@Override
	public void onDestroyView() {
		LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(preferenceReceiver);	
		super.onDestroyView();
	}

	@Override
	public void setListShown(boolean shown) {
		isShown = shown;
		listContainer.setVisibility(isShown ? View.VISIBLE : View.GONE);
		progressContainer.setVisibility(isShown ? View.GONE : View.VISIBLE);
	}	
	
	@Override
	public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
	    CursorLoader cursorLoader = new CursorLoader(getActivity(), LogsProvider.CONTENT_URI, null, null, null, Database.Location._ID + " DESC");
	    return cursorLoader;
	}

	@Override
	public void onLoadFinished(Loader<Cursor> arg0, Cursor arg1) {
		adapter.swapCursor(arg1);
		setListShown(true);
	}

	@Override
	public void onLoaderReset(Loader<Cursor> arg0) {
		adapter.swapCursor(null);
		setListShown(false);
	}
}
