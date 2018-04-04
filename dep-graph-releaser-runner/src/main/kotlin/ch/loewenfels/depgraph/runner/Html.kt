package ch.loewenfels.depgraph.runner

object Html {

    private const val ARG_OUTPUT_DIR = 1
    val htmlArguments = """
        |html requires the following arguments in the given order:
        |outDir     // path to the directory in which the html file and resources shall be created
        """.trimMargin()

    operator fun invoke(args: Array<out String>) {
        if (args.size != 2) {
            error(
                """
                |Not enough or too many arguments supplied for command: html
                |
                |$htmlArguments
                |
                |${getGivenArgs(args)}
                |
                |Following an example:
                |./produce html ./html
                """.trimMargin()
            )
        }

        val outputDir = fileVerifier.file(args[ARG_OUTPUT_DIR], "output directory")
        if (!outputDir.exists()) {
            error(
                """
                |The directory in which the resulting HTML file (and resources) shall be created does not exists:
                |Directory: ${outputDir.absolutePath}
                """.trimMargin()
            )
        }

        Orchestrator.copyResources(outputDir)
    }
}
