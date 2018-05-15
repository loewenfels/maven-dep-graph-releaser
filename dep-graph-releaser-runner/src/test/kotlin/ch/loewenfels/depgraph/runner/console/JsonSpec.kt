package ch.loewenfels.depgraph.runner.console

import ch.loewenfels.depgraph.maven.getTestDirectory
import ch.loewenfels.depgraph.runner.commands.Json
import ch.tutteli.spek.extensions.TempFolder
import java.io.File

class JsonSpec : CommandSpec(
    Json,
    Companion::getNotEnoughArgs,
    Companion::getTooManyArgs,
    9..12
) {
    companion object {
        fun getNotEnoughArgs(tempFolder: TempFolder): Array<out String> {
            val jsonFile = File(tempFolder.tmpDir, "test.json")
            return arrayOf(
                Json.name, "com.example", "a",
                getTestDirectory("managingVersions/inDependency").absolutePath,
                jsonFile.absolutePath,
                "dgr-updater",
                ".*",
                "dgr-remote-releaser"
                //the DRY_RUN_JOB is required as well
                //"dgr-dry-run",
            )
        }

        fun getTooManyArgs(tempFolder: TempFolder): Array<out String> {
            val jsonFile = File(tempFolder.tmpDir, "test.json")
            return arrayOf(
                Json.name, "com.example", "a",
                getTestDirectory("managingVersions/inDependency").absolutePath,
                jsonFile.absolutePath,
                "dgr-updater",
                ".*",
                "dgr-remote-releaser",
                "dgr-dry-run",
                "${Json.REGEX_PARAMS_ARG}.*=branch=master",
                "${Json.DISABLE_RELEASE_FOR}ch.loewenfels.*",
                "${Json.JOB_MAPPING_ARG}com.example.project=ownJobName|com.example.anotherProject=another-project",
                "unexpectedAdditionalArg"
            )
        }
    }
}
