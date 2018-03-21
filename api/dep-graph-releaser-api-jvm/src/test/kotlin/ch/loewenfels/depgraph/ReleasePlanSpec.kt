package ch.loewenfels.depgraph

import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.ReleasePlan
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.tutteli.atrium.api.cc.en_UK.*
import ch.tutteli.atrium.assert
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it

object ReleasePlanSpec : Spek({

    describe("iterator") {

        given("root project with dependent parent project with three children each on a different level"){

            val rootProjectId = MavenProjectId("com.example", "parent-parent")
            val rootProject = Project(rootProjectId, false, "1.1.0-SNAPSHOT", "1.2.0", 0, listOf())

            val parentId = MavenProjectId("com.example", "parent")
            val parent = Project(parentId, false, "1.1.0-SNAPSHOT", "1.2.0", 1, listOf())

            val projectInterfaceId = MavenProjectId("com.example", "interface")
            val projectInterface = Project(projectInterfaceId, false, "2.0", "3.0", 2, listOf())

            val projectAnnotationsId = MavenProjectId("com.example", "annotations")
            val projectAnnotations = Project(projectAnnotationsId, false, "4.0", "4.1", 3, listOf())

            val projectNotifierId = MavenProjectId("com.example", "notifier")
            val projectNotifier = Project(projectNotifierId, false, "5.0", "5.1", 4, listOf())

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

        val multiModuleId = MavenProjectId("com.example", "multi")
        val multiModule = Project(multiModuleId, false, "1.1.0-SNAPSHOT", "1.2.0", 0, listOf())

        val submodule1Id = MavenProjectId("com.example", "sub1")
        val submodule = Project(submodule1Id, true, "1.1.0-SNAPSHOT", "1.2.0", 0, listOf())

        val submodule2Id = MavenProjectId("com.example", "sub2")
        val submodule2 = Project(submodule2Id, true, "2.0", "3.0", 0, listOf())



        given("multi module project with two submodules") {
            val dependents = mapOf<ProjectId, Set<MavenProjectId>>(
                multiModuleId to setOf(submodule1Id, submodule2Id),
                submodule1Id to setOf(submodule2Id),
                submodule2Id to setOf()
            )
            val releasePlan = ReleasePlan(
                multiModuleId,
                mapOf(
                    multiModuleId to multiModule,
                    submodule1Id to submodule,
                    submodule2Id to submodule2
                ),
                dependents
            )

            it("returns the root project as first") {
                assert(releasePlan.iterator().next()).toBe(multiModule)
            }

            it("returns the submodules in any order (but have to be returned even though they are on the same level as root)") {
                assert(releasePlan.iterator().asSequence().toList()).contains.inAnyOrder.only.objects(
                    multiModule,
                    submodule,
                    submodule2
                )
            }
        }

        given("multi module project with two submodules and one dependent") {
            val dependentId = MavenProjectId("com.example", "dependent")
            val dependent = Project(dependentId, false, "4.1.0-SNAPSHOT", "4.2.0", 1, listOf())

            val dependents = mapOf<ProjectId, Set<MavenProjectId>>(
                multiModuleId to setOf(dependentId, submodule1Id, submodule2Id),
                submodule1Id to setOf(submodule2Id, dependentId),
                submodule2Id to setOf(dependentId),
                dependentId to setOf()
            )
            val releasePlan = ReleasePlan(
                multiModuleId,
                mapOf(
                    multiModuleId to multiModule,
                    submodule1Id to submodule,
                    submodule2Id to submodule2,
                    dependentId to dependent
                ),
                dependents
            )

            it("returns the root project as first") {
                assert(releasePlan.iterator().next()).toBe(multiModule)
            }

            it("returns the submodules in any order (but have to be returned even though they are on the same level as root)") {
                assert(releasePlan.iterator().asSequence().toList()).contains.inAnyOrder.only.objects(
                    multiModule,
                    submodule,
                    submodule2,
                    dependent
                )
            }
        }
    }
})
