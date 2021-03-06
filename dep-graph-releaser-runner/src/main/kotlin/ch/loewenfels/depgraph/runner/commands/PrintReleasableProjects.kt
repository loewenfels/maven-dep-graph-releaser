package ch.loewenfels.depgraph.runner.commands

import ch.loewenfels.depgraph.runner.console.ErrorHandler
import ch.loewenfels.depgraph.runner.Orchestrator

object PrintReleasableProjects : ConsoleCommand {

    override val name = "releasable"
    override val description = "prints all releasable projects"
    override val example = "./dgr $name ./repos"
    override val arguments = """
        |$name requires the following arguments in the given order:
        |dir         // path to the directory where all projects are
        """.trimMargin()

    override fun numOfArgsNotOk(number: Int) = number != 2

    override fun execute(args: Array<out String>, errorHandler: ErrorHandler) {
        val (_, unsafeDirectoryToAnalyse) = args
        val directoryToAnalyse = toVerifiedExistingFile(
            unsafeDirectoryToAnalyse, "directory to analyse", this, args, errorHandler
        )

        Orchestrator.printReleasableProjects(directoryToAnalyse)
    }
}
