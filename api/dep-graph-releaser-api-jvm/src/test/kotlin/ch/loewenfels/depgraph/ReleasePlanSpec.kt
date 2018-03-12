package ch.loewenfels.depgraph

import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.ReleasePlan
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.tutteli.atrium.api.cc.en_UK.containsStrictly
import ch.tutteli.atrium.api.cc.en_UK.toBe
import ch.tutteli.atrium.assert
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it

object ReleasePlanSpec : Spek({
    val rootProjectId = MavenProjectId("com.example", "parent-parent")
    val rootProject = Project(rootProjectId, "1.1.0-SNAPSHOT", "1.2.0", 0, listOf())

    val parentId = MavenProjectId("com.example", "parent")
    val parent = Project(parentId, "1.1.0-SNAPSHOT", "1.2.0", 1, listOf())

    val projectInterfaceId = MavenProjectId("com.example", "interface")
    val projectInterface = Project(projectInterfaceId, "2.0", "3.0", 2, listOf())

    val projectAnnotationsId = MavenProjectId("com.example", "annotations")
    val projectAnnotations = Project(projectAnnotationsId, "4.0", "4.1", 2, listOf())

    val projectNotifierId = MavenProjectId("com.example", "notifier")
    val projectNotifier = Project(projectNotifierId, "5.0", "5.1", 3, listOf())

    val dependents = mapOf<ProjectId, Set<MavenProjectId>>(
        rootProjectId to setOf(parentId),
        parentId to setOf(projectInterfaceId, projectAnnotationsId, projectNotifierId),
        projectInterfaceId to setOf(projectAnnotationsId, projectNotifierId),
        projectAnnotationsId to setOf(projectNotifierId),
        projectNotifierId to setOf()
    )
    val releasePlan = ReleasePlan(
        rootProjectId,
        mapOf(
            rootProjectId to rootProject,
            parentId to parent,
            projectInterfaceId to projectInterface,
            projectAnnotationsId to projectAnnotations,
            projectNotifierId to projectNotifier
        ),
        dependents
    )

    describe("iterator") {

        it("returns the root project as first") {
            assert(releasePlan.iterator().next()).toBe(rootProject)
        }

        it("returns the projects in the order of their levels") {
            assert(releasePlan.iterator().asSequence().toList()).containsStrictly(
                rootProject,
                parent,
                projectInterface,
                projectAnnotations,
                projectNotifier
            )
        }
    }
})
