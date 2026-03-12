package com.devson.nosvedplayer.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.devson.nosvedplayer.model.SortOrder
import com.devson.nosvedplayer.model.ViewSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.viewSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "view_settings")

class ViewSettingsRepository(private val context: Context) {

    companion object {
        val IS_GRID = booleanPreferencesKey("is_grid")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val SHOW_THUMBNAIL = booleanPreferencesKey("show_thumbnail")
        val SHOW_DURATION = booleanPreferencesKey("show_duration")
        val SHOW_SIZE = booleanPreferencesKey("show_size")
        val SHOW_DATE = booleanPreferencesKey("show_date")
        val SHOW_SUBTITLE_TYPE = booleanPreferencesKey("show_subtitle_type")
        val SHOW_RESOLUTION = booleanPreferencesKey("show_resolution")
        val SHOW_FRAMERATE = booleanPreferencesKey("show_framerate")
        val SHOW_PLAYED_TIME = booleanPreferencesKey("show_played_time")
        val SHOW_PATH = booleanPreferencesKey("show_path")
        val SHOW_EXTENSION = booleanPreferencesKey("show_extension")
    }

    val viewSettingsFlow: Flow<ViewSettings> = context.viewSettingsDataStore.data.map { preferences ->
        ViewSettings(
            isGrid = preferences[IS_GRID] ?: false,
            gridColumns = preferences[GRID_COLUMNS] ?: 2,
            sortOrder = try { SortOrder.valueOf(preferences[SORT_ORDER] ?: SortOrder.A_TO_Z.name) } catch (e: Exception) { SortOrder.A_TO_Z },
            showThumbnail = preferences[SHOW_THUMBNAIL] ?: true,
            showDuration = preferences[SHOW_DURATION] ?: true,
            showSize = preferences[SHOW_SIZE] ?: true,
            showDate = preferences[SHOW_DATE] ?: false,
            showSubtitleType = preferences[SHOW_SUBTITLE_TYPE] ?: false,
            showResolution = preferences[SHOW_RESOLUTION] ?: false,
            showFramerate = preferences[SHOW_FRAMERATE] ?: false,
            showPlayedTime = preferences[SHOW_PLAYED_TIME] ?: false,
            showPath = preferences[SHOW_PATH] ?: false,
            showFileExtension = preferences[SHOW_EXTENSION] ?: false
        )
    }

    suspend fun updateIsGrid(isGrid: Boolean) {
        context.viewSettingsDataStore.edit { it[IS_GRID] = isGrid }
    }

    suspend fun updateGridColumns(columns: Int) {
        context.viewSettingsDataStore.edit { it[GRID_COLUMNS] = columns }
    }

    suspend fun updateSortOrder(order: SortOrder) {
        context.viewSettingsDataStore.edit { it[SORT_ORDER] = order.name }
    }

    suspend fun updateShowThumbnail(show: Boolean) {
        context.viewSettingsDataStore.edit { it[SHOW_THUMBNAIL] = show }
    }

    suspend fun updateShowDuration(show: Boolean) {
        context.viewSettingsDataStore.edit { it[SHOW_DURATION] = show }
    }

    suspend fun updateShowSize(show: Boolean) {
        context.viewSettingsDataStore.edit { it[SHOW_SIZE] = show }
    }
    
    suspend fun updateShowDate(show: Boolean) {
        context.viewSettingsDataStore.edit { it[SHOW_DATE] = show }
    }
    
    suspend fun updateShowSubtitleType(show: Boolean) {
        context.viewSettingsDataStore.edit { it[SHOW_SUBTITLE_TYPE] = show }
    }
    
    suspend fun updateShowResolution(show: Boolean) {
        context.viewSettingsDataStore.edit { it[SHOW_RESOLUTION] = show }
    }
    
    suspend fun updateShowFramerate(show: Boolean) {
        context.viewSettingsDataStore.edit { it[SHOW_FRAMERATE] = show }
    }
    
    suspend fun updateShowPlayedTime(show: Boolean) {
        context.viewSettingsDataStore.edit { it[SHOW_PLAYED_TIME] = show }
    }

    suspend fun updateShowPath(show: Boolean) {
        context.viewSettingsDataStore.edit { it[SHOW_PATH] = show }
    }

    suspend fun updateShowFileExtension(show: Boolean) {
        context.viewSettingsDataStore.edit { it[SHOW_EXTENSION] = show }
    }
}
