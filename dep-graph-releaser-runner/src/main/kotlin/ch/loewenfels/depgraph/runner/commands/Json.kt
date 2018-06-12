package ch.loewenfels.depgraph.runner.commands

import ch.loewenfels.depgraph.ConfigKey
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.maven.JenkinsReleasePlanCreator
import ch.loewenfels.depgraph.parseRegexParameters
import ch.loewenfels.depgraph.parseRemoteRegex
import ch.loewenfels.depgraph.runner.Orchestrator
import ch.loewenfels.depgraph.runner.console.ErrorHandler
import ch.loewenfels.depgraph.runner.console.toOptionalArgs
import java.util.*

object Json : ConsoleCommand {

    val REGEX_PARAMS_ARG = "-${ConfigKey.REGEX_PARAMS.asString()}="
    val JOB_MAPPING_ARG = "-${ConfigKey.JOB_MAPPING.asString()}="
    val COMMIT_PREFIX_ARG = "-${ConfigKey.COMMIT_PREFIX.asString()}="
    const val DISABLE_RELEASE_FOR = "-disableRegex="

    override val name = "json"
    override val description = "analyse projects, create a release plan and serialize it to json"
    override val example = "./dgr $name com.example example-project ./repo ./release.json " +
        "dgr-updater dgr-dry-run \"ch\\..*#https://example.com/jenkins;com\\..*#https://jenkins.example.com\" " +
        "\"[^/]+/[^/]+/.+\" \"^(.*)/\$\" https://github.com/\$1" +
        "$REGEX_PARAMS_ARG\".*#branch.name=master\" $DISABLE_RELEASE_FOR\"ch\\.loewenfels:dist.*\" " +
        "$JOB_MAPPING_ARG=com.example:a=exampleA|ch.loewenfels:dgr-1=apnoea-test-1" +
        "$COMMIT_PREFIX_ARG=[RELEASE]"

    override val arguments by lazy {
        """
        |$name requires the following arguments in the given order:
        |gId                       // maven groupId of the project which shall be released
        |aId                       // maven artifactId of the project which shall be released
        |dir                       // path to the directory where all projects are
        |json                      // path + file name for the resulting json file
        |${ConfigKey.UPDATE_DEPENDENCY_JOB.asString()}       // the name of the update dependency job
        |${ConfigKey.DRY_RUN_JOB.asString()}                 // the job which executes a dry run
        |${ConfigKey.REMOTE_REGEX.asString()}               // regex which determines which project runs on a different jenkins server
        |                          // than the publish job. It takes the following form where regex has to
        |                          // match the project identifier (groupId:artifactId):
        |                          // regex#jenkinsBaseUrl;regex2#anotherJenkinsBaseUrl
        |                          // Notice that the first match is considered and the rest ignored.
        |
        |${ConfigKey.RELATIVE_PATH_EXCLUDE_PROJECT_REGEX.asString()}    // regex used in functionality such as `list dependent projects` whereas
        |                                    // the regex is used to exclude certain projects based on their relative path
        |${ConfigKey.RELATIVE_PATH_TO_GIT_REPO_REGEX.asString()}          // regex used to match a relative path of a project whereas the match is
        |                                    // then used in ${ConfigKey.RELATIVE_PATH_TO_GIT_REPO_REPLACEMENT} to turn it into a
        |                                    // git repository url
        |${ConfigKey.RELATIVE_PATH_TO_GIT_REPO_REPLACEMENT.asString()}    // replacement string used to turn a relative project path into
        |                                    // a git repository url
        |
        |(${REGEX_PARAMS_ARG}spec)       // optionally: parameters of the form regex#a=b;c=d${'$'}.*#e=f where the regex
        |                          // defines for which job the parameters shall apply. Multiple regex can be
        |                          // specified. In the above, .* matches all, so every job gets e=f as argument.
        |(${DISABLE_RELEASE_FOR}Regex)     // optionally: regex specifying for which projects
        |                          // the release commands have to be disabled
        |(${JOB_MAPPING_ARG}spec)        // optionally: in case a jenkins job differ from its artifact name,
        |                          // you can use this mapping which is of the form:
        |                          // groupId:artifactId1=jobName1|groupId:artifactId2=anotherName
        |(${COMMIT_PREFIX_ARG}spec)      // optionally: it is possible to define a commit prefix
        |                          // if not specified, the default "[DGR]" is used
        """.trimMargin()
    }

    override fun numOfArgsNotOk(number: Int) = number < 11 || number > 15

    override fun execute(args: Array<out String>, errorHandler: ErrorHandler) {
        val (_, groupId, artifactId, unsafeDirectoryToAnalyse, jsonFile) = args
        val afterFirst5 = args.drop(5)
        val (updateDependencyJob, dryRunJob, remoteRegex) = afterFirst5
        val afterFirstEight = afterFirst5.drop(3)
        val (excludeRegex, gitRepoRegex, gitRepoReplacement) = afterFirstEight
        val optionalArgs = afterFirstEight.drop(4).toOptionalArgs(
            errorHandler, REGEX_PARAMS_ARG, DISABLE_RELEASE_FOR, JOB_MAPPING_ARG, COMMIT_PREFIX_ARG
        )
        val (regexParameters, disableReleaseFor, jobMapping, commitPrefix) = optionalArgs

        val disableReleaseForRegex = if (disableReleaseFor != null) {
            Regex(disableReleaseFor.substringAfter(DISABLE_RELEASE_FOR))
        } else {
            Regex("^$") //does only match the empty string
        }

        val directoryToAnalyse = toVerifiedExistingFile(
            unsafeDirectoryToAnalyse, "directory to analyse", this, args, errorHandler
        )
        val json = toVerifiedFileIfParentExists(jsonFile, "json file", errorHandler)

        //will both throw if there is a validation error
        parseRemoteRegex(remoteRegex)
        if (regexParameters != null) parseRegexParameters(regexParameters)

        val config = mapOf(
            ConfigKey.COMMIT_PREFIX to (commitPrefix ?: "[DGR]"),
            ConfigKey.UPDATE_DEPENDENCY_JOB to updateDependencyJob,
            ConfigKey.DRY_RUN_JOB to dryRunJob,
            ConfigKey.REMOTE_REGEX to remoteRegex,
            ConfigKey.RELATIVE_PATH_EXCLUDE_PROJECT_REGEX to excludeRegex,
            ConfigKey.RELATIVE_PATH_TO_GIT_REPO_REGEX to gitRepoRegex,
            ConfigKey.RELATIVE_PATH_TO_GIT_REPO_REPLACEMENT to gitRepoReplacement,
            ConfigKey.REGEX_PARAMS to (regexParameters ?: ""),
            ConfigKey.JOB_MAPPING to (jobMapping ?: "")
        )

        val mavenProjectId = MavenProjectId(groupId, artifactId)
        val publishId = UUID.randomUUID().toString().replace("-", "").take(15)
        val releasePlanCreatorOptions = JenkinsReleasePlanCreator.Options(publishId, disableReleaseForRegex, config)

        Orchestrator.analyseAndCreateJson(
            directoryToAnalyse,
            json,
            mavenProjectId,
            releasePlanCreatorOptions
        )
    }
}
