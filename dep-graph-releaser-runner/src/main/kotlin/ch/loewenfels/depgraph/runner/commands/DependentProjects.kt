package ch.loewenfels.depgraph.runner.commands

import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.runner.Orchestrator
import ch.loewenfels.depgraph.runner.console.ErrorHandler
import ch.loewenfels.depgraph.runner.console.expectedArgsAndGiven
import ch.loewenfels.depgraph.runner.console.toOptionalArgs
import ch.loewenfels.depgraph.runner.toVerifiedFile
import java.io.File

object DependentProjects : ConsoleCommand {

    const val FORMAT = "-format="
    private const val TRANSFORM_REGEX_ARG = "transformRegex"
    private const val TRANSFORM_REGEX = "-$TRANSFORM_REGEX_ARG="
    private const val TRANSFORM_REPLACEMENT = "-transformReplacement="

    override val name = "dependents"
    override val description =
        "Somehow (depending on the format) displays the dependent projects of a given root project."
    override val example = "./dgr $name com.example example-project ./repos ${FORMAT}list" +
        "$TRANSFORM_REGEX^(.*)\$ ${TRANSFORM_REPLACEMENT}https://github.com/\$1"
    override val arguments = """
        |$name requires the following arguments in the given order:
        |groupId                  // maven groupId of the project for which we search dependent projects
        |artifactId               // maven artifactId of the project for which we search dependent projects
        |dir                      // path to the directory where all projects are
        |(${FORMAT}list)           // optionally: defines in which format the dependents are displayed.
        |                         // `list` is used if not specified. Currently supported formats:
        |                         // - list         // print dependent projects
        |                         // - clone        // print list of git clone commands
        |
        |($TRANSFORM_REGEX^(.*)$)                // optionally in the sense that it is only required
        |                                        // in case ${FORMAT}list. Defines the regex used to transform
        |                                        // the relative path to a project into a git repository url
        |(${TRANSFORM_REPLACEMENT}https://...$1    // replacement pattern of the $TRANSFORM_REGEX_ARG
        """.trimMargin()

    override fun numOfArgsNotOk(number: Int) = number < 4 || number > 7

    override fun execute(args: Array<out String>, errorHandler: ErrorHandler) {
        val (_, groupId, artifactId, unsafeDirectoryToAnalyse) = args
        val optionalArgs = args.drop(4).toOptionalArgs(errorHandler, FORMAT, TRANSFORM_REGEX, TRANSFORM_REPLACEMENT)
        val (nullableFormat, nullableTransformRegex, nullableTransformReplacement) = optionalArgs
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

        when (format) {
            "list" -> list(directoryToAnalyse, mavenProjectId)
            "clone" -> clone(
                directoryToAnalyse,
                mavenProjectId,
                nullableTransformRegex,
                nullableTransformReplacement,
                errorHandler,
                args
            )
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

    private fun list(directoryToAnalyse: File, mavenProjectId: MavenProjectId) {
        Orchestrator.printDependents(directoryToAnalyse, mavenProjectId)
    }


    private fun clone(
        directoryToAnalyse: File,
        mavenProjectId: MavenProjectId,
        nullableTransformRegex: String?,
        nullableTransformReplacement: String?,
        errorHandler: ErrorHandler,
        args: Array<out String>
    ) {
        if (nullableTransformRegex == null) {
            reportCloneMissingArgument(errorHandler, TRANSFORM_REGEX, args)
        }
        if (nullableTransformReplacement == null) {
            reportCloneMissingArgument(errorHandler, TRANSFORM_REPLACEMENT, args)
        }

        Orchestrator.printGitCloneForDependents(
            directoryToAnalyse,
            mavenProjectId,
            Regex(nullableTransformRegex),
            nullableTransformReplacement
        )
    }

    private fun reportCloneMissingArgument(
        errorHandler: ErrorHandler,
        missingArgument: String,
        args: Array<out String>
    ): Nothing = errorHandler.error(
        """
        |Format specified is clone in which case you have to pass a value for $missingArgument.
        |
        |${expectedArgsAndGiven(this, args)}
        |
        |Following an example:
        |$example
        """.trimMargin()
    )
}
