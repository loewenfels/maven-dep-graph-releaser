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
        11..14
    )

    companion object {
        fun getNotEnoughArgs(tempFolder: TempFolder): Array<out String> {
            val jsonFile = File(tempFolder.tmpDir, "test.json")
            return arrayOf(
                Json.name, "com.example", "a",
                getTestDirectory("managingVersions/inDependency").absolutePath,
                jsonFile.absolutePath,
                "dgr-updater",
                "dgr-dry-run",
                "^$#none",
                "[^/]+/[^/]+/.+",
                "^(.*)/\$"
                //the RELATIVE_PATH_TO_GIT_REPO_REPLACEMENT is required as well
                //"https://github.com/$1"
            )
        }

        fun getTooManyArgs(tempFolder: TempFolder): Array<out String> {
            val jsonFile = File(tempFolder.tmpDir, "test.json")
            return arrayOf(
                Json.name, "com.example", "a",
                getTestDirectory("managingVersions/inDependency").absolutePath,
                jsonFile.absolutePath,
                "dgr-updater",
                "dgr-dry-run",
                "^$#none",
                "[^/]+/[^/]+/.+",
                "^(.*)/\$",
                "https://github.com/$1",
                "${Json.REGEX_PARAMS_ARG}.*=branch.name=master",
                "${Json.DISABLE_RELEASE_FOR}ch.loewenfels.*",
                "${Json.JOB_MAPPING_ARG}com.example.project=ownJobName|com.example.anotherProject=another-project",
                "unexpectedAdditionalArg"
            )
        }
    }
}
