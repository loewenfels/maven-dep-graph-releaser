package ch.loewenfels.depgraph

import ch.loewenfels.depgraph.data.CommandState
import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.ReleasePlan
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency
import ch.tutteli.atrium.*
import ch.tutteli.atrium.api.cc.en_UK.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.*

object ManipulatorSpec : Spek({
    val rootProjectId = MavenProjectId("com.example", "a")
    val rootProject = Project(rootProjectId, "1.1.0-SNAPSHOT", "1.2.0", listOf())
    val projectWithDependentId = MavenProjectId("com.example", "b")
    val projectWithDependentCommands = listOf(
        JenkinsUpdateDependency(CommandState.Waiting(setOf(rootProjectId)), rootProjectId),
        JenkinsMavenReleasePlugin(CommandState.Waiting(setOf(rootProjectId)), "3.1-SNAPSHOT")
    )
    val projectWithDependent = Project(projectWithDependentId, "2.0", "3.0", projectWithDependentCommands)
    val projectWithoutDependentId = MavenProjectId("com.example", "c")
    val projectWithoutDependentCommands = listOf(
        JenkinsUpdateDependency(CommandState.Waiting(setOf(rootProjectId)), rootProjectId),
        JenkinsUpdateDependency(CommandState.Waiting(setOf(projectWithDependentId)), projectWithDependentId),
        JenkinsMavenReleasePlugin(CommandState.Waiting(setOf(projectWithDependentId, rootProjectId)), "4.2-SNAPSHOT")
    )
    val projectWithoutDependent = Project(projectWithoutDependentId, "4.0", "4.1", projectWithoutDependentCommands)

    val dependents = mapOf<ProjectId, Set<MavenProjectId>>(
        rootProjectId to setOf(projectWithDependentId),
        projectWithDependentId to setOf(projectWithoutDependentId),
        projectWithoutDependentId to setOf()
    )
    val testee = Manipulator(ReleasePlan(rootProjectId,
        mapOf(
            rootProjectId to rootProject,
            projectWithDependentId to projectWithDependent,
            projectWithoutDependentId to projectWithoutDependent
        ),
        dependents
    ))

    fun ActionBody.assertRootProjectVersionsAndDependentsUnchanged(newReleasePlan: ReleasePlan, rootProjectId: MavenProjectId, rootProject: Project) {
        test("rootProjectId is still the same") {
            assert(newReleasePlan.rootProjectId).toBe(rootProjectId)
        }
        test("rootProject is still the same instance") {
            assert(newReleasePlan.getProject(rootProjectId)).isSame(rootProject)
        }
        test("there are still 3 projects") {
            assert(newReleasePlan.projects).hasSize(3)
        }
        test("the dependents are unchanged, is still the same instance") {
            assert(newReleasePlan.dependents).isSame(dependents)
        }
        it("the project with dependent still has the same versions") {
            assert(newReleasePlan.getProject(projectWithDependentId)).hasSameVersionsAs(projectWithDependent)
        }
        it("the project without dependent still has the same versions") {
            assert(newReleasePlan.getProject(projectWithoutDependentId)).hasSameVersionsAs(projectWithoutDependent)
        }
    }

    describe("fun ${testee::deactivateProject.name}") {

        given("rootProject with dependent project with another dependent project") {

            on("deactivating project with dependent") {
                val newReleasePlan = testee.deactivateProject(projectWithDependentId)
                assertRootProjectVersionsAndDependentsUnchanged(newReleasePlan, rootProjectId, rootProject)

                it("deactivates the project with dependent (all commands)") {
                    val oldJenkinsUpdateDependency = projectWithDependentCommands[0] as JenkinsUpdateDependency
                    val oldJenkinsMavenReleasePlugin = projectWithDependentCommands[1] as JenkinsMavenReleasePlugin
                    assert(newReleasePlan.getProject(projectWithDependentId).commands).containsStrictly(
                        { isJenkinsUpdateDependencyDeactivated(oldJenkinsUpdateDependency) },
                        { isJenkinsMavenReleaseDeactivated(oldJenkinsMavenReleasePlugin) }
                    )
                }

                it("deactivates the dependent project (all commands)") {
                    val oldJenkinsUpdateDependency1 = projectWithoutDependentCommands[0] as JenkinsUpdateDependency
                    val oldJenkinsUpdateDependency2 = projectWithoutDependentCommands[1] as JenkinsUpdateDependency
                    val oldJenkinsMavenReleasePlugin = projectWithoutDependentCommands[2] as JenkinsMavenReleasePlugin
                    assert(newReleasePlan.getProject(projectWithoutDependentId).commands).containsStrictly(
                        { isJenkinsUpdateDependencyDeactivated(oldJenkinsUpdateDependency1) },
                        { isJenkinsUpdateDependencyDeactivated(oldJenkinsUpdateDependency2) },
                        { isJenkinsMavenReleaseDeactivated(oldJenkinsMavenReleasePlugin) }
                    )
                }
            }

            on("deactivating the project without dependents") {
                val newReleasePlan = testee.deactivateProject(projectWithoutDependentId)
                assertRootProjectVersionsAndDependentsUnchanged(newReleasePlan, rootProjectId, rootProject)

                test("project with dependent is still the same") {
                    assert(newReleasePlan.getProject(projectWithDependentId)).isSame(projectWithDependent)
                }

                it("deactivates the project without dependents (all commands)") {
                    val oldJenkinsUpdateDependency1 = projectWithoutDependentCommands[0] as JenkinsUpdateDependency
                    val oldJenkinsUpdateDependency2 = projectWithoutDependentCommands[1] as JenkinsUpdateDependency
                    val oldJenkinsMavenReleasePlugin = projectWithoutDependentCommands[2] as JenkinsMavenReleasePlugin
                    assert(newReleasePlan.getProject(projectWithoutDependentId).commands).containsStrictly(
                        { isJenkinsUpdateDependencyDeactivated(oldJenkinsUpdateDependency1) },
                        { isJenkinsUpdateDependencyDeactivated(oldJenkinsUpdateDependency2) },
                        { isJenkinsMavenReleaseDeactivated(oldJenkinsMavenReleasePlugin) }
                    )
                }
            }
        }

        describe("error cases") {
            given("id of the root project") {
                it("throws an IllegalArgumentException which contains rootProjectId") {
                    expect {
                        testee.deactivateProject(rootProjectId)
                    }.toThrow<IllegalArgumentException> { message { contains(rootProjectId.toString()) } }
                }
            }

            given("projectId which is not part of the analysis") {
                it("throws an IllegalArgumentException which contains projectId") {
                    val projectId = MavenProjectId("test", "one")
                    expect {
                        testee.deactivateProject(projectId)
                    }.toThrow<IllegalArgumentException> { message { contains(projectId.toString()) } }
                }
            }
        }
    }

})

