package ch.loewenfels.depgraph.runner.commands

import ch.loewenfels.depgraph.runner.Orchestrator
import ch.loewenfels.depgraph.runner.console.ErrorHandler


object JenkinsRemoteM2Release : ConsoleCommand {

    override val name = "remoteRelease"
    override val description = "releases a project by triggering the m2 release plugin on a remote jenkins."
    override val example =
        "./remoteRelease https://example.com/jenkins user pass 3 300 10 branch.name=master myJob 1.0 1.1-SNAPSHOT"
    override val arguments = """
        |$name requires the following arguments in the given order:
        |jenkinsBaseUrl           // e.g. https://mydomain.org/jenkins
        |jenkinsUsername          // username used for Basic Auth
        |jenkinsPassword          // password used for Basic Auth
        |maxTriggerTries          // max. attempts of triggering the release
        |maxReleaseTimeInSeconds  // max. time to wait for the release to complete
        |pollEverySecond          // poll completion every x. second
        |parameters               // additional parameters in the form a=b;c=d
        |jobName                  // the name of the job to trigger
        |releaseVersion           // the release version for the M2 Plugin
        |nextDevVersion           // the next dev version for the M2 Plugin
        """.trimMargin()

    override fun numOfArgsNotOk(number: Int) = number != 11

    override fun execute(args: Array<out String>, errorHandler: ErrorHandler) {
        val first5Args = args.take(5)
        val afterFirst5 = args.drop(5)
        val (_, jenkinsBaseUrl, jenkinsUsername, jenkinsPassword, maxTriggerTries) = first5Args
        val (maxReleaseTimeInSeconds, pollEverySecond, parameters, jobName, releaseVersion) = afterFirst5
        val nextDevVersion = afterFirst5.last()

        Orchestrator.remoteRelease(
            jenkinsBaseUrl,
            jenkinsUsername, jenkinsPassword,
            maxTriggerTries.toInt(),
            maxReleaseTimeInSeconds.toInt(),
            pollEverySecond.toInt(),
            parametersToStringMap(parameters, errorHandler),
            jobName,
            releaseVersion,
            nextDevVersion
        )
    }

    private fun parametersToStringMap(
        parameters: String,
        errorHandler: ErrorHandler
    ): Map<String, String> {
        return parameters
            .splitToSequence(";")
            .map { pair ->
                val index = pair.indexOf('=')
                if (index < 1) {
                    errorHandler.error("Property name requires at least one character.\nParameters: $parameters")
                }
                pair.substring(0, index) to pair.substring(index + 1)
            }
            .associateBy({ it.first }, { it.second })
    }
}
