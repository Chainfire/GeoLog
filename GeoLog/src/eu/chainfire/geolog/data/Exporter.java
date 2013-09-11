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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import eu.chainfire.geolog.Application;
import eu.chainfire.geolog.Debug;
import eu.chainfire.geolog.R;
import eu.chainfire.geolog.data.Database.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.AsyncTask;
import android.text.Html;

public class Exporter {
	public static enum Format { GPX, KML };

	private interface OnExportProgressListener {
		public void OnExportProgress(int cur, int total);
	}
			
	private final Context context;
	
	private Format format = Format.GPX;
	private long trackMergeGap = 0;
	private long dateStart = -1;
	private long dateEnd = -1;
	private long trackMinPoints = 0;
	private long trackMinTime = 0;
	private long trackMinDistance = 0;
	private long accuracyLowPowerUnknown = 0;
	private long accuracyLowPowerStill = 0;
	private long accuracyLowPowerFoot = 0;
	private long accuracyLowPowerBicycle = 0;
	private long accuracyLowPowerVehicle = 0;
	private long accuracyHighAccuracyUnknown = 0;
	private long accuracyHighAccuracyStill = 0;
	private long accuracyHighAccuracyFoot = 0;
	private long accuracyHighAccuracyBicycle = 0;
	private long accuracyHighAccuracyVehicle = 0;
	
	private int inSegment = 0;
	private boolean isSegmentStart = false;
	private long lastTime = -1;		    	
	private long lastTrackStartBytes = 0;
	private long lastTrackStartTime = 0;
	private double trackLatMin = 0;
	private double trackLatMax = 0;
	private double trackLongMin = 0;
	private double trackLongMax = 0;	
	
	public Exporter(Context context) {
		this.context = context;
	}

	public void export(Cursor cursor) {
		if (context instanceof Activity) {
			(new ExportAsync()).execute(cursor);
		} else {
			performExport(null, cursor);
		}
	}
	
	private double gps2m(double lat_a, double lng_a, double lat_b, double lng_b) {
	    double pk = (double) (180/Math.PI);

	    double a1 = lat_a / pk;
	    double a2 = lng_a / pk;
	    double b1 = lat_b / pk;
	    double b2 = lng_b / pk;

	    double t1 = Math.cos(a1)*Math.cos(a2)*Math.cos(b1)*Math.cos(b2);
	    double t2 = Math.cos(a1)*Math.sin(a2)*Math.cos(b1)*Math.sin(b2);
	    double t3 = Math.sin(a1)*Math.sin(b1);
	    double tt = Math.acos(t1 + t2 + t3);

	    return 6366000*tt;
	}	
	
	private boolean shouldCancel() {
		boolean cancel = false;
		
		if ((trackMinPoints > 0) && (inSegment < trackMinPoints)) {
			Debug.log(String.format(Locale.ENGLISH, "CANCEL POINTS %d < %d", inSegment, trackMinPoints));
			cancel = true;		    							
		}
		
		if ((trackMinTime > 0) && (lastTime - lastTrackStartTime < trackMinTime * 1000)) { 
			Debug.log(String.format(Locale.ENGLISH, "CANCEL TIME %d < %d", (int)((lastTime - lastTrackStartTime) / 1000), trackMinTime));
			cancel = true;
		}
		
		if (trackMinDistance > 0) {
			double m = gps2m(trackLatMin, trackLongMin, trackLatMax, trackLongMax);
			if (m < trackMinDistance) {
				Debug.log(String.format(Locale.ENGLISH, "CANCEL DISTANCE %d < %d", (int)m, trackMinDistance));
				cancel = true;		    								
			}		    							
		}		
		
		return cancel;
	}
			
	private String performExport(OnExportProgressListener callback, Cursor cursor) {
		String filename = "";
		switch (format) {
		case GPX: filename = Application.SDCARD_PATH + "/geolog.gpx"; break;
		case KML: filename = Application.SDCARD_PATH + "/geolog.kml"; break;
		}
		(new File(filename)).delete();
				
		try {
			FileOutputStream fos = new FileOutputStream(filename, false);			
			try {
				FormatWriter writer = null;
				switch (format) {
				case GPX: writer = new GPXWriter(fos); break;
				case KML: writer = new KMLWriter(fos); break;
				}
				
				String exporter = "GeoLog";				
		    	
				PackageManager pm = context.getPackageManager();
		    	if (pm != null) {
		    		try {
		    			PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
		    			if (pi != null) {
		    				exporter += " v" + pi.versionName;
		    			}
		    		} catch (Exception e) {
		    		}
		    	}
		    	
		    	// only used for debug logging - exported is in writer
				SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH);
				simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));			
		    			    	
		    	writer.header(exporter);
		    			    	
		    	lastTrackStartBytes = fos.getChannel().position();		
		    	writer.startSegment();

		    	Cursor c = cursor;
		    	if ((c != null) && (c.getCount() > 0)) {
			    	Database.Location loc = new Database.Location();
	    			
		    		c.moveToFirst();
		    		int index = 0;
		    		int count = c.getCount();
		    		while (true) {
		    			loc.loadFromCursor(c);
		    			
		    			Debug.log(String.format(Locale.ENGLISH, "WRITE %d %.5f %.5f %s", index, loc.getLatitude(), loc.getLongitude(), simpleDateFormat.format(new Date(loc.getTime()))));
		    			
		    			if (lastTrackStartTime == 0) {
		    				lastTrackStartTime = loc.getTime();
		    				trackLatMin = loc.getLatitude();
		    				trackLatMax = loc.getLatitude();
		    				trackLongMin = loc.getLongitude();
		    				trackLongMax = loc.getLongitude();
		    			}
		    			isSegmentStart = isSegmentStart || loc.isSegmentStart(); // carries over in case not used
		    			
		    			boolean ok = 
		    				(loc.getAccuracySetting() == Accuracy.NONE) ||
		    				((loc.getAccuracySetting() == Accuracy.LOW) && (		    					
		    					((loc.getActivity() == Database.Activity.UNKNOWN) && ((accuracyLowPowerUnknown <= 0) || loc.getAccuracyDistance() <= accuracyLowPowerUnknown)) ||
		    					((loc.getActivity() == Database.Activity.STILL) && ((accuracyLowPowerStill <= 0) || loc.getAccuracyDistance() <= accuracyLowPowerStill)) ||
		    					((loc.getActivity() == Database.Activity.FOOT) && ((accuracyLowPowerFoot <= 0) || loc.getAccuracyDistance() <= accuracyLowPowerFoot)) ||
		    					((loc.getActivity() == Database.Activity.BICYCLE) && ((accuracyLowPowerBicycle <= 0) || loc.getAccuracyDistance() <= accuracyLowPowerBicycle)) ||
		    					((loc.getActivity() == Database.Activity.VEHICLE) && ((accuracyLowPowerVehicle <= 0) || loc.getAccuracyDistance() <= accuracyLowPowerVehicle))
		    				)) ||
		    				((loc.getAccuracySetting() == Accuracy.HIGH) && (
		    					((loc.getActivity() == Database.Activity.UNKNOWN) && ((accuracyHighAccuracyUnknown <= 0) || loc.getAccuracyDistance() <= accuracyHighAccuracyUnknown)) ||
		    					((loc.getActivity() == Database.Activity.STILL) && ((accuracyHighAccuracyStill <= 0) || loc.getAccuracyDistance() <= accuracyHighAccuracyStill)) ||
		    					((loc.getActivity() == Database.Activity.FOOT) && ((accuracyHighAccuracyFoot <= 0) || loc.getAccuracyDistance() <= accuracyHighAccuracyFoot)) ||
		    					((loc.getActivity() == Database.Activity.BICYCLE) && ((accuracyHighAccuracyBicycle <= 0) || loc.getAccuracyDistance() <= accuracyHighAccuracyBicycle)) ||
		    					((loc.getActivity() == Database.Activity.VEHICLE) && ((accuracyHighAccuracyVehicle <= 0) || loc.getAccuracyDistance() <= accuracyHighAccuracyVehicle))
		    				));

		    			if (ok) {
		    				if (isSegmentStart) {
		    					if (loc.getTime() - lastTime < trackMergeGap * 1000) {
		    						Debug.log(String.format(Locale.ENGLISH, "MERGE %d %ds", index, (int)((loc.getTime() - lastTime) / 1000)));
		    						
		    						isSegmentStart = false;
		    					}
		    				}
		    					
		    				if (isSegmentStart) {
		    					if (inSegment > 0) {		    								    						
		    						if (shouldCancel()) {
		    							fos.getChannel().position(lastTrackStartBytes);
		    							fos.getChannel().truncate(lastTrackStartBytes);		    							
		    						} else {
		    							writer.endSegment();
		    						}
	    							Debug.log(String.format(Locale.ENGLISH, "TRACK %d", index));
	    							lastTrackStartBytes = fos.getChannel().position();
	    					    	writer.startSegment();
		    						
		    						lastTrackStartTime = loc.getTime();
				    				trackLatMin = loc.getLatitude();
				    				trackLatMax = loc.getLatitude();
				    				trackLongMin = loc.getLongitude();
				    				trackLongMax = loc.getLongitude();

		    						inSegment = 0;
		    					}
		    					isSegmentStart = false;
		    				}

		    				// if !ok we don't know location is correct, so we only do this here
		    				trackLatMin = Math.min(trackLatMin, loc.getLatitude());		    			
		    				trackLatMax = Math.max(trackLatMax, loc.getLatitude());
		    				trackLongMin = Math.min(trackLongMin, loc.getLongitude());
		    				trackLongMax = Math.max(trackLongMax, loc.getLongitude());
		    				
		    				writer.point(loc);
		    				inSegment++;
		    			} else {
    						Debug.log(String.format(Locale.ENGLISH, "SKIP %d %dm", index, (int)loc.getAccuracyDistance()));		    				
		    			}
		    			
		    			lastTime = loc.getTime(); // take into account even if we don't store point, because we know time is correct
		    			
		    			index++;
		    			if (callback != null) callback.OnExportProgress(index, count);
		    			if (!c.moveToNext()) break;
		    		}
		    	}
		    	
				if ((inSegment == 0) || shouldCancel()) {
					fos.getChannel().position(lastTrackStartBytes);
					fos.getChannel().truncate(lastTrackStartBytes);		    							
				} else {
					writer.endSegment();
				}		    	

		    	writer.footer();
			} finally {
				fos.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
			(new File(filename)).delete();
			return null;
		}
		return filename;
	}
	
	private class ExportAsync extends AsyncTask<Cursor, Integer, String> {
		private ProgressDialog dialog = null;

		@Override
		protected void onPreExecute() {
			dialog = new ProgressDialog(context);		
			dialog.setTitle(R.string.export_exporting);
			dialog.setIndeterminate(false);
			dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			dialog.setCancelable(false);
			dialog.setProgress(0);
			dialog.setMax(1);
			dialog.show();
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			dialog.setMax(values[1]);
			dialog.setProgress(values[0]);
		}

		@Override
		protected String doInBackground(Cursor... params) {
			return performExport(new OnExportProgressListener() {				
				@Override
				public void OnExportProgress(int cur, int total) {
					publishProgress(cur, total);
				}
			}, params[0]);
		}
		
		@Override
		protected void onPostExecute(String result) {
			dialog.dismiss();
			
			(new AlertDialog.Builder(context)).
				setTitle(R.string.export_export).
				setMessage(Html.fromHtml(context.getString((result == null) ? R.string.export_failed : R.string.export_complete))).
				setPositiveButton(R.string.generic_ok, null).
				show();
		}				
	}
	
	public Format getFormat() {
		return format;
	}

	public void setFormat(Format format) {
		this.format = format;
	}

	public long getTrackMergeGap() {
		return trackMergeGap;
	}

	public void setTrackMergeGap(long trackMergeGap) {
		this.trackMergeGap = trackMergeGap;
	}

	public long getDateStart() {
		return dateStart;
	}

	public void setDateStart(long dateStart) {
		this.dateStart = dateStart;
	}

	public long getDateEnd() {
		return dateEnd;
	}

	public void setDateEnd(long dateEnd) {
		this.dateEnd = dateEnd;
	}

	public long getAccuracyLowPowerUnknown() {
		return accuracyLowPowerUnknown;
	}

	public void setAccuracyLowPowerUnknown(long accuracyLowPowerUnknown) {
		this.accuracyLowPowerUnknown = accuracyLowPowerUnknown;
	}

	public long getAccuracyLowPowerStill() {
		return accuracyLowPowerStill;
	}

	public void setAccuracyLowPowerStill(long accuracyLowPowerStill) {
		this.accuracyLowPowerStill = accuracyLowPowerStill;
	}

	public long getAccuracyLowPowerFoot() {
		return accuracyLowPowerFoot;
	}

	public void setAccuracyLowPowerFoot(long accuracyLowPowerFoot) {
		this.accuracyLowPowerFoot = accuracyLowPowerFoot;
	}

	public long getAccuracyLowPowerBicycle() {
		return accuracyLowPowerBicycle;
	}

	public void setAccuracyLowPowerBicycle(long accuracyLowPowerBicycle) {
		this.accuracyLowPowerBicycle = accuracyLowPowerBicycle;
	}

	public long getAccuracyLowPowerVehicle() {
		return accuracyLowPowerVehicle;
	}

	public void setAccuracyLowPowerVehicle(long accuracyLowPowerVehicle) {
		this.accuracyLowPowerVehicle = accuracyLowPowerVehicle;
	}

	public long getAccuracyHighAccuracyUnknown() {
		return accuracyHighAccuracyUnknown;
	}

	public void setAccuracyHighAccuracyUnknown(long accuracyHighAccuracyUnknown) {
		this.accuracyHighAccuracyUnknown = accuracyHighAccuracyUnknown;
	}

	public long getAccuracyHighAccuracyStill() {
		return accuracyHighAccuracyStill;
	}

	public void setAccuracyHighAccuracyStill(long accuracyHighAccuracyStill) {
		this.accuracyHighAccuracyStill = accuracyHighAccuracyStill;
	}

	public long getAccuracyHighAccuracyFoot() {
		return accuracyHighAccuracyFoot;
	}

	public void setAccuracyHighAccuracyFoot(long accuracyHighAccuracyFoot) {
		this.accuracyHighAccuracyFoot = accuracyHighAccuracyFoot;
	}

	public long getAccuracyHighAccuracyBicycle() {
		return accuracyHighAccuracyBicycle;
	}

	public void setAccuracyHighAccuracyBicycle(long accuracyHighAccuracyBicycle) {
		this.accuracyHighAccuracyBicycle = accuracyHighAccuracyBicycle;
	}

	public long getAccuracyHighAccuracyVehicle() {
		return accuracyHighAccuracyVehicle;
	}

	public void setAccuracyHighAccuracyVehicle(long accuracyHighAccuracyVehicle) {
		this.accuracyHighAccuracyVehicle = accuracyHighAccuracyVehicle;
	}

	public long getTrackMinPoints() {
		return trackMinPoints;
	}

	public void setTrackMinPoints(long trackMinPoints) {
		this.trackMinPoints = trackMinPoints;
	}

	public long getTrackMinTime() {
		return trackMinTime;
	}

	public void setTrackMinTime(long trackMinTime) {
		this.trackMinTime = trackMinTime;
	}

	public long getTrackMinDistance() {
		return trackMinDistance;
	}

	public void setTrackMinDistance(long trackMinDistance) {
		this.trackMinDistance = trackMinDistance;
	}

	private abstract class FormatWriter {
		protected OutputStream os = null;
		
		public FormatWriter(OutputStream os) {
			this.os = os;
		}
		
		protected void write(String string) throws UnsupportedEncodingException, IOException {
			os.write(string.getBytes("US-ASCII"));
		}
		
		public abstract void header(String exporter) throws UnsupportedEncodingException, IOException;
		public abstract void startSegment() throws UnsupportedEncodingException, IOException;
		public abstract void point(Database.Location loc) throws UnsupportedEncodingException, IOException;
		public abstract void endSegment() throws UnsupportedEncodingException, IOException;
		public abstract void footer() throws UnsupportedEncodingException, IOException;
	}
	
	private class GPXWriter extends FormatWriter {
		protected SimpleDateFormat simpleDateFormat = null;
				
		public GPXWriter(OutputStream os) {
			super(os);
			simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH);
			simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));			
		}

		@Override
		public void header(String exporter) throws UnsupportedEncodingException, IOException {
			write(
   	    		"<?xml version=\"1.0\"?>\r\n" +
   	    		"<gpx version=\"1.0\" creator=\"" + exporter + "\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns=\"http://www.topografix.com/GPX/1/0\" xsi:schemaLocation=\"http://www.topografix.com/GPX/1/0 http://www.topografix.com/GPX/1/0/gpx.xsd\">\r\n" +
   	    		"  <trk>\r\n"
    	    );		    			    			
		}

		@Override
		public void startSegment() throws UnsupportedEncodingException, IOException {
	    	write(
   				"    <trkseg>\r\n"
	    	);
		}

		@Override
		public void point(Location loc) throws UnsupportedEncodingException, IOException {
			write(String.format(Locale.ENGLISH, "      <trkpt lat=\"%.5f\" lon=\"%.5f\"><time>%s</time></trkpt>\r\n", loc.getLatitude(), loc.getLongitude(), simpleDateFormat.format(new Date(loc.getTime()))));
		}		

		@Override
		public void endSegment() throws UnsupportedEncodingException, IOException {
			write(
				"    </trkseg>\r\n"
			);
		}

		@Override
		public void footer() throws UnsupportedEncodingException, IOException {
	    	write(
    			"  </trk>\r\n" +
    			"</gpx>\r\n"
	    	);				
		}
	}	
	
	private class KMLWriter extends FormatWriter {
		protected int trackIndex = 1;
		protected String last = "";
		
		public KMLWriter(OutputStream os) {
			super(os);			
		}
		
		@Override
		public void header(String exporter)	throws UnsupportedEncodingException, IOException {
			write(
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +
				"<kml xmlns=\"http://www.opengis.net/kml/2.2\"  xmlns:gx=\"http://www.google.com/kml/ext/2.2\" xmlns:kml=\"http://www.opengis.net/kml/2.2\"\r\n" +    
				"  xmlns:atom=\"http://www.w3.org/2005/Atom\">\r\n" +
				"  <Document>\r\n" +
				"    <name>Exported by " + exporter + "</name>\r\n" +
				"    <description></description>\r\n" +
				"    <visibility>1</visibility>\r\n" +
				"    <open>1</open>\r\n" +
				"    <Style id=\"red\"><LineStyle><color>C81400FF</color><width>4</width></LineStyle></Style>\r\n" +	        
				"    <Folder>\r\n" +
				"      <name>Tracks</name>\r\n" +
				"      <description></description>\r\n" +
				"      <visibility>1</visibility>\r\n" +            
				"      <open>1</open>\r\n"				
	        );
		}

		@Override
		public void startSegment() throws UnsupportedEncodingException,	IOException {
			write(
					"      <Placemark>\r\n" +
					"        <visibility>1</visibility>\r\n" +            
					"        <open>1</open>\r\n" + 
					"        <styleUrl>#red</styleUrl>\r\n" +
					"        <name>Track " + String.valueOf(trackIndex) + "</name>\r\n" +
					"        <description></description>\r\n" +
					"        <LineString>\r\n" +
					"          <extrude>true</extrude>\r\n" +
					"          <tessellate>true</tessellate>\r\n" +
					"          <altitudeMode>clampToGround</altitudeMode>\r\n" + 
					"            <coordinates>\r\n"
			);
			trackIndex++;
		}

		@Override
		public void point(Location loc) throws UnsupportedEncodingException, IOException {
			String now = String.format(Locale.ENGLISH, "            %.5f,%.5f\r\n", loc.getLongitude(), loc.getLatitude());
			if (!now.equals(last)) {
				write(now);
				last = now;
			}
		}

		@Override
		public void endSegment() throws UnsupportedEncodingException, IOException {
			write(
				"          </coordinates>\r\n" +
				"        </LineString>\r\n" +
				"      </Placemark>\r\n"
			);			
		}

		@Override
		public void footer() throws UnsupportedEncodingException, IOException {
			write(
				"    </Folder>\r\n" +
			    "  </Document>\r\n" +
			    "</kml>\r\n"
			);
		}
	}
}
