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

object IntegrationSpec : Spek({
    val singleProjectIdAndVersions =
        IdAndVersions(MavenProjectId("com.example", "example"), "1.0-SNAPSHOT", "1.0", "1.1-SNAPSHOT")

    fun analyseAndCreateReleasePlan(projectToRelease: ProjectId, analyser: Analyser): ReleasePlan {
        val jenkinsReleasePlanCreator = JenkinsReleasePlanCreator(VersionDeterminer())
        return jenkinsReleasePlanCreator.create(projectToRelease as MavenProjectId, analyser)
    }

    fun analyseAndCreateReleasePlan(projectToRelease: ProjectId, testDirectory: File): ReleasePlan {
        val analyser = Analyser(testDirectory, Analyser.Options())
        return analyseAndCreateReleasePlan(projectToRelease, analyser)
    }

    fun analyseAndCreateReleasePlanWithMockedPomResolver(
        projectToRelease: ProjectId,
        testDirectory: String
    ): ReleasePlan {
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
        return analyseAndCreateReleasePlan(projectToRelease, analyser)
    }

    fun assertOneUpdateAndOneReleaseCommand(releasePlan: ReleasePlan, project: IdAndVersions, dependency: IdAndVersions) {
        assert(releasePlan.projects[project.id]).isNotNull {
            idAndVersions(project)
            property(subject::commands).containsStrictly(
                { isJenkinsUpdateDependencyWaiting(dependency) },
                { isJenkinsMavenReleaseWaiting(project.nextDevVersion, dependency) }
            )
        }
    }

    fun assertTwoUpdateAndOneReleaseCommand(
        releasePlan: ReleasePlan,
        project: IdAndVersions,
        dependency1: IdAndVersions,
        dependency2: IdAndVersions
    ) {
        assert(releasePlan.projects[project.id]).isNotNull {
            idAndVersions(project)
            property(subject::commands).containsStrictly(
                { isJenkinsUpdateDependencyWaiting(dependency1) },
                { isJenkinsUpdateDependencyWaiting(dependency2) },
                { isJenkinsMavenReleaseWaiting(project.nextDevVersion, dependency2, dependency1) }
            )
        }
    }


    fun ActionBody.testReleaseSingleProject(idAndVersions: IdAndVersions, directory: String) {
        val releasePlan = analyseAndCreateReleasePlan(idAndVersions.id, getTestDirectory(directory))

        assertSingleProject(releasePlan, idAndVersions)
    }

    fun SpecBody.testReleaseAWithDependentBWithDependentC(directory: String, projectB: IdAndVersions = exampleB) {
        action("context Analyser which tries to resolve poms") {
            val releasePlan = analyseAndCreateReleasePlan(exampleA.id, getTestDirectory(directory))
            assertReleaseAWithDependentBWithDependentC(releasePlan, projectB)
        }
    }

    fun SpecBody.testReleaseAWithDependentB(directory: String) {
        action("context Analyser which does not resolve poms") {
            val releasePlan = analyseAndCreateReleasePlan(exampleA.id, getTestDirectory(directory))
            assertProjectAWithDependentB(releasePlan)
        }
    }

    fun SpecBody.testReleaseBWithNoDependent(directory: String) {
        action("we release project B (no dependent at all)") {
            testReleaseSingleProject(exampleB, directory)
        }
    }

    fun SpecBody.testReleaseAWithDependentBAndX(
        directory: String,
        projectX: IdAndVersions,
        furtherAssertions: ActionBody.(ReleasePlan) -> Unit
    ) {
        action("context Analyser which does not resolve poms") {
            val releasePlan = analyseAndCreateReleasePlan(exampleA.id, getTestDirectory(directory))
            assertRootProjectOnlyReleaseAndReady(releasePlan, exampleA)

            it("root has two dependent projects") {
                assert(releasePlan).hasDependentsForProject(exampleA, exampleB, projectX)
            }
            test("the direct dependent project has two commands, updateVersion and Release") {
                assertOneUpdateAndOneReleaseCommand(releasePlan, exampleB, exampleA)
            }
            furtherAssertions(releasePlan)
        }
    }

    fun ActionBody.assertOneDirectDependent(
        releasePlan: ReleasePlan,
        name: String,
        project: IdAndVersions,
        dependent: IdAndVersions
    ) {
        test("$name project has two commands, updateVersion and Release") {
            assertOneUpdateAndOneReleaseCommand(releasePlan, project, exampleA)
        }

        test("$name project has one dependent") {
            assert(releasePlan).hasDependentsForProject(project, dependent)
        }
        test("$name project is on level 1") {
            assert(releasePlan.getProject(project.id).level).toBe(1)
        }
    }

    fun ActionBody.assertHasNotDependentsAndIsOnLevel(
        releasePlan: ReleasePlan,
        name: String,
        dependent: IdAndVersions,
        level: Int
    ) {
        test("$name project does not have dependents") {
            assert(releasePlan).hasNotDependentsForProject(dependent)
        }
        test("$name project is on level $level") {
            assert(releasePlan.getProject(dependent.id).level).toBe(level)
        }
    }

    fun SpecBody.testReleaseAWithDependentBDAndCViaD(directory: String) {
        testReleaseAWithDependentBAndX(directory, exampleD) { releasePlan ->
            assertOneDirectDependent(releasePlan, "the direct dependent", exampleB, exampleC)
            assertOneDirectDependent(releasePlan, "the parent", exampleD, exampleC)

            test("the indirect dependent project has two updateVersion and one Release command") {
                assertTwoUpdateAndOneReleaseCommand(releasePlan, exampleC, exampleB, exampleD)
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
                analyseAndCreateReleasePlan(exampleA.id, testDirectory)
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

    describe("validation errors") {

        given("non existing directory") {
            val errMsg = "directory does not exists"
            it("throws an IllegalArgumentException, mentioning `$errMsg`") {
                expect {
                    analyseAndCreateReleasePlan(singleProjectIdAndVersions.id, File("nonExistingProject/"))
                }.toThrow<IllegalArgumentException> { message { contains(errMsg) } }
            }
        }
        given("project to release not in directory") {
            val errMsg = "Can only release a project which is part of the analysis"
            it("throws an IllegalArgumentException, mentioning `$errMsg`") {
                val wrongProject = MavenProjectId("com.other", "notThatOne")
                expect {
                    analyseAndCreateReleasePlan(wrongProject, getTestDirectory("singleProject"))
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
                    analyseAndCreateReleasePlan(exampleB.id, testDirectory)
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
        action("context Analyser which does not resolve poms") {
            testReleaseSingleProject(singleProjectIdAndVersions, "singleProject")
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
        action("context Analyser with a mocked PomFileResolver") {
            val releasePlan = analyseAndCreateReleasePlanWithMockedPomResolver(exampleA.id, "oneDependencyViaBom")
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
            val releasePlan = analyseAndCreateReleasePlanWithMockedPomResolver(exampleA.id, "parentWithParent")
            assertReleaseAWithDependentBWithDependentC(releasePlan)
        }
    }

    given("project with multi-module parent (parent has SNAPSHOT dependency to parent), old parents are not resolved") {
        testReleaseAWithDependentBWithDependentC("multiModuleParent", IdAndVersions(exampleB.id, exampleA))
    }

    given("project with multi-module parent (parent has SNAPSHOT dependency to parent), old parents are resolved") {
        action("context use an Analyser with a mocked PomFileResolver") {
            val releasePlan = analyseAndCreateReleasePlanWithMockedPomResolver(exampleA.id, "multiModuleParent")

            assertReleaseAWithDependentBWithDependentC(releasePlan, IdAndVersions(exampleB.id, exampleA))
        }
    }

    given("two projects unrelated but one has other in dependency management") {
        context("Analyser which does not resolve poms") {
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

            assertOneDirectDependent(releasePlan, "the direct dependent", exampleB, exampleC)

            test("the indirect dependent project has two updateVersion and one Release command") {
                assertTwoUpdateAndOneReleaseCommand(releasePlan, exampleC, exampleB, exampleA)
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

    given("project with explicit transitive dependent which itself has dependent") {

        action("context Analyser which does not resolve poms") {
            val releasePlan = analyseAndCreateReleasePlan(
                exampleA.id,
                getTestDirectory("transitiveExplicitWithDependent")
            )
            assertRootProjectOnlyReleaseAndReady(releasePlan, exampleA)

            it("root has two dependent projects") {
                assert(releasePlan).hasDependentsForProject(exampleA, exampleB, exampleD)
            }

            test("root has two dependent projects") {
                assert(releasePlan).hasDependentsForProject(exampleA, exampleB, exampleD)
            }

            test("the parent project has two commands, updateVersion and Release") {
                assertTwoUpdateAndOneReleaseCommand(releasePlan, exampleB, exampleD, exampleA)
            }

            test("the parent project has one dependent") {
                assert(releasePlan).hasDependentsForProject(exampleB, exampleC)
            }
            test("the parent project is on level 2") {
                assert(releasePlan.getProject(exampleB.id).level).toBe(2)
            }

            assertOneDirectDependent(releasePlan, "the direct dependent", exampleD, exampleB)

            test("the indirect dependent project has one updateVersion and one Release command") {
                assertOneUpdateAndOneReleaseCommand(releasePlan, exampleC, exampleB)
            }

            assertHasNotDependentsAndIsOnLevel(releasePlan, "the indirect dependent", exampleC, 3)

            test("release plan has four projects and four dependents") {
                assert(releasePlan) {
                    property(subject::projects).hasSize(4)
                    property(subject::dependents).hasSize(4)
                }
            }
        }
    }

    given("project with explicit transitive dependent and diamond dependency") {

        action("context Analyser which tries to resolve poms") {
            val releasePlan =
                analyseAndCreateReleasePlan(exampleA.id, getTestDirectory("transitiveExplicitTwoDependencies"))
            assertRootProjectOnlyReleaseAndReady(releasePlan, exampleA)

            test("root has three dependent projects") {
                assert(releasePlan).hasDependentsForProject(exampleA, exampleB, exampleC, exampleD)
            }

            assertOneDirectDependent(releasePlan, "first direct dependent", exampleB, exampleC)
            assertOneDirectDependent(releasePlan, "second direct dependent", exampleD, exampleC)

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
        describe("parent is first dependent") {
            action("context Analyser which does not resolve poms") {
                val releasePlan = analyseAndCreateReleasePlan(
                    exampleA.id,
                    getTestDirectory("transitiveExplicitViaParentAsFirstDependent")
                )
                assertRootProjectOnlyReleaseAndReady(releasePlan, exampleA)

                assertOneDirectDependent(releasePlan, "the parent", exampleB, exampleC)
                assertOneDirectDependent(releasePlan, "the direct dependent", exampleD, exampleC)

                test("the indirect dependent project has two updateVersion and one Release command") {
                    assertTwoUpdateAndOneReleaseCommand(releasePlan, exampleC, exampleB, exampleD)
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

        describe("parent is second dependent") {
            testReleaseAWithDependentBDAndCViaD("transitiveExplicitViaParentAsSecondDependent")
        }
    }

    given("project with explicit transitive dependent via pom (pom has dependency)") {
        testReleaseAWithDependentBDAndCViaD("transitiveExplicitViaPom")
    }
})
