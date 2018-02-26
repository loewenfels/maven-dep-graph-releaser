package ch.loewenfels.depgraph.runner

import ch.loewenfels.depgraph.maven.getTestDirectory
import ch.loewenfels.depgraph.serialization.Serializer
import ch.tutteli.atrium.api.cc.en_UK.isTrue
import ch.tutteli.atrium.api.cc.en_UK.returnValueOf
import ch.tutteli.atrium.assert
import ch.tutteli.atrium.assertProjectAWithDependentB
import ch.tutteli.spek.extensions.TempFolder
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.io.File
import java.util.*


object MainSpec : Spek({
    val tempFolder = TempFolder.perAction()
    registerListener(tempFolder)
    errorHandler = object : ErrorHandler {
        override fun error(msg: String) {
            throw AssertionError(msg)
        }
    }

    describe("happy case, project A with dependent project B") {
        on("calling main") {
            val jsonFile = File(tempFolder.tmpDir, "test.json")
            main(
                "json", "com.example", "a",
                getTestDirectory("projectWithDependency").absolutePath,
                jsonFile.absolutePath
            )
            it("creates a corresponding json file") {
                assert(jsonFile).returnValueOf(jsonFile::exists).isTrue()
            }

            test("the json file can be de-serialized and is expected project A with dependent B") {
                val json = Scanner(jsonFile, Charsets.UTF_8.name()).useDelimiter("\\Z").use { it.next() }
                val rootProject = Serializer().deserialize(json)
                assertProjectAWithDependentB(rootProject)
            }
        }
    }
})
