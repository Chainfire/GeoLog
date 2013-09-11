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
import eu.chainfire.geolog.data.Database;
import eu.chainfire.geolog.data.ProfilesProvider;
import eu.chainfire.geolog.service.BackgroundService;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.TextView;

@SuppressWarnings("deprecation")
public class ProfilesFragment extends ListFragment implements LoaderCallbacks<Cursor> {
    static final int INTERNAL_EMPTY_ID = 0x00ff0001;
    static final int INTERNAL_PROGRESS_CONTAINER_ID = 0x00ff0002;
    static final int INTERNAL_LIST_CONTAINER_ID = 0x00ff0003;

    private class ViewHolder {
		public View container = null;
		public TextView name = null;
		public ImageView timer = null;
		public ImageView edit = null;
		public ImageView delete = null;
		public Database.Profile profile = null;
	}
	
	private static final int LIST_LOADER = 1;
	private CursorAdapter adapter;
	private long currentProfileId = 0;

	private View mainView;
	private View progressContainer;
	private View listContainer;
	private View internalEmpty;
	private boolean isShown = false;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		mainView = inflater.inflate(R.layout.fragment_profiles, container, false);				
		progressContainer = mainView.findViewById(R.id.progressContainer);
		//progressContainer.setId(INTERNAL_PROGRESS_CONTAINER_ID);
		listContainer = mainView.findViewById(R.id.listContainer);
		listContainer.setId(INTERNAL_LIST_CONTAINER_ID);
		internalEmpty =	mainView.findViewById(R.id.internalEmpty);
		internalEmpty.setId(INTERNAL_EMPTY_ID);
		return mainView;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		currentProfileId = PreferenceManager.getDefaultSharedPreferences(getActivity()).getLong(SettingsFragment.PREF_CURRENT_PROFILE, 0);		
	}		
	
	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		setEmptyText(getResources().getString(R.string.profiles_empty));	    
		setListShown(false);
		
	    getLoaderManager().initLoader(LIST_LOADER, null, this);
	    adapter = new CursorAdapter(getActivity(), null, CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER) {
	    	private LayoutInflater inflater = null;	    	
	    	private String formatName = null;
	    	
	    	private ViewHolder holderFromView(View v) {
	    		if ((v.getTag() != null) && (v.getTag() instanceof ViewHolder)) {
	    			return (ViewHolder)v.getTag();
	    		} else if ((v.getParent() != null) && (v.getParent() instanceof View)) {
	    			return holderFromView((View)v.getParent());
	    		}
	    		return null;
	    	}
	    		    		    	
	    	private OnClickListener onTimerClick = new OnClickListener() {				
				@Override
				public void onClick(View v) {
					//ViewHolder holder = holderFromView(v);
					//TODO: handle
				}
			};

			private OnClickListener onEditClick = new OnClickListener() {				
				@Override
				public void onClick(View v) {
					ViewHolder holder = holderFromView(v);
					ProfileActivity.launchActivity(getActivity(), holder.profile.getId());
				}
			};
	    	
	    	private OnClickListener onDeleteClick = new OnClickListener() {				
				@Override
				public void onClick(View v) {
					ViewHolder holder = holderFromView(v);
					final long id = holder.profile.getId();
					
					(new AlertDialog.Builder(getActivity())).
						setTitle(R.string.generic_delete).
						setMessage(Html.fromHtml(String.format(Locale.ENGLISH, getString(R.string.profile_delete_confirm), holder.profile.getName()))).
						setPositiveButton(getString(R.string.generic_delete), new DialogInterface.OnClickListener() {							
							@Override
							public void onClick(DialogInterface dialog, int which) {
								Database.Profile.delete(Database.Helper.getInstance(getActivity()), id);
							}
						}).
						setNegativeButton(getString(R.string.generic_cancel), null).
						show();
				}
			};

			@Override
			public View newView(Context context, Cursor cursor, ViewGroup root) {
				if (inflater == null) inflater = LayoutInflater.from(context);
				
				formatName = context.getString(R.string.row_profiles_name);
				
				return inflater.inflate(R.layout.row_profiles, null);
			}
			
			@Override
			public void bindView(View view, Context context, Cursor cursor) {				
				ViewHolder holder = (ViewHolder)view.getTag();
				if (holder == null) { 
					holder = new ViewHolder();
					holder.container = view.findViewById(R.id.container);
					holder.name = (TextView)view.findViewById(R.id.name);
					holder.timer = (ImageView)view.findViewById(R.id.timer);
					holder.timer.setOnClickListener(onTimerClick);
					holder.edit = (ImageView)view.findViewById(R.id.edit);
					holder.edit.setOnClickListener(onEditClick);
					holder.delete = (ImageView)view.findViewById(R.id.delete);
					holder.delete.setOnClickListener(onDeleteClick);
					holder.profile = new Database.Profile();
					view.setTag(holder);
				}

				holder.profile.loadFromCursor(cursor);
				
				boolean selected = (holder.profile.getId() == currentProfileId);
				boolean readonly = (holder.profile.getType() == Database.Profile.Type.OFF);
				
				//holder.timer.setVisibility((selected || readonly) ? View.GONE : View.VISIBLE);
				holder.timer.setVisibility(View.GONE); //TODO disabled for now
				holder.edit.setVisibility(readonly ? View.GONE : View.VISIBLE);
				holder.delete.setVisibility(readonly ? View.GONE : View.VISIBLE);
				
				if (selected) {
					holder.container.setBackgroundColor(0xFFa8dff4);
				} else {
					holder.container.setBackgroundDrawable(null);
				}
				
				holder.name.setText(Html.fromHtml(String.format(Locale.ENGLISH, formatName, holder.profile.getName())));
			}
		};
	    setListAdapter(adapter);	
		
	    getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				currentProfileId = id;

				PreferenceManager.getDefaultSharedPreferences(getActivity()).edit().putLong(SettingsFragment.PREF_CURRENT_PROFILE, id).commit();
				
				Database.Profile profile = Database.Profile.getById(Database.Helper.getInstance(getActivity()), id, null);
				if (profile.getType() != Database.Profile.Type.OFF) BackgroundService.startService(getActivity());
				
				getListView().invalidateViews();				
			}
		});
	}

	@Override
	public void setListShown(boolean shown) {
		isShown = shown;
		listContainer.setVisibility(isShown ? View.VISIBLE : View.GONE);
		progressContainer.setVisibility(isShown ? View.GONE : View.VISIBLE);
	}	
	
	@Override
	public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
	    CursorLoader cursorLoader = new CursorLoader(getActivity(), ProfilesProvider.CONTENT_URI, null, null, null, Database.Location._ID);
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
