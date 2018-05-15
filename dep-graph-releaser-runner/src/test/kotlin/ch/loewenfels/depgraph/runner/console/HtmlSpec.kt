package ch.loewenfels.depgraph.runner.console

import ch.loewenfels.depgraph.runner.commands.Html
import ch.tutteli.spek.extensions.TempFolder

class HtmlSpec : CommandSpec(
    Html,
    Companion::getNotEnoughArgs,
    Companion::getTooManyArgs,
    2..2
) {
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
