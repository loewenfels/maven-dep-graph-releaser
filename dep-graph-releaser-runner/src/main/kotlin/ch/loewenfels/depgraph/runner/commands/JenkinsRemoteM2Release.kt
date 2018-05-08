package ch.loewenfels.depgraph.runner.commands

import ch.loewenfels.depgraph.runner.Orchestrator
import ch.loewenfels.depgraph.runner.console.ErrorHandler


object JenkinsRemoteM2Release : ConsoleCommand {

    override val name = "remoteRelease"
    override val description = "releases a project by triggering the m2 release plugin on a remote jenkins."
    override val example =
        "./dgr $name https://example.com/jenkins user pass 3 5 60 10 300 branch.name=master myJob 1.0 1.1-SNAPSHOT"
    override val arguments = """
        |$name requires the following arguments in the given order:
        |jenkinsBaseUrl                // e.g. https://mydomain.org/jenkins
        |jenkinsUsername               // username used for Basic Auth
        |jenkinsPassword               // password used for Basic Auth
        |maxTriggerTries               // max. attempts of triggering the release
        |pollExecutionEverySecond      // poll a queued item every x second
        |maxWaitForExecutionInSeconds  // max. time to wait for queued item to be executed
        |pollReleaseEverySecond        // poll completion every x second
        |maxReleaseTimeInSeconds       // max. time to wait for the release to complete
        |parameters                    // additional parameters in the form a=b;c=d
        |jobName                       // the name of the job to trigger
        |releaseVersion                // the release version for the M2 Plugin
        |nextDevVersion                // the next dev version for the M2 Plugin
        """.trimMargin()

    override fun numOfArgsNotOk(number: Int) = number != 13

    override fun execute(args: Array<out String>, errorHandler: ErrorHandler) {
        val (_, jenkinsBaseUrl, jenkinsUsername, jenkinsPassword, maxTriggerTries) = args
        val afterFirst5 = args.drop(5)
        val (pollExecutionEverySecond, maxWaitForExecutionInSeconds, pollReleaseEverySecond, maxReleaseTimeInSeconds) = afterFirst5
        val (parameters, jobName, releaseVersion, nextDevVersion) = afterFirst5.drop(4)

        Orchestrator.remoteRelease(
            jenkinsBaseUrl,
            jenkinsUsername, jenkinsPassword,
            maxTriggerTries.toInt(),
            pollExecutionEverySecond.toInt(),
            maxWaitForExecutionInSeconds.toInt(),
            pollReleaseEverySecond.toInt(),
            maxReleaseTimeInSeconds.toInt(),
            parametersToStringMap(parameters, errorHandler),
            jobName,
            releaseVersion,
            nextDevVersion
        )
    }

    private fun parametersToStringMap(parameters: String, errorHandler: ErrorHandler): Map<String, String> {
        return parameters
            .splitToSequence(";")
            .map { pair ->
                val index = checkParamNameNotEmpty(pair, errorHandler, parameters)
                pair.substring(0, index) to pair.substring(index + 1)
            }
            .associateBy({ it.first }, { it.second })
    }

    //TODO code duplication exists in Releaser
    private fun checkParamNameNotEmpty(pair: String, errorHandler: ErrorHandler, parameters: String): Int {
        val index = pair.indexOf('=')
        if (index < 1) {
            errorHandler.error("Parameter name requires at least one character.\nParameters: $parameters")
        }
        return index
    }
}
