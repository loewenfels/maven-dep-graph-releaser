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

        val rootProject = createInitialProject(projectToRelease, currentVersion!!, 0, CommandState.Ready)
        val projects = hashMapOf<ProjectId, Project>()
        val dependents = hashMapOf<ProjectId, Set<ProjectId>>()
        projects[rootProject.id] = rootProject
        dependents[rootProject.id] = hashSetOf()

        createDependents(rootProject, analyser, projects, dependents)
        return ReleasePlan(rootProject.id, projects, dependents)
    }

    private fun createInitialProject(
        projectId: MavenProjectId,
        currentVersion: String,
        level: Int,
        state: CommandState
    ) = Project(
        projectId,
        currentVersion,
        versionDeterminer.releaseVersion(currentVersion),
        level,
        mutableListOf(JenkinsMavenReleasePlugin(state, versionDeterminer.nextDevVersion(currentVersion)))
    )

    private fun createDependents(
        rootProject: Project,
        analyser: Analyser,
        projects: HashMap<ProjectId, Project>,
        dependents: HashMap<ProjectId, Set<ProjectId>>
    ) {
        var level = 1
        val dependentsToVisit = mutableListOf(mutableListOf(rootProject))
        while (dependentsToVisit.isNotEmpty()) {
            val dependentsOnSameLevel = dependentsToVisit[0]
            val dependent = dependentsOnSameLevel.removeAt(0)
            createCommandsForDependents(analyser, dependent, projects, dependents, level, dependentsToVisit)
            if (dependentsOnSameLevel.isEmpty()) {
                dependentsToVisit.removeAt(0)
                ++level
            }
        }
    }

    private fun createCommandsForDependents(
        analyser: Analyser,
        dependency: Project,
        projects: HashMap<ProjectId, Project>,
        dependents: HashMap<ProjectId, Set<ProjectId>>,
        level: Int,
        dependentsToVisit: MutableList<MutableList<Project>>
    ) {
        val dependencyId = dependency.id as MavenProjectId
        analyser.getDependentsOf(dependencyId).forEach { dependentIdWithVersion ->
            val dependent: Project = if (isNewProject(projects, dependentIdWithVersion)) {
                val newDependent = createInitialWaitingProject(dependentIdWithVersion, level, dependencyId)
                dependents[newDependent.id] = hashSetOf()
                addToNextLevelOfDependentsToVisit(dependentsToVisit, newDependent)
                projects[newDependent.id] = newDependent
                newDependent
            } else {
                val existingDependent = projects[dependentIdWithVersion.id]!!
                if (existingDependent.level < level) {
                    val updatedProject = Project(existingDependent, level)
                    projects[existingDependent.id] = updatedProject
                    updatedProject
                } else {
                    existingDependent
                }
            }
            addAndUpdateCommandsOfDependent(dependent, dependencyId)
            addDependentToDependentsOfDependency(dependent.id, dependents, dependencyId)
        }
    }

    private fun isNewProject(
        projects: HashMap<ProjectId, Project>,
        dependentIdWithVersion: ProjectIdWithCurrentVersion<MavenProjectId>
    ) = !projects.containsKey(dependentIdWithVersion.id)

    private fun createInitialWaitingProject(
        dependentIdWithVersion: ProjectIdWithCurrentVersion<MavenProjectId>,
        level: Int,
        dependencyId: MavenProjectId
    ): Project = createInitialProject(
        dependentIdWithVersion.id,
        dependentIdWithVersion.currentVersion,
        level,
        CommandState.Waiting(mutableSetOf(dependencyId))
    )

    private fun addDependentToDependentsOfDependency(
        dependentId: ProjectId,
        dependents: HashMap<ProjectId, Set<ProjectId>>,
        dependencyId: MavenProjectId
    ) {
        (dependents[dependencyId] as MutableSet).add(dependentId)
    }

    private fun addAndUpdateCommandsOfDependent(
        dependent: Project,
        dependencyId: MavenProjectId
    ) {
        val list = dependent.commands as MutableList
        addDependencyToJenkinsReleaseCommand(list, dependencyId)
        list.add(0, JenkinsUpdateDependency(CommandState.Waiting(setOf(dependencyId)), dependencyId))
    }

    private fun addDependencyToJenkinsReleaseCommand(list: MutableList<Command>, dependencyId: MavenProjectId) {
        val last = list.last()
        check(last is JenkinsMavenReleasePlugin) {
            "The last command has to be a ${JenkinsMavenReleasePlugin::class.simpleName}"
        }
        ((last.state as CommandState.Waiting).dependencies as MutableSet).add(dependencyId)
    }

    private fun addToNextLevelOfDependentsToVisit(
        dependentsToVisit: MutableList<MutableList<Project>>,
        dependent: Project
    ) {
        if (dependentsToVisit.size < 2) {
            dependentsToVisit.add(mutableListOf())
        }
        val nextLevelProjects = dependentsToVisit.last()
        nextLevelProjects.add(dependent)
    }
}
