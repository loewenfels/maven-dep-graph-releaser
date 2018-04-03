package ch.loewenfels.depgraph

import ch.loewenfels.depgraph.data.*

class ReleasePlanManipulator(private val releasePlan: ReleasePlan) {

    fun deactivateProject(projectId: ProjectId): ReleasePlan {
        requireValidProjectId(projectId)

        val projectsToDeactivate = collectProjectsToDeactivate(projectId)
        val newProjects = deactivateProjects(projectsToDeactivate)
        return ReleasePlan(releasePlan, newProjects)
    }

    private fun requireValidProjectId(projectId: ProjectId) {
        require(projectId != releasePlan.rootProjectId) {
            "Deactivating the rootProject (id: ${releasePlan.rootProjectId} does not make sense"
        }

        //will throw if not existing
        releasePlan.getProject(projectId)
    }

    private fun collectProjectsToDeactivate(projectId: ProjectId): Set<ProjectId> {
        return releasePlan.iterator(projectId)
            .asSequence()
            .map { it.id }
            .toHashSet()
    }

    private fun deactivateProjects(projectsToDeactivate: Set<ProjectId>): Map<ProjectId, Project> =
        deactivateProjects(projectsToDeactivate, null)

    /**
     * Deactivates all commands of all projects which are defined in [projectsToDeactivate] and returns a new [Map],
     * unless [newProject] is set and the project to deactivate is the same, in this case the [newProject] is used.
     */
    private fun deactivateProjects(
        projectsToDeactivate: Set<ProjectId>,
        newProject: Project?
    ): Map<ProjectId, Project> {
        return releasePlan.getAllProjects().entries.associate { (k, v) ->
            k to when {
                newProject != null && newProject.id == v.id -> newProject
                projectsToDeactivate.contains(k) -> deactivateProject(v)
                else -> v
            }
        }
    }

    private fun deactivateProject(project: Project): Project {
        val newCommands = project.commands.map {
            if (it.state is CommandState.Deactivated) {
                it
            } else {
                it.asDeactivated()
            }
        }
        return Project(project, newCommands)
    }

    /**
     * Deactivates the command at position [index] of the project with the given [projectId], deactivates all
     * [ReleaseCommand]s of the project and all dependent projects.
     */
    fun deactivateCommand(projectId: ProjectId, index: Int): ReleasePlan {
        requireValidProjectId(projectId)

        val newProject = createNewProjectWithDeactivatedCommand(projectId, index)
        val projectsToDeactivate = collectProjectsToDeactivate(projectId)
        val newProjects = deactivateProjects(projectsToDeactivate, newProject)
        return ReleasePlan(releasePlan, newProjects)
    }

    private fun createNewProjectWithDeactivatedCommand(projectId: ProjectId, index: Int): Project {
        val oldProject = releasePlan.getProject(projectId)
        val commands = oldProject.commands
        require(commands.size > index) {
            "Index $index was out of bound.\nNumber of commands: ${commands.size}\nProjectId: $projectId"
        }
        val commandToDeactivate = commands[index]
        require(commandToDeactivate.state !is CommandState.Deactivated) {
            "Cannot deactivate an already deactivated command.\nCommand: $commandToDeactivate\nProjectId: $projectId"
        }

        val newCommands = mutableListOf<Command>()
        (0 until index - 1).mapTo(newCommands) {
            deactivateIfNotAlreadyAndIsReleaseCommand(commands[it])
        }
        newCommands.add(commandToDeactivate.asDeactivated())
        (index + 1 until commands.size).mapTo(newCommands) {
            deactivateIfNotAlreadyAndIsReleaseCommand(commands[it])
        }
        return Project(oldProject, newCommands)
    }

    private fun deactivateIfNotAlreadyAndIsReleaseCommand(oldCommand: Command): Command {
        return if (oldCommand.state !is CommandState.Deactivated && oldCommand is ReleaseCommand) {
            oldCommand.asDeactivated()
        } else {
            oldCommand
        }
    }

}
