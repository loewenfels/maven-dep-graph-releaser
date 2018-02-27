package ch.loewenfels.depgraph.runner

import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.html.ReleasePlanToHtml
import ch.loewenfels.depgraph.maven.Analyser
import ch.loewenfels.depgraph.maven.JenkinsReleasePlanCreator
import ch.loewenfels.depgraph.maven.VersionDeterminer
import ch.loewenfels.depgraph.serialization.Serializer
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.*
import java.util.logging.Logger

object Orchestrator {
    private val logger = Logger.getLogger(Orchestrator::class.qualifiedName)
    private val releasePlaner = JenkinsReleasePlanCreator(VersionDeterminer())
    private val serializer = Serializer()
    private val toHtmlConverter = ReleasePlanToHtml()

    fun analyseAndCreateJson(directoryToAnalyse: File, outputFile: File, projectToRelease: MavenProjectId) {
        logger.info({"Going to analyse: ${directoryToAnalyse.canonicalPath}"})
        val analyser = Analyser(directoryToAnalyse)
        logger.info({"Analysed ${analyser.getNumberOfProjects()} projects."})

        logger.info("Going to create the release plan with $projectToRelease as root.")
        val rootProject = releasePlaner.create(projectToRelease, analyser)
        logger.info("Release plan created.")

        logger.info("Going to serialize the release plan to a json file.")
        val json = serializer.serialize(rootProject)
        writeToFile(outputFile, json)
        logger.info({"Created json file at: ${outputFile.canonicalPath}"})
    }

    fun createHtmlFromJson(inputJsonFile: File, outputHtmlFile: File) {
        logger.info({"Going to deserialize the json file ${inputJsonFile.canonicalPath}"})
        val jsonString = Scanner(inputJsonFile, Charsets.UTF_8.name()).useDelimiter("\\Z").use { it.next() }
        val releasePlan = serializer.deserialize(jsonString)
        logger.info("Json deserialized")

        logger.info("Going to create the html pipeline based on the release plan.")
        val html = toHtmlConverter.createHtml(releasePlan)
        writeToFile(outputHtmlFile, html.toString())
        logger.info({"Created html file at: ${outputHtmlFile.canonicalPath}"})
    }

    private fun writeToFile(outputFile: File, content: String) {
        OutputStreamWriter(FileOutputStream(outputFile), Charsets.UTF_8).use { it.write(content) }
    }

}
