package com.shonkware.droidmodloader.engine.plugins

data class OfficialPluginRule(
    val pluginName: String,
    val sourceType: String,
    val orderRank: Int,
    val locked: Boolean,
    val defaultEnabled: Boolean
)

class GamePluginRules {

    fun findRule(gameId: String, pluginName: String): OfficialPluginRule? {
        val lower = pluginName.lowercase()
        return getOfficialRules(gameId).firstOrNull {
            it.pluginName.lowercase() == lower
        }
    }

    private fun getOfficialRules(gameId: String): List<OfficialPluginRule> {
        return when (gameId) {
            "skyrim_le" -> skyrimLeRules()
            else -> emptyList()
        }
    }

    private fun skyrimLeRules(): List<OfficialPluginRule> {
        return listOf(
            OfficialPluginRule("Skyrim.esm", "base_game", 1, locked = true, defaultEnabled = true),
            OfficialPluginRule("Update.esm", "base_game", 2, locked = true, defaultEnabled = true),
            OfficialPluginRule("Dawnguard.esm", "official_dlc", 3, locked = true, defaultEnabled = true),
            OfficialPluginRule("HearthFires.esm", "official_dlc", 4, locked = true, defaultEnabled = true),
            OfficialPluginRule("Dragonborn.esm", "official_dlc", 5, locked = true, defaultEnabled = true),

            // Optional official High Resolution Texture Pack plugins.
            OfficialPluginRule("HighResTexturePack01.esp", "official_dlc", 6, locked = false, defaultEnabled = true),
            OfficialPluginRule("HighResTexturePack02.esp", "official_dlc", 7, locked = false, defaultEnabled = true),
            OfficialPluginRule("HighResTexturePack03.esp", "official_dlc", 8, locked = false, defaultEnabled = true)
        )
    }
}