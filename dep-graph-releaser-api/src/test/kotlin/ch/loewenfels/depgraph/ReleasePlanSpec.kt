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
    val rootProjectId = MavenProjectId("com.example", "a")
    val rootProject = Project(rootProjectId, "1.1.0-SNAPSHOT", "1.2.0", 0, listOf())

    val projectWithDependentId = MavenProjectId("com.example", "b")
    val projectWithDependent = Project(projectWithDependentId, "2.0", "3.0", 1, listOf())

    val projectWithoutDependentId = MavenProjectId("com.example", "c")
    val projectWithoutDependent = Project(projectWithoutDependentId, "4.0", "4.1", 2, listOf())

    val dependents = mapOf<ProjectId, Set<MavenProjectId>>(
        rootProjectId to setOf(projectWithDependentId, projectWithoutDependentId),
        projectWithDependentId to setOf(projectWithoutDependentId),
        projectWithoutDependentId to setOf()
    )
    val releasePlan = ReleasePlan(
        rootProjectId,
        mapOf(
            rootProjectId to rootProject,
            projectWithDependentId to projectWithDependent,
            projectWithoutDependentId to projectWithoutDependent
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
                projectWithDependent,
                projectWithoutDependent
            )
        }

    }
})
