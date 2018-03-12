package ch.loewenfels.depgraph.runner

import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.maven.Analyser
import ch.loewenfels.depgraph.maven.JenkinsReleasePlanCreator
import ch.loewenfels.depgraph.maven.VersionDeterminer
import ch.loewenfels.depgraph.serialization.Serializer
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.logging.Logger

object Orchestrator {
    private val logger = Logger.getLogger(Orchestrator::class.qualifiedName)
    private val releasePlaner = JenkinsReleasePlanCreator(VersionDeterminer())
    private val serializer = Serializer()

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

    fun createHtmlFromJson(jsonUrl: String, outputDir: File) {
        logger.info("Going to copy resource files")
        copyResourceToFile(outputDir, "kotlin.js")
        copyResourceToFile(outputDir, "kotlinx-html-js.js")
        copyResourceToFile(outputDir, "dep-graph-releaser-api-js.js")
        copyResourceToFile(outputDir, "dep-graph-releaser-maven-api-js.js")
        copyResourceToFile(outputDir, "dep-graph-releaser-js.js")
        copyResourceToFile(outputDir, "style.css")
        copyResourceToFile(outputDir, "pipeline.html") { inputStream, fileOut ->
            val htmlTemplate = inputStream.bufferedReader().use { it.readText() }
            val html = htmlTemplate.replace("JSON_URL", jsonUrl)
            fileOut.write(html.toByteArray(Charsets.UTF_8))
        }
        logger.info("copied resources files")
        logger.info("Everything done :)")
    }

    private fun copyResourceToFile(outputDir: File, input: String) {
        return copyResourceToFile(outputDir, input) { inputStream, fileOut ->
            inputStream.copyTo(fileOut)
        }
    }

    private fun copyResourceToFile(outputDir: File, input: String, copier: (InputStream, FileOutputStream) -> Unit) {
        val outputFile = File(outputDir, input)
        if (outputFile.exists()) {
            logger.info("The file $input already exists, going to overwrite it.")
        }
        val stream = this::class.java.getResourceAsStream("/$input")
        check(stream != null) {
            "Could not find /$input, please verify it is part of the classpath"
        }
        stream.use { inputStream ->
            outputFile.outputStream().use { fileOut ->
                copier(inputStream, fileOut)
            }
        }
        logger.info("Created ${outputFile.canonicalPath}")
    }
}
