# Credits

Skyrim Android Mod Loader is a Skyrim Legendary Edition fork of Droid Mod Loader.
It would not exist without the projects below, and their authors deserve the
credit for the foundations, reference behaviour and tooling used here.

## Upstream project

### Droid Mod Loader

- Repository: https://github.com/CyberShonk/DroidModLoader
- Role: upstream project. This fork keeps DML's archive import, installation,
  profile, plugin and deployment pipeline and changes the UI and game support for
  the Skyrim Android static port.
- License: MIT.

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
