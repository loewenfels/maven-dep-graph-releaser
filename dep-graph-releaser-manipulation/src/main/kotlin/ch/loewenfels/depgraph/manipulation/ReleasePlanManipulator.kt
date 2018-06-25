package ch.loewenfels.depgraph.manipulation

import ch.loewenfels.depgraph.data.*

class ReleasePlanManipulator(private val releasePlan: ReleasePlan) {

    fun deactivateProject(projectId: ProjectId): ReleasePlan {
        requireValidProjectId(projectId, "Deactivating")

        val newProject = transformToDeactivatedProject(projectId)
        val projectsToDeactivate = collectDependentProjects(projectId)

        val newProjects = transformProjects(
            projectsToDeactivate, newProject = null, transform = this::transformDependingCommandsToDeactivated
        )
        return ReleasePlan(releasePlan, newProjects + mapOf(projectId to newProject))
    }

    private fun transformToDeactivatedProject(projectId: ProjectId): Project {
        val project = releasePlan.getProject(projectId)
        val newCommands = project.commands.map {
            transformToDeactivatedCommand(it)
        }
        return Project(project, newCommands)
    }


    private fun stateIsWaitingAndDependent(
        state: CommandState,
        dependencies: Set<ProjectId>
    ) = state is CommandState.Waiting && state.dependencies.any { dependencies.contains(it) }

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

    private fun collectDependentProjects(targetProjectId: ProjectId): Map<ProjectId, Set<ProjectId>> {
        val projectIds = hashMapOf<ProjectId, MutableSet<ProjectId>>()
        val projectsToVisit = hashSetOf(targetProjectId)
        do {
            val projectId = projectsToVisit.iterator().next()
            projectsToVisit.remove(projectId)
            releasePlan.getDependents(projectId).forEach { dependentId ->
                val set = projectIds.computeIfAbsent(dependentId, { hashSetOf() })
                set.add(projectId)
                projectsToVisit.add(dependentId)
            }
        } while (projectsToVisit.isNotEmpty())
        return projectIds
    }

    /**
     * Transforms all projects which are defined in [projectsToTransform] with the help of [transform] and
     * returns a new [Map], unless [newProject] is set and the project to deactivate is the same,
     * in this case the [newProject] is used.
     */
    private fun transformProjects(
        projectsToTransform: Map<ProjectId, Set<ProjectId>>,
        newProject: Project?,
        transform: (Project, Set<ProjectId>) -> Project
    ): Map<ProjectId, Project> {
        return releasePlan.getProjectsWithProjectId().entries.associate { (k, v) ->
            k to when {
                newProject != null && newProject.id == v.id -> newProject
                projectsToTransform.contains(k) -> transform(v, projectsToTransform[k]!!)
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
            this::transformDependingCommandsToDeactivated
        ) {
            checkTransformationAllowed("deactivate", it, CommandState.Deactivated(it.state), projectId)
        }
    }

    private fun checkTransformationAllowed(
        action: String,
        command: Command,
        newState: CommandState,
        projectId: ProjectId
    ) {
        try {
            command.state.checkTransitionAllowed(newState)
        } catch (e: IllegalStateException) {
            throw IllegalArgumentException("Cannot $action command:\nCommand: $command\nProjectId: $projectId", e)
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
            this::transformDependingCommandsToDeactivated
        ) {
            checkTransformationAllowed("disable", it, CommandState.Disabled, projectId)
        }
    }

    private fun transformCommand(
        projectId: ProjectId,
        action: String,
        index: Int,
        transformCommand: (Command) -> Command,
        transformProject: (Project, Set<ProjectId>) -> Project,
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

    private fun transformDependingCommandsToDeactivated(project: Project, dependencies: Set<ProjectId>): Project {
        val newCommands = project.commands.map {
            if (it.state is ReleaseCommand || stateIsWaitingAndDependent(it.state, dependencies)) {
                transformToDeactivatedCommand(it)
            } else {
                it
            }
        }
        return Project(project, newCommands)
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
