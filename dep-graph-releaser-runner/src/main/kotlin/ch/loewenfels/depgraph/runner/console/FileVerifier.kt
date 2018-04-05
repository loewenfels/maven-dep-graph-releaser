package ch.loewenfels.depgraph.runner.console

import java.io.File

interface FileVerifier {
    fun file(path: String, fileDescription: String): File
}
