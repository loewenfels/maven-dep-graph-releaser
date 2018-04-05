package ch.loewenfels.depgraph.runner.commands

import ch.loewenfels.depgraph.runner.console.ErrorHandler
import ch.loewenfels.depgraph.runner.Main.fileVerifier
import ch.loewenfels.depgraph.runner.Orchestrator

object Html : ConsoleCommand {

    override val name = "html"
    override val description = "copy html pipeline including resources"
    override val example = "./produce html ./html"
    override val arguments = """
        |html requires the following arguments in the given order:
        |outDir     // path to the directory in which the html file and resources shall be created
        """.trimMargin()

    override fun numOfArgsNotOk(number: Int) = number != 2

    override fun execute(args: Array<out String>, errorHandler: ErrorHandler) {
        val (_, outputDirPath) = args
        val outputDir = fileVerifier.file(outputDirPath, "output directory")
        if (!outputDir.exists()) {
            errorHandler.error(
                """
                |The directory in which the resulting HTML file (and resources) shall be created does not exists:
                |Directory: ${outputDir.absolutePath}
                """.trimMargin()
            )
        }

        Orchestrator.copyResources(outputDir)
    }
}
