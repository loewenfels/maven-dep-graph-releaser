package ch.tutteli.atrium

import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ReleasePlan
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMultiMavenReleasePlugin
import ch.tutteli.atrium.api.cc.en_UK.*
import org.jetbrains.spek.api.dsl.ActionBody

val exampleA = IdAndVersions(MavenProjectId("com.example", "a"), "1.1.1-SNAPSHOT", "1.1.1", "1.1.2-SNAPSHOT")
val exampleB = IdAndVersions(MavenProjectId("com.example", "b"), "1.0.1-SNAPSHOT", "1.0.1", "1.0.2-SNAPSHOT")
val exampleC = IdAndVersions(MavenProjectId("com.example", "c"), "3.0.0-SNAPSHOT", "3.0.0", "3.0.1-SNAPSHOT")
val exampleD = IdAndVersions(MavenProjectId("com.example", "d"), "4.1-SNAPSHOT", "4.1", "4.2-SNAPSHOT")
val exampleDeps = IdAndVersions(MavenProjectId("com.example", "deps"), "9-SNAPSHOT", "9", "10-SNAPSHOT")


fun ActionBody.assertSingleProject(releasePlan: ReleasePlan, idAndVersions: IdAndVersions) {
    assertRootProjectOnlyReleaseCommand(releasePlan, idAndVersions)

    test("it does not have any dependent project") {
        assert(releasePlan) {
            returnValueOf(subject::getNumberOfDependents).toBe(1)
            returnValueOf(subject::getDependents, idAndVersions.id).isEmpty()
        }
    }

    assertReleasePlanHasNoWarnings(releasePlan)
}

fun ActionBody.assertProjectAWithDependentBWithDependentC(
    releasePlan: ReleasePlan,
    projectB: IdAndVersions = exampleB
) {
    assertRootProjectWithDependents(releasePlan, exampleA, projectB)

    assertOneDirectDependent(releasePlan, "direct dependent", projectB, exampleC)
    assertOneUpdateAndOneReleaseCommand(releasePlan, "indirect dependent", exampleC, exampleB)
    assertHasNoDependentsAndIsOnLevel(releasePlan, "indirect dependent", exampleC, 2)

    assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 3)
}

fun ActionBody.assertMultiModuleAWithSubmoduleBWithDependentC(
    releasePlan: ReleasePlan,
    projectB: IdAndVersions
) {
    assertRootProjectMultiReleaseCommand(releasePlan, exampleA, projectB)

    assertHasNoCommands(releasePlan, "direct dependent", projectB)
    assertHasOneDependentAndIsOnLevel(releasePlan, "direct dependent", projectB, exampleC, 1)

    assertOneUpdateAndOneReleaseCommand(releasePlan, "indirect dependent", exampleC, projectB)
    assertHasNoDependentsAndIsOnLevel(releasePlan, "indirect dependent", exampleC, 2)

    assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 3)
    assertReleasePlanHasNoWarnings(releasePlan)
}

fun ActionBody.assertProjectAWithDependentB(releasePlan: ReleasePlan) {
    assertRootProjectWithDependents(releasePlan, exampleA, exampleB)

    assertOneUpdateAndOneReleaseCommand(releasePlan, "direct dependent", exampleB, exampleA)
    assertHasNoDependentsAndIsOnLevel(releasePlan, "direct dependent", exampleB, 1)

    assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 2)
}

fun ActionBody.assertRootProjectWithDependents(
    releasePlan: ReleasePlan,
    rootProjectIdAndVersions: IdAndVersions,
    dependentIdAndVersions: IdAndVersions,
    vararg otherDependentIdAndVersions: IdAndVersions
) {
    assertRootProjectOnlyReleaseCommand(releasePlan, rootProjectIdAndVersions)

    assertRootProjectHasDependents(releasePlan, dependentIdAndVersions, otherDependentIdAndVersions)
}

private fun ActionBody.assertRootProjectHasDependents(
    releasePlan: ReleasePlan,
    dependentIdAndVersions: IdAndVersions,
    otherDependentIdAndVersions: Array<out IdAndVersions>
) {
    test("root project has ${otherDependentIdAndVersions.size + 1} dependent(s)") {
        assert(releasePlan).hasDependentsForProject(exampleA, dependentIdAndVersions, *otherDependentIdAndVersions)
    }
}

fun ActionBody.assertRootProjectMultiReleaseCommand(
    releasePlan: ReleasePlan,
    rootProjectIdAndVersions: IdAndVersions,
    submodule: IdAndVersions,
    vararg otherSubmodules: IdAndVersions
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

    test("the command has ${otherSubmodules.size + 1} projects") {
        assert(rootProject.commands[0]).isA<JenkinsMultiMavenReleasePlugin> {
            property(subject::projects).contains.inAnyOrder.only.objects(
                submodule.id,
                *otherSubmodules.map { it.id }.toTypedArray()
            )
        }
    }

    assertRootProjectHasDependents(releasePlan, submodule, otherSubmodules)
}

fun ActionBody.assertRootProjectOnlyReleaseCommand(releasePlan: ReleasePlan, rootProjectIdAndVersions: IdAndVersions) {
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


fun ActionBody.assertRootProject(releasePlan: ReleasePlan, rootProjectIdAndVersions: IdAndVersions): Project {
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

fun ActionBody.assertOneDirectDependent(
    releasePlan: ReleasePlan,
    name: String,
    project: IdAndVersions,
    dependent: IdAndVersions
) {
    assertOneUpdateAndOneReleaseCommand(releasePlan, name, project, exampleA)
    assertHasOneDependentAndIsOnLevel(releasePlan, name, project, dependent, 1)
}

fun ActionBody.assertHasOneDependentAndIsOnLevel(
    releasePlan: ReleasePlan,
    name: String,
    project: IdAndVersions,
    dependent: IdAndVersions,
    level: Int
) {
    test("$name project has one dependent") {
        assert(releasePlan).hasDependentsForProject(project, dependent)
    }
    assertProjectIsOnLevel(releasePlan, name, project, level)
}

fun ActionBody.assertHasNoDependentsAndIsOnLevel(
    releasePlan: ReleasePlan,
    name: String,
    dependent: IdAndVersions,
    level: Int
) {
    test("$name project does not have dependents") {
        assert(releasePlan).hasNotDependentsForProject(dependent)
    }
    assertProjectIsOnLevel(releasePlan, name, dependent, level)
}

private fun ActionBody.assertProjectIsOnLevel(
    releasePlan: ReleasePlan,
    name: String,
    project: IdAndVersions,
    level: Int
) {
    test("$name project is on level $level") {
        assert(releasePlan.getProject(project.id).level).toBe(level)
    }
}

fun ActionBody.assertOnlyWaitingReleaseCommand(
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

fun ActionBody.assertHasNoCommands(releasePlan: ReleasePlan, name: String, idAndVersions: IdAndVersions) {
    test("$name project has no commands") {
        assert(releasePlan.getProject(idAndVersions.id)) {
            idAndVersions(idAndVersions)
            property(subject::commands).isEmpty()
        }
    }
}

fun ActionBody.assertOneUpdateAndOneReleaseCommand(
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

fun ActionBody.assertTwoUpdateAndOneReleaseCommand(
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

fun ActionBody.assertReleasePlanHasNumOfProjectsAndDependents(releasePlan: ReleasePlan, num: Int) {
    test("release plan has $num projects and $num dependents") {
        assert(releasePlan) {
            returnValueOf(subject::getNumberOfProjects).toBe(num)
            returnValueOf(subject::getNumberOfDependents).toBe(num)
        }
    }
}

fun ActionBody.assertReleasePlanHasNoWarnings(releasePlan: ReleasePlan) {
    test("it does not have warnings") {
        assert(releasePlan.warnings).isEmpty()
    }
}

fun ActionBody.assertReleasePlanHasWarningWithDependencyGraph(
    releasePlan: ReleasePlan,
    dependencyBranch: String,
    vararg otherDependencyBranches: String
) {
    test("warning contains the cyclic dependency branch") {
        assert(releasePlan.warnings).containsStrictly({
            contains("cyclic dependencies", dependencyBranch, *otherDependencyBranches)
        })
    }
}
