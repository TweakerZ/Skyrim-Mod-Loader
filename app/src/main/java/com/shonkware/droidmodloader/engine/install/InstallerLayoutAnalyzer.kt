package com.shonkware.droidmodloader.engine.install

import java.io.File

class InstallerLayoutAnalyzer {

    private val dataRootFolderNames = setOf(
        "data",
        "meshes",
        "textures",
        "scripts",
        "interface",
        "sound",
        "music",
        "strings",
        "video",
        "menus",
        "fonts",
        "shaders",
        "skse",
        "nvse",
        "obse",
        "fose",
        "f4se",
        "lodsettings",
        "grass",
        "seq"
    )

    fun analyze(contentRoot: File, modName: String): InstallerPlan {
        val fomodConfig = findFomodConfig(contentRoot)
        if (fomodConfig != null) {
            return FomodInstallerParser().parse(contentRoot, fomodConfig, modName)
        }

        // Scripted FOMOD (fomod/ with script.cs/script.dll but no ModuleConfig).
        // C# installer scripts cannot run on Android; fall back to a raw Data
        // install with a clear warning instead of copying script.cs into Data.
        if (detectScriptedFomod(contentRoot)) {
            val simple = createSimplePlan(contentRoot, modName)
            return simple.copy(
                warnings = simple.warnings +
                    listOf(
                        "This FOMOD uses installer scripts (script.cs/script.dll) which cannot run on Android. " +
                            "Installing raw Data files only; the fomod/ installer folder is not deployed."
                    )
            )
        }

        val bainPlan = createBainPlanIfApplicable(contentRoot, modName)
        if (bainPlan != null) {
            return bainPlan
        }

        if (hasRecognizedGameContent(contentRoot)) {
            return createSimplePlan(contentRoot, modName)
        }

        val candidates = findDataRootCandidates(contentRoot)

        if (candidates.isNotEmpty()) {
            return createManualDataFolderPlan(
                contentRoot = contentRoot,
                modName = modName,
                candidates = candidates
            )
        }

        // Extraction safety is already enforced. Unknown structure requires an
        // explicit file selection and acknowledgement, not an extraction failure.
        return InstallerPlan(
            installerType = InstallerType.MANUAL_DATA_FOLDER,
            displayName = modName,
            rootPath = contentRoot.absolutePath,
            groups = emptyList()
        )
    }

    fun resolveContentRoot(rawExtractRoot: File): File {
        // 1. Unbounded single-directory wrapper peel (MO2 / Amethyst style),
        //    checking for fomod/ModuleConfig at each level before stopping on
        //    recognized game content.
        var current = rawExtractRoot
        var passes = 0

        while (passes < 5) {
            passes++

            val children = current.listFiles()
                ?.filter { it.exists() && it.name != "." && it.name != ".." }
                ?: return current

            val files = children.filter { it.isFile }
            val dirs = children.filter { it.isDirectory }

            if (findFomodConfig(current) != null) {
                return current
            }

            if (hasRecognizedGameContent(current)) {
                return current
            }

            if (files.isEmpty() && dirs.size == 1) {
                val onlyDir = dirs.single()
                val onlyDirName = onlyDir.name.lowercase()

                if (onlyDirName in dataRootFolderNames) {
                    return current
                }

                current = onlyDir
                continue
            }

            return current
        }

        // 2. Fallback BFS up to 6 levels: fomod/ may sit next to sibling files
        //    instead of at the end of a single-directory wrapper chain.
        val bfsRoot = bfsFindFomodRoot(rawExtractRoot, 6)
        if (bfsRoot != null) return bfsRoot

        return current
    }

    /**
     * Breadth-first search for a directory containing fomod/ModuleConfig.xml,
     * mirroring Amethyst's fallback detection for archives that bundle the
     * FOMOD installer alongside other files.
     */
    private fun bfsFindFomodRoot(root: File, maxDepth: Int): File? {
        var candidates: List<File> = listOf(root)
        val seen = hashSetOf(root)

        for (level in 0..maxDepth) {
            val nextLevel = mutableListOf<File>()
            for (dir in candidates) {
                if (findFomodConfig(dir) != null) {
                    return dir
                }
                if (level == maxDepth) continue
                try {
                    dir.listFiles()?.filter { it.isDirectory && it !in seen }?.forEach { child ->
                        seen.add(child)
                        nextLevel += child
                    }
                } catch (_: Exception) {
                    // Unreadable directory - skip it.
                }
            }
            if (nextLevel.isEmpty()) break
            candidates = nextLevel
        }

        return null
    }

    private fun findFomodConfig(contentRoot: File): File? {
        val fomodDir = contentRoot.listFiles()
            ?.firstOrNull {
                it.isDirectory && it.name.equals("fomod", ignoreCase = true)
            }

        if (fomodDir != null) {
            val config = fomodDir.listFiles()
                ?.firstOrNull {
                    it.isFile && it.name.equals("ModuleConfig.xml", ignoreCase = true)
                }

            if (config != null) return config
        }

        return null
    }

    /**
     * Detects a *scripted* FOMOD installer: a fomod/ folder containing
     * script.cs/script.dll but no ModuleConfig.xml. C# installer scripts cannot
     * run on Android, so callers should fall back to a raw Data install with a
     * clear warning.
     */
    fun detectScriptedFomod(root: File): Boolean {
        // Unbounded single-directory wrapper peel.
        var current = root
        for (pass in 0 until 20) {
            if (checkScriptedFomodDir(current) != null) return true
            val children = current.listFiles() ?: break
            val dirs = children.filter { it.isDirectory }
            if (children.filter { it.isFile }.isEmpty() && dirs.size == 1) {
                current = dirs.single()
                continue
            }
            break
        }

        // Fallback BFS up to 6 levels.
        var candidates: List<File> = listOf(root)
        val seen = hashSetOf(root)
        for (level in 0..6) {
            val nextLevel = mutableListOf<File>()
            for (dir in candidates) {
                if (checkScriptedFomodDir(dir) != null) return true
                if (level == 6) continue
                try {
                    dir.listFiles()?.filter { it.isDirectory && it !in seen }?.forEach { child ->
                        seen.add(child)
                        nextLevel += child
                    }
                } catch (_: Exception) {
                    // Unreadable directory - skip it.
                }
            }
            if (nextLevel.isEmpty()) break
            candidates = nextLevel
        }

        return false
    }

    private fun checkScriptedFomodDir(dir: File): File? {
        val fomodDir = dir.listFiles()?.firstOrNull {
            it.isDirectory && it.name.equals("fomod", ignoreCase = true)
        } ?: return null
        val names = fomodDir.listFiles()?.map { it.name.lowercase() } ?: return null
        if ("moduleconfig.xml" in names) return null // XML installer, not scripted
        if (names.contains("script.cs") || names.contains("script.dll")) return fomodDir
        return null
    }

    private fun createBainPlanIfApplicable(
        contentRoot: File,
        modName: String
    ): InstallerPlan? {
        val folders = contentRoot.listFiles()
            ?.filter { it.isDirectory && isBainFolder(it.name) }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

        if (folders.isEmpty()) return null

        val options = folders.mapIndexed { index, folder ->
            val core = isCoreFolder(folder.name)

            InstallerOption(
                id = "bain_$index",
                name = folder.name,
                description = if (core) {
                    "Core or required package folder"
                } else {
                    "Optional package folder"
                },
                sourcePath = folder.name,
                destinationPath = "",
                required = core,
                selectedByDefault = core
            )
        }

        val warnings = mutableListOf<String>()

        val optionalCount = options.count { !it.required }
        if (optionalCount > 0) {
            warnings.add("BAIN-style optional folders detected. Choose only the options you want installed.")
        }

        if (options.none { it.required }) {
            warnings.add("BAIN-style folders were found, but no obvious core folder was detected. Select at least one folder.")
        }

        return InstallerPlan(
            installerType = InstallerType.BAIN,
            displayName = modName,
            rootPath = contentRoot.absolutePath,
            groups = listOf(
                InstallerGroup(
                    id = "bain_main",
                    name = "BAIN Package Folders",
                    type = InstallerGroupType.SELECT_ANY,
                    options = options
                )
            ),
            warnings = warnings
        )
    }

    private fun createSimplePlan(
        contentRoot: File,
        modName: String
    ): InstallerPlan {
        val hasDataFolder = File(contentRoot, "Data").exists()
        val hasRootLevelGameRootFiles = hasRootLevelGameRootContent(contentRoot)

        val source = when {
            hasDataFolder && !hasRootLevelGameRootFiles -> "Data"
            else -> "."
        }

        return InstallerPlan(
            installerType = InstallerType.SIMPLE,
            displayName = modName,
            rootPath = contentRoot.absolutePath,
            groups = listOf(
                InstallerGroup(
                    id = "simple",
                    name = "Simple Install",
                    type = InstallerGroupType.SELECT_ANY,
                    options = listOf(
                        InstallerOption(
                            id = "simple_all",
                            name = "Install detected files",
                            sourcePath = source,
                            destinationPath = "",
                            required = true,
                            selectedByDefault = true
                        )
                    )
                )
            )
        )
    }

    private fun createManualDataFolderPlan(
        contentRoot: File,
        modName: String,
        candidates: List<File>
    ): InstallerPlan {
        val options = candidates
            .distinctBy { it.absolutePath }
            .sortedBy { candidate ->
                candidate.relativeTo(contentRoot).path.length
            }
            .take(12)
            .mapIndexed { index, candidate ->
                val relativePath = candidate
                    .relativeTo(contentRoot)
                    .path
                    .replace("\\", "/")
                    .let { if (it == ".") "" else it }

                InstallerOption(
                    id = "manual_data_folder_$index",
                    name = if (relativePath.isBlank()) {
                        "Use archive root as the Data folder"
                    } else {
                        "Use $relativePath as the Data folder"
                    },
                    description = "Install the contents of this folder as the mod's Data content.",
                    sourcePath = if (relativePath.isBlank()) "." else relativePath,
                    destinationPath = "",
                    required = false,
                    selectedByDefault = index == 0
                )
            }

        return InstallerPlan(
            installerType = InstallerType.MANUAL_DATA_FOLDER,
            displayName = modName,
            rootPath = contentRoot.absolutePath,
            groups = listOf(
                InstallerGroup(
                    id = "manual_data_folder",
                    name = "Choose Data Folder",
                    type = InstallerGroupType.SELECT_EXACTLY_ONE,
                    options = options
                )
            ),
            warnings = listOf(
                "No clear game data folder was found at the archive root. Choose which folder should be installed as the Data folder."
            )
        )
    }

    private fun findDataRootCandidates(
        root: File,
        maxDepth: Int = 4
    ): List<File> {
        val results = mutableListOf<File>()

        fun visit(dir: File, depth: Int) {
            if (depth > maxDepth) return

            if (dir.name.equals("fomod", ignoreCase = true)) {
                return
            }

            if (hasRecognizedGameContent(dir)) {
                results.add(dir)
                return
            }

            val children = dir.listFiles() ?: return

            children
                .filter { it.isDirectory }
                .filterNot { it.name.equals("fomod", ignoreCase = true) }
                .forEach { child ->
                    visit(child, depth + 1)
                }
        }

        visit(root, 0)

        return results.distinctBy { it.absolutePath }
    }

    private fun hasRecognizedGameContent(root: File): Boolean {
        val children = root.listFiles() ?: return false

        return children.any { child ->
            val name = child.name.lowercase()

            when {
                child.isDirectory -> name in dataRootFolderNames
                child.isFile -> isLikelyDataRootFile(name) || isLikelyGameRootFile(name)
                else -> false
            }
        }
    }

    /** Recognition hint for a proposed output tree; not a game compatibility check. */
    internal fun hasRecognizedGamePaths(paths: List<String>): Boolean = paths.any { path ->
        if ('/' in path) path.substringBefore('/').lowercase(java.util.Locale.ROOT) in dataRootFolderNames
        else isLikelyDataRootFile(path) || isLikelyGameRootFile(path)
    }

    private fun hasRootLevelGameRootContent(contentRoot: File): Boolean {
        val children = contentRoot.listFiles() ?: return false

        return children.any { child ->
            child.isFile && isLikelyGameRootFile(child.name)
        }
    }

    private fun isBainFolder(name: String): Boolean {
        val lower = name.lowercase()

        return lower.matches(Regex("""^\d{2,3}\s+.+""")) ||
                lower.startsWith("optional") ||
                lower.startsWith("options") ||
                lower.startsWith("patches") ||
                lower.startsWith("patch")
    }

    private fun isCoreFolder(name: String): Boolean {
        val lower = name.lowercase()

        return lower.contains("core") ||
                lower.contains("main") ||
                lower.contains("required") ||
                lower.startsWith("00")
    }

    private fun isLikelyDataRootFile(fileName: String): Boolean {
        val lower = fileName.lowercase()

        return lower.endsWith(".esp") ||
                lower.endsWith(".esm") ||
                lower.endsWith(".esl") ||
                lower.endsWith(".bsa") ||
                lower.endsWith(".ba2") ||
                lower.endsWith(".ini")
    }

    private fun isLikelyGameRootFile(fileName: String): Boolean {
        val lower = fileName.lowercase()

        return lower.endsWith(".exe") ||
                lower.endsWith(".dll") ||
                lower.endsWith(".asi") ||
                lower == "d3d9.dll" ||
                lower == "d3d11.dll" ||
                lower == "dxgi.dll" ||
                lower == "dinput8.dll" ||
                lower == "xinput1_3.dll" ||
                lower == "enblocal.ini" ||
                lower == "enbseries.ini" ||
                lower.startsWith("skse_loader") ||
                lower.startsWith("nvse_loader") ||
                lower.startsWith("obse_loader") ||
                lower.startsWith("fose_loader") ||
                lower.startsWith("f4se_loader")
    }
}