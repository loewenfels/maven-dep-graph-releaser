package ch.loewenfels.depgraph.maven

import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency
import ch.tutteli.atrium.*
import ch.tutteli.atrium.api.cc.en_UK.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.*
import java.io.File

object MavenFacadeSpec : Spek({
    val testee = MavenFacade()
    val singleProjectId = MavenProjectId("com.example", "example", "1.0-SNAPSHOT")
    val exampleAProjectId = MavenProjectId("com.example", "a", "1.1.1-SNAPSHOT")
    val exampleBProjectId = MavenProjectId("com.example", "b", "1.0.1-SNAPSHOT")

    fun getTestDirectory(name: String) = File(MavenFacadeSpec.javaClass.getResource("/$name/").path)

    fun ActionBody.testRootProjectOnlyReleaseAndReady(rootProject: Project, newVersion: String, nextDevVersion: String) {
        test("its ${Project::newVersion.name} is $newVersion"){
            assert(rootProject.newVersion).toBe(newVersion)
        }
        test("it contains just the ${JenkinsMavenReleasePlugin::class.simpleName} command, which is Ready with ${JenkinsMavenReleasePlugin::nextDevVersion.name} = $newVersion") {
            assert(rootProject) {
                property(subject::commands).containsStrictly({
                    isA<JenkinsMavenReleasePlugin> {
                        isStateReady()
                        property(subject::nextDevVersion).toBe(nextDevVersion)
                    }
                })
            }
        }
    }

    describe("validation errors") {
        given("not a ${MavenProjectId::class.simpleName}") {
            val errMsg = "Can only create a release plan for a maven project"
            it("throws an IllegalArgumentException, mentioning `$errMsg`") {
                expect {
                    val projectToRelease: ProjectId = object : ProjectId {
                        override val identifier = "bla"
                        override val version = "1.0"
                    }
                    testee.analyseAndCreateReleasePlan(projectToRelease, File("nonExistingProject/"))
                }.toThrow<IllegalArgumentException> { message { contains(errMsg) } }
            }
        }

        given("non existing directory") {
            val errMsg = "directory does not exists"
            it("throws an IllegalArgumentException, mentioning `$errMsg`") {
                expect {
                    testee.analyseAndCreateReleasePlan(singleProjectId, File("nonExistingProject/"))
                }.toThrow<IllegalArgumentException> { message { contains(errMsg) } }
            }
        }
        given("project to release not in directory") {
            val errMsg = "Can only release a project which is part of the analysis"
            it("throws an IllegalArgumentException, mentioning `$errMsg`") {
                val wrongProject = MavenProjectId("com.other", "notThatOne", "x.0")
                expect {
                    testee.analyseAndCreateReleasePlan(wrongProject, getTestDirectory("singleProject"))
                }.toThrow<IllegalArgumentException> {
                    message {
                        contains(errMsg, wrongProject.toString(), singleProjectId.toString())
                    }
                }
            }
        }
    }

    given("single project with third party dependencies") {
        describe(testee::analyseAndCreateReleasePlan.name) {
            action("the root project is the one we want to release") {
                val rootProject = testee.analyseAndCreateReleasePlan(singleProjectId, getTestDirectory("singleProject"))
                assert(rootProject.id).toBe(singleProjectId)

                testRootProjectOnlyReleaseAndReady(rootProject, "1.0", "1.1-SNAPSHOT")

                test("it does not have any dependent project") {
                    assert(rootProject.dependents).isEmpty()
                }
            }
        }
    }

    fun SpecBody.testProjectBWithDependencyToA(directory: String){
        describe(testee::analyseAndCreateReleasePlan.name) {
            action("the root project is the one we want to release") {
                val rootProject = testee.analyseAndCreateReleasePlan(exampleAProjectId, getTestDirectory(directory))
                assert(rootProject.id).toBe(exampleAProjectId)

                testRootProjectOnlyReleaseAndReady(rootProject, "1.1.1", "1.1.2-SNAPSHOT")

                test("it has one dependent project b") {
                    assert(rootProject.dependents).containsStrictly({
                        idAndNewVersion(exampleBProjectId, "1.0.1")
                    })
                }
                test("project b has two commands, updateVersion and Release") {
                    assert(rootProject.dependents[0].commands).containsStrictly(
                        {
                            isA<JenkinsUpdateDependency> {
                                stateWaitingWithDependencies(exampleAProjectId)
                                property(subject::projectId).toBe(exampleAProjectId)
                            }
                        },
                        { isA<JenkinsMavenReleasePlugin> { stateWaitingWithDependencies(exampleAProjectId) } }
                    )
                }
            }
        }
    }

    given("project with dependency incl. version") {
        testProjectBWithDependencyToA("projectWithDependency")
    }

    given("project with dependency and version in dependency management") {
        testProjectBWithDependencyToA("projectWithDependency")
    }

    given("project with parent dependency") {
        testProjectBWithDependencyToA("projectWithParent")
    }
})
