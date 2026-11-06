# Credits

Skyrim Mod Loader (SML) is a Skyrim Legendary Edition fork of Droid Mod Loader
(DML) by CyberShonk. SML adapts DML for the Skyrim static Android port.

SML would not exist without the projects below, and their authors deserve the
credit for the foundations, reference behaviour and tooling used here.

> [!NOTE]
> Yes, AI was used in building this.

## Upstream project

### Droid Mod Loader

- Repository: https://github.com/CyberShonk/DroidModLoader
- Role: upstream project. DML manages Bethesda game mods for Windows
  compatibility environments hosted on Android, such as Winlator, GameHub and
  similar tools. SML keeps DML's archive import, installation, profile, plugin
  and deployment pipeline and adapts it for the Skyrim static Android port.
- License: MIT.
- Note: SML is a fork of DML. DML is not this project, and this project is not
  DML.

## Reference projects

These projects informed how this fork parses and applies FOMOD installers and
plugin load orders. No claim is made that their code is copied where it was used
only as a behavioural reference.

### Amethyst Mod Manager

- Repository: https://github.com/ChrisDKN/Amethyst-Mod-Manager
- Role: reference for FOMOD option handling and installer flow.
- License: GNU General Public License v3.0.

### Nexus Mods FOMOD Installer

- Repository: https://github.com/Nexus-Mods/fomod-installer
- Role: reference for `ModuleConfig.xml` parsing, installer steps, groups,
  dependencies, conditional installs and option types.
- License: GNU General Public License v3.0.

### Vortex

- Repository: https://github.com/Nexus-Mods/Vortex
- Role: reference for FOMOD group constraints and installer behaviour.
- License: GNU General Public License v3.0.

### Mod Organizer 2

- Repository: https://github.com/ModOrganizer2/modorganizer
- Role: reference for mod-manager conventions and FOMOD handling.
- License: GNU General Public License v3.0.

## Compatibility environment

### GameNative

- Repository: https://github.com/utkarshdalal/GameNative
- Role: Android Windows compatibility environment used for testing and for the
  shared-storage workflow this app targets.
- License: GNU General Public License v3.0.

GameNative is a separate application. This fork does not include or modify its
code, and it does not access another app's protected private storage.

## Skyrim static Android port

### Skyrim_Android

- Repository: https://github.com/ttom795/Skyrim_Android/
- Role: Creator of the Skyrim static Android port that SML targets. SML adapts
  DML's pipeline to this port's file layout and plugin handling.
- Note: SML is independent of and not endorsed by the Skyrim_Android project.

## Companion app

### LOOT Android

- Repository: https://github.com/TweakerZ/loot-android
- Role: Android front-end for LOOT that works with SML. It reads and writes the
  same `Plugins.txt` and `loadorder.txt` that SML manages in the Skyrim static
  port, and sorts the plugin list using the real libloot engine.
- Note: LOOT Android is a separate application. SML does not include or modify
  its code.

## Runtime libraries

### Junrar

- Repository: https://github.com/junrar/junrar
- Role: RAR archive extraction.
- License: unRAR license. See `app/src/main/assets/licenses/junrar.txt`.

### Apache Commons Compress and XZ for Java

- Apache Commons Compress: https://github.com/apache/commons-compress
- XZ for Java: https://github.com/tukaani-project/xz-java
- Role: archive handling and XZ decompression.
- License: Apache License 2.0.

### AndroidX, Jetpack Compose and Kotlin

- AndroidX: https://github.com/androidx/androidx
- Jetpack Compose: https://github.com/androidx/androidx/tree/androidx-main/compose
- Kotlin: https://github.com/JetBrains/kotlin
- Role: Android UI, lifecycle, runtime and language support.
- License: Apache License 2.0.

The complete dependency list and licence texts are recorded in
[`releases/templates/THIRD-PARTY-NOTICES.txt`](releases/templates/THIRD-PARTY-NOTICES.txt).

## Community

- ARM64Nexus community: https://discord.gg/RvTWcEeJ7Z

## Trademarks

Skyrim and The Elder Scrolls are trademarks of Bethesda Softworks LLC. Nexus
Mods, Vortex, Mod Organizer and the other names above belong to their respective
owners. This project is independent and is not endorsed by or affiliated with
those owners.
