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

import android.annotation.SuppressLint;
import java.io.File;

@SuppressLint("SdCardPath")
public class Application extends android.app.Application {
	public static final String SDCARD_PATH = "/mnt/sdcard/GeoLog";
	
	static {
		(new File(SDCARD_PATH)).mkdirs();
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		new CrashCatcher();
	}
}
