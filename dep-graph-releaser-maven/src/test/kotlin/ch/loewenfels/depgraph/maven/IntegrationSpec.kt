package ch.loewenfels.depgraph.maven

import ch.loewenfels.depgraph.ConfigKey
import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.ReleasePlan
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.tutteli.atrium.*
import ch.tutteli.atrium.api.cc.en_GB.*
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
                val testDirectory = File("nonExistingProject/")
                expect {
                    analyseAndCreateReleasePlan(singleProjectIdAndVersions.id, testDirectory)
                }.toThrow<IllegalArgumentException> { message { contains(errMsg, testDirectory.absolutePath) } }
            }
        }

        given("empty directory") {
            val errMsg = "No pom files found"
            it("throws an IllegalArgumentException, mentioning `$errMsg`") {
                val testDirectory = getTestDirectory("errorCases/emptyDirectory")
                expect {
                    analyseAndCreateReleasePlan(singleProjectIdAndVersions.id, testDirectory)
                }.toThrow<IllegalArgumentException> { message { contains(errMsg, testDirectory.absolutePath) } }
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
                        contains(errMsg, wrongProject.identifier, singleProjectIdAndVersions.id.identifier)
                    }
                }
            }
        }

        given("project to release is a submodule") {
            it("throws an IllegalStateException, containing versions of project and multi module project") {
                expect {
                    analyseAndCreateReleasePlan(exampleA.id, "errorCases/rootIsSubmodule")
                }.toThrow<IllegalArgumentException> {
                    message {
                        contains(
                            "Cannot release a submodule",
                            exampleA.id.toString(),
                            exampleB.id.toString()
                        )
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
                            "${exampleB.id.identifier}:${exampleB.currentVersion} (${b.absolutePath})",
                            "${exampleA.id.identifier}:1.0.0"
                        )
                    }
                }
            }
        }

        given("single project, disableFor regex matches") {
            it("throws an IllegalArgumentException, mentioning that it does not make sense to deactivate the root project") {
                expect {
                    analyseAndCreateReleasePlan(
                        singleProjectIdAndVersions.id,
                        getTestDirectory("singleProject"),
                        JenkinsReleasePlanCreator.Options("releaseId", ".*:example")
                    )
                }.toThrow<IllegalArgumentException> {
                    message {
                        contains(
                            "Disabling a command of the root project does not make sense",
                            singleProjectIdAndVersions.id.identifier
                        )
                    }
                }
            }
        }
    }

    describe("warnings") {
        given("a project without group id") {
            test("release plan contains warning which includes file path") {
                val testDir = getTestDirectory("warnings/projectWithoutGroupId")
                val pom = File(testDir, "b.pom")
                val releasePlan = analyseAndCreateReleasePlan(exampleA.id, testDir)
                assert(releasePlan.warnings).containsStrictly({
                    contains(pom.absolutePath)
                })
            }
        }
        given("a project without version") {
            test("release plan contains warning which includes file path") {
                val testDir = getTestDirectory("warnings/projectWithoutVersion")
                val pom = File(testDir, "b.pom")
                val releasePlan = analyseAndCreateReleasePlan(exampleA.id, testDir)
                assert(releasePlan.warnings).containsStrictly({
                    contains(pom.absolutePath)
                })
            }
        }
    }

    describe("disableReleaseFor") {
        given("single project, regex does not match") {
            action("context Analyser which does not resolve poms") {
                val releasePlan = analyseAndCreateReleasePlan(
                    singleProjectIdAndVersions.id,
                    getTestDirectory("singleProject"),
                    JenkinsReleasePlanCreator.Options("releaseId", ".*notTheProject")
                )
                assertSingleProject(releasePlan, singleProjectIdAndVersions)
            }
        }

        given("project with single dependent and regex matches dependent") {
            action("context Analyser which does not resolve poms") {
                val releasePlan = analyseAndCreateReleasePlan(
                    exampleA.id,
                    getTestDirectory("managingVersions/inDependency"),
                    JenkinsReleasePlanCreator.Options("releaseId", "${exampleB.id.groupId}:${exampleB.id.artifactId}")
                )
                assertRootProjectWithDependents(releasePlan, exampleA, exampleB)

                assertOneUpdateAndOneDisabledReleaseCommand(releasePlan, "direct dependent", exampleB, exampleA)
                assertHasNoDependentsAndIsOnLevel(releasePlan, "direct dependent", exampleB, 1)

                assertReleasePlanHasNoWarningsAndNoInfos(releasePlan)
                assertReleasePlanIteratorReturnsRootAndStrictly(releasePlan, exampleB)
            }
        }

        given("project with implicit transitive dependent and regex matches direct dependent") {
            action("context Analyser which does not resolve poms") {
                val releasePlan = analyseAndCreateReleasePlan(
                    exampleA.id,
                    getTestDirectory("transitive/implicit"),
                    JenkinsReleasePlanCreator.Options("releaseId", "${exampleB.id.groupId}:${exampleB.id.artifactId}")
                )
                assertRootProjectWithDependents(releasePlan, exampleA, exampleB)

                assertOneUpdateAndOneDisabledReleaseCommand(releasePlan, "direct dependent", exampleB, exampleA)
                assertHasOneDependentAndIsOnLevel(releasePlan, "direct dependent", exampleB, exampleC, 1)

                assertOneDeactivatedUpdateAndOneDeactivatedReleaseCommand(
                    releasePlan,
                    "indirect dependent",
                    exampleC,
                    exampleB
                )
                assertHasNoDependentsAndIsOnLevel(releasePlan, "indirect dependent", exampleC, 2)

                assertReleasePlanHasNoWarningsAndNoInfos(releasePlan)
                assertReleasePlanIteratorReturnsRootAndStrictly(releasePlan, exampleB, exampleC)
            }
        }

        given("project with implicit transitive dependent and regex matches both dependent") {
            action("context Analyser which does not resolve poms") {
                val releasePlan = analyseAndCreateReleasePlan(
                    exampleA.id,
                    getTestDirectory("transitive/implicit"),
                    JenkinsReleasePlanCreator.Options(
                        "releaseId",
                        "${exampleB.id.groupId}:(${exampleB.id.artifactId}|${exampleC.id.artifactId})"
                    )
                )
                assertRootProjectWithDependents(releasePlan, exampleA, exampleB)

                assertOneUpdateAndOneDisabledReleaseCommand(releasePlan, "direct dependent", exampleB, exampleA)
                assertHasOneDependentAndIsOnLevel(releasePlan, "direct dependent", exampleB, exampleC, 1)

                assertOneDeactivatedUpdateAndOneDisabledReleaseCommand(
                    releasePlan,
                    "indirect dependent",
                    exampleC,
                    exampleB
                )
                assertHasNoDependentsAndIsOnLevel(releasePlan, "indirect dependent", exampleC, 2)

                assertReleasePlanHasNoWarningsAndNoInfos(releasePlan)
                assertReleasePlanIteratorReturnsRootAndStrictly(releasePlan, exampleB, exampleC)
            }
        }
    }

    describe("config") {
        given("no configuration") {
            action("it has an empty config list") {
                val releasePlan = analyseAndCreateReleasePlan(
                    singleProjectIdAndVersions.id,
                    getTestDirectory("singleProject"),
                    JenkinsReleasePlanCreator.Options("releaseId", Regex(".*notTheProject"), mapOf())
                )
                assertSingleProject(releasePlan, singleProjectIdAndVersions)
                assert(releasePlan.config).isEmpty()
            }
        }
        given("two configurations") {
            action("it contains both") {
                val releasePlan = analyseAndCreateReleasePlan(
                    singleProjectIdAndVersions.id,
                    getTestDirectory("singleProject"),
                    JenkinsReleasePlanCreator.Options(
                        "releaseId",
                        Regex(".*notTheProject"),
                        mapOf(ConfigKey.REMOTE_REGEX to "b", ConfigKey.COMMIT_PREFIX to "d")
                    )
                )
                assertSingleProject(releasePlan, singleProjectIdAndVersions)
                assert(releasePlan.config.entries).containsStrictly(
                    { keyValue(ConfigKey.REMOTE_REGEX, "b") },
                    { this.keyValue(ConfigKey.COMMIT_PREFIX, "d") })
            }
        }
    }

    given("single project with third party dependencies") {

        context("in root folder") {
            action("context Analyser which does not resolve poms") {
                val releasePlan =
                    analyseAndCreateReleasePlan(singleProjectIdAndVersions.id, getTestDirectory("singleProject"))
                assertSingleProject(releasePlan, singleProjectIdAndVersions)
                assertHasRelativePath(releasePlan, "root", singleProjectIdAndVersions, "./")
            }
        }
        context("in subfolder") {
            action("context Analyser which does not resolve poms") {
                val releasePlan =
                    analyseAndCreateReleasePlan(
                        singleProjectIdAndVersions.id,
                        getTestDirectory("singleProjectInSubfolder")
                    )
                assertSingleProject(releasePlan, singleProjectIdAndVersions)
                assertHasRelativePath(releasePlan, "root", singleProjectIdAndVersions, "subfolder/")
            }
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

        given("project with dependent and version in property") {
            testReleaseAWithDependentB("managingVersions/viaProperty")
            testReleaseBWithNoDependent("managingVersions/viaProperty")
        }

        //TODO project with dependent and version is $project.version

        given("project with dependent and version in dependency management") {
            testReleaseAWithDependentB("managingVersions/viaDependencyManagement")
            testReleaseBWithNoDependent("managingVersions/viaDependencyManagement")
        }

        given("project with dependent and version in bom") {
            action("context Analyser with a mocked PomFileResolver") {
                val releasePlan = analyseAndCreateReleasePlanWithPomResolverOldVersions(
                    exampleA.id, "managingVersions/viaBom"
                )
                assertRootProjectWithDependents(releasePlan, exampleA, exampleB)

                assertOnlyWaitingReleaseCommand(releasePlan, "dependent via bom", exampleB, exampleA)
                assertHasNoDependentsAndIsOnLevel(releasePlan, "dependent via bom", exampleB, 1)

                assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 2)
                assertReleasePlanHasNoWarningsAndNoInfos(releasePlan)
            }
        }

        given("project with dependent and version in bom and bom has itself in dep. management") {
            action("context Analyser with a mocked PomFileResolver") {
                val releasePlan = analyseAndCreateReleasePlanWithPomResolverOldVersions(
                    exampleA.id, "managingVersions/viaBomSelfDependency"
                )
                val deps = IdAndVersions(MavenProjectId("com.example", "deps"), "10-SNAPSHOT", "10", "11-SNAPSHOT")
                assertRootProjectWithDependents(releasePlan, exampleA, exampleB, deps)

                assertOneUpdateAndOneReleaseCommand(releasePlan, "bom", deps, exampleA)
                assertHasOneDependentAndIsOnLevel(releasePlan, "bom", deps, exampleB, 1)

                test("dependent via bom project has one waiting UpdateVersion (1 dep) and one waiting Release command (2 dep)") {
                    assert(releasePlan.getProject(exampleB.id)) {
                        idAndVersions(exampleB)
                        property(subject::commands).containsStrictly(
                            { isJenkinsUpdateDependencyWaiting(deps) },
                            { isJenkinsMavenReleaseWaiting(exampleB.nextDevVersion, deps, exampleA) }
                        )
                    }
                }
                assertHasNoDependentsAndIsOnLevel(releasePlan, "dependent via bom", exampleB, 2)

                assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 3)
                assertReleasePlanHasNoWarningsAndNoInfos(releasePlan)
            }
        }


        given("project with dependent and version in parent dependency management") {
            action("context Analyser with a mocked PomFileResolver") {
                val releasePlan = analyseAndCreateReleasePlanWithPomResolverOldVersions(
                    exampleA.id, "managingVersions/viaParent"
                )
                assertRootProjectWithDependents(releasePlan, exampleA, exampleB, exampleC)

                assertOneDirectDependent(releasePlan, "parent", exampleC, exampleB)
                assertOneUpdateAndOneMultiReleaseCommand(releasePlan, "parent", exampleC, exampleA)

                test("direct dependent project has one waiting UpdateVersion and one waiting Release command") {
                    assert(releasePlan.getProject(exampleB.id)) {
                        idAndVersions(exampleB)
                        property(subject::commands).containsStrictly(
                            { isJenkinsUpdateDependencyWaiting(exampleC) },
                            { isJenkinsMavenReleaseWaiting(exampleB.nextDevVersion, exampleA, exampleC) }
                        )
                    }
                }
                assertHasNoDependentsAndIsOnLevel(releasePlan, "direct dependent", exampleB, 2)

                assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 3)
                assertReleasePlanHasNoWarningsAndNoInfos(releasePlan)
            }
        }

        given("project with dependent and version in property of parent") {
            action("context Analyser with a mocked PomFileResolver") {
                val releasePlan = analyseAndCreateReleasePlanWithPomResolverOldVersions(
                    exampleA.id, "managingVersions/viaParentProperty"
                )
                assertRootProjectWithDependents(releasePlan, exampleA, exampleB, exampleC)

                assertOneDirectDependent(releasePlan, "parent", exampleC, exampleB)
                assertOneUpdateAndOneMultiReleaseCommand(releasePlan, "parent", exampleC, exampleA)

                test("direct dependent project has one waiting UpdateVersion and one waiting Release command") {
                    assert(releasePlan.getProject(exampleB.id)) {
                        idAndVersions(exampleB)
                        property(subject::commands).containsStrictly(
                            { isJenkinsUpdateDependencyWaiting(exampleC) },
                            { isJenkinsMavenReleaseWaiting(exampleB.nextDevVersion, exampleA, exampleC) }
                        )
                    }
                }
                assertHasNoDependentsAndIsOnLevel(releasePlan, "direct dependent", exampleB, 2)

                assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 3)
                assertReleasePlanHasNoWarningsAndNoInfos(releasePlan)
            }
        }

        given("project with dependent and version in bom which is imported in parent") {
            action("context Analyser with a mocked PomFileResolver") {
                val releasePlan = analyseAndCreateReleasePlanWithPomResolverOldVersions(
                    exampleA.id, "managingVersions/viaParentViaBom"
                )
                assertRootProjectWithDependents(releasePlan, exampleA, exampleB, exampleC)

                assertOneDirectDependent(releasePlan, "parent", exampleC, exampleB)

                test("direct dependent project has one waiting UpdateVersion and one waiting Release command") {
                    assert(releasePlan.getProject(exampleB.id)) {
                        idAndVersions(exampleB)
                        property(subject::commands).containsStrictly(
                            { isJenkinsUpdateDependencyWaiting(exampleC) },
                            { isJenkinsMavenReleaseWaiting(exampleB.nextDevVersion, exampleA, exampleC) }
                        )
                    }
                }
                assertHasNoDependentsAndIsOnLevel(releasePlan, "direct dependent", exampleB, 2)

                assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 3)
                assertReleasePlanHasNoWarningsAndNoInfos(releasePlan)
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
                val releasePlan = analyseAndCreateReleasePlanWithPomResolverOldVersions(
                    exampleA.id, "parentRelations/parentWithParent"
                )
                assertProjectAWithDependentBWithDependentC(releasePlan)
                assertReleasePlanHasNoWarningsAndNoInfos(releasePlan)
                assertReleasePlanIteratorReturnsRootAndStrictly(releasePlan, exampleB, exampleC)
            }
        }

        given("project with multi-module parent, old parents are not resolved") {
            action("context Analyser which does not resolve poms") {
                val releasePlan = analyseAndCreateReleasePlan(exampleA.id, "parentRelations/multiModuleParent")
                assertMultiModuleAWithSubmoduleBWithDependentC(releasePlan, IdAndVersions(exampleB.id, exampleA))
            }
        }

        given("project with multi-module parent, old parents are resolved") {
            action("context use an Analyser with a mocked PomFileResolver") {
                val releasePlan = analyseAndCreateReleasePlanWithPomResolverOldVersions(
                    exampleA.id, "parentRelations/multiModuleParent"
                )
                assertMultiModuleAWithSubmoduleBWithDependentC(releasePlan, IdAndVersions(exampleB.id, exampleA))
            }
        }

        given("project with multi-module parent which itself has a multi-module parent which is not root") {
            action("context Analyser which does not resolve poms") {
                val releasePlan = analyseAndCreateReleasePlan(
                    exampleA.id, "parentRelations/multiModuleParentWithMultiModuleParent"
                )

                assertRootProjectWithDependents(releasePlan, exampleA, exampleB, exampleD)

                assertOneUpdateAndOneMultiReleaseCommandAndIsOnLevelAndSubmodulesAreDependents(
                    releasePlan, "direct multi module", exampleB, exampleA, 1, exampleC
                )

                assertHasNoCommands(releasePlan, "indirect multi module", exampleC)
                assertHasSubmodules(releasePlan, "indirect multi module", exampleC, exampleD)
                assertHasOneDependentAndIsOnLevel(releasePlan, "indirect multi module", exampleC, exampleD, 1)

                assertOneUpdateCommand(releasePlan, "submodule", exampleD, exampleA)
                assertHasNoDependentsAndIsOnLevel(releasePlan, "submodule", exampleD, 1)

                assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 4)
                assertReleasePlanHasNoWarningsAndNoInfos(releasePlan)
                assertReleasePlanIteratorReturnsRootAnd(releasePlan, listOf(exampleB, exampleC, exampleD))
            }
        }
    }

    describe("transitive dependencies") {

        given("project with implicit transitive dependent") {
            testReleaseAWithDependentBWithDependentC("transitive/implicit")
        }

        given("project with explicit transitive dependent") {
            testReleaseAWithDependentBAndX("transitive/explicit", exampleC) { releasePlan ->

                assertOneDirectDependent(releasePlan, "the direct dependent", exampleB, exampleC)

                assertTwoUpdateAndOneReleaseCommand(releasePlan, "the indirect dependent", exampleC, exampleB, exampleA)
                assertHasNoDependentsAndIsOnLevel(releasePlan, "indirect dependent", exampleC, 2)

                assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 3)
                assertReleasePlanHasNoWarningsAndNoInfos(releasePlan)
                assertReleasePlanIteratorReturnsRootAndStrictly(releasePlan, exampleB, exampleC)
            }
        }

        given("project with explicit transitive dependent which itself has dependent") {
            action("context Analyser which does not resolve poms") {
                val releasePlan = analyseAndCreateReleasePlan(exampleA.id, "transitive/explicitWithDependent")
                assertRootProjectWithDependents(releasePlan, exampleA, exampleB, exampleD)

                assertOneDirectDependent(releasePlan, "direct dependent", exampleD, exampleB)

                assertTwoUpdateAndOneReleaseCommand(releasePlan, "indirect dependent", exampleB, exampleD, exampleA)
                assertHasOneDependentAndIsOnLevel(releasePlan, "indirect dependent", exampleB, exampleC, 2)

                assertOneUpdateAndOneReleaseCommand(releasePlan, "implicit indirect dependent", exampleC, exampleB)
                assertHasNoDependentsAndIsOnLevel(releasePlan, "implicit indirect dependent", exampleC, 3)

                assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 4)
                assertReleasePlanHasNoWarningsAndNoInfos(releasePlan)
                assertReleasePlanIteratorReturnsRootAndStrictly(releasePlan, exampleD, exampleB, exampleC)
            }
        }

        given("project with explicit transitive dependent over two levels") {
            action("context Analyser which does not resolve poms") {
                val releasePlan = analyseAndCreateReleasePlan(exampleA.id, "transitive/explicitOverTwoLevels")
                assertRootProjectWithDependents(releasePlan, exampleA, exampleB, exampleC, exampleD)

                assertOneDirectDependent(releasePlan, "direct dependent", exampleB, exampleD)

                assertTwoUpdateAndOneReleaseCommand(releasePlan, "indirect dependent", exampleD, exampleB, exampleA)
                assertHasOneDependentAndIsOnLevel(releasePlan, "indirect dependent", exampleD, exampleC, 2)

                assertTwoUpdateAndOneReleaseCommand(
                    releasePlan, "dependent of indirect dependent", exampleC, exampleD, exampleA
                )
                assertHasNoDependentsAndIsOnLevel(releasePlan, "dependent of indirect dependent", exampleC, 3)

                assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 4)
                assertReleasePlanHasNoWarningsAndNoInfos(releasePlan)
                assertReleasePlanIteratorReturnsRootAndStrictly(releasePlan, exampleB, exampleD, exampleC)
            }
        }

        given("project with explicit transitive dependent which has a diamond dependency") {
            action("context Analyser which tries to resolve poms") {
                val releasePlan = analyseAndCreateReleasePlan(exampleA.id, "transitive/explicitDiamondDependencies")
                assertRootProjectWithDependents(releasePlan, exampleA, exampleB, exampleC, exampleD)

                assertOneDirectDependent(releasePlan, "first direct dependent", exampleB, exampleC)
                assertOneDirectDependent(releasePlan, "second direct dependent", exampleD, exampleC)

                test("the indirect dependent project has three updateVersion and one Release command") {
                    assert(releasePlan.getProject(exampleC.id)) {
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
                assertReleasePlanHasNoWarningsAndNoInfos(releasePlan)
                assertReleasePlanIteratorReturnsRootAnd(releasePlan, listOf(exampleB, exampleD), listOf(exampleC))
            }
        }

        given("project with explicit transitive dependent which has a diamond dependency to submodules") {
            action("context Analyser which tries to resolve poms") {
                val releasePlan = analyseAndCreateReleasePlan(
                    exampleA.id, "transitive/explicitDiamondDependenciesToSubmodules"
                )
                assertRootProjectMultiReleaseCommand(releasePlan, exampleA)
                assertRootProjectHasSubmodules(releasePlan, exampleA, exampleB, exampleD)
                assertRootProjectHasDependents(releasePlan, exampleA, exampleB, exampleC, exampleD)

                assertHasNoCommands(releasePlan, "first submodule", exampleB)
                assertHasOneDependentAndIsOnLevel(releasePlan, "first submodule", exampleB, exampleC, 0)

                assertHasNoCommands(releasePlan, "second submodule", exampleD)
                assertHasOneDependentAndIsOnLevel(releasePlan, "second submodule", exampleD, exampleC, 0)

                test("the indirect dependent project has three updateVersion and one Release command") {
                    assert(releasePlan.getProject(exampleC.id)) {
                        idAndVersions(exampleC)
                        property(subject::commands).containsStrictly(
                            { isJenkinsUpdateDependencyWaiting(exampleB) },
                            { isJenkinsUpdateDependencyWaiting(exampleD) },
                            { isJenkinsUpdateDependencyWaiting(exampleA) },
                            { isJenkinsMavenReleaseWaiting(exampleC.nextDevVersion, exampleD, exampleB, exampleA) }
                        )
                    }
                }
                assertHasNoDependentsAndIsOnLevel(releasePlan, "indirect dependent", exampleC, 1)

                assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 4)
                assertReleasePlanHasNoWarningsAndNoInfos(releasePlan)
                assertReleasePlanIteratorReturnsRootAnd(releasePlan, listOf(exampleB, exampleD), listOf(exampleC))
            }
        }

        given("project with explicit transitive dependent via parent (parent with dependency)") {
            describe("parent is first dependent") {
                action("context Analyser which does not resolve poms") {
                    val releasePlan = analyseAndCreateReleasePlan(
                        exampleA.id, "transitive/explicitViaParentAsFirstDependent"
                    )
                    assertRootProjectWithDependents(releasePlan, exampleA, exampleB, exampleD)

                    assertOneDirectDependent(releasePlan, "the parent", exampleB, exampleC)
                    assertOneDirectDependent(releasePlan, "the direct dependent", exampleD, exampleC)

                    assertTwoUpdateAndOneReleaseCommand(
                        releasePlan, "the indirect dependent", exampleC, exampleB, exampleD
                    )
                    assertHasNoDependentsAndIsOnLevel(releasePlan, "the indirect dependent", exampleC, 2)

                    assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 4)
                    assertReleasePlanIteratorReturnsRootAnd(releasePlan, listOf(exampleB, exampleD), listOf(exampleC))
                }
            }

            describe("parent is second dependent") {
                testReleaseAWithDependentBDAndCViaD("transitive/explicitViaParentAsSecondDependent")
            }
        }

        given("project with explicit transitive dependent via pom (pom has dependency)") {
            testReleaseAWithDependentBDAndCViaD("transitive/explicitViaPom")
        }
    }

    describe("cyclic dependencies") {

        //TODO cyclic dependency with itself

        given("project with direct cyclic dependency") {
            action("context Analyser which does not resolve poms") {
                val releasePlan = analyseAndCreateReleasePlan(exampleA.id, "cyclic/directCyclicDependency")
                assertProjectAWithDependentB(releasePlan)

                assertReleasePlanHasWarningWithDependencyGraph(
                    releasePlan,
                    "-> ${exampleB.id.identifier} -> ${exampleA.id.identifier}"
                )
                assertReleasePlanHasNoInfos(releasePlan)
                assertReleasePlanIteratorReturnsRootAndStrictly(releasePlan, exampleB)
            }
        }

        given("project with indirect cyclic dependency") {
            action("context Analyser which does not resolve poms") {
                val releasePlan = analyseAndCreateReleasePlan(exampleA.id, "cyclic/indirectCyclicDependency")
                assertProjectAWithDependentBWithDependentC(releasePlan)

                assertReleasePlanHasWarningWithDependencyGraph(
                    releasePlan,
                    "-> ${exampleC.id.identifier} -> ${exampleB.id.identifier} -> ${exampleA.id.identifier}"
                )
                assertReleasePlanHasNoInfos(releasePlan)
                assertReleasePlanIteratorReturnsRootAndStrictly(releasePlan, exampleB, exampleC)
            }
        }

        given("project with direct and indirect cyclic dependency") {
            action("context Analyser which does not resolve poms") {
                val releasePlan = analyseAndCreateReleasePlan(exampleA.id, "cyclic/directAndIndirectCyclicDependency")
                assertRootProjectWithDependents(releasePlan, exampleA, exampleB, exampleD)

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
                assertReleasePlanHasNoInfos(releasePlan)
                assertReleasePlanIteratorReturnsRootAnd(releasePlan, listOf(exampleB, exampleD), listOf(exampleC))
            }
        }

        given("project with direct and indirect cyclic dependency where the indirect dependency is also a direct one") {
            action("context Analyser which does not resolve poms") {
                val releasePlan =
                    analyseAndCreateReleasePlan(
                        exampleA.id, "cyclic/directAndIndirectCyclicDependencyWhereIndirectIsAlsoDirect"
                    )
                assertRootProjectWithDependents(releasePlan, exampleA, exampleB, exampleC)

                assertOneDirectDependent(releasePlan, "direct cyclic dependent", exampleB, exampleC)
                assertTwoUpdateAndOneReleaseCommand(
                    releasePlan, "(in)direct cyclic dependent", exampleC, exampleB, exampleA
                )
                assertHasNoDependentsAndIsOnLevel(releasePlan, "(in)direct cyclic dependent", exampleC, 2)

                assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 3)

                assertReleasePlanHasWarningWithDependencyGraph(
                    releasePlan,
                    "-> ${exampleB.id.identifier} -> ${exampleA.id.identifier}",
                    "-> ${exampleC.id.identifier} -> ${exampleA.id.identifier}"
                )
                assertReleasePlanHasNoInfos(releasePlan)
                assertReleasePlanIteratorReturnsRootAndStrictly(releasePlan, exampleB, exampleC)
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
                assertReleasePlanHasNoInfos(releasePlan)
                assertReleasePlanIteratorReturnsRootAndStrictly(releasePlan, exampleD, exampleB, exampleC)
            }
        }

        //See also cyclic use cases in multi-module
    }

    describe("multi module projects") {
        given("inter module dependency and version in multi module root project") {
            testMultiModuleAWithSubmoduleBWithDependentSubmoduleC("multiModule/interDependencyVersionViaRoot")
        }

        given("inter module dependency and version in multi module root project is \$project.version") {
            testMultiModuleAWithSubmoduleBWithDependentSubmoduleC("multiModule/interDependencyVersionIsProjectVersionViaRoot")
        }

        given("inter module dependency and version self managed and in multi module root project is \$project.version") {
            testMultiModuleAWithSubmoduleBWithDependentSubmoduleC("multiModule/interDependencyVersionSelfAndViaRoot")
        }

        given("inter module dependency and version in multi module parent (which is not the root project)") {
            action("context Analyser which does not resolve poms") {

                val releasePlan = analyseAndCreateReleasePlan(
                    exampleA.id, "multiModule/interDependencyVersionViaParent"
                )

                assertRootProjectWithDependents(releasePlan, exampleA, exampleB, exampleC)

                assertOneUpdateAndOneMultiReleaseCommandAndIsOnLevelAndSubmodulesAreDependents(
                    releasePlan, "multi module", exampleB, exampleA, 1, exampleC, exampleD
                )

                assertOneUpdateCommand(releasePlan, "submodule-with-root-dependency", exampleC, exampleA)
                assertHasOneDependentAndIsOnLevel(releasePlan, "submodule-with-root-dependency", exampleC, exampleD, 1)

                assertHasNoCommands(releasePlan, "submodule-with-inter-dependency", exampleD)
                assertHasNoDependentsAndIsOnLevel(releasePlan, "submodule-with-inter-dependency", exampleD, 1)

                assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 4)
                assertReleasePlanHasNoWarningsAndNoInfos(releasePlan)
                assertReleasePlanIteratorReturnsRootAnd(releasePlan, listOf(exampleB, exampleC, exampleD))
            }
        }

        given("cyclic inter module dependency") {
            action("context Analyser which does not resolve poms") {

                val releasePlan = analyseAndCreateReleasePlan(exampleA.id, "multiModule/cyclicInterDependency")
                assertRootProjectMultiReleaseCommandWithSubmodulesAndSameDependents(
                    releasePlan, exampleA, exampleB, exampleC
                )

                // Notice that the order below depends on the hash function implemented.
                // Might fail if we update the JDK version, we can fix it then
                assertHasNoCommands(releasePlan, "first submodule", exampleB)
                assertHasNoDependentsAndIsOnLevel(releasePlan, "first submodule", exampleB, 0)

                assertHasNoCommands(releasePlan, "second submodule", exampleC)
                assertHasOneDependentAndIsOnLevel(releasePlan, "second submodule", exampleC, exampleB, 0)

                assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 3)
                assertReleasePlanHasNoWarnings(releasePlan)
                assertReleasePlanHasInfoWithDependencyGraph(
                    releasePlan,
                    "-> ${exampleB.id.identifier} -> ${exampleC.id.identifier}"
                )
                assertReleasePlanIteratorReturnsRootAnd(releasePlan, listOf(exampleB, exampleC))
            }
        }

        given("cyclic inter module dependency where one is the parent of the other and parent has other dependent") {
            action("context Analyser which does not resolve poms") {

                val releasePlan = analyseAndCreateReleasePlan(
                    exampleA.id, "multiModule/cyclicInterParentDependencyWithDependent"
                )
                assertRootProjectMultiReleaseCommand(releasePlan, exampleA)
                assertRootProjectHasSubmodules(releasePlan, exampleA, exampleB, exampleC)
                assertRootProjectHasDependents(releasePlan, exampleA, exampleC)

                assertHasNoCommands(releasePlan, "parent submodule", exampleC)
                assertHasTwoDependentsAndIsOnLevel(releasePlan, "parent submodule", exampleC, exampleB, exampleD, 0)

                assertHasNoCommands(releasePlan, "child submodule", exampleB)
                assertHasNoDependentsAndIsOnLevel(releasePlan, "child submodule", exampleB, 0)

                assertOneUpdateAndOneReleaseCommand(releasePlan, "dependent", exampleD, exampleC)
                assertHasNoDependentsAndIsOnLevel(releasePlan, "dependent", exampleD, 1)

                assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 4)
                assertReleasePlanHasNoWarnings(releasePlan)
                assertReleasePlanHasInfoWithDependencyGraph(
                    releasePlan,
                    "-> ${exampleB.id.identifier} -> ${exampleC.id.identifier}"
                )
                assertReleasePlanIteratorReturnsRootAnd(releasePlan, listOf(exampleB, exampleC), listOf(exampleD))
            }
        }

        given("cyclic inter module dependency where they are not a submodule of the same multi module (but common ancestor)") {
            action("context Analyser which does not resolve poms") {

                val releasePlan = analyseAndCreateReleasePlan(
                    exampleA.id, "multiModule/cyclicInterDependencyDifferentMultiModules"
                )
                assertRootProjectMultiReleaseCommandWithSubmodulesAndSameDependents(
                    releasePlan, exampleA, exampleB, exampleC
                )

                assertHasNoCommands(releasePlan, "multi module", exampleB)
                assertHasSubmodules(releasePlan, "multi module", exampleB, exampleD)
                assertHasOneDependentAndIsOnLevel(releasePlan, "parent submodule", exampleB, exampleD, 0)

                // Notice that the order below depends on the hash function implemented.
                // Might fail if we update the JDK version, we can fix it then
                assertHasNoCommands(releasePlan, "submodule", exampleC)
                assertHasOneDependentAndIsOnLevel(releasePlan, "submodule", exampleC, exampleD, 0)

                assertHasNoCommands(releasePlan, "nested submodule", exampleD)
                assertHasNoDependentsAndIsOnLevel(releasePlan, "nested submodule", exampleD, 0)

                assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 4)
                assertReleasePlanHasNoWarnings(releasePlan)
                assertReleasePlanHasInfoWithDependencyGraph(
                    releasePlan,
                    "-> ${exampleD.id.identifier} -> ${exampleC.id.identifier}"
                )
                assertReleasePlanIteratorReturnsRootAnd(releasePlan, listOf(exampleB, exampleC, exampleD))
            }
        }

        //TODO cyclic inter module dependency and a regular dependency -> regular has to be a warning, inter an info

        given("submodule with dependency") {
            action("context Analyser which does not resolve poms") {

                val releasePlan = analyseAndCreateReleasePlan(
                    exampleA.id, "multiModule/submoduleWithDependency"
                )
                assertRootProjectWithDependents(releasePlan, exampleA, exampleB, exampleD)

                assertOneUpdateAndOneMultiReleaseCommandAndIsOnLevelAndSubmodulesAreDependents(
                    releasePlan, "multi module", exampleB, exampleA, 2, exampleC
                )

                assertOneUpdateCommand(releasePlan, "submodule", exampleC, exampleD)
                assertHasNoDependentsAndIsOnLevel(releasePlan, "submodule", exampleC, 2)

                assertOneUpdateAndOneReleaseCommand(releasePlan, "dependency", exampleD, exampleA)
                assertHasTwoDependentsAndIsOnLevel(releasePlan, "dependency", exampleD, exampleC, exampleB, 1)

                assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 4)
                assertReleasePlanHasNoWarningsAndNoInfos(releasePlan)
                assertReleasePlanIteratorReturnsRootAnd(releasePlan, listOf(exampleD), listOf(exampleB, exampleC))
            }
        }

        given("submodule with dependency and version managed by multi-module") {
            action("context Analyser which does not resolve poms") {

                val releasePlan = analyseAndCreateReleasePlan(
                    exampleA.id, "multiModule/submoduleWithDependencyVersionManagedInMultiModule"
                )
                assertRootProjectWithDependents(releasePlan, exampleA, exampleB, exampleD)

                assertTwoUpdateAndOneMultiReleaseCommand(releasePlan, "multi module", exampleB, exampleD, exampleA)
                assertHasSubmodules(releasePlan, "multi module", exampleB, exampleC)
                assertHasOneDependentAndIsOnLevel(releasePlan, "multi module", exampleB, exampleC, 2)

                assertHasNoCommands(releasePlan, "submodule", exampleC)
                assertHasNoDependentsAndIsOnLevel(releasePlan, "submodule", exampleC, 2)

                assertOneUpdateAndOneReleaseCommand(releasePlan, "dependency", exampleD, exampleA)
                assertHasTwoDependentsAndIsOnLevel(releasePlan, "dependency", exampleD, exampleC, exampleB, 1)

                assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 4)
                assertReleasePlanHasNoWarningsAndNoInfos(releasePlan)
                assertReleasePlanIteratorReturnsRootAnd(releasePlan, listOf(exampleD), listOf(exampleB, exampleC))
            }
        }


        given("submodule with dependency which in turn has dependency on multi module => release cycle") {
            describe("cycle detected from multi module to dependent") {
                action("context Analyser which does not resolve poms") {
                    val releasePlan = analyseAndCreateReleasePlan(
                        exampleA.id, "multiModule/submoduleWithDependencyWithDepOnMultiModule"
                    )
                    assertRootProjectWithDependents(releasePlan, exampleA, exampleB)

                    assertOneUpdateAndOneMultiReleaseCommand(releasePlan, "multi module", exampleB, exampleA)
                    assertHasSubmodules(releasePlan, "multi module", exampleB, exampleC)
                    assertHasTwoDependentsAndIsOnLevel(releasePlan, "multi module", exampleB, exampleC, exampleD, 1)

                    assertOneUpdateCommand(releasePlan, "submodule", exampleC, exampleD)
                    assertHasNoDependentsAndIsOnLevel(releasePlan, "submodule", exampleC, 1)

                    assertOneUpdateAndOneReleaseCommand(releasePlan, "dependent", exampleD, exampleB)
                    assertHasOneDependentAndIsOnLevel(releasePlan, "dependent", exampleD, exampleC, 2)

                    assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 4)
                    assertReleasePlanHasWarningWithDependencyGraph(
                        releasePlan,
                        "${exampleD.id.identifier} -> ${exampleB.id.identifier}"
                    )
                    assertReleasePlanIteratorReturnsRootAnd(releasePlan, listOf(exampleB, exampleC), listOf(exampleD))
                }
            }

            describe("cycle detected from dependent to multi module") {
                action("context Analyser which does not resolve poms") {
                    val releasePlan = analyseAndCreateReleasePlan(
                        exampleA.id, "multiModule/submoduleWithDependencyAndMultiModuleDepOnDependency"
                    )
                    assertRootProjectWithDependents(releasePlan, exampleA, exampleD, exampleB)

                    assertOneUpdateAndOneMultiReleaseCommandAndIsOnLevelAndSubmodulesAreDependents(
                        releasePlan, "multi module", exampleB, exampleA, 2, exampleC
                    )

                    assertOneUpdateCommand(releasePlan, "submodule", exampleC, exampleD)
                    assertHasNoDependentsAndIsOnLevel(releasePlan, "submodule", exampleC, 2)

                    assertOneUpdateAndOneReleaseCommand(releasePlan, "dependent", exampleD, exampleA)
                    assertHasTwoDependentsAndIsOnLevel(releasePlan, "dependent", exampleD, exampleC, exampleB, 1)

                    assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 4)
                    assertReleasePlanHasWarningWithDependencyGraph(
                        releasePlan,
                        "${exampleB.id.identifier} -> ${exampleD.id.identifier}"
                    )
                    assertReleasePlanIteratorReturnsRootAnd(releasePlan, listOf(exampleD), listOf(exampleB, exampleC))
                }
            }
        }

        //TODO spec where we have a multi module parent:
        // once the dependent has a dependency on the top multi module
        // once the dependent has a dependency on multi module which is itself a multi module

        given("submodule of submodule with dependency") {
            action("context Analyser which does not resolve poms") {

                val releasePlan = analyseAndCreateReleasePlan(
                    exampleA.id, "multiModule/submoduleOfSubmoduleWithDependency"
                )
                assertRootProjectWithDependents(releasePlan, exampleA, exampleB, exampleE)

                assertOneUpdateAndOneMultiReleaseCommand(releasePlan, "multi module parent", exampleB, exampleA)
                assertHasSubmodules(releasePlan, "multi module parent", exampleB, exampleC)
                assertHasOneDependentAndIsOnLevel(releasePlan, "multi module parent", exampleB, exampleC, 2)

                assertHasNoCommands(releasePlan, "multi module", exampleC)
                assertHasSubmodules(releasePlan, "multi module", exampleC, exampleD)
                assertHasOneDependentAndIsOnLevel(releasePlan, "multi module parent", exampleC, exampleD, 2)

                assertOneUpdateCommand(releasePlan, "submodule", exampleD, exampleE)
                assertHasNoDependentsAndIsOnLevel(releasePlan, "submodule", exampleD, 2)

                assertOneUpdateAndOneReleaseCommand(releasePlan, "dependent", exampleE, exampleA)
                assertHasTwoDependentsAndIsOnLevel(releasePlan, "dependent", exampleE, exampleD, exampleB, 1)

                assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 5)
                assertReleasePlanHasNoWarningsAndNoInfos(releasePlan)
                assertReleasePlanIteratorReturnsRootAnd(
                    releasePlan,
                    listOf(exampleE),
                    listOf(exampleB, exampleC, exampleD)
                )
            }
        }

        given("submodule with inter dependent module and multi module not involved") {
            action("context Analyser which does not resolve poms") {

                val releasePlan = analyseAndCreateReleasePlan(
                    exampleA.id, "multiModule/submoduleWithDependencyAndMultiModuleNotInvolved"
                )
                assertRootProjectWithDependents(releasePlan, exampleA, exampleB, exampleC)

                assertOneMultiReleaseCommandAndIsOnLevelAndSubmodulesAreDependents(
                    releasePlan, "multi module", exampleB, exampleA, 1, exampleC, exampleD
                )

                assertOneUpdateCommand(releasePlan, "submodule", exampleC, exampleA)
                assertHasOneDependentAndIsOnLevel(releasePlan, "submodule", exampleC, exampleD, 1)

                assertHasNoCommands(releasePlan, "dependent submodule", exampleD)
                assertHasNoDependentsAndIsOnLevel(releasePlan, "dependent submodule", exampleD, 1)

                assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 4)
                assertReleasePlanHasNoWarningsAndNoInfos(releasePlan)
                assertReleasePlanIteratorReturnsRootAnd(releasePlan, listOf(exampleB, exampleC, exampleD))
            }
        }
    }
})

private fun SpecBody.testMultiModuleAWithSubmoduleBWithDependentSubmoduleC(testDirectory: String) {
    action("context Analyser which does not resolve poms") {

        val releasePlan = analyseAndCreateReleasePlan(exampleA.id, getTestDirectory(testDirectory))

        assertRootProjectMultiReleaseCommandWithSubmodulesAndSameDependents(releasePlan, exampleA, exampleB, exampleC)

        assertHasNoCommands(releasePlan, "direct dependent", exampleB)
        assertHasOneDependentAndIsOnLevel(releasePlan, "direct dependent", exampleB, exampleC, 0)

        assertHasNoCommands(releasePlan, "indirect dependent", exampleC)
        assertHasNoDependentsAndIsOnLevel(releasePlan, "indirect dependent", exampleC, 0)

        assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 3)
        assertReleasePlanHasNoWarningsAndNoInfos(releasePlan)
        assertReleasePlanIteratorReturnsRootAnd(releasePlan, listOf(exampleB, exampleC))
    }
}

private fun analyseAndCreateReleasePlan(projectToRelease: ProjectId, testDirectory: String) =
    analyseAndCreateReleasePlan(projectToRelease, getTestDirectory(testDirectory))

private fun analyseAndCreateReleasePlan(projectToRelease: ProjectId, testDirectory: File): ReleasePlan {
    val analyser = createAnalyserWhichDoesNotResolve(testDirectory)
    return analyseAndCreateReleasePlan(projectToRelease, analyser)
}

private fun createAnalyserWhichDoesNotResolve(testDirectory: File): Analyser =
    Analyser(testDirectory, Session(), mock())

private fun analyseAndCreateReleasePlan(
    projectToRelease: ProjectId,
    testDirectory: File,
    options: JenkinsReleasePlanCreator.Options
): ReleasePlan {
    val analyser = createAnalyserWhichDoesNotResolve(testDirectory)
    return analyseAndCreateReleasePlan(projectToRelease, analyser, options)
}


private fun analyseAndCreateReleasePlanWithPomResolverOldVersions(
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
            it.loadPomFileForGav(eq(Gav(exampleC.id.groupId, exampleC.id.artifactId, "1.0.0")), eq(null), any())
        }.thenReturn(File(oldPomsDir, "c-1.0.0.pom"))
        on {
            it.loadPomFileForGav(eq(Gav(exampleDeps.id.groupId, exampleDeps.id.artifactId, "8")), eq(null), any())
        }.thenReturn(File(oldPomsDir, "deps-8.pom"))
    }
    val analyser = Analyser(getTestDirectory(testDirectory), Session(), pomFileLoader)
    return analyseAndCreateReleasePlan(projectToRelease, analyser)
}

private fun analyseAndCreateReleasePlan(projectToRelease: ProjectId, analyser: Analyser): ReleasePlan {
    return analyseAndCreateReleasePlan(projectToRelease, analyser, JenkinsReleasePlanCreator.Options("releaseId", "^$"))
}

private fun analyseAndCreateReleasePlan(
    projectToRelease: ProjectId,
    analyser: Analyser,
    options: JenkinsReleasePlanCreator.Options
): ReleasePlan {
    val jenkinsReleasePlanCreator = JenkinsReleasePlanCreator(VersionDeterminer(), options)
    return jenkinsReleasePlanCreator.create(projectToRelease as MavenProjectId, analyser)
}

private fun ActionBody.testReleaseSingleProject(idAndVersions: IdAndVersions, directory: String) {
    val releasePlan = analyseAndCreateReleasePlan(idAndVersions.id, getTestDirectory(directory))
    assertSingleProject(releasePlan, idAndVersions)
}

private fun SpecBody.testReleaseAWithDependentBWithDependentC(directory: String, projectB: IdAndVersions = exampleB) {
    action("context Analyser which does not resolve poms") {
        val releasePlan = analyseAndCreateReleasePlan(exampleA.id, getTestDirectory(directory))
        assertProjectAWithDependentBWithDependentC(releasePlan, projectB)
        assertReleasePlanHasNoWarningsAndNoInfos(releasePlan)
        assertReleasePlanIteratorReturnsRootAndStrictly(releasePlan, exampleB, exampleC)
    }
}

private fun SpecBody.testReleaseAWithDependentB(directory: String) {
    action("context Analyser which does not resolve poms") {
        val releasePlan = analyseAndCreateReleasePlan(exampleA.id, getTestDirectory(directory))
        assertProjectAWithDependentB(releasePlan)
        assertReleasePlanHasNoWarningsAndNoInfos(releasePlan)
        assertReleasePlanIteratorReturnsRootAndStrictly(releasePlan, exampleB)
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
        assertRootProjectWithDependents(releasePlan, exampleA, exampleB, projectX)

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
        assertReleasePlanHasNoWarningsAndNoInfos(releasePlan)
        assertReleasePlanIteratorReturnsRootAnd(releasePlan, listOf(exampleB, exampleD), listOf(exampleC))
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
                    "directory: ${testDirectory.absolutePath}",
                    *poms.map {
                        "${exampleA.id.identifier}:${it.second} (${File(testDirectory, it.first).absolutePath})"
                    }.toTypedArray()
                )
            }
        }
    }
}
