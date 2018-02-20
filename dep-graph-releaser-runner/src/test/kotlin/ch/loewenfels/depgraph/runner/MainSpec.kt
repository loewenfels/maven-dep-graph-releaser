package ch.loewenfels.depgraph.runner

import ch.tutteli.atrium.assertProjectAWithDependentB
import ch.loewenfels.depgraph.maven.getTestDirectory
import ch.loewenfels.depgraph.serialization.Serializer
import ch.tutteli.atrium.api.cc.en_UK.isTrue
import ch.tutteli.atrium.api.cc.en_UK.returnValueOf
import ch.tutteli.atrium.assert
import com.google.common.io.Files
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.io.File

object MainSpec : Spek({
    errorHandler = object: ErrorHandler{
        override fun error(msg: String) {
            throw AssertionError(msg)
        }
    }

    var tmp = File("we need to initialise tmp")
    beforeEachTest {
        tmp = Files.createTempDir()
    }
    afterEachTest {
        tmp.delete()
    }

    describe("happy case, project A with dependent project B") {
        on("calling main") {
            val jsonFile = File(tmp.absolutePath, "test.json")
            main(
                "json", "com.example", "a",
                getTestDirectory("projectWithDependency").absolutePath,
                jsonFile.absolutePath
            )
            it("creates a corresponding json file") {
                assert(jsonFile).returnValueOf(jsonFile::exists).isTrue()
            }

            test("the json file can be de-serialized and is expected project A with dependent B") {
                val json = Files.readFirstLine(jsonFile, Charsets.UTF_8)
                val rootProject = Serializer().deserialize(json)
                assertProjectAWithDependentB(rootProject)
            }
        }
    }
})
