package ch.loewenfels.depgraph.maven

import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency
import ch.tutteli.atrium.api.cc.en_UK.*
import ch.tutteli.atrium.assert
import ch.tutteli.atrium.expect
import ch.tutteli.atrium.isStateReady
import ch.tutteli.atrium.stateWaitingWithDependencies
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.ActionBody
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import java.io.File

object MavenFacadeSpec : Spek({
    val testee = MavenFacade()
    val singleProjectId = MavenProjectId("com.example", "example", "1.0-SNAPSHOT")
    val exampleAProjectId = MavenProjectId("com.example", "a", "1.1-SNAPSHOT")
    val exampleBProjectId = MavenProjectId("com.example", "b", "1.0.1-SNAPSHOT")

    fun getTestDirectory(name: String) = File(MavenFacadeSpec.javaClass.getResource("/$name/").path)

    fun ActionBody.testRootProjectOnlyReleaseAndReady(rootProject: Project, newVersion: String) {
        test("its ${Project::newVersion.name} is $newVersion"){
            assert(rootProject.newVersion).toBe(newVersion)
        }
        test("it contains just the ${JenkinsMavenReleasePlugin::class.simpleName} command, which is ready") {
            assert(rootProject) {
                property(subject::commands).containsStrictly({
                    isA<JenkinsMavenReleasePlugin> {
                        isStateReady()
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

                testRootProjectOnlyReleaseAndReady(rootProject, "1.0")

                test("it does not have any dependent project") {
                    assert(rootProject.dependents).isEmpty()
                }
            }
        }
    }

    given("project with dependency incl. version") {
        describe(testee::analyseAndCreateReleasePlan.name) {
            action("the root project is the one we want to release") {
                val rootProject = testee.analyseAndCreateReleasePlan(exampleAProjectId, getTestDirectory("projectWithDependency"))
                assert(rootProject.id).toBe(exampleAProjectId)

                testRootProjectOnlyReleaseAndReady(rootProject, "1.1")

                test("it has one dependent project b") {
                    assert(rootProject.dependents).containsStrictly({
                        property(subject::id).toBe(exampleBProjectId)
                    })
                }
                test("project b has two commands, updateVersion and Release") {
                    assert(rootProject.dependents[0].commands).containsStrictly(
                        { isA<JenkinsUpdateDependency> { stateWaitingWithDependencies(exampleAProjectId) } },
                        { isA<JenkinsMavenReleasePlugin> { stateWaitingWithDependencies(exampleAProjectId) } }
                    )
                }
            }
        }
    }
})
