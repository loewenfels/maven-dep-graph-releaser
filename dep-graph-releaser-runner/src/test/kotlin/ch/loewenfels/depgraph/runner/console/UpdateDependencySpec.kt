package ch.loewenfels.depgraph.runner.console

import ch.loewenfels.depgraph.maven.getTestDirectory
import ch.loewenfels.depgraph.runner.commands.UpdateDependency
import ch.tutteli.atrium.copyPom
import ch.tutteli.spek.extensions.TempFolder
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.include
import java.io.File

class UpdateDependencySpec : Spek({
    include(UpdateCommandSpec)

    //TODO write spec for non-existing pom
    //given("non-existing pom"){}

}) {
    object UpdateCommandSpec : CommandSpec(
        UpdateDependency,
        ::getNotEnoughArgs,
        ::getTooManyArgs,
        5..5
    )

    companion object {
        fun getNotEnoughArgs(tempFolder: TempFolder): Array<out String> {
            val pom = File(getTestDirectory("singleProject"), "pom.xml")
            val tmpPom = copyPom(tempFolder, pom)
            return arrayOf(
                UpdateDependency.name,
                tmpPom.absolutePath,
                "junit",
                "junit"
                //version is required as well
                //"4.12"
            )
        }

        fun getTooManyArgs(tempFolder: TempFolder): Array<out String> {
            val pom = File(getTestDirectory("singleProject"), "pom.xml")
            val tmpPom = copyPom(tempFolder, pom)
            return arrayOf(
                UpdateDependency.name,
                tmpPom.absolutePath,
                "junit",
                "junit",
                "4.12",
                "unexpectedAdditionalArg"
            )
        }
    }
}
