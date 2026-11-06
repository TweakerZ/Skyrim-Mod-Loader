<p align="center">
  <img src="assets/source/icons/icon-dml.png" width="128" alt="Skyrim Mod Loader app icon">
</p>

<h1 align="center">Skyrim Mod Loader (SML)</h1>

<p align="center"><strong>A Skyrim Legendary Edition fork of Droid Mod Loader for the Skyrim static Android port.</strong></p>

Skyrim Mod Loader (SML) is a fork of Droid Mod Loader (DML) that targets the Skyrim
Android static port. It keeps DML's proven extraction, installer, plugin and
deployment pipeline, but uses a Material You single-page interface, the Android
system multi-file picker, and a Skyrim Legendary Edition plugin list that can be
edited from the dashboard. Imported archives are staged only long enough to
extract them; no permanent zip copy is kept.

**Droid Mod Loader (DML)** by CyberShonk manages Bethesda game mods for Windows
compatibility environments hosted on Android, such as Winlator, GameHub and
similar tools. It keeps installed mods separate, tracks profiles and plugin state,
and deploys a resolved mod setup to selected game folders.

**Skyrim Mod Loader (SML)** is for the Skyrim static Android port. It builds on
DML's foundation and adapts it to the static port's file layout and plugin
handling.

The Skyrim static Android port is created by https://github.com/ttom795/Skyrim_Android/.

> [!WARNING]
> SML is beta software that can write to game folders. Back up important game
> files before testing a new build or deployment setup.

> [!NOTE]
> Yes, AI was used in building this.

## Current support

The current release is [v0.7.0-rc8](https://github.com/TweakerZ/Skyrim-Mod-Loader/releases/tag/v0.7.0-rc8).

SML supports one game profile: **Skyrim Legendary Edition**.

Current capabilities include profiles, a Material You dashboard, Android system
multi-select archive import, managed mod installation, mod enable/disable and
priority, an editable ESP list with plugin enable/disable and ordering,
automatic Skyrim port folder detection, port `Plugins.txt` and `loadorder.txt`
synchronisation, deployment planning, physical deployment, diagnostics, and
recovery foundations.

SML uses direct filesystem paths and Android all files access for shared storage
workflows. Android 11 or newer is required.

SML installs supported ZIP, 7z, and single volume, unencrypted RAR5 archives,
including supported solid RAR5 archives. Password protected and multipart RAR
archives, and some uncommon compression or encryption variants, are unsupported.
See the [archive limits](docs/user-guide.md#current-limitations) before importing.

## New in v0.7.0-rc8

- Edit the ESP list from the dashboard: tick plugins on or off and move them up
  or down. Official Skyrim masters stay locked at the top.
- Choose **Save ESP Order** to write the pending changes to the port folder.
  `Plugins.txt` receives the enabled plugins and `loadorder.txt` receives the
  full order.
- The Skyrim port folder is detected automatically at `<game root>/port`. Its
  `save/AppData/Local/Skyrim` folder is read on startup and after profile or
  target changes, and written when ESP order is saved.
- Plugin installs, removals, mod enable/disable changes, normal deployment and
  full redeployment now reconcile the plugin list. Port plugin files are updated
  when the set of plugins changes, so removed ESPs no longer linger.
- The dashboard header shows the app name and version without the app icon.
- Launcher icons use the updated artwork with a white adaptive background and
  matching legacy and monochrome resources.

See the [release notes](releases/notes/v0.7.0-rc8.md) for changes and limitations.

## Compatibility environments

SML is intended for Android Windows compatibility setups that expose usable game
folders through shared storage. SML does not modify or depend on another
application's protected private storage.

Android may still block access to another application's protected `Android/data`
directory even when all files access is enabled.

## Get started

1. Download `DroidModLoader-v0.7.0-rc8.apk` from the [GitHub release](https://github.com/TweakerZ/Skyrim-Mod-Loader/releases/tag/v0.7.0-rc8).
2. Install the APK on your Android device. To update, install over the existing
   release app without uninstalling or clearing app data to retain profiles and
   state managed by SML.
3. Back up important game folders before deployment.
4. Follow the [User Guide](docs/user-guide.md). Choose your Skyrim main folder;
   SML checks it, finds the Data folder, and detects the port folder when present.
5. Use [Troubleshooting](docs/troubleshooting.md) if setup, import, plugin, or deployment behavior looks wrong.

## Project links

- [User Guide](docs/user-guide.md)
- [Troubleshooting](docs/troubleshooting.md)
- [Changelog](releases/changelog.md)
- [Credits](CREDITS.md)
- [Contributing](CONTRIBUTING.md)
- [LOOT Android](https://github.com/TweakerZ/loot-android) — companion load-order sorting app for SML

## Credits

SML builds on upstream and reference projects. See [CREDITS.md](CREDITS.md) for
full credits, including Droid Mod Loader, Amethyst Mod Manager, the Nexus Mods
FOMOD installer, Vortex, Mod Organizer 2, GameNative, Junrar, and the Skyrim
static Android port by ttom795.

## Contributing

Bug reports, compatibility notes, documentation corrections, reproducible test
cases, and focused code changes are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Skyrim Mod Loader is released under the [MIT License](LICENSE).

## Disclaimer

Skyrim Mod Loader is an independent project. Users remain responsible for the
licenses and permissions of games, mods, and external tools they use. SML is not
endorsed by or affiliated with Bethesda Softworks, Nexus Mods, CyberShonk, or
the authors of the Skyrim static Android port.
