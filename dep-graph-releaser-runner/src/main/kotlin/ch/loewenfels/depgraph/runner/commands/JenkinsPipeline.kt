package ch.loewenfels.depgraph.runner.commands

import ch.loewenfels.depgraph.runner.Orchestrator
import ch.loewenfels.depgraph.runner.console.ErrorHandler
import ch.loewenfels.depgraph.runner.console.toOptionalArgs
import ch.loewenfels.depgraph.runner.toVerifiedFile

object JenkinsPipeline : ConsoleCommand {

    private const val REGEX_PARAMS = "-regexParams="
    private const val JENKINSFILE = "-jenkinsfile="
    override val name = "pipeline"
    override val description = "generate and print a jenkinsfile or writes it to the specified file"
    override val example = "./produce $name ./release.json dep-graph-releaser-updater " +
        "\"^.*\" dep-graph-releaser-remote $REGEX_PARAMS\".*#branch.name=master\" $JENKINSFILE./jenkinsfile"
    override val arguments = """
        |$name requires the following arguments in the given order:
        |json                      // path to the release.json
        |updateDependencyJob       // the name of the update dependency job
        |remoteRegex               // regex which specifies which projects are released remotely
        |remoteJob                 // the job which triggers the remote build
        |(${REGEX_PARAMS}spec)       // optionally: parameters of the form regex#a=b;c=d$.*#e=f where the regex
        |                          // defines for which job the parameters shall apply. Multiple regex can be
        |                          // specified. In the above, .* matches all, so every job gets e=f as argument.
        |(${JENKINSFILE}file)       // optionally: a path to the resulting jenkinsfile,
        |                          // it gets printed to the console if not present
        """.trimMargin()

    override fun numOfArgsNotOk(number: Int) = number < 5 || number > 7

    override fun execute(args: Array<out String>, errorHandler: ErrorHandler) {
        val (_, jsonFile, updateDependencyJob, remoteRegex, remoteJob) = args
        val optionalArgs = args.drop(5).toOptionalArgs(errorHandler, REGEX_PARAMS, JENKINSFILE)
        val (regexParameters, jenkinsFilePath) = optionalArgs

        val json = jsonFile.toVerifiedFile("json file")
        if (!json.exists()) {
            errorHandler.error(
                """
                |The specified json file does not exist, cannot generate the pipeline.
                |Json: ${json.absolutePath}
                """.trimMargin()
            )
        }

        @Suppress("IfThenToElvis")
        val regexParametersList = if (regexParameters != null) {
            regexParameters.splitToSequence("$")
                .map { pair ->
                    val index = checkRegexNotEmpty(pair, errorHandler, regexParameters)
                    val parameters = pair.substring(index + 1)
                    JenkinsRemoteM2Release.checkParamNameNotEmpty(parameters, errorHandler, regexParameters)
                    Regex(pair.substring(0, index)) to parameters
                }
                .toList()
        } else {
            emptyList()
        }

        val jenkinsfile = jenkinsFilePath?.toVerifiedFile("jenkinsfile")
        if (jenkinsfile?.parentFile?.exists() == false) {
            errorHandler.error(
                """
                |The directory in which the resulting jenkinsfile shall be created does not exist.
                |Directory: ${json.parentFile.absolutePath}
                """.trimMargin()
            )
        }

        Orchestrator.jenkinsPipeline(
            json,
            updateDependencyJob,
            Regex(remoteRegex),
            remoteJob,
            regexParametersList,
            jenkinsfile
        )
    }

    private fun checkRegexNotEmpty(pair: String, errorHandler: ErrorHandler, regexParameters: String): Int {
        val index = pair.indexOf('#')
        if (index < 1) {
            errorHandler.error("regex requires at least one character.\nParameters: $regexParameters")
        }
        return index
    }
}
