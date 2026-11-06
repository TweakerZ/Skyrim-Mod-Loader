package com.shonkware.droidmodloader.engine.plugins

enum class PluginLoadOrderMechanism {
    TEXT_FILES,
    FILE_TIMESTAMPS
}

enum class PluginListMembership {
    ENABLED_ONLY,
    COMPLETE_ORDER
}

data class GamePluginLoadOrderRule(
    val gameId: String,
    val mechanism: PluginLoadOrderMechanism,
    val pluginsTxtMembership: PluginListMembership,
    val loadorderTxtMembership: PluginListMembership?
)

class GamePluginLoadOrderRules {
    fun find(gameId: String): GamePluginLoadOrderRule? {
        return when (gameId) {
            "skyrim_le" -> GamePluginLoadOrderRule(
                gameId = gameId,
                mechanism = PluginLoadOrderMechanism.TEXT_FILES,
                pluginsTxtMembership = PluginListMembership.ENABLED_ONLY,
                loadorderTxtMembership = PluginListMembership.COMPLETE_ORDER
            )

            else -> null
        }
    }

    fun require(gameId: String): GamePluginLoadOrderRule {
        return find(gameId)
            ?: throw IllegalArgumentException("Unsupported game plugin rules: $gameId")
    }
}
