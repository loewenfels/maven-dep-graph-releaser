package ch.loewenfels.depgraph.runner.commands

import ch.loewenfels.depgraph.runner.console.ErrorHandler
import ch.loewenfels.depgraph.runner.Orchestrator

object Html : ConsoleCommand {

    override val name = "html"
    override val description = "copy html pipeline including resources"
    override val example = "./dgr $name ./html"
    override val arguments = """
        |$name requires the following arguments in the given order:
        |outDir      // path to the directory in which the html file and resources shall be created
        """.trimMargin()

    override fun numOfArgsNotOk(number: Int) = number != 2

    override fun execute(args: Array<out String>, errorHandler: ErrorHandler) {
        val (_, outputDirPath) = args

        val outputDir = toVerifiedExistingFile(
            outputDirPath, "output directory", this, args,
            errorHandler,
            suffix = " in which the resulting HTML file (and resources) shall be created"
        )

        Orchestrator.copyResources(outputDir)
    }
}
