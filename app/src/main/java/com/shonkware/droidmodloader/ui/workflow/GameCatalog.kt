package com.shonkware.droidmodloader.ui.workflow

internal object GameCatalog {
    const val DEFAULT_GAME_ID = "skyrim_le"

    val supportedGameIds: List<String> = listOf("skyrim_le")

    fun displayName(gameId: String): String {
        return when (gameId) {
            "skyrim_le" -> "Skyrim Legendary Edition"
            else -> gameId
        }
    }

    fun supportedOrDefault(gameId: String): String {
        return gameId.takeIf { it in supportedGameIds } ?: DEFAULT_GAME_ID
    }
}
