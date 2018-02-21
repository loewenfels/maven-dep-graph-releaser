package ch.loewenfels.depgraph.maven

import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.tutteli.atrium.*
import ch.tutteli.atrium.api.cc.en_UK.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.*
import java.io.File

object MavenFacadeSpec : Spek({
    val testee = MavenFacade()
    val singleProjectIdAndVersions = IdAndVersions(MavenProjectId("com.example", "example"), "1.0-SNAPSHOT", "1.0", "1.1-SNAPSHOT")

    fun ActionBody.testReleaseSingleProject(idAndVersions: IdAndVersions, directory: String) {
        val releasePlan = testee.analyseAndCreateReleasePlan(idAndVersions.id, getTestDirectory(directory))

        assertRootProjectOnlyReleaseAndReady(releasePlan, idAndVersions)

        test("it does not have any dependent project") {
            assert(releasePlan.dependents) {
                property(subject::size).toBe(1)
                returnValueOf(subject::get, idAndVersions.id).isNotNull { isEmpty() }
            }
        }
    }

    fun SpecBody.testReleaseAWithDependentB(directory: String) {
        describe(testee::analyseAndCreateReleasePlan.name) {
            action("the root project is the one we want to release") {
                val releasePlan = testee.analyseAndCreateReleasePlan(exampleA.id, getTestDirectory(directory))
                assertProjectAWithDependentB(releasePlan)
            }
        }
    }

    fun SpecBody.testReleaseBWithNoDependent(directory: String) {
        action("we release project B (no dependent at all)") {
            testReleaseSingleProject(exampleB, directory)
        }
    }

    describe("validation errors") {
        given("not a ${MavenProjectId::class.simpleName}") {
            val errMsg = "Can only create a release plan for a maven project"
            it("throws an IllegalArgumentException, mentioning `$errMsg`") {
                expect {
                    val projectToRelease: ProjectId = object : ProjectId {
                        override val identifier = "bla"
                    }
                    testee.analyseAndCreateReleasePlan(projectToRelease, File("nonExistingProject/"))
                }.toThrow<IllegalArgumentException> { message { contains(errMsg) } }
            }
        }

        given("non existing directory") {
            val errMsg = "directory does not exists"
            it("throws an IllegalArgumentException, mentioning `$errMsg`") {
                expect {
                    testee.analyseAndCreateReleasePlan(singleProjectIdAndVersions.id, File("nonExistingProject/"))
                }.toThrow<IllegalArgumentException> { message { contains(errMsg) } }
            }
        }
        given("project to release not in directory") {
            val errMsg = "Can only release a project which is part of the analysis"
            it("throws an IllegalArgumentException, mentioning `$errMsg`") {
                val wrongProject = MavenProjectId("com.other", "notThatOne")
                expect {
                    testee.analyseAndCreateReleasePlan(wrongProject, getTestDirectory("singleProject"))
                }.toThrow<IllegalArgumentException> {
                    message {
                        contains(errMsg, wrongProject.toString(), singleProjectIdAndVersions.id.toString())
                    }
                }
            }
        }
    }


    given("single project with third party dependencies") {
        describe(testee::analyseAndCreateReleasePlan.name) {
            action("the root project is the one we want to release") {
                testReleaseSingleProject(singleProjectIdAndVersions, "singleProject")
            }
        }
    }

    given("project with dependency incl. version") {
        testReleaseAWithDependentB("projectWithDependency")
        testReleaseBWithNoDependent("projectWithDependency")
    }

    given("project with dependency and version in dependency management") {
        testReleaseAWithDependentB("projectWithDependencyManagement")
        testReleaseBWithNoDependent("projectWithDependencyManagement")
    }

    given("project with parent dependency") {
        testReleaseAWithDependentB("projectWithParent")
        testReleaseBWithNoDependent("projectWithParent")
    }

    given("two projects unrelated but one has other in dependency management") {
        describe(testee::analyseAndCreateReleasePlan.name) {
            action("we release project A (is in B in dependency management)") {
                testReleaseSingleProject(exampleA, "unrelatedProjects")
            }
            testReleaseBWithNoDependent("unrelatedProjects")
        }
    }

    given("project with implicit transitive dependent") {
        describe(testee::analyseAndCreateReleasePlan.name) {
            action("the root project is the one we want to release") {
                val releasePlan = testee.analyseAndCreateReleasePlan(exampleA.id, getTestDirectory("transitiveImplicit"))
                assertRootProjectOnlyReleaseAndReady(releasePlan, exampleA)

                assertWithDependent(releasePlan, exampleA, exampleB)
                test("the dependent has itself a dependent with two commands, updateVersion and Release") {
                    assertWithDependent(releasePlan, exampleB, exampleC)
                }

                test("release plan has three projects and three dependents") {
                    assert(releasePlan) {
                        property(subject::projects).hasSize(3)
                        property(subject::dependents).hasSize(3)
                    }
                }
            }
        }
    }

    given("project with explicit transitive dependent") {
        describe(testee::analyseAndCreateReleasePlan.name) {
            action("the root project is the one we want to release") {
                val releasePlan = testee.analyseAndCreateReleasePlan(exampleA.id, getTestDirectory("transitiveExplicit"))
                assertRootProjectOnlyReleaseAndReady(releasePlan, exampleA)

                it("has two dependent") {
                    assert(releasePlan).hasDependentsForProject(exampleA, exampleB, exampleC)
                }
                test("the direct dependent project has two commands, updateVersion and Release") {
                    assert(releasePlan.projects[exampleB.id]).isNotNull {
                        idAndVersions(exampleB)
                        property(subject::commands).containsStrictly(
                            { isJenkinsUpdateDependencyWaiting(exampleA) },
                            { isJenkinsMavenReleaseWaiting(exampleB.nextDevVersion, exampleA) }
                        )
                    }
                }
                test("the direct dependent has one dependent") {
                    assert(releasePlan).hasDependentsForProject(exampleB, exampleC)
                }

                test("the indirect dependent project has two commands, updateVersion and Release") {
                    assert(releasePlan.projects[exampleC.id]).isNotNull {
                        idAndVersions(exampleC)
                        property(subject::commands).containsStrictly(
                            { isJenkinsUpdateDependencyWaiting(exampleB) },
                            { isJenkinsUpdateDependencyWaiting(exampleA) },
                            { isJenkinsMavenReleaseWaiting(exampleC.nextDevVersion, exampleA, exampleB) }
                        )
                    }
                }
                test("the indirect dependent does not have dependents") {
                    assert(releasePlan).hasNotDependentsForProject(exampleC)
                }

                test("release plan has three projects and three dependents") {
                    assert(releasePlan) {
                        property(subject::projects).hasSize(3)
                        property(subject::dependents).hasSize(3)
                    }
                }
            }
        }
    }
})

