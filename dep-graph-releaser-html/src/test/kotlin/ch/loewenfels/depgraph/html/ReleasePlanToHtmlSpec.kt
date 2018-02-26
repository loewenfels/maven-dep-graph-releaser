package ch.loewenfels.depgraph.html

import ch.loewenfels.depgraph.data.CommandState
import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.ReleasePlan
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency
import ch.tutteli.atrium.api.cc.en_UK.contains
import ch.tutteli.atrium.assert
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.it
import java.io.File
import java.io.FileWriter

object ReleasePlanToHtmlSpec : Spek({

    val rootProjectId = MavenProjectId("com.example", "a")
    val rootProject = Project(rootProjectId, "1.1.0-SNAPSHOT", "1.2.0", 0, listOf(JenkinsMavenReleasePlugin(CommandState.Ready, "1.2.1-SNAPSHOT")))

    val projectWithDependentId = MavenProjectId("com.example", "b")
    val projectWithDependentUpdateDependency = JenkinsUpdateDependency(CommandState.Waiting(setOf(rootProjectId)), rootProjectId)
    val projectWithDependentJenkinsRelease = JenkinsMavenReleasePlugin(CommandState.Waiting(setOf(rootProjectId)), "3.1-SNAPSHOT")
    val projectWithDependentCommands = listOf(
        projectWithDependentUpdateDependency,
        projectWithDependentJenkinsRelease
    )
    val projectWithDependent = Project(projectWithDependentId, "2.0", "3.0", 1, projectWithDependentCommands)

    val projectWithoutDependentId = MavenProjectId("com.example", "c")
    val projectWithoutDependentUpdateDependency1 = JenkinsUpdateDependency(CommandState.Waiting(setOf(rootProjectId)), rootProjectId)
    val projectWithoutDependentUpdateDependency2 = JenkinsUpdateDependency(CommandState.Waiting(setOf(projectWithDependentId)), projectWithDependentId)
    val projectWithoutDependentJenkinsRelease = JenkinsMavenReleasePlugin(CommandState.Deactivated(CommandState.Waiting(setOf(projectWithDependentId, rootProjectId))), "4.2-SNAPSHOT")
    val projectWithoutDependentCommands = listOf(
        projectWithoutDependentUpdateDependency1,
        projectWithoutDependentUpdateDependency2,
        projectWithoutDependentJenkinsRelease
    )
    val projectWithoutDependent = Project(projectWithoutDependentId, "4.0", "4.1", 2, projectWithoutDependentCommands)

    val dependents = mapOf<ProjectId, Set<MavenProjectId>>(
        rootProjectId to setOf(projectWithDependentId),
        projectWithDependentId to setOf(projectWithoutDependentId),
        projectWithoutDependentId to setOf()
    )
    val releasePlan = ReleasePlan(rootProjectId,
        mapOf(
            rootProjectId to rootProject,
            projectWithDependentId to projectWithDependent,
            projectWithoutDependentId to projectWithoutDependent
        ),
        dependents
    )
    val testee = ReleasePlanToHtml()

    action("describe release plan to HTML smoke tests") {
        val result = testee.createHtml(releasePlan).toString()
        //TODO remove at some point, that's a hack to produce a html file to ease development (misuse of the test as build tool)
        FileWriter(File("test.html")).use { it.write(result) }

        it("contains all projects") {
            val keys = releasePlan.projects.keys.map { """title="${it.identifier}"""" }
            assert(result).contains(keys[0], *keys.subList(1, keys.size).toTypedArray())
        }
    }

})
