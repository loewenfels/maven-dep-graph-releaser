package ch.loewenfels.depgraph.runner

import ch.loewenfels.depgraph.maven.getTestDirectory
import ch.loewenfels.depgraph.runner.Main.errorHandler
import ch.loewenfels.depgraph.runner.Main.fileVerifier
import ch.loewenfels.depgraph.runner.console.ErrorHandler
import ch.loewenfels.depgraph.runner.console.FileVerifier
import ch.loewenfels.depgraph.serialization.Serializer
import ch.tutteli.atrium.*
import ch.tutteli.atrium.api.cc.en_UK.*
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
    }

    describe("update") {
        given("single project with third party dependency") {
            val pom = File(getTestDirectory("singleProject"), "pom.xml")

            context("dependency shall be updated, same version") {
                on("calling main") {
                    val tmpPom = copyPom(tempFolder, pom)
                    val errMessage = "Version is already up-to-date; did you pass wrong argument for newVersion"
                    it("throws an IllegalArgumentException, mentioning `$errMessage`") {
                        expect {
                            Main.main("update", tmpPom.absolutePath, "junit", "junit", "4.12")
                        }.toThrow<IllegalArgumentException> { message { contains(errMessage, "4.12") } }
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
})

private fun callJson(tempFolder: TempFolder): File {
    val jsonFile = File(tempFolder.tmpDir, "test.json")
    Main.main(
        "json", "com.example", "a",
        getTestDirectory("managingVersions/inDependency").absolutePath,
        jsonFile.absolutePath,
        "dgr-updater",
        ".*",
        "dgr-remote-releaser",
        "dgr-dry-run"
    )
    return jsonFile
}
