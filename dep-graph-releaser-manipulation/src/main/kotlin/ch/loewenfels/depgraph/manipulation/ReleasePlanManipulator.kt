package ch.loewenfels.depgraph.manipulation

import ch.loewenfels.depgraph.data.*

class ReleasePlanManipulator(private val releasePlan: ReleasePlan) {

    fun deactivateProject(projectId: ProjectId): ReleasePlan {
        requireValidProjectId(projectId, "Deactivating")

        val projectsToDeactivate = collectDependentProjects(projectId)
        val newProjects = transformProjects(projectsToDeactivate, this::transformToDeactivatedProject)
        return ReleasePlan(releasePlan, newProjects)
    }

    private fun transformToDeactivatedProject(project: Project): Project {
        val newCommands = project.commands.map {
            transformToDeactivatedCommand(it)
        }
        return Project(project, newCommands)
    }

    private fun transformToDeactivatedCommand(it: Command): Command {
        return if (it.state is CommandState.Deactivated || it.state === CommandState.Disabled) {
            it
        } else {
            it.asDeactivated()
        }
    }

    private fun transformToDisabledCommand(it: Command): Command {
        return if (it.state === CommandState.Disabled) {
            it
        } else {
            it.asDisabled()
        }
    }

    private fun requireValidProjectId(projectId: ProjectId, action: String) {
        require(projectId != releasePlan.rootProjectId) {
            "$action the root project does not make sense.\nRoot project: ${releasePlan.rootProjectId.identifier}"
        }

        //will throw if not existing
        releasePlan.getProject(projectId)
    }

    private fun collectDependentProjects(projectId: ProjectId): Set<ProjectId> {
        return releasePlan.iterator(projectId)
            .asSequence()
            .map { it.id }
            .toHashSet()
    }

    private fun transformProjects(
        projectsToDeactivate: Set<ProjectId>,
        transform: (Project) -> Project
    ): Map<ProjectId, Project> = transformProjects(projectsToDeactivate, null, transform)

    /**
     * Transforms all projects which are defined in [projectsToTransform] with the help of [transform] and
     * returns a new [Map], unless [newProject] is set and the project to deactivate is the same,
     * in this case the [newProject] is used.
     */
    private fun transformProjects(
        projectsToTransform: Set<ProjectId>,
        newProject: Project?,
        transform: (Project) -> Project
    ): Map<ProjectId, Project> {
        return releasePlan.getAllProjects().entries.associate { (k, v) ->
            k to when {
                newProject != null && newProject.id == v.id -> newProject
                projectsToTransform.contains(k) -> transform(v)
                else -> v
            }
        }
    }

    /**
     * Deactivates the command at position [index] of the project with the given [projectId], deactivates all
     * [ReleaseCommand]s of the project and all dependent projects.
     * @throws IllegalArgumentException in case the command is already deactivated.
     */
    fun deactivateCommand(projectId: ProjectId, index: Int): ReleasePlan {
        return transformCommand(
            projectId,
            "Deactivating a command of",
            index,
            this::transformToDeactivatedCommand,
            this::transformToDeactivatedProject
        ) {
            require(it.state !is CommandState.Deactivated) {
                "Cannot deactivate an already deactivated command.\nCommand: $it\nProjectId: $projectId"
            }
            require(it.state !== CommandState.Disabled) {
                "Cannot deactivate an already disabled command.\nCommand: $it\nProjectId: $projectId"
            }
        }
    }

    /**
     * Disables the command at position [index] of the project with the given [projectId], deactivates all
     * [ReleaseCommand]s of the project and all dependent projects.
     * @throws IllegalArgumentException in case the command is already disabled.
     */
    fun disableCommand(projectId: ProjectId, index: Int): ReleasePlan {
        return transformCommand(
            projectId,
            "Disabling a command of",
            index,
            this::transformToDisabledCommand,
            this::transformToDeactivatedProject
        ) {
            require(it.state !== CommandState.Disabled) {
                "Cannot disable an already disabled command.\nCommand: $it\nProjectId: $projectId"
            }
        }
    }

    private fun transformCommand(
        projectId: ProjectId,
        action: String,
        index: Int,
        transformCommand: (Command) -> Command,
        transformProject: (Project) -> Project,
        checkNotAlreadyTransformed: (Command) -> Unit
    ): ReleasePlan {
        requireValidProjectId(projectId, action)

        val newProject = createNewProjectWithTransformedCommand(
            projectId, index, transformCommand, checkNotAlreadyTransformed
        )
        val projectsToTransform = collectDependentProjects(projectId)
        val newProjects = transformProjects(projectsToTransform, newProject, transformProject)
        return ReleasePlan(releasePlan, newProjects)
    }

    private fun createNewProjectWithTransformedCommand(
        projectId: ProjectId,
        index: Int,
        transform: (Command) -> Command,
        checkNotAlreadyTransformed: (Command) -> Unit
    ): Project {
        val oldProject = releasePlan.getProject(projectId)
        val commands = oldProject.commands
        require(commands.size > index) {
            "Index $index was out of bound.\nNumber of commands: ${commands.size}\nProjectId: $projectId"
        }
        val commandToTransform = commands[index]
        checkNotAlreadyTransformed(commandToTransform)

        val newCommands = mutableListOf<Command>()
        (0 until index).mapTo(newCommands) {
            transformIfNotAlreadyAndIsReleaseCommand(commands[it], transform)
        }
        newCommands.add(transform(commandToTransform))
        (index + 1 until commands.size).mapTo(newCommands) {
            transformIfNotAlreadyAndIsReleaseCommand(commands[it], transform)
        }
        return Project(oldProject, newCommands)
    }

    private fun transformIfNotAlreadyAndIsReleaseCommand(
        oldCommand: Command,
        transform: (Command) -> Command
    ): Command {
        return if (oldCommand is ReleaseCommand) {
            //should only transform if not already transformed
            transform(oldCommand)
        } else {
            oldCommand
        }
    }
}
