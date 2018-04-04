package ch.tutteli.atrium

import ch.tutteli.atrium.api.cc.en_UK.toBe
import ch.tutteli.spek.extensions.TempFolder
import org.jetbrains.spek.api.dsl.TestContainer
import org.jetbrains.spek.api.dsl.it
import java.io.File

fun copyPom(tempFolder: TempFolder, pom: File): File {
    val tmpPom = tempFolder.newFile("pom.xml")
    tmpPom.writeBytes(pom.readBytes())
    return tmpPom
}

fun TestContainer.testSameContent(
    tempFolder: TempFolder,
    pom: File,
    update: (File) -> Unit
) {
    it("updates the dependency and file content is the same as before") {
        val tmpPom = copyPom(tempFolder, pom)
        update(tmpPom)
        assert(tmpPom.readText()).toBe(pom.readText())
    }
}

fun assertSameAsBeforeAfterReplace(tmpPom: File, pom: File, versionToReplace: String, newVersion: String) {
    val content = pom.readText()
    assert(tmpPom.readText()).toBe(content.replace(versionToReplace, newVersion))
}
