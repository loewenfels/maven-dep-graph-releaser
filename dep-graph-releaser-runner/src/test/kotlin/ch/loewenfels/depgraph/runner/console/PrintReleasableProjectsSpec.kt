package ch.loewenfels.depgraph.runner.console

import ch.loewenfels.depgraph.runner.commands.PrintReleasableProjects
import ch.tutteli.spek.extensions.TempFolder
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.include

class PrintReleasableProjectsSpec : Spek({
    include(PrintReleasableProjectsCommandSpec)

    //TODO write spec for wrong non-existing directory
    //given("non-existing directory"){}

}) {
    object PrintReleasableProjectsCommandSpec : CommandSpec(
        PrintReleasableProjects,
        ::getNotEnoughArgs,
        ::getTooManyArgs,
        2..2
    )

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
