package ch.loewenfels.depgraph.maven

import ch.loewenfels.depgraph.data.CommandState
import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency

class JenkinsReleasePlanCreator(private val versionDeterminer: VersionDeterminer) {

    fun create(projectToRelease: MavenProjectId, analyser: Analyser): Project {
        require(analyser.hasAnalysedProject(projectToRelease)) {
            """Can only release a project which is part of the analysis.
                |Given: $projectToRelease
                |Analysed projects: ${analyser.getAnalysedProjectsAsString()}
            """.trimMargin()
        }

        val rootProject = createInitialProject(projectToRelease, CommandState.Ready)
        val visitedProjects = hashMapOf<ProjectId, Project>()
        val projectsToVisit = mutableListOf(projectToRelease to rootProject)
        while (projectsToVisit.isNotEmpty()) {
            val dependencyPair = projectsToVisit.removeAt(0)
            createCommandsForDependents(analyser, dependencyPair, visitedProjects, projectsToVisit)
        }

        return rootProject
    }

    private fun createInitialProject(projectId: MavenProjectId, state: CommandState): Project {
        return Project(
            projectId,
            versionDeterminer.releaseVersion(projectId),
            mutableListOf(JenkinsMavenReleasePlugin(state, versionDeterminer.nextDevVersion(projectId))),
            mutableListOf()
        )
    }

    private fun createCommandsForDependents(analyser: Analyser, dependencyPair: Pair<MavenProjectId, Project>, visitedProjects: HashMap<ProjectId, Project>, projectsToVisit: MutableList<Pair<MavenProjectId, Project>>) {
        val (dependencyId, dependency) = dependencyPair
        visitedProjects[dependencyId] = dependency
        analyser.getDependentsOf(dependencyId).forEach { dependentId ->
            val dependent = visitedProjects.getOrPut(dependentId, {
                createInitialProject(dependentId, CommandState.Waiting(setOf(dependencyId)))
            })
            (dependency.dependents as MutableList).add(dependent)
            val list = dependent.commands as MutableList
            list.add(0, JenkinsUpdateDependency(CommandState.Waiting(setOf(dependencyId)), dependencyId))
            projectsToVisit.add(dependentId to dependent)
        }
    }
}
