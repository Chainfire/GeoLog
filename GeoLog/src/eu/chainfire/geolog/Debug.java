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

package eu.chainfire.geolog;

import java.io.FileOutputStream;
import java.util.Locale;

import android.os.SystemClock;
import android.util.Log;

public class Debug {
	public static final String FILE_LOG = Application.SDCARD_PATH + "/log";
	
	private static boolean initialized = false;
	private static long start = 0L;
	
	public static void log(String message) {
		if (BuildConfig.DEBUG) {	
			if (!initialized) {
				start = SystemClock.elapsedRealtime();
				initialized = true;
			}
			
			if (FILE_LOG != null) {
				long uptime = SystemClock.elapsedRealtime();
				if (uptime < start) start = uptime;
				uptime -= start;

				long ms = uptime % 1000;
				uptime /= 1000;
				long s = uptime % 60;
				uptime /= 60;
				long m = uptime % 60;
				uptime /= 60;
				long h = uptime;

				try {
					FileOutputStream fos = new FileOutputStream(FILE_LOG, true);
					fos.write(String.format(Locale.ENGLISH, "[%02d:%02d:%02d.%03d]%s%s\r\n", h, m, s, ms, (message.startsWith("[") ? "" : " "), message).getBytes("US-ASCII"));
					fos.close();
				} catch (Exception e) {		
				}
			}
			
			Log.d("GeoLog", "[GeoLog]" + (!message.startsWith("[") && !message.startsWith(" ") ? " " : "") + message);			
		}
	}
}
