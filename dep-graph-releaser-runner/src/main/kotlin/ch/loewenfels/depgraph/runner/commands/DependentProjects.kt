package ch.loewenfels.depgraph.runner.commands

import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.runner.Orchestrator
import ch.loewenfels.depgraph.runner.console.ErrorHandler
import ch.loewenfels.depgraph.runner.console.expectedArgsAndGiven
import ch.loewenfels.depgraph.runner.console.toOptionalArgs
import ch.loewenfels.depgraph.runner.toVerifiedFile

object DependentProjects : ConsoleCommand {

    const val FORMAT = "-format="

    override val name = "dependents"
    override val description = "Somehow (depending on the format) displays the dependent projects of a given root project."
    override val example = "./dgr $name com.example example-project ./repos ${FORMAT}list"
    override val arguments = """
        |$name requires the following arguments in the given order:
        |groupId            // maven groupId of the project for which we search dependent projects
        |artifactId         // maven artifactId of the project for which we search dependent projects
        |dir                // path to the directory where all projects are
        |(${FORMAT}list)     // optionally: defines in which format the dependents are displayed.
        |                   // `list` is ued if not specified. Currently supported formats:
        |                   // - list
        """.trimMargin()

    override fun numOfArgsNotOk(number: Int) = number < 4 || number > 5

    override fun execute(args: Array<out String>, errorHandler: ErrorHandler) {
        val (_, groupId, artifactId, unsafeDirectoryToAnalyse) = args
        val optionalArgs = args.drop(4).toOptionalArgs(errorHandler, FORMAT)
        val (nullableFormat) = optionalArgs
        val format = nullableFormat ?: "list"

        val directoryToAnalyse = unsafeDirectoryToAnalyse.toVerifiedFile("directory to analyse")
        if (!directoryToAnalyse.exists()) {
            errorHandler.error(
                """
                |The given directory does not exist. Maybe you mixed up the order of the arguments?
                |directory: ${directoryToAnalyse.absolutePath}
                |
                |${expectedArgsAndGiven(this, args)}
                """.trimMargin()
            )
        }

        val mavenProjectId = MavenProjectId(groupId, artifactId)

        when(format) {
            "list" -> Orchestrator.listDependents(directoryToAnalyse, mavenProjectId)
            else -> errorHandler.error(
                """
                |The given format is not supported.
                |format: $format
                |
                |${expectedArgsAndGiven(this, args)}
                """.trimMargin()
            )
        }



    }
}
