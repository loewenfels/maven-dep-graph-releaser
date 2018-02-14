package ch.loewenfels.depgraph.runner

import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.maven.Analyser
import ch.loewenfels.depgraph.maven.JenkinsReleasePlanCreator
import ch.loewenfels.depgraph.maven.VersionDeterminer
import ch.loewenfels.depgraph.serialization.Serializer
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

object Orchestrator {
    private val releasePlaner = JenkinsReleasePlanCreator(VersionDeterminer())
    private val serializer = Serializer()

    fun analyseAndCreateJson(directoryToAnalyse: File, outputFile: File, projectToRelease: MavenProjectId) {
        val analyser = Analyser(directoryToAnalyse)
        val rootProject = releasePlaner.create(projectToRelease, analyser)
        val json = serializer.serialize(rootProject)
        OutputStreamWriter(FileOutputStream(outputFile), Charsets.UTF_8).use { it.write(json) }
    }
}
