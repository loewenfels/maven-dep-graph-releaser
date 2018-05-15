package ch.loewenfels.depgraph.runner.commands

import ch.loewenfels.depgraph.ConfigKey
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.maven.Analyser
import ch.loewenfels.depgraph.maven.JenkinsReleasePlanCreator
import ch.loewenfels.depgraph.runner.Orchestrator
import ch.loewenfels.depgraph.runner.console.ErrorHandler
import ch.loewenfels.depgraph.runner.console.expectedArgsAndGiven
import ch.loewenfels.depgraph.runner.console.toOptionalArgs
import ch.loewenfels.depgraph.runner.toVerifiedFile
import java.util.*

object Json : ConsoleCommand {

    val REGEX_PARAMS_ARG = "-${ConfigKey.REGEX_PARAMS.asString()}="
    val JOB_MAPPING_ARG = "-${ConfigKey.JOB_MAPPING.asString()}="
    const val DISABLE_RELEASE_FOR = "-disableRegex="

    override val name = "json"
    override val description = "analyse projects, create a release plan and serialize it to json"
    override val example = "./dgr $name com.example example-project ./repo ./release.json " +
        "dgr-updater \"^.*\" dgr-remote-releaser dgr-dry-run " +
        "$REGEX_PARAMS_ARG\".*#branch.name=master\" $DISABLE_RELEASE_FOR\"ch\\.loewenfels:dist.*\" " +
        "$JOB_MAPPING_ARG=com.example:a=exampleA|ch.loewenfels:dgr-1=apnoea-test-1"

    override val arguments by lazy {
        """
        |$name requires the following arguments in the given order:
        |groupId                   // maven groupId of the project which shall be released
        |artifactId                // maven artifactId of the project which shall be released
        |dir                       // path to the directory where all projects are
        |json                      // path + file name for the resulting json file
        |${ConfigKey.UPDATE_DEPENDENCY_JOB.asString()}       // the name of the update dependency job
        |${ConfigKey.REMOTE_REGEX.asString()}               // regex which specifies which projects are released remotely
        |${ConfigKey.REMOTE_JOB.asString()}                 // the job which triggers the remote build
        |${ConfigKey.DRY_RUN_JOB.asString()}                 // the job which executes a dry run
        |(${REGEX_PARAMS_ARG}spec)       // optionally: parameters of the form regex#a=b;c=d${'$'}.*#e=f where the regex
        |                          // defines for which job the parameters shall apply. Multiple regex can be
        |                          // specified. In the above, .* matches all, so every job gets e=f as argument.
        |(${DISABLE_RELEASE_FOR}Regex)     // optionally: regex specifying for which projects
        |                          // the release commands have to be disabled
        |(${JOB_MAPPING_ARG}spec)        // optionally: in case a jenkins job differ from its artifact name,
        |                          // you can use this mapping which is of the form:
        |                          // groupId:artifactId1=jobName1|groupId:artifactId2=anotherName
        """.trimMargin()
    }

    override fun numOfArgsNotOk(number: Int) = number < 9 || number > 12

    override fun execute(args: Array<out String>, errorHandler: ErrorHandler) {
        val (_, groupId, artifactId, unsafeDirectoryToAnalyse, jsonFile) = args
        val afterFirst5 = args.drop(5)
        val (updateDependencyJob, remoteRegex, remoteJob, dryRunJob) = afterFirst5
        val optionalArgs = afterFirst5.drop(4).toOptionalArgs(
            errorHandler,
            REGEX_PARAMS_ARG, DISABLE_RELEASE_FOR, JOB_MAPPING_ARG
        )
        val (regexParameters, disableReleaseFor, jobMapping) = optionalArgs

        val disableReleaseForRegex = if (disableReleaseFor != null) {
            Regex(disableReleaseFor.substringAfter(DISABLE_RELEASE_FOR))
        } else {
            Regex("^$") //does only match the empty string
        }

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

        val config = mapOf(
            ConfigKey.COMMIT_PREFIX to "[DGR]",
            ConfigKey.UPDATE_DEPENDENCY_JOB to updateDependencyJob,
            ConfigKey.REMOTE_JOB to remoteJob,
            ConfigKey.REMOTE_REGEX to remoteRegex,
            ConfigKey.DRY_RUN_JOB to dryRunJob,
            ConfigKey.REGEX_PARAMS to (regexParameters ?: ""),
            ConfigKey.JOB_MAPPING to (jobMapping ?: "")
        )

        val mavenProjectId = MavenProjectId(groupId, artifactId)
        val analyserOptions = Analyser.Options()
        val publishId = UUID.randomUUID().toString().replace("-", "").take(15)
        val releasePlanCreatorOptions = JenkinsReleasePlanCreator.Options(publishId, disableReleaseForRegex, config)

        Orchestrator.analyseAndCreateJson(
            directoryToAnalyse,
            json,
            mavenProjectId,
            analyserOptions,
            releasePlanCreatorOptions
        )
    }
}
