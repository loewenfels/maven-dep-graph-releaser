package ch.loewenfels.depgraph

import ch.loewenfels.depgraph.maven.getTestDirectory
import ch.tutteli.atrium.*
import ch.tutteli.atrium.api.cc.en_UK.contains
import ch.tutteli.atrium.api.cc.en_UK.message
import ch.tutteli.atrium.api.cc.en_UK.toThrow
import ch.tutteli.spek.extensions.TempFolder
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.*
import java.io.File

object RegexBasedVersionUpdaterSpec : Spek({
    val tempFolder = TempFolder.perTest()
    registerListener(tempFolder)

    val testee = RegexBasedVersionUpdater

    describe("error cases") {
        given("single project with third party dependency without version") {
            val errMessage = "not managed by the given pom"
            it("throws an IllegalStateException, mentioning `$errMessage`") {
                val pom = File(getTestDirectory("singleProject"), "pom.xml")
                val tmpPom = copyPom(tempFolder, pom)
                expect {
                    testee.updateDependency(tmpPom, "com.google.code.gson", "gson", "4.4")
                }.toThrow<IllegalStateException> { message { contains(errMessage) } }
            }
        }

        given("project with dependent and version partially static with property") {
            val errMessage = "Version was neither static nor a reference to a single property"
            it("throws an UnsupportedOperationException, mentioning `$errMessage`") {
                val pom = getPom("errorCases/versionPartiallyStaticAndProperty.pom")
                val tmpPom = copyPom(tempFolder, pom)
                expect {
                    updateDependency(testee, tmpPom, exampleA)
                }.toThrow<UnsupportedOperationException> { message { contains(errMessage, "1.0.\${a.fix}") } }
            }
        }

        given("dependency with two <version>") {
            val errMessage = "<dependency> has two <version>"
            it("throws an IllegalStateException, mentioning `$errMessage`") {
                val pom = getPom("errorCases/twoVersions.pom")
                val tmpPom = copyPom(tempFolder, pom)
                expect {
                    updateDependency(testee, tmpPom, exampleA)
                }.toThrow<IllegalStateException> { message { contains(errMessage, exampleA.id.identifier) } }
            }
        }

        given("property which contains another property") {
            val errMessage = "Property contains another property"
            it("throws an UnsupportedOperationException, mentioning `$errMessage`") {
                val pom = getPom("errorCases/propertyWithProperty.pom")
                val tmpPom = copyPom(tempFolder, pom)
                expect {
                    updateDependency(testee, tmpPom, exampleA)
                }.toThrow<UnsupportedOperationException> {
                    message { contains(errMessage, "a.version", "\${aVersion}") }
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
        testWithExampleA(testee, tempFolder, pom)
    }

    given("project with parent dependency") {
        val pom = File(getTestDirectory("parentRelations/parent"), "b.pom")
        testWithExampleA(testee, tempFolder, pom)
    }

    given("project with dependent and version in property") {
        val pom = File(getTestDirectory("managingVersions/viaProperty"), "b.pom")
        testWithExampleA(testee, tempFolder, pom)
    }

    given("project with dependent and empty <properties>") {
        val pom = getPom("emptyProperties.pom")
        testWithExampleA(testee, tempFolder, pom)
    }

    given("project with dependent and version in property which is also in profiles") {
        val pom = getPom("propertiesInProfile.pom")
        testWithExampleA(testee, tempFolder, pom)
    }
})

private fun SpecBody.testWithExampleA(
    testee: RegexBasedVersionUpdater,
    tempFolder: TempFolder,
    pom: File
) {
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

private fun getPom(pomName: String): File = File(RegexBasedVersionUpdaterSpec.javaClass.getResource("/$pomName").path)

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

private fun TestContainer.testSameContent(
    testee: RegexBasedVersionUpdater,
    tempFolder: TempFolder,
    pom: File,
    idAndVersions: IdAndVersions
) {
    testSameContent(tempFolder, pom) { tmpPom ->
        updateDependency(testee, tmpPom, idAndVersions, "1.0.0")
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

