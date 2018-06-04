package ch.loewenfels.depgraph.runner.console

import ch.loewenfels.depgraph.runner.commands.Html
import ch.tutteli.spek.extensions.TempFolder
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.include

class HtmlSpec : Spek({
    include(HtmlCommandSpec)

    //TODO write spec for wrong non-existing directory
    //given("non-existing directory"){}

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
