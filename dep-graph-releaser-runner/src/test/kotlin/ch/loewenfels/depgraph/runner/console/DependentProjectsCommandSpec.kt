package ch.loewenfels.depgraph.runner.console

import ch.loewenfels.depgraph.maven.getTestDirectory
import ch.loewenfels.depgraph.runner.commands.DependentProjects
import ch.tutteli.atrium.copyPom
import ch.tutteli.spek.extensions.TempFolder
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.include
import java.io.File

class DependentProjectsSpec : Spek({
    include(DependentProjectsCommandSpec)

    //TODO write spec for non-existing pom
    //given("non-existing dir"){}

}) {
    object DependentProjectsCommandSpec : CommandSpec(
        DependentProjects,
        ::getNotEnoughArgs,
        ::getTooManyArgs,
        4..5
    )

    companion object {
        fun getNotEnoughArgs(@Suppress("UNUSED_PARAMETER") tempFolder: TempFolder): Array<out String> {
            return arrayOf(
                DependentProjects.name, "com.example", "project"
                //dir is required as well
                //getTestDirectory("managingVersions/inDependency").absolutePath
            )
        }

        fun getTooManyArgs(@Suppress("UNUSED_PARAMETER") tempFolder: TempFolder): Array<out String> {
            return arrayOf(
                DependentProjects.name, "com.example", "project",
                getTestDirectory("managingVersions/inDependency").absolutePath,
                "${DependentProjects.FORMAT}list",
                "unexpectedAdditionalArg"
            )
        }
    }
}
