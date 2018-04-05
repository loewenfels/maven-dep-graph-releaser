package ch.loewenfels.depgraph.runner

import ch.loewenfels.depgraph.console.ConsoleCommand
import ch.loewenfels.depgraph.console.ErrorHandler
import ch.loewenfels.depgraph.runner.Main.fileVerifier

object Html : ConsoleCommand{
    private const val ARG_OUTPUT_DIR = 1

    override val name= "html"
    override val description = "copy html pipeline including resources"
    override val example= "./produce html ./html"
    override val arguments = """
        |html requires the following arguments in the given order:
        |outDir     // path to the directory in which the html file and resources shall be created
        """.trimMargin()

    override fun numOfArgsNotOk(number: Int) = number != 2

    override fun execute(args: Array<out String>, errorHandler: ErrorHandler) {

        val outputDir = fileVerifier.file(args[ARG_OUTPUT_DIR], "output directory")
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
