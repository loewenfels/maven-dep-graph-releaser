package ch.tutteli.atrium

import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ReleasePlan
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMavenReleasePlugin
import ch.tutteli.atrium.api.cc.en_UK.*
import org.jetbrains.spek.api.dsl.ActionBody

val exampleA = IdAndVersions(MavenProjectId("com.example", "a"), "1.1.1-SNAPSHOT", "1.1.1", "1.1.2-SNAPSHOT")
val exampleB = IdAndVersions(MavenProjectId("com.example", "b"), "1.0.1-SNAPSHOT", "1.0.1", "1.0.2-SNAPSHOT")
val exampleC = IdAndVersions(MavenProjectId("com.example", "c"), "3.0.0-SNAPSHOT", "3.0.0", "3.0.1-SNAPSHOT")
val exampleD = IdAndVersions(MavenProjectId("com.example", "d"), "4.1-SNAPSHOT", "4.1", "4.2-SNAPSHOT")
val exampleDeps = IdAndVersions(MavenProjectId("com.example", "deps"), "9-SNAPSHOT", "9", "10-SNAPSHOT")


fun ActionBody.assertSingleProject(releasePlan: ReleasePlan, idAndVersions: IdAndVersions) {
    assertRootProjectOnlyReleaseAndReady(releasePlan, idAndVersions)

    test("it does not have any dependent project") {
        assert(releasePlan.dependents) {
            property(subject::size).toBe(1)
            returnValueOf(subject::get, idAndVersions.id).isNotNull { isEmpty() }
        }
    }
}

fun ActionBody.assertReleaseAWithDependentBWithDependentC(
    releasePlan: ReleasePlan,
    projectB: IdAndVersions = exampleB
) {
    assertRootProjectOnlyReleaseAndReady(releasePlan, exampleA)

    test("root project has one dependent") {
        assert(releasePlan).hasDependentsForProject(exampleA, projectB)
    }

    assertOneDirectDependent(releasePlan, "direct dependent", projectB, exampleC)
    assertHasNoDependentsAndIsOnLevel(releasePlan, "indirect dependent", exampleC, 2)

    assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 3)
}

fun ActionBody.assertProjectAWithDependentB(releasePlan: ReleasePlan) {
    assertRootProjectOnlyReleaseAndReady(releasePlan, exampleA)

    assertHasNoDependentsAndIsOnLevel(releasePlan, "direct dependent", exampleB, 1)

    assertReleasePlanHasNumOfProjectsAndDependents(releasePlan, 2)
}

fun ActionBody.assertRootProjectOnlyReleaseAndReady(releasePlan: ReleasePlan, idAndVersions: IdAndVersions) {
    test("${ReleasePlan::rootProjectId.name} is expected rootProject") {
        assert(releasePlan.rootProjectId).toBe(idAndVersions.id)
    }
    val rootProject = releasePlan.projects[idAndVersions.id]!!
    test("root project's ${Project::id.name} and versions are $idAndVersions") {
        assert(rootProject) {
            idAndVersions(idAndVersions)
        }
    }
    test("root project's level is 0") {
        assert(rootProject) {
            property(subject::level).toBe(0)
        }
    }
    test("root project contains just the ${JenkinsMavenReleasePlugin::class.simpleName} command, which is Ready with ${JenkinsMavenReleasePlugin::nextDevVersion.name} = ${idAndVersions.nextDevVersion}") {
        assert(rootProject) {
            property(subject::commands).containsStrictly({
                isA<JenkinsMavenReleasePlugin> {
                    isStateReady()
                    property(subject::nextDevVersion).toBe(idAndVersions.nextDevVersion)
                }
            })
        }
    }
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

fun ActionBody.assertOneUpdateAndOneReleaseCommand(
    releasePlan: ReleasePlan,
    name: String,
    project: IdAndVersions,
    dependency: IdAndVersions
) {
    test("$name project has two commands, updateVersion and Release") {
        assert(releasePlan.projects[project.id]).isNotNull {
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
    test("$name project has two UpdateVersion and one Release command") {
        assert(releasePlan.projects[project.id]).isNotNull {
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
            property(subject::projects).hasSize(num)
            property(subject::dependents).hasSize(num)
        }
    }
}

fun ActionBody.assertReleasePlanHasWarningWithDependencyGraph(
    releasePlan: ReleasePlan,
    dependencyBranch: String,
    vararg otherDependencyBranches: String
) {
    test("warning contains the cyclic dependency branch") {
        assert(releasePlan.warnings).containsStrictly({
            contains(dependencyBranch, *otherDependencyBranches)
        })
    }
}
