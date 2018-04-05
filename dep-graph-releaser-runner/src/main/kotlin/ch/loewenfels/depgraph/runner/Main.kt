package ch.loewenfels.depgraph.runner

import ch.loewenfels.depgraph.console.ErrorHandler
import ch.loewenfels.depgraph.console.dispatch
import java.io.File

object Main {
    fun main(vararg args: String?) {
        dispatch(args, errorHandler, listOf(Json, Html, UpdateDependency))
    }

    internal var errorHandler: ErrorHandler = object : ErrorHandler {
        override fun error(msg: String): Nothing {
            System.err.println(msg)
            System.exit(-1)
            throw RuntimeException("System.exit(-1) did not abort execution")
        }
    }
    internal var fileVerifier: FileVerifier = object : FileVerifier {
        override fun file(path: String, fileDescription: String): File {
            require(!path.contains("..")) {
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

    internal interface FileVerifier {
        fun file(path: String, fileDescription: String): File
    }
}


