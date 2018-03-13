package ch.loewenfels.depgraph.maven

import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.ReleasePlan
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.tutteli.atrium.*
import ch.tutteli.atrium.api.cc.en_UK.*
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import fr.lteconsulting.pomexplorer.PomFileLoader
import fr.lteconsulting.pomexplorer.Session
import fr.lteconsulting.pomexplorer.model.Gav
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.*
import java.io.File

object MavenFacadeSpec : Spek({
    val testee = MavenFacade()
    val singleProjectIdAndVersions = IdAndVersions(MavenProjectId("com.example", "example"), "1.0-SNAPSHOT", "1.0", "1.1-SNAPSHOT")

    fun ActionBody.testReleaseSingleProject(idAndVersions: IdAndVersions, directory: String) {
        val releasePlan = testee.analyseAndCreateReleasePlan(idAndVersions.id, getTestDirectory(directory))

        assertSingleProject(releasePlan, idAndVersions)
    }

    fun SpecBody.testReleaseAWithDependentBWithDependentC(directory: String, projectB: IdAndVersions = exampleB) {
        describe(testee::analyseAndCreateReleasePlan.name) {
            action("the root project is the one we want to release") {
                val releasePlan = testee.analyseAndCreateReleasePlan(exampleA.id, getTestDirectory(directory))
                assertReleaseAWithDependentBWithDependentC(releasePlan, projectB)
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

    fun SpecBody.testReleaseAWithDependentBAndX(directory: String, projectX: IdAndVersions, furtherAssertions: ActionBody.(ReleasePlan) -> Unit) {
        describe(testee::analyseAndCreateReleasePlan.name) {
            action("the root project is the one we want to release") {
                val releasePlan = testee.analyseAndCreateReleasePlan(exampleA.id, getTestDirectory(directory))
                assertRootProjectOnlyReleaseAndReady(releasePlan, exampleA)

                it("has two dependent projects") {
                    assert(releasePlan).hasDependentsForProject(exampleA, exampleB, projectX)
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
                furtherAssertions(releasePlan)
            }
        }
    }

    fun ActionBody.assertOneDirectDependent(releasePlan: ReleasePlan, name: String, directDependent: IdAndVersions) {
        test("$name project has two commands, updateVersion and Release") {
            assert(releasePlan.projects[directDependent.id]).isNotNull {
                idAndVersions(directDependent)
                property(subject::commands).containsStrictly(
                    { isJenkinsUpdateDependencyWaiting(exampleA) },
                    { isJenkinsMavenReleaseWaiting(directDependent.nextDevVersion, exampleA) }
                )
            }
        }

        test("$name direct dependent has one dependent") {
            assert(releasePlan).hasDependentsForProject(directDependent, exampleC)
        }
        test("$name direct dependent is on level 1") {
            assert(releasePlan.getProject(directDependent.id).level).toBe(1)
        }
    }

    fun ActionBody.assertHasNotDependentsAndIsOnLevel(releasePlan: ReleasePlan, name: String, dependent: IdAndVersions, level: Int) {
        test("$name project does not have dependents") {
            assert(releasePlan).hasNotDependentsForProject(dependent)
        }
        test("$name project is on level $level") {
            assert(releasePlan.getProject(dependent.id).level).toBe(level)
        }
    }

    fun SpecBody.testReleaseAWithDependentBDAndCViaD(directory: String) {
        testReleaseAWithDependentBAndX(directory, exampleD) { releasePlan ->
            assertOneDirectDependent(releasePlan, "the direct dependent", exampleB)
            assertOneDirectDependent(releasePlan, "the parent", exampleD)

            test("the indirect dependent project has two updateVersion and one Release command") {
                assert(releasePlan.projects[exampleC.id]).isNotNull {
                    idAndVersions(exampleC)
                    property(subject::commands).containsStrictly(
                        { isJenkinsUpdateDependencyWaiting(exampleB) },
                        { isJenkinsUpdateDependencyWaiting(exampleD) },
                        { isJenkinsMavenReleaseWaiting(exampleC.nextDevVersion, exampleD, exampleB) }
                    )
                }
            }
            assertHasNotDependentsAndIsOnLevel(releasePlan, "the indirect dependent", exampleC, 2)

            test("release plan has four projects and four dependents") {
                assert(releasePlan) {
                    property(subject::projects).hasSize(4)
                    property(subject::dependents).hasSize(4)
                }
            }
        }
    }

    fun SpecBody.testDuplicateProject(directory: String, vararg poms: Pair<String, String>) {
        it("throws an IllegalStateException, containing versions of all projects inclusive path") {
            val testDirectory = getTestDirectory(directory)
            expect {
                testee.analyseAndCreateReleasePlan(exampleA.id, testDirectory)
            }.toThrow<IllegalStateException> {
                message {
                    contains(
                        "directory: ${testDirectory.canonicalPath}",
                        *poms.map {
                            "${exampleA.id.identifier}:${it.second} (${File(testDirectory, it.first).canonicalPath})"
                        }.toTypedArray()
                    )
                }
            }
        }
    }

    fun analyseAndCreateReleasePlanWithResolvingAnalyser(testDirectory: String): ReleasePlan {
        val oldPomsDir = getTestDirectory("oldPoms")
        val pomFileLoader = mock<PomFileLoader> {
            on {
                it.loadPomFileForGav(eq(Gav(exampleA.id.groupId, exampleA.id.artifactId, "1.0.0")), eq(null), any())
            }.thenReturn(File(oldPomsDir, "a-1.0.0.pom"))
            on {
                it.loadPomFileForGav(eq(Gav(exampleA.id.groupId, exampleA.id.artifactId, "0.9.0")), eq(null), any())
            }.thenReturn(File(oldPomsDir, "a-0.9.0.pom"))
            on {
                it.loadPomFileForGav(eq(Gav(exampleB.id.groupId, exampleB.id.artifactId, "1.0.0")), eq(null), any())
            }.thenReturn(File(oldPomsDir, "b-1.0.0.pom"))
            on {
                it.loadPomFileForGav(eq(Gav(exampleDeps.id.groupId, exampleDeps.id.artifactId, "8")), eq(null), any())
            }.thenReturn(File(oldPomsDir, "deps-8.pom"))
        }
        val analyser = Analyser(getTestDirectory(testDirectory), Session(), pomFileLoader)
        val jenkinsReleasePlanCreator = JenkinsReleasePlanCreator(VersionDeterminer())
        return jenkinsReleasePlanCreator.create(exampleA.id, analyser)
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

        given("duplicate projects, twice the same version") {
            testDuplicateProject(
                "duplicatedProjectTwiceTheSameVersion",
                "a.pom" to exampleA.currentVersion,
                "a/a.pom" to exampleA.currentVersion
            )
        }

        given("duplicate projects, current and an older version") {
            testDuplicateProject(
                "duplicatedProjectCurrentAndOld",
                "a.pom" to exampleA.currentVersion,
                "aOld.pom" to "1.0.1-SNAPSHOT"
            )
        }

        given("duplicate projects, twice the same version and an older version") {
            testDuplicateProject(
                "duplicatedProjectTwiceTheSameVersionAndAnOld",
                "a.pom" to exampleA.currentVersion,
                "a/a.pom" to exampleA.currentVersion,
                "aOld.pom" to "1.0.1-SNAPSHOT"
            )
        }

        given("parent not in analysis") {
            it("throws an IllegalStateException, containing versions of project and parent and path of project") {
                val testDirectory = getTestDirectory("parentNotInAnalysis")
                val b = File(testDirectory, "b.pom")
                expect {
                    testee.analyseAndCreateReleasePlan(exampleB.id, testDirectory)
                }.toThrow<IllegalStateException> {
                    message {
                        contains(
                            "${exampleB.id.identifier}:${exampleB.currentVersion} (${b.canonicalPath})",
                            "${exampleA.id.identifier}:1.0.0"
                        )
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
        testReleaseAWithDependentB("oneDependency")
        testReleaseBWithNoDependent("oneDependency")
    }

    given("project with dependency and version in dependency management") {
        testReleaseAWithDependentB("oneDependencyViaManagement")
        testReleaseBWithNoDependent("oneDependencyViaManagement")
    }

    given("project with dependency and version in bom") {
        action("context use an Analyser with a mocked PomFileResolver") {
            val releasePlan = analyseAndCreateReleasePlanWithResolvingAnalyser("oneDependencyViaBom")
            assertProjectAWithDependentB(releasePlan)
        }
    }

    given("project with parent dependency") {
        testReleaseAWithDependentB("parent")
        testReleaseBWithNoDependent("parent")
    }

    given("project with parent which itself has a parent, old parents are not resolved") {
        testReleaseAWithDependentBWithDependentC("parentWithParent")
    }

    given("project with parent which itself has a parent, old parents are resolved") {
        action("context use an Analyser with a mocked PomFileResolver") {
            val releasePlan = analyseAndCreateReleasePlanWithResolvingAnalyser("parentWithParent")
            assertReleaseAWithDependentBWithDependentC(releasePlan)
        }
    }

    given("project with multi-module parent (parent has SNAPSHOT dependency to parent), old parents are not resolved") {
        testReleaseAWithDependentBWithDependentC("multiModuleParent", IdAndVersions(exampleB.id, exampleA))
    }

    given("project with multi-module parent (parent has SNAPSHOT dependency to parent), old parents are resolved") {
        action("context use an Analyser with a mocked PomFileResolver") {
            val releasePlan = analyseAndCreateReleasePlanWithResolvingAnalyser("multiModuleParent")

            assertReleaseAWithDependentBWithDependentC(releasePlan, IdAndVersions(exampleB.id, exampleA))
        }
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
        testReleaseAWithDependentBWithDependentC("transitiveImplicit")
    }

    given("project with explicit transitive dependent") {
        testReleaseAWithDependentBAndX("transitiveExplicit", exampleC) { releasePlan ->

            assertOneDirectDependent(releasePlan, "the direct dependent", exampleB)

            test("the indirect dependent project has two updateVersion and one Release command") {
                assert(releasePlan.projects[exampleC.id]).isNotNull {
                    idAndVersions(exampleC)
                    property(subject::commands).containsStrictly(
                        { isJenkinsUpdateDependencyWaiting(exampleB) },
                        { isJenkinsUpdateDependencyWaiting(exampleA) },
                        { isJenkinsMavenReleaseWaiting(exampleC.nextDevVersion, exampleA, exampleB) }
                    )
                }
            }

            assertHasNotDependentsAndIsOnLevel(releasePlan, "indirect dependent", exampleC, 2)

            test("release plan has three projects and three dependents") {
                assert(releasePlan) {
                    property(subject::projects).hasSize(3)
                    property(subject::dependents).hasSize(3)
                }
            }
        }
    }

    given("project with explicit transitive dependent and diamond dependency") {

        action("the root project is the one we want to release") {
            val releasePlan = testee.analyseAndCreateReleasePlan(exampleA.id, getTestDirectory("transitiveExplicitTwoDependencies"))
            assertRootProjectOnlyReleaseAndReady(releasePlan, exampleA)

            it("has three dependent projects") {
                assert(releasePlan).hasDependentsForProject(exampleA, exampleB, exampleC, exampleD)
            }

            assertOneDirectDependent(releasePlan, "first direct dependent", exampleB)
            assertOneDirectDependent(releasePlan, "second direct dependent", exampleD)

            test("the indirect dependent project has three updateVersion and one Release command") {
                assert(releasePlan.projects[exampleC.id]).isNotNull {
                    idAndVersions(exampleC)
                    property(subject::commands).containsStrictly(
                        { isJenkinsUpdateDependencyWaiting(exampleB) },
                        { isJenkinsUpdateDependencyWaiting(exampleD) },
                        { isJenkinsUpdateDependencyWaiting(exampleA) },
                        { isJenkinsMavenReleaseWaiting(exampleC.nextDevVersion, exampleD, exampleB, exampleA) }
                    )
                }
            }
            assertHasNotDependentsAndIsOnLevel(releasePlan, "indirect dependent", exampleC, 2)

            test("release plan has four projects and four dependents") {
                assert(releasePlan) {
                    property(subject::projects).hasSize(4)
                    property(subject::dependents).hasSize(4)
                }
            }
        }
    }

    given("project with explicit transitive dependent via parent (parent with dependency)") {
        testReleaseAWithDependentBDAndCViaD("transitiveExplicitViaParent")
    }

    given("project with explicit transitive dependent via pom (pom has dependency)") {
        testReleaseAWithDependentBDAndCViaD("transitiveExplicitViaPom")
    }
})
