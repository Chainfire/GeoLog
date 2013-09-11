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

import android.content.Context;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;

public class Pref {
    public static PreferenceCategory Category(Context context, PreferenceScreen root, String caption) {
    	PreferenceCategory retval = new PreferenceCategory(context);
    	retval.setTitle(caption);
    	root.addPreference(retval);
    	return retval;    	
    }
    
    public static PreferenceCategory Category(Context context, PreferenceScreen root, int caption) {
    	PreferenceCategory retval = new PreferenceCategory(context);
    	if (caption > 0) retval.setTitle(caption);
    	root.addPreference(retval);
    	return retval;    	
    }

    public static Preference Preference(Context context, PreferenceCategory category, String caption, String summary, boolean enabled, Preference.OnPreferenceClickListener onClick) {
    	Preference retval = new Preference(context);
    	retval.setTitle(caption);
    	retval.setSummary(summary);
    	retval.setEnabled(enabled);
    	if (onClick != null) {
    		retval.setOnPreferenceClickListener(onClick);
    	}
    	if (category != null) category.addPreference(retval);
    	return retval;
    }

    public static Preference Preference(Context context, PreferenceCategory category, int caption, int summary, boolean enabled, Preference.OnPreferenceClickListener onClick) {
    	Preference retval = new Preference(context);
    	if (caption > 0) retval.setTitle(caption);
    	if (summary > 0) retval.setSummary(summary);
    	retval.setEnabled(enabled);
    	if (onClick != null) {
    		retval.setOnPreferenceClickListener(onClick);
    	}
    	if (category != null) category.addPreference(retval);
    	return retval;
    }

    public static CheckBoxPreference Check(Context context, PreferenceCategory category, String caption, String summary, String key, Object defaultValue) {
    	return Check(context, category, caption, summary, key, defaultValue, true);
    }

    public static CheckBoxPreference Check(Context context, PreferenceCategory category, String caption, String summary, String key, Object defaultValue, boolean enabled) {
    	CheckBoxPreference retval = new CheckBoxPreference(context);
    	retval.setTitle(caption);
    	retval.setSummary(summary);
    	retval.setEnabled(enabled);
    	retval.setKey(key);
    	retval.setDefaultValue(defaultValue);
    	if (category != null) category.addPreference(retval);
    	return retval;
    }
    
    public static CheckBoxPreference Check(Context context, PreferenceCategory category, int caption, int summary, String key, Object defaultValue) {
    	return Check(context, category, caption, summary, key, defaultValue, true);
    }

    public static CheckBoxPreference Check(Context context, PreferenceCategory category, int caption, int summary, String key, Object defaultValue, boolean enabled) {
    	CheckBoxPreference retval = new CheckBoxPreference(context);
    	if (caption > 0) retval.setTitle(caption);
    	if (summary > 0) retval.setSummary(summary);
    	retval.setEnabled(enabled);
    	retval.setKey(key);
    	retval.setDefaultValue(defaultValue);
    	if (category != null) category.addPreference(retval);
    	return retval;
    }

    public static ListPreference List(Context context, PreferenceCategory category, String caption, String summary, String dialogCaption, String key, Object defaultValue, CharSequence[] entries, CharSequence[] entryValues) {
    	return List(context, category, caption, summary, dialogCaption, key, defaultValue, entries, entryValues, true);
    }
    
    public static ListPreference List(Context context, PreferenceCategory category, String caption, String summary, String dialogCaption, String key, Object defaultValue, CharSequence[] entries, CharSequence[] entryValues, boolean enabled) {
    	ListPreference retval = new ListPreference(context);
    	retval.setTitle(caption);
    	retval.setSummary(summary);
    	retval.setEnabled(enabled);
    	retval.setKey(key);
    	retval.setDefaultValue(defaultValue);
    	retval.setDialogTitle(dialogCaption);
    	retval.setEntries(entries);
    	retval.setEntryValues(entryValues);    	
    	if (category != null) category.addPreference(retval);
    	return retval;
    }
    
    public static ListPreference List(Context context, PreferenceCategory category, int caption, int summary, int dialogCaption, String key, Object defaultValue, CharSequence[] entries, CharSequence[] entryValues) {
    	return List(context, category, caption, summary, dialogCaption, key, defaultValue, entries, entryValues, true);
    }
    
    public static ListPreference List(Context context, PreferenceCategory category, int caption, int summary, int dialogCaption, String key, Object defaultValue, CharSequence[] entries, CharSequence[] entryValues, boolean enabled) {
    	ListPreference retval = new ListPreference(context);
    	if (caption > 0) retval.setTitle(caption);
    	if (summary > 0) retval.setSummary(summary);
    	retval.setEnabled(enabled);
    	retval.setKey(key);
    	retval.setDefaultValue(defaultValue);
    	if (dialogCaption > 0) retval.setDialogTitle(dialogCaption);
    	retval.setEntries(entries);
    	retval.setEntryValues(entryValues);    	
    	if (category != null) category.addPreference(retval);
    	return retval;
    }

    public static EditTextPreference Edit(Context context, PreferenceCategory category, String caption, String summary, String dialogCaption, String key, Object defaultValue) {
    	return Edit(context, category, caption, summary, dialogCaption, key, defaultValue, true, null);
    }

    public static EditTextPreference Edit(Context context, PreferenceCategory category, String caption, String summary, String dialogCaption, String key, Object defaultValue, boolean enabled) {
    	return Edit(context, category, caption, summary, dialogCaption, key, defaultValue, enabled, null);    	
    }

    public static EditTextPreference Edit(Context context, PreferenceCategory category, String caption, String summary, String dialogCaption, String key, Object defaultValue, Integer type) {
    	return Edit(context, category, caption, summary, dialogCaption, key, defaultValue, true, type);
    }

    public static EditTextPreference Edit(Context context, PreferenceCategory category, String caption, String summary, String dialogCaption, String key, Object defaultValue, boolean enabled, Integer type) {
    	EditTextPreference retval = new EditTextPreference(context);
    	retval.setTitle(caption);
    	retval.setSummary(summary);
    	retval.setEnabled(enabled);
    	retval.setKey(key);
    	retval.setDefaultValue(defaultValue);
    	retval.setDialogTitle(dialogCaption);
    	if (type != null) {
    		retval.getEditText().setInputType(type);
    	}
    	if (category != null) category.addPreference(retval);
    	return retval;
    }    

    public static EditTextPreference Edit(Context context, PreferenceCategory category, int caption, int summary, int dialogCaption, String key, Object defaultValue) {
    	return Edit(context, category, caption, summary, dialogCaption, key, defaultValue, true, null);
    }

    public static EditTextPreference Edit(Context context, PreferenceCategory category, int caption, int summary, int dialogCaption, String key, Object defaultValue, boolean enabled) {
    	return Edit(context, category, caption, summary, dialogCaption, key, defaultValue, enabled, null);    	
    }

    public static EditTextPreference Edit(Context context, PreferenceCategory category, int caption, int summary, int dialogCaption, String key, Object defaultValue, Integer type) {
    	return Edit(context, category, caption, summary, dialogCaption, key, defaultValue, true, type);
    }

    public static EditTextPreference Edit(Context context, PreferenceCategory category, int caption, int summary, int dialogCaption, String key, Object defaultValue, boolean enabled, Integer type) {
    	EditTextPreference retval = new EditTextPreference(context);
    	if (caption > 0) retval.setTitle(caption);
    	if (summary > 0) retval.setSummary(summary);
    	retval.setEnabled(enabled);
    	retval.setKey(key);
    	retval.setDefaultValue(defaultValue);
    	if (dialogCaption > 0) retval.setDialogTitle(dialogCaption);
    	if (type != null) {
    		retval.getEditText().setInputType(type);
    	}
    	if (category != null) category.addPreference(retval);
    	return retval;
    }    
}
