package ch.loewenfels.depgraph.maven

import ch.loewenfels.depgraph.ConfigKey
import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.ReleasePlan
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.tutteli.atrium.*
import ch.tutteli.atrium.api.cc.en_GB.*
import ch.tutteli.niok.absolutePathAsString
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import fr.lteconsulting.pomexplorer.PomFileLoader
import fr.lteconsulting.pomexplorer.Session
import fr.lteconsulting.pomexplorer.model.Gav
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.Suite
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Path
import java.nio.file.Paths

object IntegrationSpec : Spek({

    val groupIdAndArtifactId = singleProjectIdAndVersions.id.identifier

    describe("validation errors") {

        context("non existing directory") {
            val errMsg = "directory does not exists"
            it("throws an IllegalArgumentException, mentioning `$errMsg`") {
                val testDirectory = Paths.get("nonExistingProject/")
                expect {
                    analyseAndCreateReleasePlan(singleProjectIdAndVersions.id, testDirectory)
                }.toThrow<IllegalArgumentException> { messageContains(errMsg, testDirectory.absolutePathAsString) }
            }
        }

        context("empty directory") {
            val errMsg = "No pom files found"
            it("throws an IllegalArgumentException, mentioning `$errMsg`") {
                val testDirectory = getTestDirectory("errorCases/emptyDirectory")
                expect {
                    analyseAndCreateReleasePlan(singleProjectIdAndVersions.id, testDirectory)
                }.toThrow<IllegalArgumentException> { messageContains(errMsg, testDirectory.absolutePathAsString) }
            }
        }

        context("no projects given") {
            val errMsg = "No project given which should be released, aborting now"
            it("throws an IllegalArgumentException, mentioning `$errMsg`") {
                expect {
                    analyseAndCreateReleasePlan(listOf(), "singleProject")
                }.toThrow<IllegalArgumentException> {
                    messageContains(errMsg)
                }
            }
        }

        context("project to release not in directory") {
            val errMsg = "Can only release a project which is part of the analysis"
            it("throws an IllegalArgumentException, mentioning `$errMsg`") {
                val wrongProject = MavenProjectId("com.other", "notThatOne")
                expect {
                    analyseAndCreateReleasePlan(wrongProject, "singleProject")
                }.toThrow<IllegalArgumentException> {
                    messageContains(errMsg, wrongProject.identifier, groupIdAndArtifactId)
                }
            }
        }

        context("one of the projects to release not in directory") {
            val errMsg = "Can only release a project which is part of the analysis"
            it("throws an IllegalArgumentException, mentioning `$errMsg`") {
                val wrongProject = MavenProjectId("com.other", "notThatOne")
                expect {
                    analyseAndCreateReleasePlan(listOf(singleProjectIdAndVersions.id, wrongProject), "singleProject")
                }.toThrow<IllegalArgumentException> {
                    messageContains(errMsg, wrongProject.identifier, groupIdAndArtifactId)
                }
            }
        }

        context("project to release is a submodule") {
            it("throws an IllegalStateException, containing versions of project and multi module project") {
                expect {
                    analyseAndCreateReleasePlan(exampleA.id, "errorCases/rootIsSubmodule")
                }.toThrow<IllegalArgumentException> {
                    messageContains(
                        "Cannot release a submodule",
                        exampleA.id.toString(),
                        exampleB.id.toString()
                    )
                }
            }
        }

        context("one of the projects to release is a submodule") {
            it("throws an IllegalStateException, containing versions of project and multi module project") {
                expect {
                    analyseAndCreateReleasePlan(listOf(exampleB.id, exampleA.id), "errorCases/rootIsSubmodule")
                }.toThrow<IllegalArgumentException> {
                    messageContains(
                        "Cannot release a submodule",
                        exampleA.id.toString(),
                        exampleB.id.toString()
                    )
                }
            }
        }

        context("duplicate projects, twice the same version") {
            testDuplicateProject(
                "errorCases/duplicatedProjectTwiceTheSameVersion",
                "a.pom" to exampleA.currentVersion,
                "a/a.pom" to exampleA.currentVersion
            )
        }

        context("duplicate projects, current and an older version") {
            testDuplicateProject(
                "errorCases/duplicatedProjectCurrentAndOld",
                "a.pom" to exampleA.currentVersion,
                "aOld.pom" to "1.0.1-SNAPSHOT"
            )
        }

        context("duplicate projects, twice the same version and an older version") {
            testDuplicateProject(
                "errorCases/duplicatedProjectTwiceTheSameVersionAndAnOld",
                "a.pom" to exampleA.currentVersion,
                "a/a.pom" to exampleA.currentVersion,
                "aOld.pom" to "1.0.1-SNAPSHOT"
            )
        }

        context("parent not in analysis") {
            it("throws an IllegalStateException, containing versions of project and parent and path of project") {
                val testDirectory = getTestDirectory("errorCases/parentNotInAnalysis")
                val b = testDirectory.resolve("b.pom")
                expect {
                    analyseAndCreateReleasePlan(exampleB.id, testDirectory)
                }.toThrow<IllegalStateException> {
                    messageContains(
                        "${exampleB.id.identifier}:${exampleB.currentVersion} (${b.absolutePathAsString})",
                        "${exampleA.id.identifier}:1.0.0"
                    )
                }
            }
        }

        context("single project, disableFor regex matches") {
            it("throws an IllegalArgumentException, mentioning that it does not make sense to deactivate the root project") {
                expect {
                    analyseAndCreateReleasePlan(
                        singleProjectIdAndVersions.id,
                        getTestDirectory("singleProject"),
                        JenkinsReleasePlanCreator.Options("releaseId", ".*:example")
                    )
                }.toThrow<IllegalArgumentException> {
                    messageContains(
                        "Disabling a command of the root project does not make sense",
                        groupIdAndArtifactId
                    )
                }
            }
        }
    }

    describe("warnings") {
        context("a project without group id") {
            it("release plan contains warning which includes file path") {
                val testDir = getTestDirectory("warnings/projectWithoutGroupId")
                val pom = testDir.resolve("b.pom")
                val releasePlan = analyseAndCreateReleasePlan(exampleA.id, testDir)
                assert(releasePlan.warnings).containsExactly {
                    contains(pom.absolutePathAsString)
                }
            }
        }
        context("a project without version") {
            it("release plan contains warning which includes file path") {
                val testDir = getTestDirectory("warnings/projectWithoutVersion")
                val pom = testDir.resolve("b.pom")
                val releasePlan = analyseAndCreateReleasePlan(exampleA.id, testDir)
                assert(releasePlan.warnings).containsExactly {
                    contains(pom.absolutePathAsString)
                }
            }
        }
    }

    describe("disableReleaseFor") {
        context("single project, regex does not match") {
            val releasePlan = analyseAndCreateReleasePlan(
                singleProjectIdAndVersions.id,
                getTestDirectory("singleProject"),
                JenkinsReleasePlanCreator.Options("releaseId", ".*notTheProject")
            )
            assertSingleProject(releasePlan, singleProjectIdAndVersions)
        }

        context("project with single dependent and regex matches dependent") {
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

        context("project with implicit transitive dependent and regex matches direct dependent") {
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

        context("project with implicit transitive dependent and regex matches both dependent") {
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

    describe("config") {
        context("no configuration") {
            val releasePlan = analyseAndCreateReleasePlan(
                singleProjectIdAndVersions.id,
                getTestDirectory("singleProject"),
                JenkinsReleasePlanCreator.Options("releaseId", Regex(".*notTheProject"), mapOf())
            )
            assertSingleProject(releasePlan, singleProjectIdAndVersions)
            it("config contains only empty ${ConfigKey.REMOTE_REGEX.asString()} and ${ConfigKey.JOB_MAPPING.asString()}") {
                assert(releasePlan.config.entries).containsExactly(
                    { isKeyValue(ConfigKey.REMOTE_REGEX, "") },
                    { isKeyValue(ConfigKey.JOB_MAPPING, "") }
                )
            }
        }

        context("two configurations") {
            val releasePlan = analyseSingleProjectInDirectory(
                "singleProject",
                mapOf(ConfigKey.REMOTE_REGEX to "b", ConfigKey.COMMIT_PREFIX to "d")
            )
            assertSingleProject(releasePlan, singleProjectIdAndVersions)
            it("it contains both + empty ${ConfigKey.JOB_MAPPING.asString()} (one is ${ConfigKey.REMOTE_REGEX.asString()}  thus not empty)") {
                assert(releasePlan.config.entries).containsExactly(
                    { isKeyValue(ConfigKey.REMOTE_REGEX, "b") },
                    { isKeyValue(ConfigKey.COMMIT_PREFIX, "d") },
                    { isKeyValue(ConfigKey.JOB_MAPPING, "") }
                )
            }
        }

        describe("project with ciManagement") {
            val predefinedRegex = "regex"
            val predefinedJobMapping = "test:project=project1"
            listOf(
                Triple(
                    "jenkinsInLower", listOf(
                        "$groupIdAndArtifactId#https://example.com\n" to
                            "\n$groupIdAndArtifactId=lib-example",
                        "$groupIdAndArtifactId#https://example.com\n$predefinedRegex" to
                            "$predefinedJobMapping\n$groupIdAndArtifactId=lib-example"
                    ), listOf()
                ),
                Triple(
                    "jenkinsInUpper", listOf(
                        "$groupIdAndArtifactId#https://example.com\n" to "",
                        "$groupIdAndArtifactId#https://example.com\n$predefinedRegex" to predefinedJobMapping
                    ), listOf()
                ),
                Triple(
                    "multiBranchWithTwiceJob", listOf(
                        "$groupIdAndArtifactId#https://example.com\n" to "",
                        "$groupIdAndArtifactId#https://example.com\n$predefinedRegex" to predefinedJobMapping
                    ), listOf()
                ),
                Triple(
                    "urlWithoutJob", listOf(
                        "" to "",
                        predefinedRegex to predefinedJobMapping
                    ), listOf(
                        "ciManagement url was invalid, cannot use it for ${ConfigKey.REMOTE_REGEX.asString()} nor for ${ConfigKey.JOB_MAPPING.asString()}, please adjust manually if necessary." +
                            "\nProject: ${singleProjectIdAndVersions.id.identifier}\nciManagement-url: https://example.com" +
                            "\n\nWe look for /job/ in the given <url>. Please define the url in the following format: https://server.com/jenkins/job/jobName"
                    )
                ),
                Triple(
                    "withoutSystem", listOf(
                        "" to "",
                        predefinedRegex to predefinedJobMapping
                    ), listOf()
                ),
                Triple(
                    "withoutUrl", listOf(
                        "" to "",
                        predefinedRegex to predefinedJobMapping
                    ), listOf()
                ),
                Triple(
                    "withWrongSystem", listOf(
                        "" to "",
                        predefinedRegex to predefinedJobMapping
                    ), listOf("ciManagement defined with an unsupported ci-system, please verify if you really want to release with jenkins." +
                        "\nProject: ${singleProjectIdAndVersions.id.identifier}\nSystem: gocd\nUrl: https://example.com/jenkins"
                    )
                )
            ).forEach { (folder, list, warnings) ->
                context(folder) {
                    val (p1, p2) = list
                    val (regex1, jobMapping1) = p1
                    val (regex2, jobMapping2) = p2
                    context("no other remoteRegex and jobMappings") {
                        val releasePlan = analyseSingleProjectInDirectory(
                            "ciManagement/$folder", mapOf()
                        )
                        assertHasConfig(releasePlan, ConfigKey.REMOTE_REGEX, regex1)
                        assertHasConfig(releasePlan, ConfigKey.JOB_MAPPING, jobMapping1)
                        if (warnings.isEmpty()) {
                            assertReleasePlanHasNoWarnings(releasePlan)
                        } else {
                            assertReleasePlanHasWarningsAboutCiManagement(releasePlan, warnings)
                        }
                        assertReleasePlanHasNoInfos(releasePlan)
                    }
                    context("other remoteRegex and other jobMappings") {
                        val releasePlan = analyseSingleProjectInDirectory(
                            "ciManagement/$folder", mapOf(
                            ConfigKey.REMOTE_REGEX to predefinedRegex,
                            ConfigKey.JOB_MAPPING to predefinedJobMapping
                        )
                        )
                        assertHasConfig(releasePlan, ConfigKey.REMOTE_REGEX, regex2)
                        assertHasConfig(releasePlan, ConfigKey.JOB_MAPPING, jobMapping2)
                        if (warnings.isEmpty()) {
                            assertReleasePlanHasNoWarnings(releasePlan)
                        } else {
                            assertReleasePlanHasWarningsAboutCiManagement(releasePlan, warnings)
                        }
                        assertReleasePlanHasNoInfos(releasePlan)
                    }
                    context("other remoteRegex and other jobMappings both starting and ending with \n") {
                        val releasePlan = analyseSingleProjectInDirectory(
                            "ciManagement/$folder", mapOf(
                            ConfigKey.REMOTE_REGEX to "\n" + predefinedRegex + "\n",
                            ConfigKey.JOB_MAPPING to "\n" + predefinedJobMapping + "\n"
                        )
                        )
                        assertHasConfig(releasePlan, ConfigKey.REMOTE_REGEX, regex2)
                        assertHasConfig(releasePlan, ConfigKey.JOB_MAPPING, jobMapping2)
                        if (warnings.isEmpty()) {
                            assertReleasePlanHasNoWarnings(releasePlan)
                        } else {
                            assertReleasePlanHasWarningsAboutCiManagement(releasePlan, warnings)
                        }
                        assertReleasePlanHasNoInfos(releasePlan)
                    }
                }
            }
        }
    }

    describe("single project with third party dependencies") {

        context("in root folder") {
            val releasePlan = analyseAndCreateReleasePlan(singleProjectIdAndVersions.id, "singleProject")
            assertSingleProject(releasePlan, singleProjectIdAndVersions)
            assertHasRelativePath(releasePlan, "root", singleProjectIdAndVersions, "./")
        }
        context("in sub folder") {
            val releasePlan = analyseAndCreateReleasePlan(singleProjectIdAndVersions.id, "singleProjectInSubfolder")
            assertSingleProject(releasePlan, singleProjectIdAndVersions)
            assertHasRelativePath(releasePlan, "root", singleProjectIdAndVersions, "subfolder/")
        }
    }

    describe("project with dependent only via dependency management") {
        testReleaseAWithDependentB("oneDependentOnlyViaManagement")
        testReleaseBWithNoDependent("oneDependentOnlyViaManagement")
    }

    describe("two projects unrelated") {
        context("we release project A") {
            testReleaseSingleProject(exampleA, "unrelatedProjects")
            testReleaseBWithNoDependent("unrelatedProjects")
        }

        context("we release both project A and project B") {
            val releasePlan = analyseAndCreateReleasePlan(listOf(exampleA.id, exampleB.id), "unrelatedProjects")
            assertSyntheticRootProject(releasePlan)

            assertHasTwoDependentsAndIsOnLevel(releasePlan, "synthetic root", syntheticRoot, exampleA, exampleB, 0)

            assertOneReleaseCommandWaitingForSyntheticRoot(releasePlan, "project A", exampleA)
            assertHasNoDependentsAndIsOnLevel(releasePlan, "project A", exampleA, 1)
            assertHasRelativePath(releasePlan, "project A", exampleA, "./")

            assertOneReleaseCommandWaitingForSyntheticRoot(releasePlan, "project B", exampleB)
            assertHasNoDependentsAndIsOnLevel(releasePlan, "project B", exampleB, 1)
            assertHasRelativePath(releasePlan, "project B", exampleB, "./")

            assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 3)
            assertReleasePlanHasNoWarningsAndNoInfos(releasePlan)
            it("ReleasePlan.iterator() returns the root Project followed by the unrelated projects in any order") {
                assert(releasePlan).iteratorReturnsRootAndInOrderGrouped(listOf(exampleA.id, exampleB.id))
            }
        }
    }

    describe("different ways of managing versions") {
        context("project with dependent and version in dependency itself") {
            testReleaseAWithDependentB("managingVersions/inDependency")
            testReleaseBWithNoDependent("managingVersions/inDependency")
        }

        context("project with dependent and version in property") {
            testReleaseAWithDependentB("managingVersions/viaProperty")
            testReleaseBWithNoDependent("managingVersions/viaProperty")
        }

        context("project with dependent and version is \$project.version") {
            testReleaseAWithDependentB("managingVersions/isProjectVersion")
            testReleaseBWithNoDependent("managingVersions/isProjectVersion")
        }

        context("project with dependent and version in dependency management") {
            testReleaseAWithDependentB("managingVersions/viaDependencyManagement")
            testReleaseBWithNoDependent("managingVersions/viaDependencyManagement")
        }

        context("project with dependent and version in bom") {
            val releasePlan = analyseAndCreateReleasePlanWithPomResolverOldVersions(
                exampleA.id, "managingVersions/viaBom"
            )
            assertRootProjectWithDependents(releasePlan, exampleA, exampleB)

            assertOnlyWaitingReleaseCommand(releasePlan, "dependent via bom", exampleB, exampleA)
            assertHasNoDependentsAndIsOnLevel(releasePlan, "dependent via bom", exampleB, 1)

            assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 2)
            assertReleasePlanHasNoWarningsAndNoInfos(releasePlan)
        }

        describe("project with dependent and version in bom and bom has itself in dep. management") {
            context("context Analyser with a mocked PomFileResolver") {
                val releasePlan = analyseAndCreateReleasePlanWithPomResolverOldVersions(
                    exampleA.id, "managingVersions/viaBomSelfDependency"
                )
                val deps = IdAndVersions(MavenProjectId("com.example", "deps"), "10-SNAPSHOT", "10", "11-SNAPSHOT")
                assertRootProjectWithDependents(releasePlan, exampleA, exampleB, deps)

                assertOneUpdateAndOneReleaseCommand(releasePlan, "bom", deps, exampleA)
                assertHasOneDependentAndIsOnLevel(releasePlan, "bom", deps, exampleB, 1)

                it("dependent via bom project has one waiting UpdateVersion (1 dep) and one waiting Release command (2 dep)") {
                    assert(releasePlan.getProject(exampleB.id)) {
                        idAndVersions(exampleB)
                        property(subject::commands).containsExactly(
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


        describe("project with dependent and version in parent dependency management") {
            context("context Analyser with a mocked PomFileResolver") {
                val releasePlan = analyseAndCreateReleasePlanWithPomResolverOldVersions(
                    exampleA.id, "managingVersions/viaParent"
                )
                assertRootProjectWithDependents(releasePlan, exampleA, exampleB, exampleC)

                assertOneDirectDependent(releasePlan, "parent", exampleC, exampleB)

                it("direct dependent project has one waiting UpdateVersion and one waiting Release command") {
                    assert(releasePlan.getProject(exampleB.id)) {
                        idAndVersions(exampleB)
                        property(subject::commands).containsExactly(
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

        describe("project with dependent and version in property of parent") {
            context("context Analyser with a mocked PomFileResolver") {
                val releasePlan = analyseAndCreateReleasePlanWithPomResolverOldVersions(
                    exampleA.id, "managingVersions/viaParentProperty"
                )
                assertRootProjectWithDependents(releasePlan, exampleA, exampleB, exampleC)

                assertOneDirectDependent(releasePlan, "parent", exampleC, exampleB)

                it("direct dependent project has one waiting UpdateVersion and one waiting Release command") {
                    assert(releasePlan.getProject(exampleB.id)) {
                        idAndVersions(exampleB)
                        property(subject::commands).containsExactly(
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

        describe("project with dependent and version in bom which is imported in parent") {
            context("context Analyser with a mocked PomFileResolver") {
                val releasePlan = analyseAndCreateReleasePlanWithPomResolverOldVersions(
                    exampleA.id, "managingVersions/viaParentViaBom"
                )
                assertRootProjectWithDependents(releasePlan, exampleA, exampleB, exampleC)

                assertOneDirectDependent(releasePlan, "parent", exampleC, exampleB)

                it("direct dependent project has one waiting UpdateVersion and one waiting Release command") {
                    assert(releasePlan.getProject(exampleB.id)) {
                        idAndVersions(exampleB)
                        property(subject::commands).containsExactly(
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

        context("project with parent dependency") {
            testReleaseAWithDependentB("parentRelations/parent")
            testReleaseBWithNoDependent("parentRelations/parent")
        }

        context("project with parent which itself has a parent, old parents are not resolved") {
            testReleaseAWithDependentBWithDependentC("parentRelations/parentWithParent")
        }

        context("project with parent which itself has a parent, old parents are resolved") {
            val releasePlan = analyseAndCreateReleasePlanWithPomResolverOldVersions(
                exampleA.id, "parentRelations/parentWithParent"
            )
            assertProjectAWithDependentBWithDependentC(releasePlan)
            assertReleasePlanHasNoWarningsAndNoInfos(releasePlan)
            assertReleasePlanIteratorReturnsRootAndStrictly(releasePlan, exampleB, exampleC)
        }

        context("project with multi-module parent, old parents are not resolved") {
            val releasePlan = analyseAndCreateReleasePlan(exampleA.id, "parentRelations/multiModuleParent")
            assertMultiModuleAWithSubmoduleBWithDependentC(releasePlan, IdAndVersions(exampleB.id, exampleA))
        }

        context("project with multi-module parent, old parents are resolved") {
            val releasePlan = analyseAndCreateReleasePlanWithPomResolverOldVersions(
                exampleA.id, "parentRelations/multiModuleParent"
            )
            assertMultiModuleAWithSubmoduleBWithDependentC(releasePlan, IdAndVersions(exampleB.id, exampleA))
        }

        context("project with multi-module parent which itself has a multi-module parent which is not root") {
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

    describe("transitive dependencies") {

        context("project with implicit transitive dependent") {
            testReleaseAWithDependentBWithDependentC("transitive/implicit")
        }

        context("project with explicit transitive dependent") {
            testReleaseAWithDependentBAndX("transitive/explicit", exampleC) { releasePlan ->

                assertOneDirectDependent(releasePlan, "the direct dependent", exampleB, exampleC)

                assertTwoUpdateAndOneReleaseCommand(releasePlan, "the indirect dependent", exampleC, exampleB, exampleA)
                assertHasNoDependentsAndIsOnLevel(releasePlan, "indirect dependent", exampleC, 2)

                assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 3)
                assertReleasePlanHasNoWarningsAndNoInfos(releasePlan)
                assertReleasePlanIteratorReturnsRootAndStrictly(releasePlan, exampleB, exampleC)
            }
        }

        context("project with explicit transitive dependent which itself has dependent") {
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

        context("project with explicit transitive dependent over two levels") {
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

        describe("project with explicit transitive dependent which has a diamond dependency") {
            context("context Analyser which tries to resolve poms") {
                val releasePlan = analyseAndCreateReleasePlan(exampleA.id, "transitive/explicitDiamondDependencies")
                assertRootProjectWithDependents(releasePlan, exampleA, exampleB, exampleC, exampleD)

                assertOneDirectDependent(releasePlan, "first direct dependent", exampleB, exampleC)
                assertOneDirectDependent(releasePlan, "second direct dependent", exampleD, exampleC)

                it("the indirect dependent project has three updateVersion and one Release command") {
                    assert(releasePlan.getProject(exampleC.id)) {
                        idAndVersions(exampleC)
                        property(subject::commands).containsExactly(
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

        describe("project with explicit transitive dependent which has a diamond dependency to submodules") {
            context("context Analyser which tries to resolve poms") {
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

                it("the indirect dependent project has three updateVersion and one Release command") {
                    assert(releasePlan.getProject(exampleC.id)) {
                        idAndVersions(exampleC)
                        property(subject::commands).containsExactly(
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

        describe("project with explicit transitive dependent via parent (parent with dependency)") {
            context("parent is first dependent") {
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

            describe("parent is second dependent") {
                testReleaseAWithDependentBDAndCViaD("transitive/explicitViaParentAsSecondDependent")
            }
        }

        describe("project with explicit transitive dependent via pom (pom has dependency)") {
            testReleaseAWithDependentBDAndCViaD("transitive/explicitViaPom")
        }
    }

    describe("cyclic dependencies") {

        context("project with cyclic dependency with itself") {
            val releasePlan = analyseAndCreateReleasePlan(exampleA.id, "cyclic/cyclicDependencyWithItself")
            assertHasNoDependentsAndIsOnLevel(releasePlan, "direct dependent", exampleA, 0)
            assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 1)

            assertReleasePlanHasWarningWithDependencyGraph(
                releasePlan,
                "-> ${exampleA.id.identifier} -> ${exampleA.id.identifier}"
            )
            assertReleasePlanHasNoInfos(releasePlan)
            assertReleasePlanIteratorReturnsRootAndStrictly(releasePlan)
        }

        context("project with direct cyclic dependency") {
            val releasePlan = analyseAndCreateReleasePlan(exampleA.id, "cyclic/directCyclicDependency")
            assertProjectAWithDependentB(releasePlan)

            assertReleasePlanHasWarningWithDependencyGraph(
                releasePlan,
                "-> ${exampleB.id.identifier} -> ${exampleA.id.identifier}"
            )
            assertReleasePlanHasNoInfos(releasePlan)
            assertReleasePlanIteratorReturnsRootAndStrictly(releasePlan, exampleB)
        }

        context("project with indirect cyclic dependency") {
            val releasePlan = analyseAndCreateReleasePlan(exampleA.id, "cyclic/indirectCyclicDependency")
            assertProjectAWithDependentBWithDependentC(releasePlan)

            assertReleasePlanHasWarningWithDependencyGraph(
                releasePlan,
                "-> ${exampleC.id.identifier} -> ${exampleB.id.identifier} -> ${exampleA.id.identifier}"
            )
            assertReleasePlanHasNoInfos(releasePlan)
            assertReleasePlanIteratorReturnsRootAndStrictly(releasePlan, exampleB, exampleC)
        }

        context("project with direct and indirect cyclic dependency") {
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

        context("project with direct and indirect cyclic dependency where the indirect dependency is also a direct one") {
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

        context("project with dependent which itself has a direct cyclic dependent") {
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
        context("inter module dependency and version in multi module root project") {
            testMultiModuleAWithSubmoduleBWithDependentSubmoduleC("multiModule/interDependencyVersionViaRoot")
        }

        context("inter module dependency and version in multi module root project is \$project.version") {
            testMultiModuleAWithSubmoduleBWithDependentSubmoduleC("multiModule/interDependencyVersionIsProjectVersionViaRoot")
        }

        context("inter module dependency and version self managed and in multi module root project is \$project.version") {
            testMultiModuleAWithSubmoduleBWithDependentSubmoduleC("multiModule/interDependencyVersionSelfAndViaRoot")
        }

        context("inter module dependency and version in multi module parent (which is not the root project)") {

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

        context("cyclic inter module dependency") {

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

        context("cyclic inter module dependency where one is the parent of the other and parent has other dependent") {

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

        context("cyclic inter module dependency where they are not a submodule of the same multi module (but common ancestor)") {

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

        //TODO cyclic inter module dependency and a regular dependency -> regular has to be a warning, inter an info
        // => https://github.com/loewenfels/dep-graph-releaser/issues/26

        context("submodule with dependency") {

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

        context("submodule with dependency and version managed by multi-module") {

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


        describe("submodule with dependency which in turn has dependency on multi module => release cycle") {
            context("cycle detected from multi module to dependent") {
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

            context("cycle detected from dependent to multi module") {
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

        //TODO spec where we have a multi module parent:
        // once the dependent has a dependency on the top multi module
        // once the dependent has a dependency on multi module which is itself a multi module
        // => https://github.com/loewenfels/dep-graph-releaser/issues/27

        context("submodule of submodule with dependency") {

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

        context("submodule with inter dependent module and multi module not involved") {

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
})

private fun Suite.testMultiModuleAWithSubmoduleBWithDependentSubmoduleC(testDirectory: String) {

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

private fun analyseAndCreateReleasePlan(projectToRelease: ProjectId, testDirectory: String) =
    analyseAndCreateReleasePlan(projectToRelease, getTestDirectory(testDirectory))

private fun analyseAndCreateReleasePlan(projectToRelease: ProjectId, testDirectory: Path): ReleasePlan {
    val analyser = createAnalyserWhichDoesNotResolve(testDirectory)
    return analyseAndCreateReleasePlan(projectToRelease, analyser)
}

private fun analyseAndCreateReleasePlan(projectsToRelease: List<MavenProjectId>, testDirectory: String): ReleasePlan {
    val analyser = createAnalyserWhichDoesNotResolve(getTestDirectory(testDirectory))
    return analyseAndCreateReleasePlan(projectsToRelease, analyser, JenkinsReleasePlanCreator.Options("id", "^$"))
}

private fun createAnalyserWhichDoesNotResolve(testDirectory: Path): Analyser =
    Analyser(testDirectory, Session(), mock())

fun analyseSingleProjectInDirectory(dir: String, config: Map<ConfigKey, String>) =
    analyseAndCreateReleasePlan(
        singleProjectIdAndVersions.id,
        getTestDirectory(dir),
        JenkinsReleasePlanCreator.Options("releaseId", Regex(".*#notTheProject"), config)
    )

private fun analyseAndCreateReleasePlan(
    projectToRelease: ProjectId,
    testDirectory: Path,
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
        }.thenReturn(oldPomsDir.resolve("a-1.0.0.pom").toFile())
        on {
            it.loadPomFileForGav(eq(Gav(exampleA.id.groupId, exampleA.id.artifactId, "0.9.0")), eq(null), any())
        }.thenReturn(oldPomsDir.resolve("a-0.9.0.pom").toFile())
        on {
            it.loadPomFileForGav(eq(Gav(exampleB.id.groupId, exampleB.id.artifactId, "1.0.0")), eq(null), any())
        }.thenReturn(oldPomsDir.resolve("b-1.0.0.pom").toFile())
        on {
            it.loadPomFileForGav(eq(Gav(exampleC.id.groupId, exampleC.id.artifactId, "2.0.0")), eq(null), any())
        }.thenReturn(oldPomsDir.resolve("c-2.0.0.pom").toFile())
        on {
            it.loadPomFileForGav(eq(Gav(exampleC.id.groupId, exampleC.id.artifactId, "1.0.0")), eq(null), any())
        }.thenReturn(oldPomsDir.resolve("c-1.0.0.pom").toFile())
        on {
            it.loadPomFileForGav(eq(Gav(exampleDeps.id.groupId, exampleDeps.id.artifactId, "8")), eq(null), any())
        }.thenReturn(oldPomsDir.resolve("deps-8.pom").toFile())
    }
    val analyser = Analyser(getTestDirectory(testDirectory), Session(), pomFileLoader)
    return analyseAndCreateReleasePlan(projectToRelease, analyser)
}

private fun analyseAndCreateReleasePlan(projectToRelease: ProjectId, analyser: Analyser): ReleasePlan =
    analyseAndCreateReleasePlan(projectToRelease, analyser, JenkinsReleasePlanCreator.Options("releaseId", "^$"))

private fun analyseAndCreateReleasePlan(
    projectToRelease: ProjectId,
    analyser: Analyser,
    options: JenkinsReleasePlanCreator.Options
): ReleasePlan = analyseAndCreateReleasePlan(listOf(projectToRelease as MavenProjectId), analyser, options)

private fun analyseAndCreateReleasePlan(
    projectsToRelease: List<MavenProjectId>,
    analyser: Analyser,
    options: JenkinsReleasePlanCreator.Options
): ReleasePlan {
    val jenkinsReleasePlanCreator = JenkinsReleasePlanCreator(VersionDeterminer(), options)
    return jenkinsReleasePlanCreator.create(projectsToRelease, analyser)
}

private fun Suite.testReleaseSingleProject(idAndVersions: IdAndVersions, directory: String) {
    val releasePlan = analyseAndCreateReleasePlan(idAndVersions.id, directory)
    assertSingleProject(releasePlan, idAndVersions)
}

private fun Suite.testReleaseAWithDependentBWithDependentC(directory: String, projectB: IdAndVersions = exampleB) {
    val releasePlan = analyseAndCreateReleasePlan(exampleA.id, getTestDirectory(directory))
    assertProjectAWithDependentBWithDependentC(releasePlan, projectB)
    assertReleasePlanHasNoWarningsAndNoInfos(releasePlan)
    assertReleasePlanIteratorReturnsRootAndStrictly(releasePlan, exampleB, exampleC)
}

private fun Suite.testReleaseAWithDependentB(directory: String) {
    val releasePlan = analyseAndCreateReleasePlan(exampleA.id, getTestDirectory(directory))
    assertProjectAWithDependentB(releasePlan)
    assertReleasePlanHasNoWarningsAndNoInfos(releasePlan)
    assertReleasePlanIteratorReturnsRootAndStrictly(releasePlan, exampleB)
}

private fun Suite.testReleaseBWithNoDependent(directory: String) {
    context("we release project B (no dependent at all)") {
        testReleaseSingleProject(exampleB, directory)
    }
}

private fun Suite.testReleaseAWithDependentBAndX(
    directory: String,
    projectX: IdAndVersions,
    furtherAssertions: Suite.(ReleasePlan) -> Unit
) {
    val releasePlan = analyseAndCreateReleasePlan(exampleA.id, getTestDirectory(directory))
    assertRootProjectWithDependents(releasePlan, exampleA, exampleB, projectX)

    furtherAssertions(releasePlan)
}


private fun Suite.testReleaseAWithDependentBDAndCViaD(directory: String) {
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

private fun Suite.testDuplicateProject(directory: String, vararg poms: Pair<String, String>) {
    it("throws an IllegalStateException, containing versions of all projects inclusive path") {
        val testDirectory = getTestDirectory(directory)
        expect {
            analyseAndCreateReleasePlan(exampleA.id, testDirectory)
        }.toThrow<IllegalStateException> {
            message {
                contains(
                    "directory: ${testDirectory.absolutePathAsString}",
                    *poms.map {
                        "${exampleA.id.identifier}:${it.second} (${testDirectory.resolve(it.first).absolutePathAsString})"
                    }.toTypedArray()
                )
            }
        }
    }
}
