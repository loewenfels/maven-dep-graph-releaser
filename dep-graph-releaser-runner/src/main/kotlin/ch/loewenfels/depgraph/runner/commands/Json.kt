package ch.loewenfels.depgraph.runner.commands

import ch.loewenfels.depgraph.Config.REGEX_PARAMS
import ch.loewenfels.depgraph.Config.REMOTE_JOB
import ch.loewenfels.depgraph.Config.REMOTE_REGEX
import ch.loewenfels.depgraph.Config.UPDATE_DEPENDENCY_JOB
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.maven.Analyser
import ch.loewenfels.depgraph.maven.JenkinsReleasePlanCreator
import ch.loewenfels.depgraph.runner.Orchestrator
import ch.loewenfels.depgraph.runner.console.ErrorHandler
import ch.loewenfels.depgraph.runner.console.expectedArgsAndGiven
import ch.loewenfels.depgraph.runner.console.toOptionalArgs
import ch.loewenfels.depgraph.runner.toVerifiedFile

object Json : ConsoleCommand {

    private const val REGEX_PARAMS_ARG = "-$REGEX_PARAMS="
    internal const val MAVEN_PARENT_ANALYSIS_OFF = "-mpoff"
    private const val DISABLE_RELEASE_FOR = "-dr="

    override val name = "json"
    override val description = "analyse projects, create a release plan and serialize it to json"
    override val example = "./produce $name com.example example-project ./repo ./release.json " +
        "dgr-updater \"^.*\" dgr-remote-releaser " +
        "$REGEX_PARAMS_ARG\".*#branch.name=master\" $DISABLE_RELEASE_FOR\"ch\\.loewenfels:dist.*\" $MAVEN_PARENT_ANALYSIS_OFF"

    override val arguments by lazy {
        """
        |$name requires the following arguments in the given order:
        |groupId                   // maven groupId of the project which shall be released
        |artifactId                // maven artifactId of the project which shall be released
        |dir                       // path to the directory where all projects are
        |json                      // path + file name for the resulting json file
        |$UPDATE_DEPENDENCY_JOB       // the name of the update dependency job
        |$REMOTE_REGEX               // regex which specifies which projects are released remotely
        |$REMOTE_JOB                 // the job which triggers the remote build
        |(${REGEX_PARAMS_ARG}spec)       // optionally: parameters of the form regex#a=b;c=d${'$'}.*#e=f where the regex
        |                          // defines for which job the parameters shall apply. Multiple regex can be
        |                          // specified. In the above, .* matches all, so every job gets e=f as argument.
        |(${DISABLE_RELEASE_FOR}Regex) // optionally: regex specifying for which projects
        |               the release commands have to be disabled
        |($MAVEN_PARENT_ANALYSIS_OFF)    // optionally: turns missing parent analysis off
        """.trimMargin()
    }

    override fun numOfArgsNotOk(number: Int) = number < 8 || number > 11

    override fun execute(args: Array<out String>, errorHandler: ErrorHandler) {
        val (_, groupId, artifactId, unsafeDirectoryToAnalyse, jsonFile) = args
        val afterFirst5 = args.drop(5)
        val (updateDependencyJob, remoteRegex, remoteJob) = afterFirst5
        val optionalArgs = afterFirst5.drop(3).toOptionalArgs(
            errorHandler,
            REGEX_PARAMS_ARG, DISABLE_RELEASE_FOR, MAVEN_PARENT_ANALYSIS_OFF
        )
        val (regexParameters, disableReleaseFor, missingParentAnalysis) = optionalArgs

        val disableReleaseForRegex = if (disableReleaseFor != null) {
            Regex(disableReleaseFor.substringAfter(DISABLE_RELEASE_FOR))
        } else {
            Regex("^$") //does only match the empty string
        }

        val turnMissingPartnerAnalysisOff = missingParentAnalysis != null

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

        val json = jsonFile.toVerifiedFile("json file")
        if (!json.parentFile.exists()) {
            errorHandler.error(
                """
                |The directory in which the resulting JSON file shall be created does not exist.
                |Directory: ${json.parentFile.absolutePath}
                """.trimMargin()
            )
        }

        val config = listOf(
            UPDATE_DEPENDENCY_JOB to updateDependencyJob,
            REMOTE_JOB to remoteJob,
            REMOTE_REGEX to remoteRegex
        ) + if (regexParameters != null) {
            listOf(REGEX_PARAMS to regexParameters)
        } else {
            listOf()
        }


        val mavenProjectId = MavenProjectId(groupId, artifactId)
        val analyserOptions = Analyser.Options(!turnMissingPartnerAnalysisOff)
        val releasePlanCreatorOptions = JenkinsReleasePlanCreator.Options(disableReleaseForRegex, config)

        Orchestrator.analyseAndCreateJson(
            directoryToAnalyse,
            json,
            mavenProjectId,
            analyserOptions,
            releasePlanCreatorOptions
        )
    }
}
