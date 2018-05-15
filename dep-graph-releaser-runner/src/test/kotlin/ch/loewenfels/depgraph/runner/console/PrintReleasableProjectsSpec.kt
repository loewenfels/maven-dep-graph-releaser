package ch.loewenfels.depgraph.runner.console

import ch.loewenfels.depgraph.runner.commands.PrintReleasableProjects
import ch.tutteli.spek.extensions.TempFolder

class PrintReleasableProjectsSpec : CommandSpec(
    PrintReleasableProjects,
    Companion::getNotEnoughArgs,
    Companion::getTooManyArgs,
    2..2
) {
    companion object {
        fun getNotEnoughArgs(@Suppress("UNUSED_PARAMETER") tempFolder: TempFolder): Array<out String> {
            return arrayOf(
                PrintReleasableProjects.name
                //dir is required as well
                //tempFolder.tmpDir.absolutePath,
            )
        }

        fun getTooManyArgs(tempFolder: TempFolder): Array<out String> {
            return arrayOf(
                PrintReleasableProjects.name,
                tempFolder.tmpDir.absolutePath,
                "unexpectedAdditionalArg"
            )
        }
    }
}
