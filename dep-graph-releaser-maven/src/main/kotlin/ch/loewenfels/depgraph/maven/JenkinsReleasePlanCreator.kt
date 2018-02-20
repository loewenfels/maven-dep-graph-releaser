package ch.loewenfels.depgraph.maven

import ch.loewenfels.depgraph.data.*
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsMavenReleasePlugin
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency

class JenkinsReleasePlanCreator(private val versionDeterminer: VersionDeterminer) {

    fun create(projectToRelease: MavenProjectId, analyser: Analyser): ReleasePlan {
        val currentVersion = analyser.getCurrentVersion(projectToRelease)
        require(currentVersion != null) {
            """Can only release a project which is part of the analysis.
                |Given: $projectToRelease
                |Analysed projects: ${analyser.getAnalysedProjectsAsString()}
            """.trimMargin()
        }

        val rootProject = createInitialProject(projectToRelease, currentVersion!!, CommandState.Ready)
        val projects = hashMapOf<ProjectId, Project>()
        val dependents = hashMapOf<ProjectId, Set<ProjectId>>()
        projects[rootProject.id] = rootProject
        dependents[rootProject.id] = hashSetOf()

        val projectsToVisit = mutableListOf(rootProject)
        while (projectsToVisit.isNotEmpty()) {
            val project = projectsToVisit.removeAt(0)
            createCommandsForDependents(analyser, project, projects, dependents, projectsToVisit)
        }
        return ReleasePlan(rootProject.id, projects, dependents)
    }

    private fun createInitialProject(projectId: MavenProjectId, currentVersion: String, state: CommandState): Project {
        return Project(
            projectId,
            currentVersion,
            versionDeterminer.releaseVersion(currentVersion),
            mutableListOf(JenkinsMavenReleasePlugin(state, versionDeterminer.nextDevVersion(currentVersion)))
        )
    }

    private fun createCommandsForDependents(
        analyser: Analyser,
        dependency: Project,
        projects: HashMap<ProjectId, Project>,
        dependents: HashMap<ProjectId, Set<ProjectId>>,
        projectsToVisit: MutableList<Project>
    ) {
        val dependencyId = dependency.id as MavenProjectId
        analyser.getDependentsOf(dependencyId).forEach { dependentIdWithVersion ->
            val dependent = if (!projects.containsKey(dependentIdWithVersion.id)) {
                val dependent = createInitialProject(dependentIdWithVersion.id, dependentIdWithVersion.currentVersion, CommandState.Waiting(mutableSetOf(dependencyId)))
                projects[dependent.id] = dependent
                dependents[dependent.id] = hashSetOf()
                dependent
            } else {
                projects[dependentIdWithVersion.id]!!
            }
            (dependents[dependencyId] as MutableSet).add(dependent.id)
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
