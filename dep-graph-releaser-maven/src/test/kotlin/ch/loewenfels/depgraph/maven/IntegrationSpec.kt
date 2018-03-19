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
                    analyseAndCreateReleasePlan(wrongProject, "singleProject")
                }.toThrow<IllegalArgumentException> {
                    message {
                        contains(errMsg, wrongProject.toString(), singleProjectIdAndVersions.id.toString())
                    }
                }
            }
        }

        given("duplicate projects, twice the same version") {
            testDuplicateProject(
                "errorCases/duplicatedProjectTwiceTheSameVersion",
                "a.pom" to exampleA.currentVersion,
                "a/a.pom" to exampleA.currentVersion
            )
        }

        given("duplicate projects, current and an older version") {
            testDuplicateProject(
                "errorCases/duplicatedProjectCurrentAndOld",
                "a.pom" to exampleA.currentVersion,
                "aOld.pom" to "1.0.1-SNAPSHOT"
            )
        }

        given("duplicate projects, twice the same version and an older version") {
            testDuplicateProject(
                "errorCases/duplicatedProjectTwiceTheSameVersionAndAnOld",
                "a.pom" to exampleA.currentVersion,
                "a/a.pom" to exampleA.currentVersion,
                "aOld.pom" to "1.0.1-SNAPSHOT"
            )
        }

        given("parent not in analysis") {
            it("throws an IllegalStateException, containing versions of project and parent and path of project") {
                val testDirectory = getTestDirectory("errorCases/parentNotInAnalysis")
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

    describe("warnings") {
        given("a project without group id") {
            test("release plan contains warning which includes file path") {
                val testDir = getTestDirectory("errorCases/projectWithoutGroupId")
                val pom = File(testDir, "b.pom")
                val releasePlan = analyseAndCreateReleasePlan(exampleA.id, testDir)
                assert(releasePlan.warnings).containsStrictly({
                    contains(pom.canonicalPath)
                })
            }
        }
        given("a project without version") {
            test("release plan contains warning which includes file path") {
                val testDir = getTestDirectory("errorCases/projectWithoutVersion")
                val pom = File(testDir, "b.pom")
                val releasePlan = analyseAndCreateReleasePlan(exampleA.id, testDir)
                assert(releasePlan.warnings).containsStrictly({
                    contains(pom.canonicalPath)
                })
            }
        }
    }

    given("single project with third party dependencies") {
        action("context Analyser which does not resolve poms") {
            testReleaseSingleProject(singleProjectIdAndVersions, "singleProject")
        }
    }

    given("project with dependent only via dependency management") {
        testReleaseAWithDependentB("oneDependentOnlyViaManagement")
        testReleaseBWithNoDependent("oneDependentOnlyViaManagement")
    }

    given("two projects unrelated") {
        context("Analyser which does not resolve poms") {
            action("we release project A") {
                testReleaseSingleProject(exampleA, "unrelatedProjects")
            }
            testReleaseBWithNoDependent("unrelatedProjects")
        }
    }

    describe("different ways of managing versions") {
        given("project with dependent and version in dependency itself") {
            testReleaseAWithDependentB("managingVersions/inDependency")
            testReleaseBWithNoDependent("managingVersions/inDependency")
        }

        //TODO project with dependent and version in property
        //TODO project with dependent and version is $project.version

        given("project with dependent and version in dependency management") {
            testReleaseAWithDependentB("managingVersions/viaDependencyManagement")
            testReleaseBWithNoDependent("managingVersions/viaDependencyManagement")
        }

        given("project with dependent and version in bom") {
            action("context Analyser with a mocked PomFileResolver") {
                val releasePlan = analyseAndCreateReleasePlanWithMockedPomResolver(exampleA.id, "managingVersions/viaBom")
                assertRootProjectOnlyReleaseAndReady(releasePlan, exampleA)

                assertOnlyWaitingReleaseCommand(releasePlan, "indirect dependent", exampleB, exampleA)
                assertHasNoDependentsAndIsOnLevel(releasePlan, "direct dependent", exampleB, 1)

                assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 2)
                assertReleasePlanHasNoWarnings(releasePlan)
            }
        }

        given("project with dependent and version in parent dependency management") {
            action("context Analyser with a mocked PomFileResolver") {
                val releasePlan = analyseAndCreateReleasePlanWithMockedPomResolver(exampleA.id, "managingVersions/viaParent")
                assertRootProjectOnlyReleaseAndReady(releasePlan, exampleA)

                assertOneDirectDependent(releasePlan, "parent", exampleC, exampleB)

                test("direct dependent project has one waiting UpdateVersion and one waiting Release command") {
                    assert(releasePlan.projects[exampleB.id]).isNotNull {
                        idAndVersions(exampleB)
                        property(subject::commands).containsStrictly(
                            { isJenkinsUpdateDependencyWaiting(exampleC) },
                            { isJenkinsMavenReleaseWaiting(exampleB.nextDevVersion, exampleA, exampleC) }
                        )
                    }
                }
                assertHasNoDependentsAndIsOnLevel(releasePlan, "direct dependent", exampleB, 2)

                assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 3)
                assertReleasePlanHasNoWarnings(releasePlan)
            }
        }
    }

    describe("parent relations") {

        given("project with parent dependency") {
            testReleaseAWithDependentB("parentRelations/parent")
            testReleaseBWithNoDependent("parentRelations/parent")
        }

        given("project with parent which itself has a parent, old parents are not resolved") {
            testReleaseAWithDependentBWithDependentC("parentRelations/parentWithParent")
        }

        given("project with parent which itself has a parent, old parents are resolved") {
            action("context use an Analyser with a mocked PomFileResolver") {
                val releasePlan = analyseAndCreateReleasePlanWithMockedPomResolver(exampleA.id, "parentRelations/parentWithParent")
                assertReleaseAWithDependentBWithDependentC(releasePlan)
                assertReleasePlanHasNoWarnings(releasePlan)
            }
        }

        given("project with multi-module parent (parent has SNAPSHOT dependency to parent), old parents are not resolved") {
            testReleaseAWithDependentBWithDependentC("parentRelations/multiModuleParent", IdAndVersions(exampleB.id, exampleA))
        }

        given("project with multi-module parent (parent has SNAPSHOT dependency to parent), old parents are resolved") {
            action("context use an Analyser with a mocked PomFileResolver") {
                val releasePlan = analyseAndCreateReleasePlanWithMockedPomResolver(exampleA.id, "parentRelations/multiModuleParent")

                assertReleaseAWithDependentBWithDependentC(releasePlan, IdAndVersions(exampleB.id, exampleA))
                assertReleasePlanHasNoWarnings(releasePlan)
            }
        }
    }

    describe("transitive dependencies") {

        given("project with implicit transitive dependent") {
            testReleaseAWithDependentBWithDependentC("transitive/transitiveImplicit")
        }

        given("project with explicit transitive dependent") {
            testReleaseAWithDependentBAndX("transitive/transitiveExplicit", exampleC) { releasePlan ->

                assertOneDirectDependent(releasePlan, "the direct dependent", exampleB, exampleC)

                assertTwoUpdateAndOneReleaseCommand(releasePlan, "the indirect dependent", exampleC, exampleB, exampleA)
                assertHasNoDependentsAndIsOnLevel(releasePlan, "indirect dependent", exampleC, 2)

                assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 3)
                assertReleasePlanHasNoWarnings(releasePlan)
            }
        }

        given("project with explicit transitive dependent which itself has dependent") {

            action("context Analyser which does not resolve poms") {
                val releasePlan = analyseAndCreateReleasePlan(exampleA.id, "transitive/transitiveExplicitWithDependent")
                assertRootProjectOnlyReleaseAndReady(releasePlan, exampleA)

                it("root has two dependent projects") {
                    assert(releasePlan).hasDependentsForProject(exampleA, exampleB, exampleD)
                }

                test("root has two dependent projects") {
                    assert(releasePlan).hasDependentsForProject(exampleA, exampleB, exampleD)
                }

                assertTwoUpdateAndOneReleaseCommand(releasePlan, "the parent", exampleB, exampleD, exampleA)
                assertHasOneDependentAndIsOnLevel(releasePlan, "the parent", exampleB, exampleC, 2)

                assertOneDirectDependent(releasePlan, "the direct dependent", exampleD, exampleB)

                assertOneUpdateAndOneReleaseCommand(releasePlan, "the indirect dependent", exampleC, exampleB)
                assertHasNoDependentsAndIsOnLevel(releasePlan, "the indirect dependent", exampleC, 3)

                assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 4)
                assertReleasePlanHasNoWarnings(releasePlan)
            }
        }

        given("project with explicit transitive dependent and diamond dependency") {

            action("context Analyser which tries to resolve poms") {
                val releasePlan =
                    analyseAndCreateReleasePlan(exampleA.id, "transitive/transitiveExplicitTwoDependencies")
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
                assertHasNoDependentsAndIsOnLevel(releasePlan, "indirect dependent", exampleC, 2)

                assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 4)
                assertReleasePlanHasNoWarnings(releasePlan)
            }
        }

        given("project with explicit transitive dependent via parent (parent with dependency)") {
            describe("parent is first dependent") {
                action("context Analyser which does not resolve poms") {
                    val releasePlan = analyseAndCreateReleasePlan(
                        exampleA.id, "transitive/transitiveExplicitViaParentAsFirstDependent"
                    )
                    assertRootProjectOnlyReleaseAndReady(releasePlan, exampleA)

                    assertOneDirectDependent(releasePlan, "the parent", exampleB, exampleC)
                    assertOneDirectDependent(releasePlan, "the direct dependent", exampleD, exampleC)

                    assertTwoUpdateAndOneReleaseCommand(
                        releasePlan,
                        "the indirect dependent",
                        exampleC,
                        exampleB,
                        exampleD
                    )
                    assertHasNoDependentsAndIsOnLevel(releasePlan, "the indirect dependent", exampleC, 2)

                    assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 4)
                }
            }

            describe("parent is second dependent") {
                testReleaseAWithDependentBDAndCViaD("transitive/transitiveExplicitViaParentAsSecondDependent")
            }
        }

        given("project with explicit transitive dependent via pom (pom has dependency)") {
            testReleaseAWithDependentBDAndCViaD("transitive/transitiveExplicitViaPom")
        }
    }

    describe("cyclic dependencies") {

        given("project with direct cyclic dependency") {
            action("context Analyser which does not resolve poms") {
                val releasePlan = analyseAndCreateReleasePlan(exampleA.id, "cyclic/directCyclicDependency")
                assertProjectAWithDependentB(releasePlan)

                assertReleasePlanHasWarningWithDependencyGraph(
                    releasePlan,
                    "-> ${exampleB.id.identifier} -> ${exampleA.id.identifier}"
                )
            }
        }

        given("project with indirect cyclic dependency") {
            action("context Analyser which does not resolve poms") {
                val releasePlan = analyseAndCreateReleasePlan(exampleA.id, "cyclic/indirectCyclicDependency")
                assertReleaseAWithDependentBWithDependentC(releasePlan)

                assertReleasePlanHasWarningWithDependencyGraph(
                    releasePlan,
                    "-> ${exampleC.id.identifier} -> ${exampleB.id.identifier} -> ${exampleA.id.identifier}"
                )
            }
        }

        given("project with direct and indirect cyclic dependency") {
            action("context Analyser which does not resolve poms") {
                val releasePlan = analyseAndCreateReleasePlan(exampleA.id, "cyclic/directAndIndirectCyclicDependency")
                assertRootProjectOnlyReleaseAndReady(releasePlan, exampleA)

                test("root project has two dependents") {
                    assert(releasePlan).hasDependentsForProject(exampleA, exampleB, exampleD)
                }

                assertOneUpdateAndOneReleaseCommand(releasePlan, "direct cyclic dependent", exampleB, exampleA)
                assertHasNoDependentsAndIsOnLevel(releasePlan, "direct cyclic dependent", exampleB, 1)

                assertOneUpdateAndOneReleaseCommand(releasePlan, "indirect dependent", exampleD, exampleA)
                assertHasOneDependentAndIsOnLevel(releasePlan, "indirect dependent", exampleD, exampleC, 1)

                assertOneUpdateAndOneReleaseCommand(releasePlan, "indirect cyclic dependent", exampleC, exampleD)
                assertHasNoDependentsAndIsOnLevel(releasePlan, "indirect cyclic dependent", exampleC, 2)

                assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 4)

                assertReleasePlanHasWarningWithDependencyGraph(
                    releasePlan,
                    "-> ${exampleB.id.identifier} -> ${exampleA.id.identifier}",
                    "-> ${exampleC.id.identifier} -> ${exampleD.id.identifier} -> ${exampleA.id.identifier}"
                )
            }
        }

        given("project with direct and indirect cyclic dependency where the indirect dependency is also a direct one") {
            action("context Analyser which does not resolve poms") {
                val releasePlan =
                    analyseAndCreateReleasePlan(
                        exampleA.id,
                        "cyclic/directAndIndirectCyclicDependencyWhereIndirectIsAlsoDirect"
                    )
                assertRootProjectOnlyReleaseAndReady(releasePlan, exampleA)

                test("root project has two dependents") {
                    assert(releasePlan).hasDependentsForProject(exampleA, exampleB, exampleC)
                }

                assertOneDirectDependent(releasePlan, "direct cyclic dependent", exampleB, exampleC)
                assertTwoUpdateAndOneReleaseCommand(
                    releasePlan,
                    "(in)direct cyclic dependent",
                    exampleC,
                    exampleB,
                    exampleA
                )
                assertHasNoDependentsAndIsOnLevel(releasePlan, "(in)direct cyclic dependent", exampleC, 2)

                assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 3)

                assertReleasePlanHasWarningWithDependencyGraph(
                    releasePlan,
                    "-> ${exampleB.id.identifier} -> ${exampleA.id.identifier}",
                    "-> ${exampleC.id.identifier} -> ${exampleA.id.identifier}"
                )
            }
        }

        given("project with dependent which itself has a direct cyclic dependent") {
            testReleaseAWithDependentBAndX("cyclic/dependentWithDirectCyclicDependency", exampleD) { releasePlan ->
                assertOneDirectDependent(releasePlan, "the direct dependent", exampleD, exampleB)

                assertTwoUpdateAndOneReleaseCommand(releasePlan, "the cyclic partner", exampleB, exampleD, exampleA)
                assertHasOneDependentAndIsOnLevel(releasePlan, "the cyclic partner", exampleB, exampleC, 2)

                assertOneUpdateAndOneReleaseCommand(releasePlan, "the indirect dependent", exampleC, exampleB)
                assertHasNoDependentsAndIsOnLevel(releasePlan, "the indirect dependent", exampleC, 3)

                assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 4)

                assertReleasePlanHasWarningWithDependencyGraph(
                    releasePlan,
                    "-> ${exampleB.id.identifier} -> ${exampleD.id.identifier}"
                )
            }
        }
    }
})

private fun analyseAndCreateReleasePlan(projectToRelease: ProjectId, testDirectory: String) =
    analyseAndCreateReleasePlan(projectToRelease, getTestDirectory(testDirectory))

private fun analyseAndCreateReleasePlan(projectToRelease: ProjectId, testDirectory: File): ReleasePlan {
    val pomFileLoader = mock<PomFileLoader>()
    val analyser = Analyser(testDirectory, Session(), pomFileLoader)
    return analyseAndCreateReleasePlan(projectToRelease, analyser)
}

private fun analyseAndCreateReleasePlanWithMockedPomResolver(
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
            it.loadPomFileForGav(eq(Gav(exampleC.id.groupId, exampleC.id.artifactId, "2.0.0")), eq(null), any())
        }.thenReturn(File(oldPomsDir, "c-2.0.0.pom"))
        on {
            it.loadPomFileForGav(eq(Gav(exampleDeps.id.groupId, exampleDeps.id.artifactId, "8")), eq(null), any())
        }.thenReturn(File(oldPomsDir, "deps-8.pom"))
    }
    val analyser = Analyser(getTestDirectory(testDirectory), Session(), pomFileLoader)
    return analyseAndCreateReleasePlan(projectToRelease, analyser)
}

private fun analyseAndCreateReleasePlan(projectToRelease: ProjectId, analyser: Analyser): ReleasePlan {
    val jenkinsReleasePlanCreator = JenkinsReleasePlanCreator(VersionDeterminer())
    return jenkinsReleasePlanCreator.create(projectToRelease as MavenProjectId, analyser)
}

private fun ActionBody.testReleaseSingleProject(idAndVersions: IdAndVersions, directory: String) {
    val releasePlan = analyseAndCreateReleasePlan(idAndVersions.id, getTestDirectory(directory))

    assertSingleProject(releasePlan, idAndVersions)
}

private fun SpecBody.testReleaseAWithDependentBWithDependentC(directory: String, projectB: IdAndVersions = exampleB) {
    action("context Analyser which does not resolve poms") {
        val releasePlan = analyseAndCreateReleasePlan(exampleA.id, getTestDirectory(directory))
        assertReleaseAWithDependentBWithDependentC(releasePlan, projectB)
        assertReleasePlanHasNoWarnings(releasePlan)
    }
}

private fun SpecBody.testReleaseAWithDependentB(directory: String) {
    action("context Analyser which does not resolve poms") {
        val releasePlan = analyseAndCreateReleasePlan(exampleA.id, getTestDirectory(directory))
        assertProjectAWithDependentB(releasePlan)
        assertReleasePlanHasNoWarnings(releasePlan)
    }
}

private fun SpecBody.testReleaseBWithNoDependent(directory: String) {
    action("we release project B (no dependent at all)") {
        testReleaseSingleProject(exampleB, directory)
    }
}

private fun SpecBody.testReleaseAWithDependentBAndX(
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
        furtherAssertions(releasePlan)
    }
}


private fun SpecBody.testReleaseAWithDependentBDAndCViaD(directory: String) {
    testReleaseAWithDependentBAndX(directory, exampleD) { releasePlan ->
        assertOneDirectDependent(releasePlan, "the direct dependent", exampleB, exampleC)
        assertOneDirectDependent(releasePlan, "the parent", exampleD, exampleC)

        assertTwoUpdateAndOneReleaseCommand(releasePlan, "the indirect dependent", exampleC, exampleB, exampleD)
        assertHasNoDependentsAndIsOnLevel(releasePlan, "the indirect dependent", exampleC, 2)

        assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 4)
        assertReleasePlanHasNoWarnings(releasePlan)
    }
}

private fun SpecBody.testDuplicateProject(directory: String, vararg poms: Pair<String, String>) {
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
