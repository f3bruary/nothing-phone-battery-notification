package com.example.nothingphonebatterynotifier.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.nothingphonebatterynotifier.model.GlyphProfile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "glyph_profiles")

class ProfileRepository(private val context: Context) {
    private val gson = Gson()
    private val profilesKey = stringPreferencesKey("profiles")

    val profilesFlow: Flow<List<GlyphProfile>> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val profilesJson = preferences[profilesKey] ?: return@map emptyList<GlyphProfile>()
            val type = object : TypeToken<List<GlyphProfile>>() {}.type
            try {
                gson.fromJson<List<GlyphProfile>>(profilesJson, type)
            } catch (e: Exception) {
                emptyList()
            }
        }

    suspend fun saveProfiles(profiles: List<GlyphProfile>) {
        context.dataStore.edit { preferences ->
            preferences[profilesKey] = gson.toJson(profiles)
        }
    }

    suspend fun addProfile(profile: GlyphProfile) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[profilesKey]
            val type = object : TypeToken<MutableList<GlyphProfile>>() {}.type
            val currentList: MutableList<GlyphProfile> = if (currentJson == null) {
                mutableListOf()
            } else {
                try {
                    gson.fromJson(currentJson, type)
                } catch (e: Exception) {
                    mutableListOf()
                }
            }
            currentList.add(profile)
            preferences[profilesKey] = gson.toJson(currentList)
        }
    }
    
    suspend fun updateProfile(updatedProfile: GlyphProfile) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[profilesKey] ?: return@edit
            val type = object : TypeToken<MutableList<GlyphProfile>>() {}.type
            val currentList: MutableList<GlyphProfile> = gson.fromJson(currentJson, type)
            val index = currentList.indexOfFirst { it.id == updatedProfile.id }
            if (index != -1) {
                currentList[index] = updatedProfile
                preferences[profilesKey] = gson.toJson(currentList)
            }
        }
    }

    suspend fun deleteProfile(profileId: String) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[profilesKey] ?: return@edit
            val type = object : TypeToken<MutableList<GlyphProfile>>() {}.type
            val currentList: MutableList<GlyphProfile> = gson.fromJson(currentJson, type)
            currentList.removeAll { it.id == profileId }
            preferences[profilesKey] = gson.toJson(currentList)
        }
    }
}
