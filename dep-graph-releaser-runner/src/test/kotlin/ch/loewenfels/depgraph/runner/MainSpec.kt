package ch.loewenfels.depgraph.runner

import ch.loewenfels.depgraph.maven.getTestDirectory
import ch.loewenfels.depgraph.runner.Main.errorHandler
import ch.loewenfels.depgraph.runner.Main.fileVerifier
import ch.loewenfels.depgraph.runner.commands.Json.MAVEN_PARENT_ANALYSIS_OFF
import ch.loewenfels.depgraph.runner.console.ErrorHandler
import ch.loewenfels.depgraph.runner.console.FileVerifier
import ch.loewenfels.depgraph.serialization.Serializer
import ch.tutteli.atrium.*
import ch.tutteli.atrium.api.cc.en_UK.contains
import ch.tutteli.atrium.api.cc.en_UK.containsRegex
import ch.tutteli.atrium.api.cc.en_UK.isTrue
import ch.tutteli.atrium.api.cc.en_UK.returnValueOf
import ch.tutteli.spek.extensions.TempFolder
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.*
import java.io.File
import java.util.*


object MainSpec : Spek({
    val tempFolder = TempFolder.perAction()
    registerListener(tempFolder)
    errorHandler = object : ErrorHandler {
        override fun error(msg: String) = throw AssertionError(msg)
    }
    fileVerifier = object : FileVerifier {
        override fun file(path: String, fileDescription: String) = File(path)
    }

    describe("json") {
        given("project A with dependent project B (happy case)") {
            on("calling main") {
                val jsonFile = callJson(tempFolder)
                it("creates a corresponding json file") {
                    assert(jsonFile).returnValueOf(jsonFile::exists).isTrue()
                }

                test("the json file can be de-serialized and is expected project A with dependent B") {
                    val json = Scanner(jsonFile, Charsets.UTF_8.name()).useDelimiter("\\Z").use { it.next() }
                    val releasePlan = Serializer().deserialize(json)
                    assertProjectAWithDependentB(releasePlan)
                    assertReleasePlanHasNoWarningsAndNoInfos(releasePlan)
                }
            }
        }

        given("parent not in analysis, does not matter with $MAVEN_PARENT_ANALYSIS_OFF") {
            on("calling main") {
                val jsonFile = File(tempFolder.tmpDir, "test.json")
                Main.main(
                    "json", "com.example", "b",
                    getTestDirectory("errorCases/parentNotInAnalysis").absolutePath,
                    jsonFile.absolutePath,
                    MAVEN_PARENT_ANALYSIS_OFF
                )
                it("creates a corresponding json file") {
                    assert(jsonFile).returnValueOf(jsonFile::exists).isTrue()
                }

                test("the json file can be de-serialized and is expected project A with dependent B") {
                    val json = Scanner(jsonFile, Charsets.UTF_8.name()).useDelimiter("\\Z").use { it.next() }
                    val releasePlan = Serializer().deserialize(json)
                    assertSingleProject(releasePlan, exampleB)
                }
            }
        }
    }

    describe("update") {
        given("single project with third party dependency") {
            val pom = File(getTestDirectory("singleProject"), "pom.xml")

            context("dependency shall be updated, same version") {
                on("calling main") {
                    val tmpPom = copyPom(tempFolder, pom)

                    testSameContent(tempFolder, pom) {
                        Main.main("update", tmpPom.absolutePath, "junit", "junit", "4.12")
                    }
                }
            }

            context("dependency shall be updated, new version") {
                on("calling main") {
                    val tmpPom = copyPom(tempFolder, pom)

                    it("updates the dependency") {
                        Main.main("update", tmpPom.absolutePath, "junit", "junit", "4.4")
                        assertSameAsBeforeAfterReplace(tmpPom, pom, "4.12", "4.4")
                    }
                }
            }
        }
    }

    describe("pipeline") {
        given("project A with dependent project B, remoteRegex=.* (happy case)") {
            on("calling main") {
                val jsonFile = callJson(tempFolder)
                assert(jsonFile).returnValueOf(jsonFile::exists).isTrue()

                val jenkinsfile = File(tempFolder.tmpDir, "jenkinsfile")
                Main.main(
                    "pipeline",
                    jsonFile.absolutePath,
                    "dep-graph-releaser-updater",
                    "^.*",
                    "dep-graph-releaser-remote",
                    ".*#branch=master",
                    jenkinsfile.absolutePath
                )
                it("creates a corresponding jenkinsfile") {
                    assert(jenkinsfile).returnValueOf(jenkinsfile::exists).isTrue()
                }

                it("contains remote release command for both a and b") {
                    val content = jenkinsfile.readText()
                    assert(content).containsRegex(
                        "build job: 'dep-graph-releaser-remote'[\\S\\s]*?name: 'jobName', value: '${exampleA.id.artifactId}'",
                        "build job: 'dep-graph-releaser-remote'[\\S\\s]*?name: 'jobName', value: '${exampleB.id.artifactId}'"
                    )
                }

            }
        }
    }
})

private fun callJson(tempFolder: TempFolder): File {
    val jsonFile = File(tempFolder.tmpDir, "test.json")
    Main.main(
        "json", "com.example", "a",
        getTestDirectory("managingVersions/inDependency").absolutePath,
        jsonFile.absolutePath
    )
    return jsonFile
}
