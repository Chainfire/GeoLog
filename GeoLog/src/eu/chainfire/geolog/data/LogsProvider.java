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

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;

public class LogsProvider extends ContentProvider {
	public static final String AUTHORITY = "eu.chainfire.geolog.logsprovider";
	public static final String BASE_PATH = "locations";
	public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + BASE_PATH);

	private static final int LOCATIONS = 10;
	private static final int LOCATION = 20;
	
	private static final UriMatcher URIMatcher = new UriMatcher(UriMatcher.NO_MATCH);
			  
	static {
		URIMatcher.addURI(AUTHORITY, BASE_PATH, LOCATIONS);
	    URIMatcher.addURI(AUTHORITY, BASE_PATH + "/#", LOCATION);
	}
	
	private Database.Helper databaseHelper;
	
	public static Uri URILocations() {
		return Uri.parse("content://" + AUTHORITY + "/" + BASE_PATH);
	}
	
	public static Uri URILocation(long id) {
		return Uri.parse("content://" + AUTHORITY + "/" + BASE_PATH + "/" + String.valueOf(id));
	}

	@Override
	public boolean onCreate() {
		databaseHelper = Database.Helper.getInstance(getContext());
		return false;
	}
	
	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
		queryBuilder.setTables(Database.Location.TABLE_NAME);

		int uriType = URIMatcher.match(uri);
		switch (uriType) {
		case LOCATIONS:
			break;
		case LOCATION:
			queryBuilder.appendWhere(Database.Location._ID + " = " + uri.getLastPathSegment());
			break;
		default:
			throw new IllegalArgumentException("Unknown URI: " + uri);
		}

		SQLiteDatabase db = databaseHelper.getReadableDatabase();
		Cursor cursor = queryBuilder.query(db, projection, selection, selectionArgs, null, null, sortOrder);
		cursor.setNotificationUri(getContext().getContentResolver(), uri);

		return cursor;
	}	
	
	@Override
	public String getType(Uri uri) {
		return null;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		throw new UnsupportedOperationException();
	}
}
