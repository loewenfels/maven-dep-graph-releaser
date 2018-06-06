package ch.loewenfels.depgraph.runner

import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.manipulation.RegexBasedVersionUpdater
import ch.loewenfels.depgraph.maven.Analyser
import ch.loewenfels.depgraph.maven.JenkinsReleasePlanCreator
import ch.loewenfels.depgraph.maven.VersionDeterminer
import ch.loewenfels.depgraph.serialization.Serializer
import java.io.File
import java.util.logging.Logger

object Orchestrator {
    private val logger = Logger.getLogger(Orchestrator::class.qualifiedName)
    private val serializer = Serializer()

    fun analyseAndCreateJson(
        directoryToAnalyse: File,
        outputFile: File,
        projectToRelease: MavenProjectId,
        releasePlanCreatorOptions: JenkinsReleasePlanCreator.Options
    ) {
        logger.info({ "Going to analyse: ${directoryToAnalyse.absolutePath}" })
        val analyser = Analyser(directoryToAnalyse, Analyser.Options())
        logger.info({ "Analysed ${analyser.getNumberOfProjects()} projects." })

        logger.info("Going to create the release plan with ${projectToRelease.identifier} as root.")
        val releasePlaner = JenkinsReleasePlanCreator(VersionDeterminer(), releasePlanCreatorOptions)
        val releasePlan = releasePlaner.create(projectToRelease, analyser)
        logger.info("Release plan created.")

        logger.info("Going to serialize the release plan to a json file.")
        logIfFileExists(outputFile, "resulting json file")
        val json = serializer.serialize(releasePlan)
        outputFile.writeText(json)
        logger.info({ "Created json file at: ${outputFile.absolutePath}" })
    }

    fun printReleasableProjects(directoryToAnalyse: File) {
        logger.info({ "Going to analyse: ${directoryToAnalyse.absolutePath}" })
        val analyser = Analyser(directoryToAnalyse, Analyser.Options(false))
        logger.info({ "Analysed ${analyser.getNumberOfProjects()} projects." })
        val list = analyser.getAllReleasableProjects().sortedBy { it.artifactId }.joinToString("\n") {
            it.artifactId.padEnd(30, " -") + " groupId: " + it.groupId
        }
        println(list)
    }

    private fun CharSequence.padEnd(length: Int, padString: String): String {
        if (length <= this.length)
            return this.subSequence(0, this.length).toString()

        val sb = StringBuilder(length)
        sb.append(this)
        for (i in 1..(length - this.length) step padString.length)
            sb.append(padString)
        return sb.toString()
    }

    fun copyResources(outputDir: File) {
        logger.info("Going to copy resource files")
        copyResourceToFile(outputDir, "kotlin.js")
        copyResourceToFile(outputDir, "kotlinx-html-js.js")
        copyResourceToFile(outputDir, "kbox-js.js")
        copyResourceToFile(outputDir, "dep-graph-releaser-api-js.js")
        copyResourceToFile(outputDir, "dep-graph-releaser-maven-api-js.js")
        copyResourceToFile(outputDir, "dep-graph-releaser-gui.js")
        copyResourceToFile(outputDir, "style.css")
        copyResourceToFile(outputDir, "index.html")
        copyResourceToFile(outputDir, "material-icons.css")
        copyResourceToFile(outputDir, "MaterialIcons-Regular.ttf")
        copyResourceToFile(outputDir, "MaterialIcons-Regular.woff")
        copyResourceToFile(outputDir, "MaterialIcons-Regular.woff2")
        logger.info("copied resources files")
        logger.info("Everything done :)")
    }

    private fun copyResourceToFile(outputDir: File, input: String) {
        val outputFile = File(outputDir, input)
        logIfFileExists(outputFile, "file $input")
        val stream = this::class.java.getResourceAsStream("/$input")
        check(stream != null) {
            "Could not find /$input, please verify it is part of the classpath"
        }
        stream.use { inputStream ->
            outputFile.outputStream().use { fileOut ->
                inputStream.copyTo(fileOut)
            }
        }
        logger.fine({ "Created ${outputFile.absolutePath}" })
    }

    private fun logIfFileExists(file: File, fileDescription: String) {
        if (file.exists()) {
            logger.info("The $fileDescription already exists, going to overwrite it.")
        }
    }

    fun updateDependency(pom: File, groupId: String, artifactId: String, newVersion: String) {
        RegexBasedVersionUpdater.updateDependency(pom, groupId, artifactId, newVersion)
        logger.info("updated dependency $groupId:$artifactId to new version $newVersion")
    }

    fun listDependents(directoryToAnalyse: File, projectToAnalyse: MavenProjectId) {
        logger.info({ "Going to analyse: ${directoryToAnalyse.absolutePath}" })
        val analyser = Analyser(directoryToAnalyse, Analyser.Options())
        logger.info({ "Analysed ${analyser.getNumberOfProjects()} projects." })

        logger.info("Going to create the release plan with ${projectToAnalyse.identifier} as root.")
        val releasePlaner = JenkinsReleasePlanCreator(
            VersionDeterminer(), JenkinsReleasePlanCreator.Options("list", "^$")
        )
        val releasePlan = releasePlaner.create(projectToAnalyse, analyser)
        logger.info("Release plan created.")

        val list =  releasePlan.iterator()
            .asSequence()
            .filter { !it.isSubmodule }
            .joinToString("\n") { it.id.identifier }

        println("Following the dependent projects (incl. dependents of dependent projects etc. but without submodules) of ${projectToAnalyse.identifier}:" +
            "\n$list")
    }
}
