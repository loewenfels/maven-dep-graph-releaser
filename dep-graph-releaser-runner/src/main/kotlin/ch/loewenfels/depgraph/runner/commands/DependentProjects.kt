package ch.loewenfels.depgraph.runner.commands

import ch.loewenfels.depgraph.ConfigKey
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.runner.Orchestrator
import ch.loewenfels.depgraph.runner.console.ErrorHandler
import ch.loewenfels.depgraph.runner.console.expectedArgsAndGiven
import ch.loewenfels.depgraph.runner.console.toOptionalArgs
import java.io.File

object DependentProjects : ConsoleCommand {

    const val FORMAT_ARG = "-format="
    val RELATIVE_PATH_TO_GIT_REPO_REGEX_ARG = "-${ConfigKey.RELATIVE_PATH_TO_GIT_REPO_REGEX.asString()}="
    private const val TRANSFORM_REPLACEMENT_ARG = "transformReplacement"
    val RELATIVE_PATH_TO_GIT_REPO_REPLACEMENT = "-${ConfigKey.RELATIVE_PATH_TO_GIT_REPO_REPLACEMENT.asString()}="
    const val PSF = "-psf="

    private const val LIST = "list"
    private const val CLONE = "clone"
    private const val ECLIPSE_PSF = "psf"

    override val name = "dependents"
    override val description = "Somehow (depending on the format) displays the dependent projects of a given root project."
    override val example = "./dgr $name com.example example-project ./repos \"[^/]+/[^/]+/.+\" " +
        "${FORMAT_ARG}list $RELATIVE_PATH_TO_GIT_REPO_REGEX_ARG^(.*)/\$ ${RELATIVE_PATH_TO_GIT_REPO_REPLACEMENT}https://github.com/\$1 $PSF./import.psf"
    override val arguments = """
        |$name requires the following arguments in the given order:
        |groupId                           // maven groupId of the project for which we search dependent projects
        |artifactId                        // maven artifactId of the project for which we search dependent projects
        |dir                               // path to the directory where all projects are
        |${ConfigKey.RELATIVE_PATH_EXCLUDE_PROJECT_REGEX.asString()}  // a relative path of the project matching the entire regex is excluded
        |
        |(${FORMAT_ARG}list)                    // optionally: defines in which format the dependents are displayed.
        |                                  // `list` is used if not specified. Currently supported formats:
        |                                  // - $LIST         // print dependent projects
        |                                  // - $CLONE        // print list of git clone commands
        |                                  // - $ECLIPSE_PSF   // prints an psf file for eclipse
        |
        |($RELATIVE_PATH_TO_GIT_REPO_REGEX_ARG^(.*)$)                // optionally in the sense that it is only required
        |                                                    // in case ${FORMAT_ARG}$LIST. Defines the regex used to transform
        |                                                    // the relative path to a project into a git repository url
        |(${RELATIVE_PATH_TO_GIT_REPO_REPLACEMENT}https://...$1    // optionally in the sense that it is only required
        |                                                    // in case ${FORMAT_ARG}$LIST. Is the replacement pattern
        |                                                    // of the ${ConfigKey.RELATIVE_PATH_EXCLUDE_PROJECT_REGEX.asString()}
        |
        |(${PSF}file)                       // optionally in the sense that it is only required in case ${FORMAT_ARG}$ECLIPSE_PSF
        |                                  // path + file name for the resulting psf file
        """.trimMargin()

    override fun numOfArgsNotOk(number: Int) = number < 5 || number > 9

    override fun execute(args: Array<out String>, errorHandler: ErrorHandler) {
        val (_, groupId, artifactId, unsafeDirectoryToAnalyse, excludeRegexString) = args
        val optionalArgs = args.drop(5).toOptionalArgs(
            errorHandler,
            FORMAT_ARG, RELATIVE_PATH_TO_GIT_REPO_REGEX_ARG, RELATIVE_PATH_TO_GIT_REPO_REPLACEMENT, PSF
        )
        val (nullableFormat, nullableTransformRegex, nullableTransformReplacement, nullablePsf) = optionalArgs
        val format = nullableFormat ?: "list"

        val directoryToAnalyse = toVerifiedExistingFile(
            unsafeDirectoryToAnalyse, "directory to analyse", this, args, errorHandler
        )

        if (excludeRegexString.startsWith(FORMAT_ARG) ||
            excludeRegexString.startsWith(RELATIVE_PATH_TO_GIT_REPO_REGEX_ARG) ||
            excludeRegexString.startsWith(RELATIVE_PATH_TO_GIT_REPO_REPLACEMENT)
        ) {
            errorHandler.error(
                """You have forgotten to provide an argument for excludeRegex or you have mixed up the order of the arguments.
                |excludeRegex: $excludeRegexString
                |format: $nullableFormat
                |$RELATIVE_PATH_TO_GIT_REPO_REGEX_ARG: $nullableTransformRegex
                |$TRANSFORM_REPLACEMENT_ARG: $nullableTransformReplacement
                |
                |${expectedArgsAndGiven(this, args)}
                |
                |Following an example:
                |$example
                """.trimMargin()
            )
        }

        val mavenProjectId = MavenProjectId(groupId, artifactId)
        val excludeRegex = Regex(excludeRegexString)

        when (format) {
            LIST -> list(directoryToAnalyse, mavenProjectId, excludeRegex)
            CLONE -> clone(
                directoryToAnalyse,
                mavenProjectId,
                excludeRegex,
                nullableTransformRegex,
                nullableTransformReplacement,
                errorHandler,
                args
            )
            ECLIPSE_PSF -> eclipsePsf(
                directoryToAnalyse,
                mavenProjectId,
                excludeRegex,
                nullableTransformRegex,
                nullableTransformReplacement,
                nullablePsf,
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

    private fun list(
        directoryToAnalyse: File,
        mavenProjectId: MavenProjectId,
        excludeRegex: Regex
    ) = Orchestrator.printDependents(directoryToAnalyse, mavenProjectId, excludeRegex)

    private fun clone(
        directoryToAnalyse: File,
        mavenProjectId: MavenProjectId,
        excludeRegex: Regex,
        nullableTransformRegex: String?,
        nullableTransformReplacement: String?,
        errorHandler: ErrorHandler,
        args: Array<out String>
    ) {
        val (transformRegex, transformReplacement) = checkTransformArgsPresent(
            errorHandler,
            CLONE,
            args,
            nullableTransformRegex,
            nullableTransformReplacement
        )

        Orchestrator.printGitCloneForDependents(
            directoryToAnalyse,
            mavenProjectId,
            excludeRegex,
            transformRegex,
            transformReplacement
        )
    }

    private fun eclipsePsf(
        directoryToAnalyse: File,
        mavenProjectId: MavenProjectId,
        excludeRegex: Regex,
        nullableTransformRegex: String?,
        nullableTransformReplacement: String?,
        nullablePsfFile: String?,
        errorHandler: ErrorHandler,
        args: Array<out String>
    ) {
        val (transformRegex, transformReplacement) = checkTransformArgsPresent(
            errorHandler,
            ECLIPSE_PSF,
            args,
            nullableTransformRegex,
            nullableTransformReplacement
        )
        if (nullablePsfFile == null) {
            reportMissingArgument(errorHandler, ECLIPSE_PSF, PSF, args)
        }

        val psfFile = toVerifiedFileIfParentExists(nullablePsfFile, "psf file", errorHandler)

        Orchestrator.createPsfFileForDependents(
            directoryToAnalyse,
            mavenProjectId,
            excludeRegex,
            transformRegex,
            transformReplacement,
            psfFile
        )
    }

    private fun checkTransformArgsPresent(
        errorHandler: ErrorHandler,
        format: String,
        args: Array<out String>,
        nullableTransformRegex: String?,
        nullableTransformReplacement: String?
    ): Pair<Regex, String> {
        if (nullableTransformRegex == null) {
            reportMissingArgument(errorHandler, format, RELATIVE_PATH_TO_GIT_REPO_REGEX_ARG, args)
        }
        if (nullableTransformReplacement == null) {
            reportMissingArgument(errorHandler, format, RELATIVE_PATH_TO_GIT_REPO_REPLACEMENT, args)
        }
        return Regex(nullableTransformRegex) to nullableTransformReplacement
    }

    private fun reportMissingArgument(
        errorHandler: ErrorHandler,
        format: String,
        missingArgument: String,
        args: Array<out String>
    ): Nothing = errorHandler.error(
        """
        |Format specified is $format in which case you have to pass a value for $missingArgument.
        |
        |${expectedArgsAndGiven(this, args)}
        |
        |Following an example:
        |$example
        """.trimMargin()
    )
}
