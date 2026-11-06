package com.shonkware.droidmodloader.engine.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FomodInstallerParserTest {

    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun `folders inside files install their contents relative to the archive root`() {
        val fixture = fixture(
            xml = """
                <config>
                  <moduleName>Folder Test</moduleName>
                  <installSteps order="Explicit">
                    <installStep name="Installation">
                      <optionalFileGroups order="Explicit">
                        <group name="Installation" type="SelectExactlyOne">
                          <plugins order="Explicit">
                            <plugin name="Extended">
                              <description>Extended</description>
                              <files>
                                <folder source="skeleton meshes" destination="" priority="0" />
                              </files>
                              <typeDescriptor><type name="Optional" /></typeDescriptor>
                            </plugin>
                          </plugins>
                        </group>
                      </optionalFileGroups>
                    </installStep>
                  </installSteps>
                </config>
            """.trimIndent(),
            files = listOf("Skeleton Meshes/meshes/body.nif")
        )

        val plan = fixture.parse()

        assertEquals(1, plan.groups.size)
        val option = plan.groups.single().options.single()
        val file = option.files.single()
        // Windows-style FOMOD casing resolves to the real archive casing.
        assertEquals("Skeleton Meshes", file.source)
        assertEquals("", file.destination)
        assertTrue(file.isDirectory)

        val selection = plan.initialSelection()
        assertEquals(setOf(option.id), selection)
        assertEquals(listOf(file), plan.resolveInstallFiles(selection))
    }

    @Test
    fun `flag dependencies reveal and hide later install steps`() {
        val fixture = fixture(
            xml = """
                <config>
                  <moduleName>Flags</moduleName>
                  <installSteps order="Explicit">
                    <installStep name="Installation">
                      <optionalFileGroups order="Explicit">
                        <group name="Installation" type="SelectExactlyOne">
                          <plugins order="Explicit">
                            <plugin name="Extended">
                              <conditionFlags><flag name="Ext">On</flag></conditionFlags>
                              <files><folder source="Skeleton" destination="" /></files>
                              <typeDescriptor><type name="Optional" /></typeDescriptor>
                            </plugin>
                            <plugin name="Basic">
                              <conditionFlags><flag name="Ext">Off</flag></conditionFlags>
                              <files><folder source="Other" destination="" /></files>
                              <typeDescriptor><type name="Optional" /></typeDescriptor>
                            </plugin>
                          </plugins>
                        </group>
                      </optionalFileGroups>
                    </installStep>
                    <installStep name="Rigs">
                      <visible>
                        <dependencies operator="And">
                          <flagDependency flag="Ext" value="On" />
                        </dependencies>
                      </visible>
                      <optionalFileGroups order="Explicit">
                        <group name="Rigs" type="SelectAny">
                          <plugins order="Explicit">
                            <plugin name="Rig">
                              <files><folder source="RigFiles" destination="" /></files>
                              <typeDescriptor><type name="Required" /></typeDescriptor>
                            </plugin>
                          </plugins>
                        </group>
                      </optionalFileGroups>
                    </installStep>
                  </installSteps>
                </config>
            """.trimIndent(),
            files = listOf(
                "Skeleton/skeleton.nif",
                "Other/other.nif",
                "RigFiles/rig.nif"
            )
        )

        val plan = fixture.parse()
        val extended = plan.groups[0].options.single { it.name == "Extended" }
        val basic = plan.groups[0].options.single { it.name == "Basic" }
        val rig = plan.groups[1].options.single()

        val initial = plan.initialSelection()
        assertTrue(extended.id in initial)
        assertTrue(rig.id in initial)
        assertEquals(2, plan.resolvedGroups(initial).size)

        val basicOnly = plan.resolvedGroups(setOf(basic.id))
        assertEquals(listOf("Installation"), basicOnly.map { it.name })

        val files = plan.resolveInstallFiles(setOf(basic.id))
        assertTrue(files.any { it.source == "Other" })
        assertFalse(files.any { it.source == "RigFiles" })
    }

    @Test
    fun `required conditional always and install-if-usable files follow installer order`() {
        val fixture = fixture(
            xml = """
                <config>
                  <moduleName>Ordering</moduleName>
                  <requiredInstallFiles>
                    <file source="required.txt" destination="required.txt" />
                  </requiredInstallFiles>
                  <installSteps order="Explicit">
                    <installStep name="Main">
                      <optionalFileGroups order="Explicit">
                        <group name="Main" type="SelectExactlyOne">
                          <plugins order="Explicit">
                            <plugin name="Core">
                              <conditionFlags><flag name="Enabled">Yes</flag></conditionFlags>
                              <files><file source="main.txt" destination="main.txt" priority="5" /></files>
                              <typeDescriptor><type name="Optional" /></typeDescriptor>
                            </plugin>
                          </plugins>
                        </group>
                        <group name="Extras" type="SelectAny">
                          <plugins order="Explicit">
                            <plugin name="Always">
                              <files><file source="always.txt" destination="always.txt" alwaysInstall="true" /></files>
                              <typeDescriptor><type name="Optional" /></typeDescriptor>
                            </plugin>
                            <plugin name="IfUsable">
                              <files><file source="ifusable.txt" destination="ifusable.txt" installIfUsable="true" /></files>
                              <typeDescriptor><type name="Optional" /></typeDescriptor>
                            </plugin>
                          </plugins>
                        </group>
                      </optionalFileGroups>
                    </installStep>
                  </installSteps>
                  <conditionalFileInstalls>
                    <patterns>
                      <pattern>
                        <dependencies operator="And">
                          <flagDependency flag="Enabled" value="Yes" />
                        </dependencies>
                        <files>
                          <file source="patch.txt" destination="patch.txt" />
                        </files>
                      </pattern>
                    </patterns>
                  </conditionalFileInstalls>
                </config>
            """.trimIndent(),
            files = listOf(
                "required.txt",
                "main.txt",
                "always.txt",
                "ifusable.txt",
                "patch.txt"
            )
        )

        val plan = fixture.parse()
        assertEquals(1, plan.requiredFiles.size)
        assertEquals(1, plan.conditionalFileInstalls.size)

        val files = plan.resolveInstallFiles(plan.initialSelection())
        val destinations = files.map { it.destination }

        assertEquals("required.txt", destinations.first())
        assertEquals("patch.txt", destinations.last())
        assertTrue("main.txt" in destinations)
        assertTrue("always.txt" in destinations)
        assertTrue("ifusable.txt" in destinations)
    }

    @Test
    fun `impossible group constraints are relaxed like vortex and mo2`() {
        val fixture = fixture(
            xml = """
                <config>
                  <moduleName>Groups</moduleName>
                  <installSteps order="Explicit">
                    <installStep name="Main">
                      <optionalFileGroups order="Explicit">
                        <group name="AtMost" type="SelectAtMostOne">
                          <plugins order="Explicit">
                            <plugin name="One">
                              <files><file source="one.txt" destination="one.txt" /></files>
                              <typeDescriptor><type name="Required" /></typeDescriptor>
                            </plugin>
                            <plugin name="Two">
                              <files><file source="two.txt" destination="two.txt" /></files>
                              <typeDescriptor><type name="Required" /></typeDescriptor>
                            </plugin>
                          </plugins>
                        </group>
                        <group name="Exactly" type="SelectExactlyOne">
                          <plugins order="Explicit">
                            <plugin name="Three">
                              <files><file source="three.txt" destination="three.txt" /></files>
                              <typeDescriptor><type name="Required" /></typeDescriptor>
                            </plugin>
                            <plugin name="Four">
                              <files><file source="four.txt" destination="four.txt" /></files>
                              <typeDescriptor><type name="Required" /></typeDescriptor>
                            </plugin>
                          </plugins>
                        </group>
                      </optionalFileGroups>
                    </installStep>
                  </installSteps>
                </config>
            """.trimIndent(),
            files = listOf("one.txt", "two.txt", "three.txt", "four.txt")
        )

        val plan = fixture.parse()
        assertEquals(InstallerGroupType.SELECT_ANY, plan.groups[0].type)
        assertEquals(InstallerGroupType.SELECT_AT_LEAST_ONE, plan.groups[1].type)
        assertEquals(4, plan.initialSelection().size)
    }

    @Test
    fun `omitted destination defaults to the source path`() {
        val fixture = fixture(
            xml = """
                <config>
                  <moduleName>Destinations</moduleName>
                  <installSteps order="Explicit">
                    <installStep name="Main">
                      <optionalFileGroups order="Explicit">
                        <group name="Main" type="SelectAny">
                          <plugins order="Explicit">
                            <plugin name="Core">
                              <files>
                                <file source="sub/a.txt" />
                                <folder source="Folder" />
                              </files>
                              <typeDescriptor><type name="Required" /></typeDescriptor>
                            </plugin>
                          </plugins>
                        </group>
                      </optionalFileGroups>
                    </installStep>
                  </installSteps>
                </config>
            """.trimIndent(),
            files = listOf("sub/a.txt", "Folder/b.txt")
        )

        val plan = fixture.parse()
        val files = plan.groups.single().options.single().files
        assertEquals("sub/a.txt", files.single { !it.isDirectory }.destination)
        assertEquals("Folder", files.single { it.isDirectory }.destination)
    }

    @Test
    fun `finalize copies folder required and conditional mappings`() {
        val fixture = fixture(
            xml = """
                <config>
                  <moduleName>Finalize</moduleName>
                  <requiredInstallFiles>
                    <file source="required.txt" destination="required.txt" />
                  </requiredInstallFiles>
                  <installSteps order="Explicit">
                    <installStep name="Main">
                      <optionalFileGroups order="Explicit">
                        <group name="Main" type="SelectExactlyOne">
                          <plugins order="Explicit">
                            <plugin name="Core">
                              <conditionFlags><flag name="Enabled">Yes</flag></conditionFlags>
                              <files>
                                <folder source="Payload" destination="" />
                              </files>
                              <typeDescriptor><type name="Optional" /></typeDescriptor>
                            </plugin>
                          </plugins>
                        </group>
                      </optionalFileGroups>
                    </installStep>
                  </installSteps>
                  <conditionalFileInstalls>
                    <patterns>
                      <pattern>
                        <dependencies operator="And">
                          <flagDependency flag="Enabled" value="Yes" />
                        </dependencies>
                        <files>
                          <file source="patch.txt" destination="patch.txt" />
                        </files>
                      </pattern>
                    </patterns>
                  </conditionalFileInstalls>
                </config>
            """.trimIndent(),
            files = listOf(
                "Payload/meshes/body.nif",
                "required.txt",
                "patch.txt"
            )
        )

        val plan = fixture.parse()
        val session = File(fixture.root, "session").apply { mkdirs() }
        val mods = File(fixture.root, "mods").apply { mkdirs() }
        val temp = File(fixture.root, "temp").apply { mkdirs() }

        val prepared = PreparedArchiveInstall(
            archivePath = File(fixture.root, "Example.zip").absolutePath,
            archiveName = "Example.zip",
            modName = "Finalize",
            sessionRootPath = session.absolutePath,
            extractedRootPath = fixture.root.absolutePath,
            installRootPath = fixture.root.absolutePath,
            plan = plan
        )

        val installer = PreparedArchiveInstaller(
            tempDir = temp,
            modsDir = mods,
            directoryReplacer = InstalledModDirectoryReplacer(),
            operationId = { "test" },
            debugLog = {}
        )

        val result = installer.finalizeInstall(
            prepared = prepared,
            selection = InstallerSelection(plan.initialSelection())
        )

        assertTrue(File(result, "required.txt").isFile)
        assertTrue(File(result, "patch.txt").isFile)
        assertTrue(File(result, "meshes/body.nif").isFile)
        assertFalse(File(result, "Payload/meshes/body.nif").exists())
    }

    @Test
    fun `conditional type descriptors disable and re-enable options`() {
        val fixture = fixture(
            xml = """
                <config>
                  <moduleName>Conditional Types</moduleName>
                  <installSteps order="Explicit">
                    <installStep name="Main">
                      <optionalFileGroups order="Explicit">
                        <group name="Mode" type="SelectExactlyOne">
                          <plugins order="Explicit">
                            <plugin name="New">
                              <conditionFlags><flag name="Mode">New</flag></conditionFlags>
                              <files><file source="new.txt" destination="new.txt" /></files>
                              <typeDescriptor><type name="Optional" /></typeDescriptor>
                            </plugin>
                            <plugin name="Old">
                              <conditionFlags><flag name="Mode">Old</flag></conditionFlags>
                              <files><file source="old.txt" destination="old.txt" /></files>
                              <typeDescriptor><type name="Optional" /></typeDescriptor>
                            </plugin>
                          </plugins>
                        </group>
                        <group name="Patch" type="SelectAny">
                          <plugins order="Explicit">
                            <plugin name="Legacy Patch">
                              <files><file source="patch.txt" destination="patch.txt" /></files>
                              <typeDescriptor>
                                <dependencyType>
                                  <defaultType><type name="NotUsable" /></defaultType>
                                  <patterns>
                                    <pattern>
                                      <dependencies operator="And">
                                        <flagDependency flag="Mode" value="Old" />
                                      </dependencies>
                                      <type name="Optional" />
                                    </pattern>
                                  </patterns>
                                </dependencyType>
                              </typeDescriptor>
                            </plugin>
                          </plugins>
                        </group>
                      </optionalFileGroups>
                    </installStep>
                  </installSteps>
                </config>
            """.trimIndent(),
            files = listOf("new.txt", "old.txt", "patch.txt")
        )

        val plan = fixture.parse()
        val new = plan.groups[0].options.single { it.name == "New" }
        val old = plan.groups[0].options.single { it.name == "Old" }
        val patch = plan.groups[1].options.single()

        val withNew = plan.initialSelection()
        assertEquals(setOf(new.id), withNew)
        assertEquals(
            InstallerOptionType.NOT_USABLE,
            plan.resolvedGroups(withNew)[1].options.single().type
        )
        // Even if an unusable option lingers in the selection set it must not install.
        assertFalse(
            plan.resolveInstallFiles(withNew + patch.id)
                .any { it.destination == "patch.txt" }
        )
        // Dropping an unusable selection is a no-op for the rest of the plan.
        assertEquals(
            withNew,
            plan.normalizeSelection(withNew + patch.id)
        )

        val oldSelected = plan.resolvedGroups(setOf(old.id))
        assertEquals(
            InstallerOptionType.OPTIONAL,
            oldSelected[1].options.single().type
        )
        assertTrue(
            plan.resolveInstallFiles(setOf(old.id, patch.id))
                .any { it.destination == "patch.txt" }
        )
    }

    private data class Fixture(
        val root: File,
        val config: File
    ) {
        fun parse(): InstallerPlan = FomodInstallerParser().parse(root, config, "Example")
    }

    private fun fixture(xml: String, files: List<String>): Fixture {
        val root = temporary.newFolder()
        val fomod = File(root, "fomod").apply { mkdirs() }
        val config = File(fomod, "ModuleConfig.xml")
        config.writeBytes(xml.toByteArray(Charsets.UTF_16))
        files.forEach { path ->
            File(root, path).apply {
                parentFile?.mkdirs()
                writeText(path)
            }
        }
        return Fixture(root, config)
    }
}
