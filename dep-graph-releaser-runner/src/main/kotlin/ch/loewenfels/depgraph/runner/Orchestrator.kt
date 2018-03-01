package ch.loewenfels.depgraph.runner

import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.html.ReleasePlanToHtml
import ch.loewenfels.depgraph.maven.Analyser
import ch.loewenfels.depgraph.maven.JenkinsReleasePlanCreator
import ch.loewenfels.depgraph.maven.VersionDeterminer
import ch.loewenfels.depgraph.serialization.Serializer
import java.io.File
import java.util.*
import java.util.logging.Logger

object Orchestrator {
    private val logger = Logger.getLogger(Orchestrator::class.qualifiedName)
    private val releasePlaner = JenkinsReleasePlanCreator(VersionDeterminer())
    private val serializer = Serializer()
    private val toHtmlConverter = ReleasePlanToHtml()

    fun analyseAndCreateJson(directoryToAnalyse: File, outputFile: File, projectToRelease: MavenProjectId) {
        logger.info({ "Going to analyse: ${directoryToAnalyse.canonicalPath}" })
        val analyser = Analyser(directoryToAnalyse)
        logger.info({ "Analysed ${analyser.getNumberOfProjects()} projects." })

        logger.info("Going to create the release plan with $projectToRelease as root.")
        val rootProject = releasePlaner.create(projectToRelease, analyser)
        logger.info("Release plan created.")

        logger.info("Going to serialize the release plan to a json file.")
        if (outputFile.exists()) {
            logger.info("The resulting json file already exists, going to overwrite it.")
        }
        val json = serializer.serialize(rootProject)
        outputFile.writeText(json)
        logger.info({ "Created json file at: ${outputFile.canonicalPath}" })
    }

    fun createHtmlFromJson(inputJsonFile: File, outputDir: File) {
        logger.info({ "Going to deserialize the json file ${inputJsonFile.canonicalPath}" })
        val jsonString = Scanner(inputJsonFile, Charsets.UTF_8.name()).useDelimiter("\\Z").use { it.next() }
        val releasePlan = serializer.deserialize(jsonString)
        logger.info("Json deserialized")

        logger.info("Going to create the html pipeline file based on the release plan.")
        val html = toHtmlConverter.createHtml(releasePlan)
        val outputHtmlFile = File(outputDir, "pipeline.html")
        if (outputHtmlFile.exists()) {
            logger.info("The resulting html file already exists, going to overwrite it.")
        }
        outputHtmlFile.writeText(html.toString())
        logger.info({ "Created html file at: ${outputHtmlFile.canonicalPath}" })

        logger.info("Going to copy resource files")
        copyResourceToFile(outputDir, "kotlin.js")
        copyResourceToFile(outputDir, "dep-graph-releaser-js.js", "script.js")
        copyResourceToFile(outputDir, "style.css")
        logger.info("copied resources files")
        logger.info("Everything done :)")
    }

    private fun copyResourceToFile(outputDir: File, input: String, output: String = input) {
        val outputFile = File(outputDir, output)
        if (outputFile.exists()) {
            logger.info("The file $output already exists, going to overwrite it.")
        }
        this::class.java.getResourceAsStream("/$input").use { jsInput ->
            outputFile.outputStream().use { fileOut ->
                jsInput.copyTo(fileOut)
            }
        }
        logger.info("Created ${outputFile.canonicalPath}")
    }
}
