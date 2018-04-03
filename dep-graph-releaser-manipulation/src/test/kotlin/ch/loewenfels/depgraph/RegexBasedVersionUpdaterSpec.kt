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
                    val tmpPom = tempFolder.newFile("pom.xml")
                    tmpPom.writeBytes(pom.readBytes())
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
                val tmpPom = tempFolder.newFile("pom.xml")
                tmpPom.writeBytes(pom.readBytes())
                testee.updateDependency(tmpPom, "junit", "junit", "4.4")
                assert(tmpPom.readText()).containsRegex(
                    "<dependency>[\\S\\s]*" +
                        "<groupId>junit</groupId>[\\S\\s]*" +
                        "<artifactId>junit</artifactId>[\\S\\s]*" +
                        "<version>4.4</version>[\\S\\s]*" +
                        "</dependency>"
                )
            }
        }

        context("dependency occurs multiple times") {
            it("updates the dependency") {
                val tmpPom = tempFolder.newFile("pom.xml")
                tmpPom.writeBytes(pom.readBytes())
                testee.updateDependency(tmpPom, "test", "test", "2.0")
                assert(tmpPom.readText()).containsRegex(
                    "<dependency>[\\S\\s]*" +
                        "<groupId>test</groupId>[\\S\\s]*" +
                        "<artifactId>test</artifactId>[\\S\\s]*" +
                        "<version>2.0</version>[\\S\\s]*" +
                        "</dependency>",
                    "<dependency>[\\S\\s]*" +
                        "<groupId>test</groupId>[\\S\\s]*" +
                        "<version>2.0</version>[\\S\\s]*" +
                        "<artifactId>test</artifactId>[\\S\\s]*" +
                        "</dependency>",
                    "<dependency>[\\S\\s]*" +
                        "<artifactId>test</artifactId>[\\S\\s]*" +
                        "<groupId>test</groupId>[\\S\\s]*" +
                        "<version>2.0</version>[\\S\\s]*" +
                        "</dependency>",
                    "<dependency>[\\S\\s]*" +
                        "<artifactId>test</artifactId>[\\S\\s]*" +
                        "<version>2.0</version>[\\S\\s]*" +
                        "<groupId>test</groupId>[\\S\\s]*" +
                        "</dependency>"
                )
            }
        }

        context("dependency once without version") {
            it("updates the dependency with version") {
                val tmpPom = tempFolder.newFile("pom.xml")
                tmpPom.writeBytes(pom.readBytes())
                testee.updateDependency(tmpPom, "test", "onceWithoutVersion", "3.4")
                assert(tmpPom.readText()).containsRegex(
                    "<dependency>[\\S\\s]*" +
                        "<groupId>test</groupId>[\\S\\s]*" +
                        "<artifactId>onceWithoutVersion</artifactId>[\\S\\s]*" +
                        "<version>3.4</version>[\\S\\s]*" +
                        "</dependency>",
                    "<dependency>[\\S\\s]*" +
                        "<groupId>test</groupId>[\\S\\s]*" +
                        "<artifactId>onceWithoutVersion</artifactId>[\\S\\s]*" +
                        "</dependency>"
                )
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
                val tmpPom = tempFolder.newFile("pom.xml")
                tmpPom.writeBytes(pom.readBytes())

                updateDependency(testee, tmpPom, exampleA)
                assert(tmpPom.readText()).containsRegex(
                    "<dependency>[\\S\\s]*" +
                        "<groupId>${exampleA.id.groupId}</groupId>[\\S\\s]*" +
                        "<artifactId>${exampleA.id.artifactId}</artifactId>[\\S\\s]*" +
                        "<version>${exampleA.releaseVersion}</version>[\\S\\s]*" +
                        "</dependency>"
                )
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
                val tmpPom = tempFolder.newFile("pom.xml")
                tmpPom.writeBytes(pom.readBytes())
                updateDependency(testee, tmpPom, exampleA)
                assert(tmpPom.readText()).containsRegex(
                    "<parent>[\\S\\s]*" +
                        "<groupId>${exampleA.id.groupId}</groupId>[\\S\\s]*" +
                        "<artifactId>${exampleA.id.artifactId}</artifactId>[\\S\\s]*" +
                        "<version>${exampleA.releaseVersion}</version>[\\S\\s]*" +
                        "</parent>"
                )
            }
        }
    }
})

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
        val tmpPom = tempFolder.newFile("pom.xml")
        tmpPom.writeBytes(pom.readBytes())
        update(tmpPom)
        assert(tmpPom.readText()).toBe(pom.readText())
    }
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

