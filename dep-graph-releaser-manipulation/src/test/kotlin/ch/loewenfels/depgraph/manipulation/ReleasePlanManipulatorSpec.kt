package ch.loewenfels.depgraph.manipulation

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

object ReleasePlanManipulatorSpec : Spek({
    val rootProjectId = MavenProjectId("com.example", "a")
    val rootProject = Project(rootProjectId, false, "1.1.0-SNAPSHOT", "1.2.0", 0, listOf())

    val projectWithDependentId = MavenProjectId("com.example", "b")
    val projectWithDependentUpdateDependency = JenkinsUpdateDependency(CommandState.Waiting(setOf(rootProjectId)), rootProjectId)
    val projectWithDependentJenkinsRelease = JenkinsMavenReleasePlugin(CommandState.Waiting(setOf(rootProjectId)), "3.1-SNAPSHOT")
    val projectWithDependentCommands = listOf(
        projectWithDependentUpdateDependency,
        projectWithDependentJenkinsRelease
    )
    val projectWithDependent = Project(projectWithDependentId, false, "2.0", "3.0", 1, projectWithDependentCommands)

    val projectWithoutDependentId = MavenProjectId("com.example", "c")
    val projectWithoutDependentUpdateDependency1 = JenkinsUpdateDependency(CommandState.Waiting(setOf(rootProjectId)), rootProjectId)
    val projectWithoutDependentUpdateDependency2 = JenkinsUpdateDependency(CommandState.Waiting(setOf(projectWithDependentId)), projectWithDependentId)
    val projectWithoutDependentJenkinsRelease = JenkinsMavenReleasePlugin(CommandState.Deactivated(CommandState.Waiting(setOf(projectWithDependentId, rootProjectId))), "4.2-SNAPSHOT")
    val projectWithoutDependentCommands = listOf(
        projectWithoutDependentUpdateDependency1,
        projectWithoutDependentUpdateDependency2,
        projectWithoutDependentJenkinsRelease
    )
    val projectWithoutDependent = Project(projectWithoutDependentId, false, "4.0", "4.1", 2,  projectWithoutDependentCommands)

    val dependents = mapOf<ProjectId, Set<MavenProjectId>>(
        rootProjectId to setOf(projectWithDependentId),
        projectWithDependentId to setOf(projectWithoutDependentId),
        projectWithoutDependentId to setOf()
    )
    val testee = ReleasePlanManipulator(
        ReleasePlan(
            rootProjectId,
            mapOf(
                rootProjectId to rootProject,
                projectWithDependentId to projectWithDependent,
                projectWithoutDependentId to projectWithoutDependent
            ),
            mapOf(),
            dependents
        )
    )

    fun ActionBody.assertRootProjectVersionsAndDependentsUnchanged(newReleasePlan: ReleasePlan) {
        test("rootProjectId is still the same") {
            assert(newReleasePlan.rootProjectId).toBe(rootProjectId)
        }
        test("rootProject is still the same instance") {
            assert(newReleasePlan.getProject(rootProjectId)).isSame(rootProject)
        }
        test("there are still 3 projects") {
            assert(newReleasePlan.getNumberOfProjects()).toBe(3)
        }
        test("the dependents are unchanged, is still the same instance") {
            assert(newReleasePlan){
                returnValueOf(subject::getNumberOfDependents).toBe(dependents.size)
                dependents.forEach {
                    returnValueOf(subject::getDependents, it.key).isSame(it.value)
                }
            }
        }

        test("the project with dependent still has the same versions") {
            assert(newReleasePlan.getProject(projectWithDependentId)).hasSameVersionsAs(projectWithDependent)
        }
        test("the project with dependent still has the same level") {
            assert(newReleasePlan.getProject(projectWithDependentId).level).toBe(projectWithDependent.level)
        }

        test("the project without dependent still has the same versions") {
            assert(newReleasePlan.getProject(projectWithoutDependentId)).hasSameVersionsAs(projectWithoutDependent)
        }
        test("the project without dependent still has the same level") {
            assert(newReleasePlan.getProject(projectWithoutDependentId).level).toBe(projectWithoutDependent.level)
        }
    }

    fun ActionBody.assertProjectWithDependentStillSame(newReleasePlan: ReleasePlan) {
        test("project with dependent is still the same") {
            assert(newReleasePlan.getProject(projectWithDependentId)).isSame(projectWithDependent)
        }
    }

    fun SpecBody.errorCasesInvalidProjectId(act: (ProjectId) -> Unit) {
        given("id of the root project") {
            it("throws an IllegalArgumentException which contains rootProjectId") {
                expect {
                    act(rootProjectId)
                }.toThrow<IllegalArgumentException> { message { contains(rootProjectId.toString()) } }
            }
        }

        given("projectId which is not part of the analysis") {
            it("throws an IllegalArgumentException which contains projectId") {
                val projectId = MavenProjectId("test", "one")
                expect {
                    act(projectId)
                }.toThrow<IllegalArgumentException> { message { contains(projectId.toString()) } }
            }
        }
    }

    describe("fun ${testee::deactivateProject.name}") {

        describe("error cases") {
            errorCasesInvalidProjectId { projectId ->
                testee.deactivateProject(projectId)
            }
        }

        given("rootProject with dependent project with another dependent project") {

            on("deactivating project with dependent") {
                val newReleasePlan = testee.deactivateProject(projectWithDependentId)
                assertRootProjectVersionsAndDependentsUnchanged(newReleasePlan)

                it("deactivates the project with dependent (all commands)") {
                    assert(newReleasePlan.getProject(projectWithDependentId).commands).containsStrictly(
                        { isJenkinsUpdateDependencyDeactivated(projectWithDependentUpdateDependency) },
                        { isJenkinsMavenReleaseDeactivated(projectWithDependentJenkinsRelease) }
                    )
                }

                it("deactivates the dependent project (only the update commands, release is already deactivated)") {
                    assert(newReleasePlan.getProject(projectWithoutDependentId).commands).containsStrictly(
                        { isJenkinsUpdateDependencyDeactivated(projectWithoutDependentUpdateDependency1) },
                        { isJenkinsUpdateDependencyDeactivated(projectWithoutDependentUpdateDependency2) },
                        { isSame(projectWithoutDependentJenkinsRelease) }
                    )
                }
            }

            on("deactivating the project without dependents") {
                val newReleasePlan = testee.deactivateProject(projectWithoutDependentId)
                assertRootProjectVersionsAndDependentsUnchanged(newReleasePlan)
                assertProjectWithDependentStillSame(newReleasePlan)

                it("deactivates the project without dependents (only the update commands, release is already deactivated)") {
                    assert(newReleasePlan.getProject(projectWithoutDependentId).commands).containsStrictly(
                        { isJenkinsUpdateDependencyDeactivated(projectWithoutDependentUpdateDependency1) },
                        { isJenkinsUpdateDependencyDeactivated(projectWithoutDependentUpdateDependency2) },
                        { isSame(projectWithoutDependentJenkinsRelease) }
                    )
                }
            }
        }
    }

    describe("fun ${testee::deactivateCommand.name}") {

        describe("error cases") {
            errorCasesInvalidProjectId { projectId ->
                testee.deactivateCommand(projectId, 0)
            }

            given("index which is bigger than the number of commands the project has") {
                it("throws an IllegalArgumentException, containing the index and the projectId") {
                    expect {
                        testee.deactivateCommand(projectWithDependentId, 5)
                    }.toThrow<IllegalArgumentException> {
                        message { contains(5, projectWithDependentId.toString()) }
                    }
                }
            }

            given("deactivate already deactivated command") {
                it("throws an IllegalArgumentException, containing the index and the projectId") {
                    expect {
                        testee.deactivateCommand(projectWithoutDependentId, 2)
                    }.toThrow<IllegalArgumentException> {
                        message {
                            contains(
                                projectWithoutDependentJenkinsRelease.toString(),
                                projectWithoutDependentId.toString()
                            )
                        }
                    }
                }
            }
        }

        given("rootProject with dependent project with another dependent project") {

            on("deactivate first ${JenkinsUpdateDependency::class.simpleName} on project with dependent") {
                val newReleasePlan = testee.deactivateCommand(projectWithDependentId, 0)
                assertRootProjectVersionsAndDependentsUnchanged(newReleasePlan)

                it("has deactivated both commands (since second is release command)") {
                    assert(newReleasePlan.getProject(projectWithDependentId).commands).containsStrictly(
                        { isJenkinsUpdateDependencyDeactivated(projectWithDependentUpdateDependency) },
                        { isJenkinsMavenReleaseDeactivated(projectWithDependentJenkinsRelease) }
                    )
                }

                it("deactivates the project without dependents (only the update commands, release is already deactivated)") {
                    assert(newReleasePlan.getProject(projectWithoutDependentId).commands).containsStrictly(
                        { isJenkinsUpdateDependencyDeactivated(projectWithoutDependentUpdateDependency1) },
                        { isJenkinsUpdateDependencyDeactivated(projectWithoutDependentUpdateDependency2) },
                        { isSame(projectWithoutDependentJenkinsRelease) }
                    )
                }
            }

            on("deactivate first ${JenkinsUpdateDependency::class.simpleName} on project without dependent") {
                val newReleasePlan = testee.deactivateCommand(projectWithoutDependentId, 0)
                assertRootProjectVersionsAndDependentsUnchanged(newReleasePlan)
                assertProjectWithDependentStillSame(newReleasePlan)

                it("has deactivated only first update command") {
                    assert(newReleasePlan.getProject(projectWithoutDependentId).commands).containsStrictly(
                        { isJenkinsUpdateDependencyDeactivated(projectWithoutDependentUpdateDependency1) },
                        { isSame(projectWithoutDependentUpdateDependency2) },
                        { isSame(projectWithoutDependentJenkinsRelease) }
                    )
                }
            }
        }
    }

})

