package ch.loewenfels.depgraph.runner.console

import ch.loewenfels.depgraph.maven.getTestDirectory
import ch.loewenfels.depgraph.runner.commands.UpdateDependency
import ch.tutteli.atrium.copyPom
import ch.tutteli.spek.extensions.TempFolder
import java.io.File

class UpdateDependencySpec : CommandSpec(
    UpdateDependency,
    Companion::getNotEnoughArgs,
    Companion::getTooManyArgs,
    5..5
) {
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
