package ch.loewenfels.depgraph.runner.commands

import ch.loewenfels.depgraph.runner.Main.fileVerifier
import ch.loewenfels.depgraph.runner.Orchestrator
import ch.loewenfels.depgraph.runner.console.ErrorHandler

object JenkinsPipeline : ConsoleCommand {

    override val name = "pipeline"
    override val description = "copy html pipeline including resources"
    override val example = "./produce $name ./release.json .* dep-graph-releaser-remote \".*|branch=master\""
    override val arguments = """
        |$name requires the following arguments in the given order:
        |json             // path to the release.json
        |remoteRegex      // regex which specifies which projects are released remotely
        |remoteJobName    // the job which triggers the remote build
        |regexParameters  // parameters of the form regex#a=b;c=d$.*#e=f where the regex defines for
        |                 // which job the parameters shall apply. Multiple regex can be specified.
        |                 // In the above, .* matches all, so every job gets e=f as argument
        """.trimMargin()

    override fun numOfArgsNotOk(number: Int) = number != 5

    override fun execute(args: Array<out String>, errorHandler: ErrorHandler) {
        val (_, jsonFile, remoteRegex, remoteJobName, regexParameters) = args
        val json = fileVerifier.file(jsonFile, "json file")
        if (!json.exists()) {
            errorHandler.error(
                """
                |The specified json file does not exist, cannot generate the pipeline.
                |Json: ${json.absolutePath}
                """.trimMargin()
            )
        }

        val regexParametersList = regexParameters.splitToSequence("$")
            .map { pair ->
                val index = checkRegexNotEmpty(pair, errorHandler, regexParameters)
                val parameters = pair.substring(index + 1)
                JenkinsRemoteM2Release.checkParamNameNotEmpty(parameters, errorHandler, regexParameters)
                Regex(pair.substring(0, index)) to parameters
            }
            .toList()
        Orchestrator.jenkinsPipeline(json, Regex(remoteRegex), remoteJobName, regexParametersList)
    }

    private fun checkRegexNotEmpty(pair: String, errorHandler: ErrorHandler, regexParameters: String): Int {
        val index = pair.indexOf('#')
        if (index < 1) {
            errorHandler.error("regex requires at least one character.\nParameters: $regexParameters")
        }
        return index
    }
}
