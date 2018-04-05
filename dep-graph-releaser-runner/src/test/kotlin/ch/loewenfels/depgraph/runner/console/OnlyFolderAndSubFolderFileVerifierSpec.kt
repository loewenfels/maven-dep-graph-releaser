package ch.loewenfels.depgraph.runner.console

import ch.tutteli.atrium.api.cc.en_UK.contains
import ch.tutteli.atrium.api.cc.en_UK.message
import ch.tutteli.atrium.api.cc.en_UK.toThrow
import ch.tutteli.atrium.expect
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it

object OnlyFolderAndSubFolderFileVerifierSpec : Spek({

    describe("error cases") {
        action("given path which contains ..") {
            val fileDescription = "testFile"
            val errMsg = "Using `..` in the path of the $fileDescription is prohibited"
            it("throws an IllegalArgumentException, mentioning $errMsg") {
                expect {
                    OnlyFolderAndSubFolderFileVerifier.file("../test", fileDescription)
                }.toThrow<IllegalArgumentException> { message { contains(errMsg) } }
            }
        }

         action("given which points to /Windows, hence not folder or subfolder") {
            val fileDescription = "testFile"
            val errMsg = "$fileDescription is neither a relative path nor an absolute path"
            it("throws an IllegalArgumentException, mentioning $errMsg") {
                expect {
                    OnlyFolderAndSubFolderFileVerifier.file("/Windows", fileDescription)
                }.toThrow<IllegalArgumentException> { message { contains(errMsg) } }
            }
        }
    }
})
