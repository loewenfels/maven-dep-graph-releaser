package ch.loewenfels.depgraph.manipulation

import ch.loewenfels.depgraph.data.CommandState
import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.ReleasePlan
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency
import ch.tutteli.atrium.*
import ch.tutteli.atrium.api.cc.en_GB.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.*

object ReleasePlanManipulatorSpec : Spek({
    val rootProjectId = MavenProjectId("com.example", "root")
    val rootProject = Project(rootProjectId, false, "1.1.0-SNAPSHOT", "1.2.0", 0, listOf(), "")

    val multiModuleId = MavenProjectId("com.example", "multi-module")
    val multiModuleUpdateRootProject =
        JenkinsUpdateDependency(CommandState.Waiting(setOf(rootProjectId)), rootProjectId)
    val multiModuleJenkinsRelease =
        JenkinsMavenReleasePlugin(CommandState.Waiting(setOf(rootProjectId)), "3.1-SNAPSHOT")
    val multiModuleCommands = listOf(
        multiModuleUpdateRootProject,
        multiModuleJenkinsRelease
    )
    val multiModule = Project(multiModuleId, false, "2.0", "3.0", 1, multiModuleCommands, "")

    val submoduleId = MavenProjectId("com.example", "submodule")
    val submoduleUpdateRootProject =
        JenkinsUpdateDependency(CommandState.Waiting(setOf(rootProjectId)), rootProjectId)
    val submoduleUpdateMultiModule =
        JenkinsUpdateDependency(CommandState.Waiting(setOf(multiModuleId)), multiModuleId)
    val submoduleJenkinsRelease =
        JenkinsMavenReleasePlugin(CommandState.Waiting(setOf(multiModuleId, rootProjectId)), "3.1-SNAPSHOT")
    val submoduleCommands = listOf(
        submoduleUpdateRootProject,
        submoduleUpdateMultiModule,
        submoduleJenkinsRelease
    )
    val submodule = Project(submoduleId, false, "2.0", "3.0", 2, submoduleCommands, "")

    val projectWithoutDependentId = MavenProjectId("com.example", "project-without-dependent")
    val projectWithoutDependentUpdateRootProject =
        JenkinsUpdateDependency(CommandState.Waiting(setOf(rootProjectId)), rootProjectId)
    val projectWithoutDependentUpdateMultiModule =
        JenkinsUpdateDependency(
            CommandState.Deactivated(CommandState.Waiting(setOf(multiModuleId))),
            multiModuleId
        )
    val projectWithoutDependentUpdateSubmodule =
        JenkinsUpdateDependency(CommandState.Waiting(setOf(submoduleId)), submoduleId)
    val projectWithoutDependentJenkinsRelease = JenkinsMavenReleasePlugin(
        CommandState.Waiting(setOf(submoduleId, multiModuleId, rootProjectId)),
        "4.2-SNAPSHOT"
    )
    val projectWithoutDependentCommands = listOf(
        projectWithoutDependentUpdateRootProject,
        projectWithoutDependentUpdateMultiModule,
        projectWithoutDependentUpdateSubmodule,
        projectWithoutDependentJenkinsRelease
    )
    val projectWithoutDependent =
        Project(projectWithoutDependentId, false, "4.0", "4.1", 3, projectWithoutDependentCommands, "")

    val dependents = mapOf<ProjectId, Set<MavenProjectId>>(
        rootProjectId to setOf(multiModuleId),
        multiModuleId to setOf(submoduleId),
        submoduleId to setOf(projectWithoutDependentId),
        projectWithoutDependentId to setOf()
    )
    val testee = ReleasePlanManipulator(
        createReleasePlanWithDefaults(
            "releaseId",
            rootProjectId,
            mapOf(
                rootProjectId to rootProject,
                multiModuleId to multiModule,
                submoduleId to submodule,
                projectWithoutDependentId to projectWithoutDependent
            ),
            mapOf(),
            dependents
        )
    )

    fun TestContainer.assertRootProjectVersionsAndDependentsUnchanged(newReleasePlan: ReleasePlan) {
        test("rootProjectId is still the same") {
            assert(newReleasePlan.rootProjectId).toBe(rootProjectId)
        }
        test("rootProject is still the same instance") {
            assert(newReleasePlan.getProject(rootProjectId)).isSameAs(rootProject)
        }
        test("there are still 4 projects") {
            assert(newReleasePlan.getNumberOfProjects()).toBe(4)
        }
        test("the dependents are unchanged, is still the same instance") {
            assert(newReleasePlan) {
                returnValueOf(subject::getNumberOfDependents).toBe(dependents.size)
                dependents.forEach {
                    returnValueOf(subject::getDependents, it.key).isSameAs(it.value)
                }
            }
        }

        test("the project with dependent still has the same versions") {
            assert(newReleasePlan.getProject(multiModuleId)).hasSameVersionsAs(multiModule)
        }
        test("the project with dependent still has the same level") {
            assert(newReleasePlan.getProject(multiModuleId).level).toBe(multiModule.level)
        }

        test("the project without dependent still has the same versions") {
            assert(newReleasePlan.getProject(projectWithoutDependentId)).hasSameVersionsAs(projectWithoutDependent)
        }
        test("the project without dependent still has the same level") {
            assert(newReleasePlan.getProject(projectWithoutDependentId).level).toBe(projectWithoutDependent.level)
        }
    }

    fun TestContainer.assertMultiModuleStillSame(newReleasePlan: ReleasePlan) {
        test("multi module is still the same") {
            assert(newReleasePlan.getProject(multiModuleId)).isSameAs(multiModule)
        }
    }

    fun TestContainer.assertSubmoduleStillSame(newReleasePlan: ReleasePlan) {
        test("submodule is still the same") {
            assert(newReleasePlan.getProject(submoduleId)).isSameAs(submodule)
        }
    }

    fun SpecBody.errorCasesInvalidProjectId(action: String, act: (ProjectId) -> Unit) {
        given("id of the root project") {
            it("throws an IllegalArgumentException which contains rootProjectId") {
                expect {
                    act(rootProjectId)
                }.toThrow<IllegalArgumentException> {
                    message {
                        contains(
                            "$action the root project does not make sense",
                            rootProjectId.identifier
                        )
                    }
                }
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
            errorCasesInvalidProjectId("Deactivating") { projectId ->
                testee.deactivateProject(projectId)
            }
        }

        given("rootProject with dependent multi module with one submodule which has another dependent project") {

            on("deactivating the multi module") {
                val newReleasePlan = testee.deactivateProject(multiModuleId)
                assertRootProjectVersionsAndDependentsUnchanged(newReleasePlan)

                it("deactivates the multi module (all commands)") {
                    assert(newReleasePlan.getProject(multiModuleId).commands).containsStrictly(
                        { isJenkinsUpdateDependencyDeactivated(multiModuleUpdateRootProject) },
                        { isJenkinsMavenReleaseDeactivated(multiModuleJenkinsRelease) }
                    )
                }

                it("deactivates depending and release command of the submodule module") {
                    assert(newReleasePlan.getProject(submoduleId).commands).containsStrictly(
                        { isSameAs(submoduleUpdateRootProject) },
                        { isJenkinsUpdateDependencyDeactivated(submoduleUpdateMultiModule) },
                        { isJenkinsMavenReleaseDeactivated(submoduleJenkinsRelease) }
                    )
                }

                it("deactivates submodule depending command and the release command of the project without dependents (multi module depending command is already deactivated)") {
                    assert(newReleasePlan.getProject(projectWithoutDependentId).commands).containsStrictly(
                        { isSameAs(projectWithoutDependentUpdateRootProject) },
                        { isSameAs(projectWithoutDependentUpdateMultiModule) },
                        { isJenkinsUpdateDependencyDeactivated(projectWithoutDependentUpdateSubmodule) },
                        { isJenkinsMavenReleaseDeactivated(projectWithoutDependentJenkinsRelease) }
                    )
                }
            }

            on("deactivating the submodule") {
                val newReleasePlan = testee.deactivateProject(submoduleId)
                assertRootProjectVersionsAndDependentsUnchanged(newReleasePlan)
                assertMultiModuleStillSame(newReleasePlan)

                it("deactivates the submodule (all commands)") {
                    assert(newReleasePlan.getProject(submoduleId).commands).containsStrictly(
                        { isJenkinsUpdateDependencyDeactivated(submoduleUpdateRootProject) },
                        { isJenkinsUpdateDependencyDeactivated(submoduleUpdateMultiModule) },
                        { isJenkinsMavenReleaseDeactivated(submoduleJenkinsRelease) }
                    )
                }

                it("deactivates the depending and the release command of the dependent project") {
                    assert(newReleasePlan.getProject(projectWithoutDependentId).commands).containsStrictly(
                        { isSameAs(projectWithoutDependentUpdateRootProject) },
                        { isSameAs(projectWithoutDependentUpdateMultiModule) },
                        { isJenkinsUpdateDependencyDeactivated(projectWithoutDependentUpdateSubmodule) },
                        { isJenkinsMavenReleaseDeactivated(projectWithoutDependentJenkinsRelease) }
                    )
                }
            }

            on("deactivating the project without dependents") {
                val newReleasePlan = testee.deactivateProject(projectWithoutDependentId)
                assertRootProjectVersionsAndDependentsUnchanged(newReleasePlan)
                assertMultiModuleStillSame(newReleasePlan)
                assertSubmoduleStillSame(newReleasePlan)

                it("deactivates the project without dependents (first and third command, second was already deactivated)") {
                    assert(newReleasePlan.getProject(projectWithoutDependentId).commands).containsStrictly(
                        { isJenkinsUpdateDependencyDeactivated(projectWithoutDependentUpdateRootProject) },
                        { isSameAs(projectWithoutDependentUpdateMultiModule) },
                        { isJenkinsUpdateDependencyDeactivated(projectWithoutDependentUpdateSubmodule) },
                        { isJenkinsMavenReleaseDeactivated(projectWithoutDependentJenkinsRelease) }
                    )
                }
            }
        }
    }

    describe("fun ${testee::deactivateCommand.name}") {

        describe("error cases") {
            errorCasesInvalidProjectId("Deactivating a command of") { projectId ->
                testee.deactivateCommand(projectId, 0)
            }

            given("index which is bigger than the number of commands the project has") {
                it("throws an IllegalArgumentException, containing the index and the projectId") {
                    expect {
                        testee.deactivateCommand(multiModuleId, 5)
                    }.toThrow<IllegalArgumentException> {
                        message { contains(5, multiModuleId.toString()) }
                    }
                }
            }

            given("deactivate already deactivated command") {
                it("throws an IllegalArgumentException, containing the index and the projectId") {
                    expect {
                        testee.deactivateCommand(projectWithoutDependentId, 1)
                    }.toThrow<IllegalArgumentException> {
                        message {
                            contains(
                                projectWithoutDependentUpdateMultiModule.toString(),
                                projectWithoutDependentId.toString()
                            )
                        }
                    }
                }
            }
        }

        given("rootProject with dependent multi module with one submodule which has another dependent project") {

            on("deactivate ${JenkinsUpdateDependency::class.simpleName} of multi module") {
                val newReleasePlan = testee.deactivateCommand(multiModuleId, 0)
                assertRootProjectVersionsAndDependentsUnchanged(newReleasePlan)

                it("has deactivated both commands of the multi module (since second is release command)") {
                    assert(newReleasePlan.getProject(multiModuleId).commands).containsStrictly(
                        { isJenkinsUpdateDependencyDeactivated(multiModuleUpdateRootProject) },
                        { isJenkinsMavenReleaseDeactivated(multiModuleJenkinsRelease) }
                    )
                }

                it("deactivates depending command and release command of the submodule ") {
                    assert(newReleasePlan.getProject(submoduleId).commands).containsStrictly(
                        { isSameAs(submoduleUpdateRootProject) },
                        { isJenkinsUpdateDependencyDeactivated(submoduleUpdateMultiModule) },
                        { isJenkinsMavenReleaseDeactivated(submoduleJenkinsRelease) }
                    )
                }

                it("deactivates submodule depending command and release command of the project without dependents (multi module depending command was already deactivated)") {
                    assert(newReleasePlan.getProject(projectWithoutDependentId).commands).containsStrictly(
                        { isSameAs(projectWithoutDependentUpdateRootProject) },
                        { isSameAs(projectWithoutDependentUpdateMultiModule) },
                        { isJenkinsUpdateDependencyDeactivated(projectWithoutDependentUpdateSubmodule) },
                        { isJenkinsMavenReleaseDeactivated(projectWithoutDependentJenkinsRelease) }
                    )
                }
            }

            on("deactivate first ${JenkinsUpdateDependency::class.simpleName} of submodule") {
                val newReleasePlan = testee.deactivateCommand(submoduleId, 0)
                assertRootProjectVersionsAndDependentsUnchanged(newReleasePlan)
                assertMultiModuleStillSame(newReleasePlan)

                it("has deactivated first command and release command of the submodule") {
                    assert(newReleasePlan.getProject(submoduleId).commands).containsStrictly(
                        { isJenkinsUpdateDependencyDeactivated(submoduleUpdateRootProject) },
                        { isSameAs(submoduleUpdateMultiModule) },
                        { isJenkinsMavenReleaseDeactivated(submoduleJenkinsRelease) }
                    )
                }

                it("deactivates the depending and the release command of the project without dependents") {
                    assert(newReleasePlan.getProject(projectWithoutDependentId).commands).containsStrictly(
                        { isSameAs(projectWithoutDependentUpdateRootProject) },
                        { isSameAs(projectWithoutDependentUpdateMultiModule) },
                        { isJenkinsUpdateDependencyDeactivated(projectWithoutDependentUpdateSubmodule) },
                        { isJenkinsMavenReleaseDeactivated(projectWithoutDependentJenkinsRelease) }
                    )
                }
            }

            on("deactivate first ${JenkinsUpdateDependency::class.simpleName} on project without dependent") {
                val newReleasePlan = testee.deactivateCommand(projectWithoutDependentId, 0)
                assertRootProjectVersionsAndDependentsUnchanged(newReleasePlan)
                assertMultiModuleStillSame(newReleasePlan)
                assertSubmoduleStillSame(newReleasePlan)

                it("has deactivated first update command and release command") {
                    assert(newReleasePlan.getProject(projectWithoutDependentId).commands).containsStrictly(
                        { isJenkinsUpdateDependencyDeactivated(projectWithoutDependentUpdateRootProject) },
                        { isSameAs(projectWithoutDependentUpdateMultiModule) },
                        { isSameAs(projectWithoutDependentUpdateSubmodule) },
                        { isJenkinsMavenReleaseDeactivated(projectWithoutDependentJenkinsRelease) }
                    )
                }
            }
        }
    }

    describe("fun ${testee::disableCommand.name}") {

        describe("error cases") {
            describe("error cases") {
                errorCasesInvalidProjectId("Disabling a command of") { projectId ->
                    testee.disableCommand(projectId, 0)
                }

                given("index which is bigger than the number of commands the project has") {
                    it("throws an IllegalArgumentException, containing the index and the projectId") {
                        expect {
                            testee.disableCommand(multiModuleId, 5)
                        }.toThrow<IllegalArgumentException> {
                            message { contains(5, multiModuleId.toString()) }
                        }
                    }
                }

                given("disable already disable command") {
                    it("throws an IllegalArgumentException, containing the index and the projectId") {
                        val newReleasePlan = testee.disableCommand(projectWithoutDependentId, 2)
                        val oldReleaseCommand = newReleasePlan.getProject(projectWithoutDependentId).commands[2]
                        val tmpTestee = ReleasePlanManipulator(newReleasePlan)
                        expect {
                            tmpTestee.disableCommand(projectWithoutDependentId, 2)
                        }.toThrow<IllegalArgumentException> {
                            message {
                                contains(
                                    oldReleaseCommand.toString(),
                                    projectWithoutDependentId.toString()
                                )
                            }
                        }
                    }
                }
            }

        }

        given("rootProject with dependent multi module with one submodule which has another dependent project") {

            on("disable ${JenkinsUpdateDependency::class.simpleName} of multi module") {
                val newReleasePlan = testee.disableCommand(multiModuleId, 0)
                assertRootProjectVersionsAndDependentsUnchanged(newReleasePlan)

                it("disables both commands of the multi module (since second is release command)") {
                    assert(newReleasePlan.getProject(multiModuleId).commands).containsStrictly(
                        { isJenkinsUpdateDependencyDisabled(multiModuleUpdateRootProject) },
                        { isJenkinsMavenReleaseDisabled(multiModuleJenkinsRelease.nextDevVersion) }
                    )
                }

                it("deactivates depending command and release command of the submodule ") {
                    assert(newReleasePlan.getProject(submoduleId).commands).containsStrictly(
                        { isSameAs(submoduleUpdateRootProject) },
                        { isJenkinsUpdateDependencyDeactivated(submoduleUpdateMultiModule) },
                        { isJenkinsMavenReleaseDeactivated(submoduleJenkinsRelease) }
                    )
                }

                it("deactivates submodule depending command and the release command of the project without dependents (multi module depending command is already deactivated)") {
                    assert(newReleasePlan.getProject(projectWithoutDependentId).commands).containsStrictly(
                        { isSameAs(projectWithoutDependentUpdateRootProject) },
                        { isSameAs(projectWithoutDependentUpdateMultiModule) },
                        { isJenkinsUpdateDependencyDeactivated(projectWithoutDependentUpdateSubmodule) },
                        { isJenkinsMavenReleaseDeactivated(projectWithoutDependentJenkinsRelease) }
                    )
                }
            }

            on("disable first ${JenkinsUpdateDependency::class.simpleName} of submodule") {
                val newReleasePlan = testee.disableCommand(submoduleId, 0)
                assertRootProjectVersionsAndDependentsUnchanged(newReleasePlan)
                assertMultiModuleStillSame(newReleasePlan)

                it("disables first command and release command of the submodule") {
                    assert(newReleasePlan.getProject(submoduleId).commands).containsStrictly(
                        { isJenkinsUpdateDependencyDisabled(submoduleUpdateRootProject) },
                        { isSameAs(submoduleUpdateMultiModule) },
                        { isJenkinsMavenReleaseDisabled(submoduleJenkinsRelease.nextDevVersion) }
                    )
                }

                it("deactivates the depending and the release command of the project without dependents") {
                    assert(newReleasePlan.getProject(projectWithoutDependentId).commands).containsStrictly(
                        { isSameAs(projectWithoutDependentUpdateRootProject) },
                        { isSameAs(projectWithoutDependentUpdateMultiModule) },
                        { isJenkinsUpdateDependencyDeactivated(projectWithoutDependentUpdateSubmodule) },
                        { isJenkinsMavenReleaseDeactivated(projectWithoutDependentJenkinsRelease) }
                    )
                }
            }

            on("disable first ${JenkinsUpdateDependency::class.simpleName} on project without dependent") {
                val newReleasePlan = testee.disableCommand(projectWithoutDependentId, 0)
                assertRootProjectVersionsAndDependentsUnchanged(newReleasePlan)
                assertMultiModuleStillSame(newReleasePlan)
                assertSubmoduleStillSame(newReleasePlan)

                it("disables first update command and release command") {
                    assert(newReleasePlan.getProject(projectWithoutDependentId).commands).containsStrictly(
                        { isJenkinsUpdateDependencyDisabled(projectWithoutDependentUpdateRootProject) },
                        { isSameAs(projectWithoutDependentUpdateMultiModule) },
                        { isSameAs(projectWithoutDependentUpdateSubmodule) },
                        { isJenkinsMavenReleaseDisabled(projectWithoutDependentJenkinsRelease.nextDevVersion) }
                    )
                }
            }
        }
    }
})

