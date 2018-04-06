package ch.tutteli.atrium

import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ReleasePlan
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMultiMavenReleasePlugin
import ch.tutteli.atrium.api.cc.en_UK.*
import org.jetbrains.spek.api.dsl.TestContainer

val exampleA = IdAndVersions(MavenProjectId("com.example", "a"), "1.1.1-SNAPSHOT", "1.1.1", "1.1.2-SNAPSHOT")
val exampleB = IdAndVersions(MavenProjectId("com.example", "b"), "1.0.1-SNAPSHOT", "1.0.1", "1.0.2-SNAPSHOT")
val exampleC = IdAndVersions(MavenProjectId("com.example", "c"), "3.0.0-SNAPSHOT", "3.0.0", "3.0.1-SNAPSHOT")
val exampleD = IdAndVersions(MavenProjectId("com.example", "d"), "4.1-SNAPSHOT", "4.1", "4.2-SNAPSHOT")
val exampleE = IdAndVersions(MavenProjectId("com.example", "e"), "5.1.3-SNAPSHOT", "5.1.3", "5.1.4-SNAPSHOT")
val exampleDeps = IdAndVersions(MavenProjectId("com.example", "deps"), "9-SNAPSHOT", "9", "10-SNAPSHOT")


fun TestContainer.assertSingleProject(releasePlan: ReleasePlan, projectToRelease: IdAndVersions) {
    assertRootProjectOnlyReleaseCommand(releasePlan, projectToRelease)

    assertHasNoDependentsAndIsOnLevel(releasePlan, "root", projectToRelease, 0)
    assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 1)
    assertReleasePlanHasNoWarningsAndNoInfos(releasePlan)
    test("ReleasePlan.iterator() returns only the root Project projects in the expected order") {
        assert(releasePlan).iteratorReturnsRootAndStrictly()
    }
}

fun TestContainer.assertProjectAWithDependentBWithDependentC(
    releasePlan: ReleasePlan,
    projectB: IdAndVersions = exampleB
) {
    assertRootProjectWithDependents(releasePlan, exampleA, projectB)

    assertOneDirectDependent(releasePlan, "direct dependent", projectB, exampleC)
    assertOneUpdateAndOneReleaseCommand(releasePlan, "indirect dependent", exampleC, exampleB)
    assertHasNoDependentsAndIsOnLevel(releasePlan, "indirect dependent", exampleC, 2)

    assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 3)
}

fun TestContainer.assertMultiModuleAWithSubmoduleBWithDependentC(
    releasePlan: ReleasePlan,
    projectB: IdAndVersions
) {
    assertRootProjectMultiReleaseCommandWithSubmodulesAndSameDependents(releasePlan, exampleA, projectB)

    assertHasNoCommands(releasePlan, "submodule", projectB)
    assertHasOneDependentAndIsOnLevel(releasePlan, "submodule", projectB, exampleC, 0)

    assertOneUpdateAndOneReleaseCommand(releasePlan, "indirect dependent", exampleC, projectB)
    assertHasNoDependentsAndIsOnLevel(releasePlan, "indirect dependent", exampleC, 1)

    assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 3)
    assertReleasePlanHasNoWarningsAndNoInfos(releasePlan)
    assertReleasePlanIteratorReturnsRootAndStrictly(releasePlan, exampleB, exampleC)
}

fun TestContainer.assertProjectAWithDependentB(releasePlan: ReleasePlan) {
    assertRootProjectWithDependents(releasePlan, exampleA, exampleB)

    assertOneUpdateAndOneReleaseCommand(releasePlan, "direct dependent", exampleB, exampleA)
    assertHasNoDependentsAndIsOnLevel(releasePlan, "direct dependent", exampleB, 1)

    assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 2)
}

fun TestContainer.assertRootProjectWithDependents(
    releasePlan: ReleasePlan,
    rootProjectIdAndVersions: IdAndVersions,
    dependentIdAndVersions: IdAndVersions,
    vararg otherDependentIdAndVersions: IdAndVersions
) {
    assertRootProjectOnlyReleaseCommand(releasePlan, rootProjectIdAndVersions)

    assertRootProjectHasDependents(
        releasePlan,
        rootProjectIdAndVersions,
        dependentIdAndVersions,
        *otherDependentIdAndVersions
    )
}

fun TestContainer.assertRootProjectHasDependents(
    releasePlan: ReleasePlan,
    rootProjectIdAndVersions: IdAndVersions,
    dependentIdAndVersions: IdAndVersions,
    vararg otherDependentIdAndVersions: IdAndVersions
) {
    assertHasDependents(
        releasePlan,
        "root",
        rootProjectIdAndVersions,
        dependentIdAndVersions,
        *otherDependentIdAndVersions
    )
}

fun TestContainer.assertRootProjectMultiReleaseCommandWithSubmodulesAndSameDependents(
    releasePlan: ReleasePlan,
    rootProjectIdAndVersions: IdAndVersions,
    submodule: IdAndVersions,
    vararg otherSubmodules: IdAndVersions
) {
    assertRootProjectMultiReleaseCommand(releasePlan, rootProjectIdAndVersions)
    assertRootProjectHasSubmodules(releasePlan, rootProjectIdAndVersions, submodule, *otherSubmodules)
    assertRootProjectHasDependents(releasePlan, rootProjectIdAndVersions, submodule, *otherSubmodules)
}

fun TestContainer.assertRootProjectHasSubmodules(
    releasePlan: ReleasePlan,
    project: IdAndVersions,
    submodule: IdAndVersions,
    vararg otherSubmodules: IdAndVersions
) = assertHasSubmodules(releasePlan, "root", project, submodule, *otherSubmodules)

fun TestContainer.assertRootProjectMultiReleaseCommand(
    releasePlan: ReleasePlan,
    rootProjectIdAndVersions: IdAndVersions
) {
    val rootProject = assertRootProject(releasePlan, rootProjectIdAndVersions)
    test("root project contains just the ${JenkinsMultiMavenReleasePlugin::class.simpleName} command") {
        assert(rootProject) {
            property(subject::commands).containsStrictly({
                isA<JenkinsMultiMavenReleasePlugin> {}
            })
        }
    }
    test("the command is in state Ready with ${JenkinsMavenReleasePlugin::nextDevVersion.name} = ${rootProjectIdAndVersions.nextDevVersion}\"") {
        assert(rootProject.commands[0]).isA<JenkinsMultiMavenReleasePlugin> {
            isStateReady()
            property(subject::nextDevVersion).toBe(rootProjectIdAndVersions.nextDevVersion)
        }
    }
}

fun TestContainer.assertRootProjectOnlyReleaseCommand(
    releasePlan: ReleasePlan,
    rootProjectIdAndVersions: IdAndVersions
) {
    val rootProject = assertRootProject(releasePlan, rootProjectIdAndVersions)
    test("root project contains just the ${JenkinsMavenReleasePlugin::class.simpleName} command, which is Ready with ${JenkinsMavenReleasePlugin::nextDevVersion.name} = ${rootProjectIdAndVersions.nextDevVersion}") {
        assert(rootProject) {
            property(subject::commands).containsStrictly({
                isA<JenkinsMavenReleasePlugin> {
                    isStateReady()
                    property(subject::nextDevVersion).toBe(rootProjectIdAndVersions.nextDevVersion)
                }
            })
        }
    }
}


fun TestContainer.assertRootProject(releasePlan: ReleasePlan, rootProjectIdAndVersions: IdAndVersions): Project {
    test("${ReleasePlan::rootProjectId.name} is expected rootProject") {
        assert(releasePlan.rootProjectId).toBe(rootProjectIdAndVersions.id)
    }
    val rootProject = releasePlan.getProject(rootProjectIdAndVersions.id)
    test("root project's ${Project::id.name} and versions are $rootProjectIdAndVersions") {
        assert(rootProject) {
            idAndVersions(rootProjectIdAndVersions)
        }
    }
    test("root project's level is 0") {
        assert(rootProject) {
            property(subject::level).toBe(0)
        }
    }
    return rootProject
}

fun TestContainer.assertOneDirectDependent(
    releasePlan: ReleasePlan,
    name: String,
    project: IdAndVersions,
    dependent: IdAndVersions
) {
    assertOneUpdateAndOneReleaseCommand(releasePlan, name, project, exampleA)
    assertHasOneDependentAndIsOnLevel(releasePlan, name, project, dependent, 1)
}

fun TestContainer.assertHasOneDependentAndIsOnLevel(
    releasePlan: ReleasePlan,
    name: String,
    project: IdAndVersions,
    dependent: IdAndVersions,
    level: Int
) {
    assertHasDependents(releasePlan, name, project, dependent)
    assertProjectIsOnLevel(releasePlan, name, project, level)
}

fun TestContainer.assertHasTwoDependentsAndIsOnLevel(
    releasePlan: ReleasePlan,
    name: String,
    project: IdAndVersions,
    dependent1: IdAndVersions,
    dependent2: IdAndVersions,
    level: Int
) {
    assertHasDependents(releasePlan, name, project, dependent1, dependent2)
    assertProjectIsOnLevel(releasePlan, name, project, level)
}

private fun TestContainer.assertHasDependents(
    releasePlan: ReleasePlan,
    name: String,
    project: IdAndVersions,
    dependentIdAndVersions: IdAndVersions,
    vararg otherDependentIdAndVersions: IdAndVersions
) {
    test("$name project has ${otherDependentIdAndVersions.size + 1} dependent(s)") {
        assert(releasePlan).hasDependentsForProject(project, dependentIdAndVersions, *otherDependentIdAndVersions)
    }
}


fun TestContainer.assertHasNoDependentsAndIsOnLevel(
    releasePlan: ReleasePlan,
    name: String,
    dependent: IdAndVersions,
    level: Int
) {
    test("$name project does not have dependents") {
        assert(releasePlan).hasNoDependentsForProject(dependent)
    }
    assertProjectIsOnLevel(releasePlan, name, dependent, level)
}

fun TestContainer.assertProjectIsOnLevel(
    releasePlan: ReleasePlan,
    name: String,
    project: IdAndVersions,
    level: Int
) {
    test("$name project is on level $level") {
        assert(releasePlan.getProject(project.id).level).toBe(level)
    }
}

fun TestContainer.assertOnlyWaitingReleaseCommand(
    releasePlan: ReleasePlan,
    name: String,
    project: IdAndVersions,
    dependency: IdAndVersions
) {
    test("$name project has only one waiting Release command") {
        assert(releasePlan.getProject(project.id)) {
            idAndVersions(project)
            property(subject::commands).containsStrictly(
                { isJenkinsMavenReleaseWaiting(project.nextDevVersion, dependency) }
            )
        }
    }
}

fun TestContainer.assertHasNoCommands(releasePlan: ReleasePlan, name: String, idAndVersions: IdAndVersions) {
    test("$name project has no commands") {
        assert(releasePlan.getProject(idAndVersions.id)) {
            idAndVersions(idAndVersions)
            property(subject::commands).isEmpty()
        }
    }
}

fun TestContainer.assertOneUpdateCommand(
    releasePlan: ReleasePlan,
    name: String,
    project: IdAndVersions,
    dependency: IdAndVersions
) {
    test("$name project has one waiting UpdateVersion and one waiting Release command") {
        assert(releasePlan.getProject(project.id)) {
            idAndVersions(project)
            property(subject::commands).containsStrictly(
                { isJenkinsUpdateDependencyWaiting(dependency) }
            )
        }
    }
}

fun TestContainer.assertOneUpdateAndOneReleaseCommand(
    releasePlan: ReleasePlan,
    name: String,
    project: IdAndVersions,
    dependency: IdAndVersions
) {
    test("$name project has one waiting UpdateVersion and one waiting Release command") {
        assert(releasePlan.getProject(project.id)) {
            idAndVersions(project)
            property(subject::commands).containsStrictly(
                { isJenkinsUpdateDependencyWaiting(dependency) },
                { isJenkinsMavenReleaseWaiting(project.nextDevVersion, dependency) }
            )
        }
    }
}

fun TestContainer.assertOneUpdateAndOneDisabledReleaseCommand(
    releasePlan: ReleasePlan,
    name: String,
    project: IdAndVersions,
    dependency: IdAndVersions
) {
    test("$name has one waiting Update and one disabled Release command with ${JenkinsMavenReleasePlugin::nextDevVersion.name} = ${project.nextDevVersion}") {
        assert(releasePlan.getProject(project.id)) {
            property(subject::commands).containsStrictly(
                { isJenkinsUpdateDependencyWaiting(dependency) },
                { isJenkinsMavenReleaseDisabled(project.nextDevVersion) }
            )
        }
    }
}

fun TestContainer.assertOneDeactivatedUpdateAndOneDeactivatedReleaseCommand(
    releasePlan: ReleasePlan,
    name: String,
    project: IdAndVersions,
    dependency: IdAndVersions
) {
    test("$name has one deactivated Update and one deactivated Release command with ${JenkinsMavenReleasePlugin::nextDevVersion.name} = ${project.nextDevVersion}") {
        assert(releasePlan.getProject(project.id)) {
            property(subject::commands).containsStrictly(
                { isJenkinsUpdateDependencyDeactivatedWaiting(dependency) },
                { isJenkinsMavenReleaseDeactivatedWaiting(project.nextDevVersion, dependency) }
            )
        }
    }
}

fun TestContainer.assertOneDeactivatedUpdateAndOneDisabledReleaseCommand(
    releasePlan: ReleasePlan,
    name: String,
    project: IdAndVersions,
    dependency: IdAndVersions
) {
    test("$name has one deactivated Update and one disabled Release command with ${JenkinsMavenReleasePlugin::nextDevVersion.name} = ${project.nextDevVersion}") {
        assert(releasePlan.getProject(project.id)) {
            property(subject::commands).containsStrictly(
                { isJenkinsUpdateDependencyDeactivatedWaiting(dependency) },
                { isJenkinsMavenReleaseDisabled(project.nextDevVersion) }
            )
        }
    }
}

fun TestContainer.assertOneUpdateAndOneMultiReleaseCommandAndSubmodulesAndSameDependents(
    releasePlan: ReleasePlan,
    name: String,
    project: IdAndVersions,
    dependency: IdAndVersions,
    submodule: IdAndVersions,
    vararg otherSubmodules: IdAndVersions
) {
    assertOneUpdateAndOneMultiReleaseCommand(releasePlan, name, project, dependency)
    assertHasSubmodules(releasePlan, name, project, submodule, *otherSubmodules)
    assertHasDependents(releasePlan, name, project, submodule, *otherSubmodules)
}

fun TestContainer.assertOneUpdateAndOneMultiReleaseCommand(
    releasePlan: ReleasePlan,
    name: String,
    project: IdAndVersions,
    dependency: IdAndVersions
) {
    test("$name project has one waiting UpdateVersion and one waiting Release command") {
        assert(releasePlan.getProject(project.id)) {
            idAndVersions(project)
            property(subject::commands).containsStrictly(
                { isJenkinsUpdateDependencyWaiting(dependency) },
                { isJenkinsMultiMavenReleaseWaiting(project.nextDevVersion, dependency, arrayOf()) }
            )
        }
    }
}

fun TestContainer.assertTwoUpdateAndOneReleaseCommand(
    releasePlan: ReleasePlan,
    name: String,
    project: IdAndVersions,
    dependency1: IdAndVersions,
    dependency2: IdAndVersions
) {
    test("$name project has two waiting UpdateVersion and one waiting Release command") {
        assert(releasePlan.getProject(project.id)) {
            idAndVersions(project)
            property(subject::commands).containsStrictly(
                { isJenkinsUpdateDependencyWaiting(dependency1) },
                { isJenkinsUpdateDependencyWaiting(dependency2) },
                { isJenkinsMavenReleaseWaiting(project.nextDevVersion, dependency2, dependency1) }
            )
        }
    }
}

fun TestContainer.assertReleasePlanHasNumOfProjectsAndDependents(releasePlan: ReleasePlan, num: Int) {
    test("release plan has $num projects and $num dependents") {
        assert(releasePlan) {
            returnValueOf(subject::getNumberOfProjects).toBe(num)
            returnValueOf(subject::getNumberOfDependents).toBe(num)
        }
    }
}

fun TestContainer.assertReleasePlanHasNoWarningsAndNoInfos(releasePlan: ReleasePlan) {
    assertReleasePlanHasNoWarnings(releasePlan)
    assertReleasePlanHasNoInfos(releasePlan)
}

fun TestContainer.assertReleasePlanHasNoWarnings(releasePlan: ReleasePlan) {
    test("it does not have warnings") {
        assert(releasePlan.warnings).isEmpty()
    }
}

fun TestContainer.assertReleasePlanHasNoInfos(releasePlan: ReleasePlan) {
    test("it does not have infos") {
        assert(releasePlan.infos).isEmpty()
    }
}

fun TestContainer.assertReleasePlanHasWarningWithDependencyGraph(
    releasePlan: ReleasePlan,
    dependencyBranch: String,
    vararg otherDependencyBranches: String
) {
    test("warnings contains the cyclic dependency branch") {
        assert(releasePlan.warnings).containsStrictly({
            contains("cyclic dependencies", dependencyBranch, *otherDependencyBranches)
        })
    }
}

fun TestContainer.assertReleasePlanHasInfoWithDependencyGraph(
    releasePlan: ReleasePlan,
    dependencyBranch: String,
    vararg otherDependencyBranches: String
) {
    test("infos contains the cyclic dependency branch") {
        assert(releasePlan.infos).containsStrictly({
            contains("cyclic dependencies", dependencyBranch, *otherDependencyBranches)
        })
    }
}

fun TestContainer.assertReleasePlanIteratorReturnsRootAndStrictly(
    releasePlan: ReleasePlan,
    vararg projects: IdAndVersions
) {
    test("ReleasePlan.iterator() returns the projects in the expected order") {
        assert(releasePlan).iteratorReturnsRootAndStrictly(*projects.mapToProjectIds())
    }
}

fun TestContainer.assertReleasePlanIteratorReturnsRootAnd(
    releasePlan: ReleasePlan,
    vararg groups: List<IdAndVersions>
) {
    test("ReleasePlan.iterator() returns the projects in the expected order") {
        val projectGroups = groups.map { it.map { it.id } }.toTypedArray()
        assert(releasePlan).iteratorReturnsRootAndInOrderGrouped(*projectGroups)
    }
}

fun TestContainer.assertHasSubmodules(
    releasePlan: ReleasePlan,
    name: String,
    project: IdAndVersions,
    submodule: IdAndVersions,
    vararg otherSubmodules: IdAndVersions
) {
    test("$name project has ${otherSubmodules.size + 1} submodules") {
        assert(releasePlan.getSubmodules(project.id)).contains.inAnyOrder.only.objects(
            submodule.id, *otherSubmodules.mapToProjectIds()
        )
    }
}


fun Array<out IdAndVersions>.mapToProjectIds() = this.map { it.id }.toTypedArray()
