package com.daydreamin.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/** The app's settings file — the same name every earlier version used, so nothing is lost on update. */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "daydreamin_prefs")

fun AppPreferences(context: Context): AppPreferences = AppPreferences(context.dataStore, context.filesDir)
