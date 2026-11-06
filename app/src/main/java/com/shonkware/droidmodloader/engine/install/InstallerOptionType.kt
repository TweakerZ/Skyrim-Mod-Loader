package com.shonkware.droidmodloader.engine.install

/** FOMOD option types from the XmlScript 1.0-5.0 schemas. */
enum class InstallerOptionType {
    REQUIRED,
    RECOMMENDED,
    OPTIONAL,
    NOT_USABLE,
    COULD_BE_USABLE;

    val isUsable: Boolean
        get() = this != NOT_USABLE
}
