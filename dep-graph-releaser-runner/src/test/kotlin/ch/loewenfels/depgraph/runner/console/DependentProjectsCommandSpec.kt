package ch.loewenfels.depgraph.runner.console

import ch.loewenfels.depgraph.maven.getTestDirectory
import ch.loewenfels.depgraph.runner.commands.DependentProjects
import ch.tutteli.spek.extensions.TempFolder
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.include

class DependentProjectsSpec : Spek({
    include(DependentProjectsCommandSpec)

    //TODO write spec for non-existing pom and wrong format
    //given("non-existing dir"){}

}) {
    object DependentProjectsCommandSpec : CommandSpec(
        DependentProjects,
        ::getNotEnoughArgs,
        ::getTooManyArgs,
        5..9
    )

    companion object {
        fun getNotEnoughArgs(@Suppress("UNUSED_PARAMETER") tempFolder: TempFolder): Array<out String> {
            return arrayOf(
                DependentProjects.name, "com.example", "project",
                getTestDirectory("managingVersions/inDependency").absolutePath
                //excludeRegex is required as well
                //"^(.*)$ "
            )
        }

        fun getTooManyArgs(@Suppress("UNUSED_PARAMETER") tempFolder: TempFolder): Array<out String> {
            return arrayOf(
                DependentProjects.name, "com.example", "project",
                getTestDirectory("managingVersions/inDependency").absolutePath,
                "^$",
                "${DependentProjects.FORMAT}list",
                "${DependentProjects.TRANSFORM_REGEX}^(.*)/$",
                "${DependentProjects.TRANSFORM_REPLACEMENT}https://github.com/$1",
                "${DependentProjects.PSF}./import.psf",
                "unexpectedAdditionalArg"
            )
        }
    }
}
