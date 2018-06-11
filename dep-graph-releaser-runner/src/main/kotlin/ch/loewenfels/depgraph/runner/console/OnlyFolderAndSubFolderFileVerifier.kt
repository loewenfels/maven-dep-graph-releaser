package ch.loewenfels.depgraph.runner.console

import java.io.File

object OnlyFolderAndSubFolderFileVerifier : FileVerifier {
    override fun file(path: String, fileDescription: String): File {
        require(!path.contains("..")) {
            "Using `..` in the path of the $fileDescription is prohibited due to security reasons." +
                "\nPath: $path"
        }
        val secureFile = File(path).absoluteFile
        require(secureFile.absolutePath.startsWith(File("").absolutePath)) {
            "$fileDescription is neither a relative path nor " +
                "an absolute path pointing to the same folder (or sub folder) where this command is executed." +
                "\nPath: $path"
        }
        return secureFile
    }
}
