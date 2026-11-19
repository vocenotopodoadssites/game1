package com.example.alienfall.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.alienfall.model.EquipmentItem
import com.example.alienfall.model.GameSettings
import com.example.alienfall.model.GraphicsQuality
import com.example.alienfall.model.HeroClass
import com.example.alienfall.model.PersistentProgress
import com.example.alienfall.model.SkinVariant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.alienfallDataStore: DataStore<Preferences> by preferencesDataStore(name = "alienfall_save")

class PersistenceManager(private val context: Context) {

    private val progressKey = stringPreferencesKey("progress_json_v1")

    val progressFlow: Flow<PersistentProgress> = context.alienfallDataStore.data.map { prefs ->
        val raw = prefs[progressKey]
        if (raw.isNullOrBlank()) PersistentProgress() else decode(raw)
    }

    suspend fun load(): PersistentProgress = progressFlow.first()

    suspend fun save(progress: PersistentProgress) {
        context.alienfallDataStore.edit { prefs ->
            prefs[progressKey] = encode(progress)
        }
    }

    private fun encode(p: PersistentProgress): String {
        val obj = JSONObject()
        obj.put("coins", p.coins)
        obj.put("selectedHeroId", p.selectedHeroId)
        obj.put("unlockedHeroIds", JSONArray(p.unlockedHeroIds.toList()))
        obj.put("selectedSkins", JSONObject(p.selectedSkins))
        obj.put("unlockedSkins", JSONArray(p.unlockedSkins.toList()))
        obj.put("permanentUpgrades", JSONObject(p.permanentUpgrades))
        obj.put("unlockedEquipments", JSONArray(p.unlockedEquipments.toList()))
        obj.put("equippedItemId", p.equippedItemId)
        obj.put("totalKills", p.totalKills)
        obj.put("totalBossesDefeated", p.totalBossesDefeated)
        obj.put("totalRuns", p.totalRuns)
        obj.put("bestSurvivalSeconds", p.bestSurvivalSeconds)
        obj.put("bestKillsInRun", p.bestKillsInRun)
        obj.put("highestLevelReached", p.highestLevelReached)

        val settingsObj = JSONObject()
        settingsObj.put("graphicsQuality", p.settings.graphicsQuality.name)
        settingsObj.put("musicEnabled", p.settings.musicEnabled)
        settingsObj.put("sfxEnabled", p.settings.sfxEnabled)
        settingsObj.put("manualAimDefault", p.settings.manualAimDefault)
        settingsObj.put("showDamageNumbers", p.settings.showDamageNumbers)
        obj.put("settings", settingsObj)
        return obj.toString()
    }

    private fun decode(raw: String): PersistentProgress {
        return try {
            val obj = JSONObject(raw)
            val unlockedHeroes = jsonArrayToSet(obj.optJSONArray("unlockedHeroIds"))
                .ifEmpty { setOf(HeroClass.BERSERKER.id, HeroClass.TECNOLOGICO.id, HeroClass.GUERREIRO.id) }
            val unlockedSkins = jsonArrayToSet(obj.optJSONArray("unlockedSkins"))
                .ifEmpty { setOf(SkinVariant.DEFAULT.id) }
            val unlockedEquip = jsonArrayToSet(obj.optJSONArray("unlockedEquipments"))
                .ifEmpty { setOf(EquipmentItem.NONE.id) }

            val skinsMap = mutableMapOf<String, String>()
            obj.optJSONObject("selectedSkins")?.let { sObj ->
                sObj.keys().forEach { k -> skinsMap[k] = sObj.optString(k, SkinVariant.DEFAULT.id) }
            }

            val upgradesMap = mutableMapOf<String, Int>()
            obj.optJSONObject("permanentUpgrades")?.let { uObj ->
                uObj.keys().forEach { k -> upgradesMap[k] = uObj.optInt(k, 0) }
            }

            val sObj = obj.optJSONObject("settings")
            val quality = runCatching {
                GraphicsQuality.valueOf(sObj?.optString("graphicsQuality") ?: GraphicsQuality.MEDIUM.name)
            }.getOrDefault(GraphicsQuality.MEDIUM)

            PersistentProgress(
                coins = obj.optInt("coins", 100),
                selectedHeroId = obj.optString("selectedHeroId", HeroClass.BERSERKER.id),
                unlockedHeroIds = unlockedHeroes,
                selectedSkins = skinsMap,
                unlockedSkins = unlockedSkins,
                permanentUpgrades = upgradesMap,
                unlockedEquipments = unlockedEquip,
                equippedItemId = obj.optString("equippedItemId", EquipmentItem.NONE.id),
                totalKills = obj.optInt("totalKills", 0),
                totalBossesDefeated = obj.optInt("totalBossesDefeated", 0),
                totalRuns = obj.optInt("totalRuns", 0),
                bestSurvivalSeconds = obj.optInt("bestSurvivalSeconds", 0),
                bestKillsInRun = obj.optInt("bestKillsInRun", 0),
                highestLevelReached = obj.optInt("highestLevelReached", 1),
                settings = GameSettings(
                    graphicsQuality = quality,
                    musicEnabled = sObj?.optBoolean("musicEnabled", true) ?: true,
                    sfxEnabled = sObj?.optBoolean("sfxEnabled", true) ?: true,
                    manualAimDefault = sObj?.optBoolean("manualAimDefault", false) ?: false,
                    showDamageNumbers = sObj?.optBoolean("showDamageNumbers", true) ?: true
                )
            )
        } catch (_: Exception) {
            PersistentProgress()
        }
    }

    private fun jsonArrayToSet(arr: JSONArray?): Set<String> {
        if (arr == null) return emptySet()
        val out = mutableSetOf<String>()
        for (i in 0 until arr.length()) {
            val s = arr.optString(i)
            if (s.isNotBlank()) out.add(s)
        }
        return out
    }
}
