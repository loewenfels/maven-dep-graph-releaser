package ch.loewenfels.depgraph.maven

import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.ReleasePlan
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMavenReleasePlugin
import ch.tutteli.atrium.api.cc.en_UK.*
import ch.tutteli.atrium.expect
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import java.io.File

object MavenFacadeSpec : Spek({
    val testee = MavenFacade()
    val projectId = MavenProjectId("com.example", "example", "1.0-SNAPSHOT")

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
                    testee.analyseAndCreateReleasePlan(projectId, File("nonExistingProject/"))
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
                        contains(errMsg, wrongProject.toString(), "${projectId.groupId}:${projectId.artifactId}:${projectId.version}")
                    }
                }
            }
        }
    }

    given("single project with third party dependencies") {
        group("on ${testee::analyseAndCreateReleasePlan.name}") {
            val releasePlan = testee.analyseAndCreateReleasePlan(projectId, getTestDirectory("singleProject"))
            group("the ${ReleasePlan::class.simpleName} contains only the project") {
                ch.tutteli.atrium.assert(releasePlan.projects).containsStrictly({
                    property(subject::id).toBe(projectId)
                })
                test("the project contains just one command in ${Project::innerCommands.name}") {
                    ch.tutteli.atrium.assert(releasePlan.projects[0]) {
                        property(subject::outerCommands).isEmpty()
                        property(subject::innerCommands).containsStrictly({
                            isA<JenkinsMavenReleasePlugin> { }
                        })
                    }
                }
            }
        }
    }
})

private fun getTestDirectory(name: String) = File(MavenFacadeSpec.javaClass.getResource("/$name/").path)
