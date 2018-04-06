package ch.loewenfels.depgraph.runner.commands

import ch.loewenfels.depgraph.runner.console.ErrorHandler
import ch.loewenfels.depgraph.runner.Orchestrator
import ch.loewenfels.depgraph.runner.toVerifiedFile

object PrintReleasableProjects : ConsoleCommand {

    override val name = "releasable"
    override val description = "prints all releasable projects"
    override val example = "./produce $name ./repos"
    override val arguments = """
        |$name requires the following arguments in the given order:
        |dir         // path to the directory where all projects are
        """.trimMargin()

    override fun numOfArgsNotOk(number: Int) = number != 2

    override fun execute(args: Array<out String>, errorHandler: ErrorHandler) {
        val (_, unsafeDirectoryToAnalyse) = args
        val directoryToAnalyse = unsafeDirectoryToAnalyse.toVerifiedFile("directory to analyse")
        if (!directoryToAnalyse.exists()) {
            errorHandler.error(
                """
                |The given directory dos not exist:
                |Directory: ${directoryToAnalyse.absolutePath}
                """.trimMargin()
            )
        }

        Orchestrator.printReleasableProjects(directoryToAnalyse)
    }
}
