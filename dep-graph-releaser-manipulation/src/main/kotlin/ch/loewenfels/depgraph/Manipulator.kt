package ch.loewenfels.depgraph

import ch.loewenfels.depgraph.data.CommandState
import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.ReleasePlan

class Manipulator(private val releasePlan: ReleasePlan) {

    fun deactivateProject(projectId: ProjectId): ReleasePlan {
        require(projectId != releasePlan.rootProjectId) {
            "Deactivating the rootProject (id: ${releasePlan.rootProjectId} does not make sense"
        }
        require(releasePlan.projects.containsKey(projectId)) {
            "Cannot deactivate the project with id $projectId because it is not part of the analysis"
        }

        val projectsToDeactivate = collectProjectsToDeactivate(projectId)
        val newProjects = deactivateProjects(projectsToDeactivate)
        return ReleasePlan(releasePlan, newProjects)
    }

    private fun collectProjectsToDeactivate(projectId: ProjectId): Set<ProjectId> {
        val set = hashSetOf<ProjectId>()
        collectProjectsToDeactivate(mutableListOf(projectId), set)
        return set
    }

    private tailrec fun collectProjectsToDeactivate(projectsToVisit: MutableList<ProjectId>, set: HashSet<ProjectId>) {
        if (projectsToVisit.isNotEmpty()) {
            val projectId = projectsToVisit.removeAt(0)
            set.add(projectId)
            projectsToVisit.addAll(releasePlan.getDependents(projectId))
            return collectProjectsToDeactivate(projectsToVisit, set)
        }
    }

    private fun deactivateProjects(projectsToDeactivate: Set<ProjectId>): Map<ProjectId, Project> {
        return releasePlan.projects.entries.associate { (k, v) ->
            k to if (projectsToDeactivate.contains(k)) {
                deactivateProject(v)
            } else {
                v
            }
        }
    }

    private fun deactivateProject(project: Project): Project {
        val newCommands = project.commands
            .asSequence()
            .filter { it.state !is CommandState.Deactivated }
            .map { it.asDeactivated() }
            .toList()

        return Project(project, newCommands)
    }

}
