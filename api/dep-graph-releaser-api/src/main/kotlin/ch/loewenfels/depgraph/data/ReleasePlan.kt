package ch.loewenfels.depgraph.data

/**
 * Represents a release plan where the [Project] with the given [rootProjectId] shall be released.
 *
 * Dependent projects (incl. transitive) of the root project as well as the root project itself
 * are stored in [projects]. The relation between the projects are stored in [dependents]
 * where the key represents the dependency and the value the dependent.
 */
data class ReleasePlan(
    val rootProjectId: ProjectId,
    val projects: Map<ProjectId, Project>,
    val dependents: Map<ProjectId, Set<ProjectId>>
) {
    constructor(releasePlan: ReleasePlan, projects: Map<ProjectId, Project>) :
        this(releasePlan.rootProjectId, projects, releasePlan.dependents)

    fun getProject(projectId: ProjectId): Project {
        return projects[projectId] ?: throw IllegalArgumentException("Could not find the project with id $projectId")
    }

    fun getDependents(projectId: ProjectId): Set<ProjectId> {
        return dependents[projectId]
            ?: throw IllegalArgumentException("Could not find dependents for project with id $projectId")
    }

    fun iterator(): Iterator<Project> = ReleasePlanIterator(this, rootProjectId)
    fun iterator(entryPoint: ProjectId): Iterator<Project> = ReleasePlanIterator(this, entryPoint)

    private class ReleasePlanIterator(private val releasePlan: ReleasePlan, private val entryPoint: ProjectId) : Iterator<Project> {
        private val projectsToVisit = linkedMapOf(entryPoint to releasePlan.getProject(entryPoint))
        private val visitedProjects = hashSetOf<ProjectId>()

        override fun hasNext() = projectsToVisit.isNotEmpty()
        override fun next(): Project {
            if (projectsToVisit.isEmpty()) {
                throw NoSuchElementException("No project left; entry point was $entryPoint")
            }
            val project = projectsToVisit.remove(projectsToVisit.iterator().next().key)
            releasePlan.getDependents(project!!.id)
                .asSequence()
                .filter { !visitedProjects.contains(it) }
                .map { releasePlan.getProject(it) }
                .filter { project.level + 1 == it.level }
                .associateByTo(projectsToVisit, { it.id }, { it })
            return project
        }
    }
}
