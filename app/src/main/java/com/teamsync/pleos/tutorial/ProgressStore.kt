package com.teamsync.pleos.tutorial

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.teamsync.pleos.log.AppLog
import kotlinx.coroutines.flow.first
import org.json.JSONObject

private val Context.progressDataStore by preferencesDataStore(name = "tutorial_progress")

/** 단계별 결과를 DataStore에 저장 → 앱을 다시 열어도 이어서 할 수 있다. 형식: {"<stepId>": {"o": "COMPLETED", "d": "..."}} */
class ProgressStore(private val context: Context) {

    suspend fun load(): Map<String, StepResult> = try {
        val raw = context.progressDataStore.data.first()[KEY_RESULTS] ?: return emptyMap()
        val json = JSONObject(raw)
        json.keys().asSequence().mapNotNull { id ->
            val item = json.getJSONObject(id)
            val outcome = runCatching { Outcome.valueOf(item.getString("o")) }.getOrNull() ?: return@mapNotNull null
            id to StepResult(outcome, item.optString("d"))
        }.toMap().also { AppLog.i(TAG, "load ${it.size} results") }
    } catch (e: Exception) {
        AppLog.e(TAG, "load failed: ${e.message}", e)
        emptyMap()
    }

    suspend fun save(results: Map<String, StepResult>) {
        try {
            val json = JSONObject()
            results.forEach { (id, r) -> json.put(id, JSONObject().put("o", r.outcome.name).put("d", r.detail)) }
            context.progressDataStore.edit { it[KEY_RESULTS] = json.toString() }
            AppLog.d(TAG, "saved ${results.size} results")
        } catch (e: Exception) {
            AppLog.e(TAG, "save failed: ${e.message}", e)
        }
    }

    suspend fun openingSeen(): Boolean =
        runCatching { context.progressDataStore.data.first()[KEY_OPENING_SEEN] == true }
            .onFailure { AppLog.e(TAG, "openingSeen read failed: ${it.message}", it) }
            .getOrDefault(false)

    suspend fun markOpeningSeen() {
        runCatching { context.progressDataStore.edit { it[KEY_OPENING_SEEN] = true } }
            .onFailure { AppLog.e(TAG, "markOpeningSeen failed: ${it.message}", it) }
    }

    private companion object {
        const val TAG = "Progress"
        val KEY_RESULTS = stringPreferencesKey("results")
        val KEY_OPENING_SEEN = booleanPreferencesKey("opening_seen")
    }
}
