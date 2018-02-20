package ch.loewenfels.depgraph.maven

import ch.loewenfels.depgraph.data.Command
import ch.loewenfels.depgraph.data.CommandState
import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency

class JenkinsReleasePlanCreator(private val versionDeterminer: VersionDeterminer) {

    fun create(projectToRelease: MavenProjectId, analyser: Analyser): Project {
        val currentVersion = analyser.getCurrentVersion(projectToRelease)
        require(currentVersion != null) {
            """Can only release a project which is part of the analysis.
                |Given: $projectToRelease
                |Analysed projects: ${analyser.getAnalysedProjectsAsString()}
            """.trimMargin()
        }

        val rootProject = createInitialProject(projectToRelease, currentVersion!!, CommandState.Ready)
        val visitedProjects = hashMapOf<ProjectId, Project>()
        val projectsToVisit = mutableListOf(rootProject)
        while (projectsToVisit.isNotEmpty()) {
            val project = projectsToVisit.removeAt(0)
            createCommandsForDependents(analyser, project, visitedProjects, projectsToVisit)
        }

        return rootProject
    }

    private fun createInitialProject(projectId: MavenProjectId, currentVersion: String, state: CommandState): Project {
        return Project(
            projectId,
            currentVersion,
            versionDeterminer.releaseVersion(currentVersion),
            mutableListOf(JenkinsMavenReleasePlugin(state, versionDeterminer.nextDevVersion(currentVersion))),
            mutableListOf()
        )
    }

    private fun createCommandsForDependents(analyser: Analyser, dependency: Project, visitedProjects: HashMap<ProjectId, Project>, projectsToVisit: MutableList<Project>) {
        val dependencyId = dependency.id as MavenProjectId
        visitedProjects[dependencyId] = dependency
        analyser.getDependentsOf(dependencyId).forEach { dependentIdWithVersion ->
            val dependent = visitedProjects.getOrPut(dependentIdWithVersion.id, {
                createInitialProject(dependentIdWithVersion.id, dependentIdWithVersion.currentVersion, CommandState.Waiting(mutableSetOf(dependencyId)))
            })
            (dependency.dependents as MutableList).add(dependent)
            val list = dependent.commands as MutableList
            addDependencyToJenkinsReleaseCommand(list, dependencyId)
            list.add(0, JenkinsUpdateDependency(CommandState.Waiting(setOf(dependencyId)), dependencyId))
            projectsToVisit.add(dependent)
        }
    }

    private fun addDependencyToJenkinsReleaseCommand(list: MutableList<Command>, dependencyId: MavenProjectId) {
        val last = list.last()
        check(last is JenkinsMavenReleasePlugin) {
            "The last command has to be a ${JenkinsMavenReleasePlugin::class.simpleName}"
        }
        ((last.state as CommandState.Waiting).dependencies as MutableSet).add(dependencyId)
    }
}
