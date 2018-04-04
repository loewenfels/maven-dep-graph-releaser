package ch.loewenfels.depgraph

import ch.loewenfels.depgraph.maven.getTestDirectory
import ch.tutteli.atrium.IdAndVersions
import ch.tutteli.atrium.api.cc.en_UK.containsRegex
import ch.tutteli.atrium.api.cc.en_UK.toBe
import ch.tutteli.atrium.api.cc.en_UK.toThrow
import ch.tutteli.atrium.assert
import ch.tutteli.atrium.exampleA
import ch.tutteli.atrium.expect
import ch.tutteli.spek.extensions.TempFolder
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.*
import java.io.File

object RegexBasedVersionUpdaterSpec : Spek({
    val tempFolder = TempFolder.perTest()
    registerListener(tempFolder)

    val testee = RegexBasedVersionUpdater()

    describe("error cases") {
        given("single project with third party dependency") {
            val pom = File(getTestDirectory("singleProject"), "pom.xml")
            context("dependency without version shall be updated") {
                it("throws an IllegalStateException, mentioning that at least one dependency should be updated") {
                    val tmpPom = copyPom(tempFolder, pom)
                    expect {
                        testee.updateDependency(tmpPom, "com.google.code.gson", "gson", "4.4")
                    }.toThrow<IllegalStateException>()
                }
            }
        }
    }


    given("single project with third party dependency") {
        val pom = File(getTestDirectory("singleProject"), "pom.xml")

        context("dependency shall be updated, same version") {
            testSameContent(testee, tempFolder, pom, "junit", "junit", "4.12")
        }

        context("dependency shall be updated, new version") {
            it("updates the dependency") {
                val tmpPom = copyPom(tempFolder, pom)
                testee.updateDependency(tmpPom, "junit", "junit", "4.4")
                assertSameAsBeforeAfterReplace(tmpPom, pom, "4.12", "4.4")
            }
        }

        context("dependency occurs multiple times") {
            it("updates the dependency") {
                val tmpPom = copyPom(tempFolder, pom)
                testee.updateDependency(tmpPom, "test", "test", "3.0")
                assertSameAsBeforeAfterReplace(tmpPom, pom, "2.0", "3.0")
            }
        }

        context("dependency once without version") {
            it("updates the dependency with version") {
                val tmpPom = copyPom(tempFolder, pom)
                testee.updateDependency(tmpPom, "test", "onceWithoutVersion", "3.4")
                assertSameAsBeforeAfterReplace(tmpPom, pom, "3.0", "3.4")
            }
        }
    }


    given("project with dependency and version in dependency management") {
        val pom = File(getTestDirectory("managingVersions/viaDependencyManagement"), "b.pom")

        context("dependency shall be updated, same version") {
            testSameContent(testee, tempFolder, pom, exampleA)
        }

        context("dependency shall be updated, new version") {
            it("updates the dependency") {
                val tmpPom = copyPom(tempFolder, pom)
                updateDependency(testee, tmpPom, exampleA)
                assertSameAsBeforeAfterReplace(tmpPom, pom, "1.0.0", "1.1.1")
            }
        }
    }

    given("project with parent dependency") {
        val pom = File(getTestDirectory("parentRelations/parent"), "b.pom")

        context("parent dependency shall be updated, same version") {
            testSameContent(testee, tempFolder, pom, exampleA)
        }

        context("parent dependency shall be updated, new version") {
            it("updates the dependency") {
                val tmpPom = copyPom(tempFolder, pom)
                updateDependency(testee, tmpPom, exampleA)
                assertSameAsBeforeAfterReplace(tmpPom, pom, "1.0.0", "1.1.1")
            }
        }
    }

    given("project with dependent and version in property") {
        val pom = File(getTestDirectory("managingVersions/viaProperty"), "b.pom")

        context("dependency shall be updated, same version") {
            testSameContent(testee, tempFolder, pom, exampleA)
        }

        context("dependency shall be updated, new version") {
            it("updates the property") {
                val tmpPom = copyPom(tempFolder, pom)
                updateDependency(testee, tmpPom, exampleA)
                assertSameAsBeforeAfterReplace(tmpPom, pom, "1.0.0", "1.1.1")
            }
        }
    }
})

fun assertSameAsBeforeAfterReplace(tmpPom: File, pom: File, versionToReplace: String, newVersion: String) {
    val content = pom.readText()
    assert(tmpPom.readText()).toBe(content.replace(versionToReplace, newVersion))
}

private fun SpecBody.testSameContent(
    testee: RegexBasedVersionUpdater,
    tempFolder: TempFolder,
    pom: File,
    groupId: String,
    artifactId: String,
    newVersion: String
) {
    testSameContent(tempFolder, pom) { tmpPom ->
        testee.updateDependency(tmpPom, groupId, artifactId, newVersion)
    }
}

private fun SpecBody.testSameContent(
    testee: RegexBasedVersionUpdater,
    tempFolder: TempFolder,
    pom: File,
    idAndVersions: IdAndVersions
) {
    testSameContent(tempFolder, pom) { tmpPom ->
        updateDependency(testee, tmpPom, idAndVersions, "1.0.0")
    }
}

private fun SpecBody.testSameContent(
    tempFolder: TempFolder,
    pom: File,
    update: (File) -> Unit
) {
    it("updates the dependency and file content is the same as before") {
        val tmpPom = copyPom(tempFolder, pom)
        update(tmpPom)
        assert(tmpPom.readText()).toBe(pom.readText())
    }
}

private fun copyPom(tempFolder: TempFolder, pom: File): File {
    val tmpPom = tempFolder.newFile("pom.xml")
    tmpPom.writeBytes(pom.readBytes())
    return tmpPom
}

private fun updateDependency(testee: RegexBasedVersionUpdater, tmpPom: File, project: IdAndVersions) {
    updateDependency(testee, tmpPom, project, project.releaseVersion)
}

private fun updateDependency(
    testee: RegexBasedVersionUpdater,
    tmpPom: File,
    project: IdAndVersions,
    oldVersion: String
) {
    testee.updateDependency(tmpPom, project.id.groupId, project.id.artifactId, oldVersion)
}

