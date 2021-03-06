package ch.loewenfels.depgraph.runner.console

import ch.loewenfels.depgraph.maven.getTestDirectory
import ch.loewenfels.depgraph.runner.commands.DependentProjects
import ch.tutteli.niok.absolutePathAsString
import ch.tutteli.spek.extensions.MemoizedTempFolder
import org.spekframework.spek2.Spek

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
        fun getNotEnoughArgs(@Suppress("UNUSED_PARAMETER") ignored: MemoizedTempFolder): Array<out String> {
            return arrayOf(
                DependentProjects.name, "com.example", "project",
                getTestDirectory("managingVersions/inDependency").absolutePathAsString
                //excludeRegex is required as well
                //"^(.*)$ "
            )
        }

        fun getTooManyArgs(@Suppress("UNUSED_PARAMETER") ignored: MemoizedTempFolder): Array<out String> {
            return arrayOf(
                DependentProjects.name, "com.example", "project",
                getTestDirectory("managingVersions/inDependency").absolutePathAsString,
                "^$",
                "${DependentProjects.FORMAT_ARG}list",
                "${DependentProjects.RELATIVE_PATH_TO_GIT_REPO_REGEX_ARG}^(.*)/$",
                "${DependentProjects.RELATIVE_PATH_TO_GIT_REPO_REPLACEMENT}https://github.com/$1",
                "${DependentProjects.PSF}./import.psf",
                "unexpectedAdditionalArg"
            )
        }
    }
}
