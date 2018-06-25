package ch.loewenfels.depgraph

import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.ReleasePlan
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.tutteli.atrium.api.cc.en_GB.*
import ch.tutteli.atrium.assert
import ch.tutteli.atrium.iteratorReturnsRootAndInOrderGrouped
import ch.tutteli.atrium.iteratorReturnsRootAndStrictly
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import ch.tutteli.atrium.createReleasePlanWithDefaults

object ReleasePlanSpec : Spek({

    fun createProject(
        id: ProjectId,
        isSubmodule: Boolean,
        currentVersion: String,
        releaseVersion: String, level: Int
    ) = Project(id, isSubmodule, currentVersion, releaseVersion, level, listOf(), "")

    describe("iterator") {

        given("root project with dependent parent project with three children each on a different level"){

            val rootProjectId = MavenProjectId("com.example", "parent-parent")
            val rootProject = createProject(rootProjectId, false, "1.1.0-SNAPSHOT", "1.2.0", 0)

            val parentId = MavenProjectId("com.example", "parent")
            val parent = createProject(parentId, false, "1.1.0-SNAPSHOT", "1.2.0", 1)

            val projectInterfaceId = MavenProjectId("com.example", "interface")
            val projectInterface = createProject(projectInterfaceId, false, "2.0", "3.0", 2)

            val projectAnnotationsId = MavenProjectId("com.example", "annotations")
            val projectAnnotations = createProject(projectAnnotationsId, false, "4.0", "4.1", 3)

            val projectNotifierId = MavenProjectId("com.example", "notifier")
            val projectNotifier = createProject(projectNotifierId, false, "5.0", "5.1", 4)

            val dependents = mapOf<ProjectId, Set<MavenProjectId>>(
                rootProjectId to setOf(parentId),
                parentId to setOf(projectInterfaceId, projectAnnotationsId, projectNotifierId),
                projectInterfaceId to setOf(projectAnnotationsId, projectNotifierId),
                projectAnnotationsId to setOf(projectNotifierId),
                projectNotifierId to setOf()
            )
            val releasePlan = createReleasePlanWithDefaults(
                "releaseId",
                rootProjectId,
                mapOf(
                    rootProjectId to rootProject,
                    parentId to parent,
                    projectInterfaceId to projectInterface,
                    projectAnnotationsId to projectAnnotations,
                    projectNotifierId to projectNotifier
                ),
                mapOf(),
                dependents
            )

            it("returns the root project as first") {
                assert(releasePlan.iterator().next()).toBe(rootProject)
            }

            it("returns the projects in the order of their levels") {
                assert(releasePlan).iteratorReturnsRootAndStrictly(
                    parent.id,
                    projectInterface.id,
                    projectAnnotations.id,
                    projectNotifier.id
                )
            }
        }

        val multiModuleId = MavenProjectId("com.example", "multi")
        val multiModule = createProject(multiModuleId, false, "1.1.0-SNAPSHOT", "1.2.0", 0)

        val submodule1Id = MavenProjectId("com.example", "sub1")
        val submodule = createProject(submodule1Id, true, "1.1.0-SNAPSHOT", "1.2.0", 0)

        val submodule2Id = MavenProjectId("com.example", "sub2")
        val submodule2 = createProject(submodule2Id, true, "2.0", "3.0", 0)



        given("multi module project with two submodules") {
            val dependents = mapOf<ProjectId, Set<MavenProjectId>>(
                multiModuleId to setOf(submodule1Id, submodule2Id),
                submodule1Id to setOf(submodule2Id),
                submodule2Id to setOf()
            )
            val releasePlan = createReleasePlanWithDefaults(
                "releaseId",
                multiModuleId,
                mapOf(
                    multiModuleId to multiModule,
                    submodule1Id to submodule,
                    submodule2Id to submodule2
                ),
                mapOf(multiModuleId to setOf(submodule1Id, submodule2Id)),
                dependents
            )

            it("returns the root project as first") {
                assert(releasePlan.iterator().next()).toBe(multiModule)
            }

            it("returns the submodules in any order (but have to be returned even though they are on the same level as root)") {
                assert(releasePlan).iteratorReturnsRootAndInOrderGrouped(listOf(submodule.id, submodule2.id))
            }
        }

        given("multi module project with two submodules and one dependent") {
            val dependentId = MavenProjectId("com.example", "dependent")
            val dependent = createProject(dependentId, false, "4.1.0-SNAPSHOT", "4.2.0", 1)

            val dependents = mapOf<ProjectId, Set<MavenProjectId>>(
                multiModuleId to setOf(dependentId, submodule1Id, submodule2Id),
                submodule1Id to setOf(submodule2Id, dependentId),
                submodule2Id to setOf(dependentId),
                dependentId to setOf()
            )
            val releasePlan = createReleasePlanWithDefaults(
                "releaseId",
                multiModuleId,
                mapOf(
                    multiModuleId to multiModule,
                    submodule1Id to submodule,
                    submodule2Id to submodule2,
                    dependentId to dependent
                ),
                mapOf(multiModuleId to setOf(submodule1Id, submodule2Id)),
                dependents
            )

            it("returns the root project as first") {
                assert(releasePlan.iterator().next()).toBe(multiModule)
            }

            it("returns the submodules in any order (but have to be returned even though they are on the same level as root)") {
                assert(releasePlan).iteratorReturnsRootAndInOrderGrouped(
                    listOf(submodule.id, submodule2.id, dependent.id)
                )
            }
        }
    }
})
