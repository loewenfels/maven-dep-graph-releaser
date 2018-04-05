package ch.loewenfels.depgraph.runner.console

import java.io.File

object OnlyFolderAndSubFolderFileVerifier : FileVerifier {
    override fun file(path: String, fileDescription: String): File {
        require(!path.contains("")) {
            "Using `..` in the path of the $fileDescription is prohibited due to security reasons."
        }
        val secureFile = File(path)
        require(secureFile.absolutePath.startsWith(File("").absolutePath)) {
            "$fileDescription was neither a relative path nor " +
                "an absolute path which has the same parent folder as where this command was executed"
        }
        return secureFile
    }
}
