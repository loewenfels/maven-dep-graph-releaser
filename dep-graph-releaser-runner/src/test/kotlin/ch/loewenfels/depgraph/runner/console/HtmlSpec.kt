package ch.loewenfels.depgraph.runner.console

import ch.loewenfels.depgraph.runner.commands.Html
import ch.tutteli.atrium.api.cc.en_GB.contains
import ch.tutteli.atrium.api.cc.en_GB.message
import ch.tutteli.atrium.api.cc.en_GB.toThrow
import ch.tutteli.atrium.expect
import ch.tutteli.spek.extensions.TempFolder
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.include

class HtmlSpec : Spek({
    include(HtmlCommandSpec)

    given("non-existing directory") {
        val expectedOutputDirectory = "myOutDir"
        val errMsg = "The given output directory in which the resulting HTML file (and resources) shall be created does not exist"
        val inputArgs = arrayOf("projectName",expectedOutputDirectory)
        it("throws an error, mentioning $errMsg") {
            expect {
                Html.execute(inputArgs, errorHandler)
            }.toThrow<IllegalStateException> { message { contains(errMsg, expectedOutputDirectory) } }
        }
    }

}) {
    object HtmlCommandSpec : CommandSpec(
        Html,
        ::getNotEnoughArgs,
        ::getTooManyArgs,
        2..2
    )

    companion object {
        fun getNotEnoughArgs(@Suppress("UNUSED_PARAMETER") tempFolder: TempFolder): Array<out String> {
            return arrayOf(
                Html.name
                //out dir is required as well
                //tempFolder.tmpDir.absolutePath,
            )
        }

        fun getTooManyArgs(tempFolder: TempFolder): Array<out String> {
            return arrayOf(
                Html.name,
                tempFolder.tmpDir.absolutePath,
                "unexpectedAdditionalArg"
            )
        }
    }
}
