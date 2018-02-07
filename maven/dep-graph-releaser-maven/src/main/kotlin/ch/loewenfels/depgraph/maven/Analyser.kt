package ch.loewenfels.depgraph.maven

import fr.lteconsulting.pomexplorer.DefaultPomFileLoader
import fr.lteconsulting.pomexplorer.Log
import fr.lteconsulting.pomexplorer.PomAnalysis
import fr.lteconsulting.pomexplorer.Session
import java.io.File

class Analyser {

    fun analyse(directoryWithProjects: File): Session {
        require(directoryWithProjects.exists()){
            "Cannot analyse because the given directory does not exists: ${directoryWithProjects.absolutePath}"
        }
        val session = Session()
        val nullLogger = Log { }
        PomAnalysis.runFullRecursiveAnalysis(directoryWithProjects.absolutePath, session, DefaultPomFileLoader(session, true), null, false, nullLogger)
        return session
    }
}
