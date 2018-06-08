package ch.loewenfels.depgraph.runner.console

import ch.loewenfels.depgraph.maven.getTestDirectory
import ch.loewenfels.depgraph.runner.commands.Json
import ch.tutteli.spek.extensions.TempFolder
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.include
import java.io.File

class JsonSpec : Spek({
    include(JsonCommandSpec)

    //TODO write spec for wrong regex, non-existing directory etc.
    //given("non-existing directory"){}

}) {
    object JsonCommandSpec : CommandSpec(
        Json,
        ::getNotEnoughArgs,
        ::getTooManyArgs,
        8..11
    )

    companion object {
        fun getNotEnoughArgs(tempFolder: TempFolder): Array<out String> {
            val jsonFile = File(tempFolder.tmpDir, "test.json")
            return arrayOf(
                Json.name, "com.example", "a",
                getTestDirectory("managingVersions/inDependency").absolutePath,
                jsonFile.absolutePath,
                "dgr-updater",
                "^$#none"
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
                "^$#none",
                "dgr-dry-run",
                "${Json.REGEX_PARAMS_ARG}.*=branch.name=master",
                "${Json.DISABLE_RELEASE_FOR}ch.loewenfels.*",
                "${Json.JOB_MAPPING_ARG}com.example.project=ownJobName|com.example.anotherProject=another-project",
                "unexpectedAdditionalArg"
            )
        }
    }
}