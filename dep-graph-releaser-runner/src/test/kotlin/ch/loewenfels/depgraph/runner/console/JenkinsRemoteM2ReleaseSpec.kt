package ch.loewenfels.depgraph.runner.console

import ch.loewenfels.depgraph.runner.commands.JenkinsRemoteM2Release
import ch.tutteli.spek.extensions.TempFolder

class JenkinsRemoteM2ReleaseSpec : CommandSpec(
    JenkinsRemoteM2Release,
    Companion::getNotEnoughArgs,
    Companion::getTooManyArgs,
    13..13
) {
    companion object {
        fun getNotEnoughArgs(@Suppress("UNUSED_PARAMETER") tempFolder: TempFolder): Array<out String> {
            return arrayOf(
                JenkinsRemoteM2Release.name,
                "http://jenkins.example.com",
                "jenkinsUsername",
                "jenkinsPassword",
                "1",
                "5",
                "60",
                "1",
                "120",
                "a=b;c=d",
                "jobName",
                "releaseVersion"
                //nextDevVersion is required as well
                //"nextDevVersion"
            )
        }

        fun getTooManyArgs(@Suppress("UNUSED_PARAMETER") tempFolder: TempFolder): Array<out String> {
            return arrayOf(
                JenkinsRemoteM2Release.name,
                "http://jenkins.example.com",
                "jenkinsUsername",
                "jenkinsPassword",
                "1",
                "5",
                "60",
                "1",
                "120",
                "a=b;c=d",
                "jobName",
                "releaseVersion",
                "nextDevVersion",
                "unexpectedAdditionalArg"
            )
        }
    }
}
