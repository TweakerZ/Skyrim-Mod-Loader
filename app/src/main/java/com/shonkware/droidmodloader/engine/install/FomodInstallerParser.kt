package com.shonkware.droidmodloader.engine.install

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import java.nio.charset.Charset
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses FOMOD `ModuleConfig.xml` files (XmlScript 1.0-5.0) into an
 * [InstallerPlan].
 *
 * The implementation follows the Nexus-Mods `fomod-installer` reference and the
 * Amethyst Mod Manager reference:
 *
 * - `<installSteps>` (v2+) or root `<optionalFileGroups>` (v1).
 * - `<group type="SelectAny|SelectAtLeastOne|SelectAtMostOne|SelectExactlyOne|SelectAll">`.
 * - `<plugin>` entries with `<files>` containing any mix of `<file>` and `<folder>`.
 * - Options and groups are kept even when they have no resolvable file
 *   mappings; they may still set flags or drive conditional installs.
 * - `<typeDescriptor>` static and conditional (`dependencyType`) option types.
 * - `<conditionFlags>` and `<visible>` flag dependencies.
 * - `<requiredInstallFiles>` and `<conditionalFileInstalls>`.
 * - `<moduleDependencies>` pre-install gate (parsed here, evaluated at install time).
 * - `priority`, `alwaysInstall`, and `installIfUsable` file attributes.
 * - Source paths resolved case-insensitively against the extracted archive root,
 *   with a fallback relative to the `fomod/` folder. Resolution is best-effort
 *   at parse time and completed at install time.
 *
 * XML is parsed tolerance-first: XML namespaces are stripped by comparing local
 * names, UTF-16 / BOM / misdeclared encodings are tolerated, and the XML
 * declaration is removed so a wrong declared encoding cannot reject the document.
 */
class FomodInstallerParser {

    private enum class SortOrder { ASCENDING, DESCENDING, EXPLICIT }

    fun parse(contentRoot: File, moduleConfigFile: File, modName: String): InstallerPlan {
        val warnings = mutableListOf<String>()
        val document = parseModuleConfigXml(moduleConfigFile)
        document.documentElement.normalize()
        val root = document.documentElement
        val fomodDir = moduleConfigFile.parentFile

        val index = ContentIndex(contentRoot)

        val requiredFiles = parseFileList(
            container = findDirectChild(root, "requiredInstallFiles"),
            index = index,
            contentRoot = contentRoot,
            fomodDir = fomodDir,
            warnings = warnings,
            context = "required install files"
        )

        val groups = mutableListOf<InstallerGroup>()
        var groupCounter = 0

        val stepsContainer = findDirectChild(root, "installSteps")
        val stepElements = stepsContainer
            ?.let { getDirectChildElements(it, "installStep") }
            .orEmpty()

        if (stepElements.isNotEmpty()) {
            val orderedSteps = sortElements(
                stepElements,
                parseSortOrder(stepsContainer?.getAttribute("order"), default = SortOrder.EXPLICIT)
            ) { attribute(it, "name") }

            orderedSteps.forEachIndexed { stepIndex, stepElement ->
                val stepName = attribute(stepElement, "name").ifBlank { "Step ${stepIndex + 1}" }
                val visibility = parseVisibility(findDirectChild(stepElement, "visible"), warnings)

                val groupsContainer = findDirectChild(stepElement, "optionalFileGroups")
                val groupElements = groupsContainer
                    ?.let { getDirectChildElements(it, "group") }
                    .orEmpty()

                sortElements(
                    groupElements,
                    parseSortOrder(groupsContainer?.getAttribute("order"), default = SortOrder.EXPLICIT)
                ) { attribute(it, "name") }.forEach { groupElement ->
                    val group = parseGroup(
                        groupElement = groupElement,
                        groupId = "fomod_g${groupCounter++}",
                        stepName = stepName,
                        visibility = visibility,
                        index = index,
                        contentRoot = contentRoot,
                        fomodDir = fomodDir,
                        warnings = warnings
                    )
                    groups += group
                }
            }
        } else {
            // XmlScript 1.0 keeps <optionalFileGroups> directly under <config>.
            val groupsContainer = findDirectChild(root, "optionalFileGroups")
            val groupElements = when {
                groupsContainer != null -> getDirectChildElements(groupsContainer, "group")
                else -> descendants(root, "group")
            }

            sortElements(
                groupElements,
                parseSortOrder(groupsContainer?.getAttribute("order"), default = SortOrder.EXPLICIT)
            ) { attribute(it, "name") }.forEach { groupElement ->
                val group = parseGroup(
                    groupElement = groupElement,
                    groupId = "fomod_g${groupCounter++}",
                    stepName = "",
                    visibility = null,
                    index = index,
                    contentRoot = contentRoot,
                    fomodDir = fomodDir,
                    warnings = warnings
                )
                groups += group
            }
        }

        val conditionalFileInstalls = parseConditionalFileInstalls(
            root = root,
            index = index,
            contentRoot = contentRoot,
            fomodDir = fomodDir,
            warnings = warnings
        )

        val moduleDependency = parseModuleDependency(root, warnings)

        val moduleName = findDirectChild(root, "moduleName")
            ?.textContent
            ?.trim()
            .orEmpty()

        return InstallerPlan(
            installerType = InstallerType.FOMOD,
            displayName = moduleName.ifBlank { modName },
            rootPath = contentRoot.absolutePath,
            groups = groups,
            warnings = warnings.distinct(),
            requiredFiles = requiredFiles,
            conditionalFileInstalls = conditionalFileInstalls,
            moduleDependency = moduleDependency
        )
    }

    // ------------------------------------------------------------------
    // Group / option / file parsing
    // ------------------------------------------------------------------

    private fun parseGroup(
        groupElement: Element,
        groupId: String,
        stepName: String,
        visibility: InstallerCondition?,
        index: ContentIndex,
        contentRoot: File,
        fomodDir: File?,
        warnings: MutableList<String>
    ): InstallerGroup {
        val groupName = attribute(groupElement, "name").ifBlank { "Options" }
        val groupType = parseGroupType(attribute(groupElement, "type"))

        val pluginsContainer = findDirectChild(groupElement, "plugins")
        val pluginElements = when {
            pluginsContainer != null -> getDirectChildElements(pluginsContainer, "plugin")
            else -> descendants(groupElement, "plugin")
        }

        val orderedPlugins = sortElements(
            pluginElements,
            parseSortOrder(pluginsContainer?.getAttribute("order"), default = SortOrder.EXPLICIT)
        ) { attribute(it, "name") }

        // Keep groups even when they have no usable options: they may set
        // condition flags or drive conditional installs.
        val options = orderedPlugins.mapIndexed { pluginIndex, pluginElement ->
            parsePlugin(
                pluginElement = pluginElement,
                optionId = "${groupId}_p$pluginIndex",
                optionName = attribute(pluginElement, "name").ifBlank { "Option ${pluginIndex + 1}" },
                index = index,
                contentRoot = contentRoot,
                fomodDir = fomodDir,
                warnings = warnings
            )
        }

        // Vortex/MO2 relax impossible group constraints: several Required
        // plugins cannot be squeezed into an at-most-one or exactly-one group.
        val requiredCount = options.count { it.type == InstallerOptionType.REQUIRED }
        val effectiveType = when {
            groupType == InstallerGroupType.SELECT_AT_MOST_ONE && requiredCount > 1 ->
                InstallerGroupType.SELECT_ANY
            groupType == InstallerGroupType.SELECT_EXACTLY_ONE && requiredCount > 1 ->
                InstallerGroupType.SELECT_AT_LEAST_ONE
            else -> groupType
        }

        return InstallerGroup(
            id = groupId,
            name = groupName,
            type = effectiveType,
            options = options,
            stepName = stepName,
            visibleCondition = visibility
        )
    }

    private fun parsePlugin(
        pluginElement: Element,
        optionId: String,
        optionName: String,
        index: ContentIndex,
        contentRoot: File,
        fomodDir: File?,
        warnings: MutableList<String>
    ): InstallerOption {
        val description = findDirectChild(pluginElement, "description")
            ?.textContent
            ?.trim()
            .orEmpty()

        val imagePath = findDirectChild(pluginElement, "image")
            ?.let { attribute(it, "path") }
            ?.let { normalizeDestination(it) }
            .orEmpty()

        val descriptor = parseTypeDescriptor(findDirectChild(pluginElement, "typeDescriptor"), warnings)
        val type = descriptor.staticType ?: descriptor.defaultType ?: InstallerOptionType.OPTIONAL

        val filesContainer = findDirectChild(pluginElement, "files")
        val fileElements = when {
            filesContainer != null -> getDirectChildElements(filesContainer, "file") +
                    getDirectChildElements(filesContainer, "folder")
            else -> getDirectChildElements(pluginElement, "file") +
                    getDirectChildElements(pluginElement, "folder")
        }

        // Keep options even when they have no resolvable file mappings: they
        // may set condition flags or drive conditional installs. Unresolvable
        // sources are kept (with a warning) and resolved at install time.
        val files = fileElements.mapNotNull { fileElement ->
            parseFileElement(
                element = fileElement,
                index = index,
                contentRoot = contentRoot,
                fomodDir = fomodDir,
                warnings = warnings,
                context = "option '$optionName'"
            )
        }

        val flags = parseConditionFlags(pluginElement)
        val pluginCondition = parseCondition(findDirectChild(pluginElement, "condition"), warnings)

        val primary = files.firstOrNull()
        return InstallerOption(
            id = optionId,
            name = optionName,
            description = description,
            sourcePath = primary?.source ?: "",
            destinationPath = primary?.destination ?: "",
            required = type == InstallerOptionType.REQUIRED,
            selectedByDefault = type == InstallerOptionType.REQUIRED ||
                    type == InstallerOptionType.RECOMMENDED,
            files = files,
            condition = pluginCondition?.toString(),
            type = type,
            defaultType = descriptor.defaultType,
            typePatterns = descriptor.patterns,
            flags = flags,
            imagePath = imagePath
        )
    }

    private fun parseFileList(
        container: Element?,
        index: ContentIndex,
        contentRoot: File,
        fomodDir: File?,
        warnings: MutableList<String>,
        context: String
    ): List<InstallerFile> {
        if (container == null) return emptyList()
        return (getDirectChildElements(container, "file") +
                getDirectChildElements(container, "folder")).mapNotNull { element ->
            parseFileElement(
                element = element,
                index = index,
                contentRoot = contentRoot,
                fomodDir = fomodDir,
                warnings = warnings,
                context = context
            )
        }
    }

    private fun parseFileElement(
        element: Element,
        index: ContentIndex,
        contentRoot: File,
        fomodDir: File?,
        warnings: MutableList<String>,
        context: String
    ): InstallerFile? {
        val rawSource = attribute(element, "source").trim()
        if (rawSource.isBlank()) {
            warnings += "FOMOD $context has a file or folder without a source path."
            return null
        }

        val isDirectory = element.localName.equals("folder", ignoreCase = true)
        val normalizedSource = normalizeRelativePath(rawSource)

        // Best-effort case-insensitive resolution against the archive root, with
        // a fallback relative to the fomod/ folder. Unresolvable paths are kept
        // as-is (with a warning) and resolved again at install time, since they
        // may be created by another option or an installer script.
        val resolvedSource = index.resolve(normalizedSource, fomodDir)
        val source = resolvedSource ?: normalizedSource
        if (resolvedSource == null) {
            warnings += "FOMOD $context references a missing path: $rawSource"
        }

        val destination = if (element.hasAttribute("destination")) {
            // Explicit destination: "" means the destination root; "." means root.
            normalizeDestination(attribute(element, "destination"))
        } else {
            // Absent destination installs to the source's own path.
            source
        }

        return InstallerFile(
            source = source,
            destination = destination,
            isDirectory = isDirectory,
            priority = parsePriority(attribute(element, "priority")),
            alwaysInstall = parseBooleanAttr(element, "alwaysInstall"),
            installIfUsable = parseBooleanAttr(element, "installIfUsable")
        )
    }

    private fun parseConditionalFileInstalls(
        root: Element,
        index: ContentIndex,
        contentRoot: File,
        fomodDir: File?,
        warnings: MutableList<String>
    ): List<ConditionalFileInstall> {
        val container = findDirectChild(root, "conditionalFileInstalls") ?: return emptyList()
        val patterns = findDirectChild(container, "patterns") ?: return emptyList()

        return getDirectChildElements(patterns, "pattern").mapNotNull { pattern ->
            val condition = parseComposite(findDirectChild(pattern, "dependencies"), warnings)
                ?: InstallerCondition.Always
            val files = parseFileList(
                container = findDirectChild(pattern, "files"),
                index = index,
                contentRoot = contentRoot,
                fomodDir = fomodDir,
                warnings = warnings,
                context = "conditional install files"
            )
            ConditionalFileInstall(condition, files)
        }
    }

    private fun parseModuleDependency(root: Element, warnings: MutableList<String>): InstallerCondition? {
        val el = findDirectChild(root, "moduleDependencies")
            ?: findDirectChild(root, "moduleDependancies")
            ?: return null
        return parseComposite(el, warnings) ?: InstallerCondition.Always
    }

    private data class TypeDescriptor(
        val staticType: InstallerOptionType?,
        val defaultType: InstallerOptionType?,
        val patterns: List<InstallerOptionTypePattern>
    )

    private fun parseTypeDescriptor(element: Element?, warnings: MutableList<String>): TypeDescriptor {
        if (element == null) return TypeDescriptor(null, null, emptyList())

        val staticType = findDirectChild(element, "type")
            ?.let { parseOptionType(attribute(it, "name")) }

        val dependencyType = findDirectChild(element, "dependencyType")
            ?: findDirectChild(element, "dependancyType")
            ?: return TypeDescriptor(staticType, null, emptyList())

        val defaultValue = findDirectChild(dependencyType, "defaultType")
            ?.let { findDirectChild(it, "type") }
            ?.let { parseOptionType(attribute(it, "name")) }
            ?: InstallerOptionType.NOT_USABLE

        val patternsContainer = findDirectChild(dependencyType, "patterns")
        val patterns = patternsContainer
            ?.let { getDirectChildElements(it, "pattern") }
            .orEmpty()
            .mapNotNull { pattern ->
                val condition = parseComposite(
                    findDirectChild(pattern, "dependencies")
                        ?: findDirectChild(pattern, "dependancies"),
                    warnings
                ) ?: return@mapNotNull null
                val type = findDirectChild(pattern, "type")
                    ?.let { parseOptionType(attribute(it, "name")) }
                    ?: return@mapNotNull null
                InstallerOptionTypePattern(condition, type)
            }

        return TypeDescriptor(staticType, defaultValue, patterns)
    }

    private fun parseConditionFlags(pluginElement: Element): Map<String, String> {
        val container = findDirectChild(pluginElement, "conditionFlags") ?: return emptyMap()
        val result = linkedMapOf<String, String>()
        getDirectChildElements(container, "flag").forEach { flag ->
            val name = attribute(flag, "name").trim()
            if (name.isEmpty()) return@forEach
            // The XSD defines the flag value as element text; tolerate a
            // `value` attribute when the element is empty / self-closing.
            val value = flag.textContent.trim().ifBlank { attribute(flag, "value") }
            result[name] = value
        }
        return result
    }

    private fun parseVisibility(
        element: Element?,
        warnings: MutableList<String>
    ): InstallerCondition? = parseComposite(element, warnings)

    /**
     * Parses a `<visible>`/`<dependencies>` condition tree. Unknown dependency
     * types are treated as unsatisfiable (fail closed).
     */
    private fun parseComposite(
        element: Element?,
        warnings: MutableList<String>
    ): InstallerCondition? {
        if (element == null) return null

        val tag = element.localName.lowercase()
        if (tag in setOf("dependencies", "dependancies", "visible", "moduledependencies", "moduledependancies")) {
            val conditions = mutableListOf<InstallerCondition>()
            for (child in childElements(element)) {
                parseCondition(child, warnings)?.let { conditions += it }
            }
            return when {
                conditions.isEmpty() -> InstallerCondition.Always
                attribute(element, "operator").equals("Or", ignoreCase = true) ->
                    InstallerCondition.Any(conditions)
                else -> InstallerCondition.All(conditions)
            }
        }

        return parseCondition(element, warnings)
    }

    private fun parseCondition(element: Element?, warnings: MutableList<String>): InstallerCondition? {
        if (element == null) return null

        return when (element.localName.lowercase()) {
            "dependencies", "dependancies" -> parseComposite(element, warnings)

            "flagdependency" -> {
                val name = attribute(element, "flag").trim()
                val value = attribute(element, "value").trim()
                if (name.isEmpty()) null else InstallerCondition.Flag(name, value)
            }

            "filedependency", "modulefiledependency", "modulefiledependancy", "dependancy" -> {
                val path = attribute(element, "file").trim()
                val state = attribute(element, "state").ifBlank { "Active" }
                if (path.isEmpty()) null else InstallerCondition.FileState(path, state)
            }

            // Version / game / script-extender / mod-manager dependencies cannot
            // be verified from inside the archive. They defer to the caller's
            // versionPass flag.
            "gamedependency", "fommdependency", "fosedependency",
            "sksedependency", "nvsedependency", "f4sedependency",
            "modmodependency", "moduleversiondependency", "falloutdependency",
            "versiondependency" -> InstallerCondition.Version

            else -> {
                warnings += "FOMOD uses an unsupported dependency condition: ${element.localName}"
                InstallerCondition.Never
            }
        }
    }

    private fun parseGroupType(type: String): InstallerGroupType {
        return when (type.trim().lowercase()) {
            "selectexactlyone" -> InstallerGroupType.SELECT_EXACTLY_ONE
            "selectatmostone" -> InstallerGroupType.SELECT_AT_MOST_ONE
            "selectatleastone" -> InstallerGroupType.SELECT_AT_LEAST_ONE
            "selectall" -> InstallerGroupType.SELECT_ALL
            else -> InstallerGroupType.SELECT_ANY
        }
    }

    private fun parseOptionType(name: String): InstallerOptionType {
        return when (name.trim().lowercase()) {
            "required" -> InstallerOptionType.REQUIRED
            "recommended" -> InstallerOptionType.RECOMMENDED
            "notusable" -> InstallerOptionType.NOT_USABLE
            "couldbeusable" -> InstallerOptionType.COULD_BE_USABLE
            else -> InstallerOptionType.OPTIONAL
        }
    }

    private fun parsePriority(value: String): Int {
        return value.trim().toIntOrNull() ?: 0
    }

    private fun parseBooleanAttr(element: Element, name: String): Boolean {
        val v = attribute(element, name).trim().lowercase()
        return v == "true" || v == "1" || v == "yes"
    }

    private fun parseSortOrder(
        value: String?,
        default: SortOrder = SortOrder.EXPLICIT
    ): SortOrder {
        return when (value?.trim()?.lowercase()) {
            "ascending" -> SortOrder.ASCENDING
            "descending" -> SortOrder.DESCENDING
            "explicit" -> SortOrder.EXPLICIT
            else -> default
        }
    }

    private fun sortElements(
        elements: List<Element>,
        order: SortOrder,
        name: (Element) -> String
    ): List<Element> {
        return when (order) {
            SortOrder.EXPLICIT -> elements
            SortOrder.ASCENDING -> elements.sortedBy { name(it).lowercase() }
            SortOrder.DESCENDING -> elements.sortedByDescending { name(it).lowercase() }
        }
    }

    private fun normalizeRelativePath(path: String): String {
        return path
            .replace('\\', '/')
            .trim()
            .replace(Regex("/+"), "/")
            .trimStart('/')
    }

    /**
     * Normalizes a destination path. `""` and `"."` both mean the destination
     * root. A leading Windows drive letter (e.g. `C:\`) is stripped.
     */
    private fun normalizeDestination(path: String): String {
        val p = normalizeRelativePath(path)
        return when {
            p.isBlank() || p == "." -> ""
            p.length >= 2 && p[1] == ':' && p[0].lowercaseChar() in 'a'..'z' ->
                p.substring(2).trimStart('/')
            else -> p
        }
    }

    private fun attribute(element: Element, name: String): String {
        return element.getAttribute(name)
    }

    private fun findDirectChild(parent: Element, tagName: String): Element? {
        return childElements(parent).firstOrNull {
            it.localName.equals(tagName, ignoreCase = true)
        }
    }

    private fun getDirectChildElements(parent: Element, tagName: String): List<Element> {
        return childElements(parent).filter {
            it.localName.equals(tagName, ignoreCase = true)
        }
    }

    private fun descendants(parent: Element, tagName: String): List<Element> {
        val nodes = parent.getElementsByTagName(tagName)
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    private fun childElements(parent: Element): List<Element> {
        val result = mutableListOf<Element>()
        val children = parent.childNodes
        for (index in 0 until children.length) {
            val node = children.item(index)
            if (node.nodeType == Node.ELEMENT_NODE) {
                result += node as Element
            }
        }
        return result
    }

    /**
     * Parses a ModuleConfig.xml file, tolerating incorrect encoding declarations
     * and XML namespaces.
     */
    private fun parseModuleConfigXml(file: File): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            runCatching {
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            }
        }
        val builder: DocumentBuilder = factory.newDocumentBuilder()
        return builder.parse(InputSource(StringReader(decodeXmlText(file))))
    }

    private fun decodeXmlText(file: File): String {
        val raw = file.readBytes()
        for (enc in encodingCandidates(raw)) {
            val decoded = runCatching { String(raw, Charset.forName(enc)) }.getOrNull()
            if (decoded != null) return decoded
        }
        return String(raw, Charset.forName("ISO-8859-1"))
    }

    private fun encodingCandidates(raw: ByteArray): List<String> {
        val b0 = raw.getOrNull(0)?.toInt()?.and(0xFF) ?: -1
        val b1 = raw.getOrNull(1)?.toInt()?.and(0xFF) ?: -1
        val b2 = raw.getOrNull(2)?.toInt()?.and(0xFF) ?: -1
        val b3 = raw.getOrNull(3)?.toInt()?.and(0xFF) ?: -1
        return when {
            b0 == 0xFF && b1 == 0xFE -> listOf("UTF-16")   // UTF-16 LE BOM
            b0 == 0xFE && b1 == 0xFF -> listOf("UTF-16")   // UTF-16 BE BOM
            b0 == 0xEF && b1 == 0xBB && b2 == 0xBF -> listOf("UTF-8", "UTF-16", "ISO-8859-1")
            b0 == 0x3C && b1 == 0x00 && b2 == 0x3F && b3 == 0x00 ->
                listOf("UTF-16LE", "UTF-8", "ISO-8859-1") // '<?' UTF-16 LE (no BOM)
            b0 == 0x00 && b1 == 0x3C && b2 == 0x00 && b3 == 0x3F ->
                listOf("UTF-16BE", "UTF-8", "ISO-8859-1") // '<?' UTF-16 BE (no BOM)
            else -> listOf("UTF-8", "ISO-8859-1")
        }
    }

    /** Case-insensitive index of the extracted archive, mirroring Windows semantics. */
    private class ContentIndex(private val root: File) {
        private val byLowercasePath: Map<String, String> by lazy {
            val result = HashMap<String, String>()
            root.walkTopDown().forEach { file ->
                if (file == root) return@forEach
                val relative = file.relativeTo(root)
                    .invariantSeparatorsPath
                    .trimStart('/')
                if (relative.isNotEmpty()) {
                    result.putIfAbsent(relative.lowercase(), relative)
                }
            }
            result
        }

        fun resolve(source: String, fomodDir: File?): String? {
            val normalized = source
                .replace('\\', '/')
                .trim()
                .replace(Regex("/+"), "/")
                .trimStart('/')
                .trimEnd('/')

            if (normalized.isEmpty()) return null
            if (normalized.split('/').any { it == ".." }) {
                return null
            }

            byLowercasePath[normalized.lowercase()]?.let { return it }

            if (File(root, normalized).exists()) return normalized

            if (fomodDir != null) {
                val candidate = File(fomodDir, normalized)
                if (candidate.exists()) {
                    return runCatching {
                        root.toPath().relativize(candidate.toPath()).toString()
                            .replace(File.separatorChar, '/')
                    }.getOrNull()
                }
            }

            return null
        }
    }

}
