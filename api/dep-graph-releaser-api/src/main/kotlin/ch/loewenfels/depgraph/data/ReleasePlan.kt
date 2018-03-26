package ch.loewenfels.depgraph.data

import ch.loewenfels.depgraph.LevelIterator

/**
 * Represents a release plan where the [Project] with the given [rootProjectId] shall be released.
 *
 * Dependent projects (incl. transitive) of the root project as well as the root project itself
 * are stored in [projects]. The relation between the projects are stored in [dependents]
 * where the key represents the dependency and the value the dependent.
 */
data class ReleasePlan(
    val rootProjectId: ProjectId,
    private val projects: Map<ProjectId, Project>,
    private val submodules: Map<ProjectId, Set<ProjectId>>,
    private val dependents: Map<ProjectId, Set<ProjectId>>,
    val warnings: List<String>,
    val infos: List<String>
) {

    /**
     * Copy constructor to replace [projects].
     */
    constructor(releasePlan: ReleasePlan, projects: Map<ProjectId, Project>) :
        this(
            releasePlan.rootProjectId,
            projects,
            releasePlan.submodules,
            releasePlan.dependents,
            releasePlan.warnings,
            releasePlan.infos
        )

    /**
     * Creates a [ReleasePlan] with an empty list of [warnings] and [infos]
     */
    constructor(
        rootProjectId: ProjectId,
        projects: Map<ProjectId, Project>,
        submodulesOfProject: Map<ProjectId, Set<ProjectId>>,
        dependents: Map<ProjectId, Set<ProjectId>>
    ) :
        this(rootProjectId, projects, submodulesOfProject, dependents, listOf(), listOf())

    fun getProject(projectId: ProjectId): Project {
        return projects[projectId] ?: throw IllegalArgumentException("Could not find the project with id $projectId")
    }

    fun hasSubmodules(projectId: ProjectId) = getSubmodules(projectId).isNotEmpty()
    fun getSubmodules(projectId: ProjectId): Set<ProjectId> {
        return submodules[projectId]
            ?: throw IllegalArgumentException("Could not find submodules for project with id $projectId")
    }

    fun getDependents(projectId: ProjectId): Set<ProjectId> {
        return dependents[projectId]
            ?: throw IllegalArgumentException("Could not find dependents for project with id $projectId")
    }

    fun iterator(): Iterator<Project> = ReleasePlanIterator(this, rootProjectId)
    fun iterator(entryPoint: ProjectId): Iterator<Project> = ReleasePlanIterator(this, entryPoint)

    fun getProjectIds(): Set<ProjectId> = projects.keys
    fun getProjects(): Collection<Project> = projects.values
    fun getNumberOfProjects() = projects.size
    fun getAllProjects(): Map<ProjectId, Project> = projects

    fun getNumberOfDependents() = dependents.size
    fun getAllDependents(): Map<ProjectId, Set<ProjectId>> = dependents
    fun getAllSubmodules(): Map<ProjectId, Set<ProjectId>> = submodules


    private class ReleasePlanIterator(
        private val releasePlan: ReleasePlan,
        entryPoint: ProjectId
    ) : Iterator<Project> {
        private val levelIterator = LevelIterator(entryPoint to releasePlan.getProject(entryPoint))
        private val visitedProjects = hashSetOf<ProjectId>()

        override fun hasNext() = levelIterator.hasNext()
        override fun next(): Project {
            val project = levelIterator.next()
            visitedProjects.add(project.id)
            releasePlan.getDependents(project.id)
                .asSequence()
                .filter { !visitedProjects.contains(it) }
                .map { releasePlan.getProject(it) }
                .filter { it.level == project.level + 1 || (it.isSubmodule && it.level == project.level) }
                .forEach {
                    if(it.level == project.level){
                        levelIterator.addToCurrentLevel(it.id to it)
                    } else {
                        levelIterator.addToNextLevel(it.id to it)
                    }
                }
            return project
        }
    }
}
