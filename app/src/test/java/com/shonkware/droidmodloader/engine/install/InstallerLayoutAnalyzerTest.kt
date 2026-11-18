package com.shonkware.droidmodloader.engine.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class InstallerLayoutAnalyzerTest {

    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun `malformed module config falls back to a simple install`() {
        val root = temporary.newFolder()
        File(root, "fomod").mkdirs()
        File(root, "fomod/ModuleConfig.xml")
            .writeText("<config><installSteps>")
        File(root, "meshes").mkdirs()
        File(root, "meshes/body.nif").writeText("mesh")

        val plan = InstallerLayoutAnalyzer().analyze(root, "Broken")

        assertEquals(InstallerType.SIMPLE, plan.installerType)
        assertTrue(
            plan.warnings.any {
                it.contains("could not be read") &&
                    it.contains("fomod folder is not deployed")
            }
        )
        assertTrue(
            plan.resolveInstallFiles(plan.initialSelection())
                .any { it.source == "." }
        )
    }

    @Test
    fun `fomod folder without a module config warns about the raw install`() {
        val root = temporary.newFolder()
        File(root, "fomod").mkdirs()
        File(root, "fomod/info.xml").writeText("<fomod/>")
        File(root, "meshes").mkdirs()
        File(root, "meshes/body.nif").writeText("mesh")

        val plan = InstallerLayoutAnalyzer().analyze(root, "NoConfig")

        assertEquals(InstallerType.SIMPLE, plan.installerType)
        assertTrue(
            plan.warnings.any {
                it.contains("no usable ModuleConfig.xml")
            }
        )
    }

    @Test
    fun `valid module config still produces a fomod plan`() {
        val root = temporary.newFolder()
        File(root, "fomod").mkdirs()
        File(root, "fomod/ModuleConfig.xml").writeText(
            """
                <config>
                  <moduleName>Working</moduleName>
                  <installSteps>
                    <installStep name="Main">
                      <optionalFileGroups>
                        <group name="Main" type="SelectExactlyOne">
                          <plugins>
                            <plugin name="Core">
                              <files><folder source="meshes" destination="" /></files>
                              <typeDescriptor><type name="Required" /></typeDescriptor>
                            </plugin>
                          </plugins>
                        </group>
                      </optionalFileGroups>
                    </installStep>
                  </installSteps>
                </config>
            """.trimIndent()
        )
        File(root, "meshes").mkdirs()
        File(root, "meshes/body.nif").writeText("mesh")

        val plan = InstallerLayoutAnalyzer().analyze(root, "Working")

        assertEquals(InstallerType.FOMOD, plan.installerType)
        assertEquals("Working", plan.displayName)
        assertTrue(plan.warnings.isEmpty())
    }
}