package ch.loewenfels.depgraph.maven

import ch.loewenfels.depgraph.data.Relation
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.tutteli.kbox.appendToStringBuilder
import fr.lteconsulting.pomexplorer.*
import fr.lteconsulting.pomexplorer.graph.relation.DependencyLikeRelation
import fr.lteconsulting.pomexplorer.graph.relation.ParentRelation
import fr.lteconsulting.pomexplorer.model.Gav
import java.io.File
import java.util.logging.Logger

class Analyser internal constructor(
    directoryWithProjects: File,
    private val session: Session,
    pomFileLoader: PomFileLoader,
    options: Options = Options()
) {
    constructor(directoryWithProjects: File, options: Options)
        : this(directoryWithProjects, Session(), options)

    private constructor(directoryWithProjects: File, session: Session, options: Options)
        : this(directoryWithProjects, session, DefaultPomFileLoader(session, true), options)

    private val logger = Logger.getLogger(Analyser::class.qualifiedName)
    private val dependents: Map<String, Set<Relation<MavenProjectId>>>
    private val projectIds: Map<MavenProjectId, String>
    private val submodulesOfProjectId: Map<MavenProjectId, Set<MavenProjectId>>
    private val multiModulesOfSubmodule: Map<MavenProjectId, Set<MavenProjectId>>
    private val pomAnalysis: PomAnalysis

    init {
        require(directoryWithProjects.exists()) {
            "Cannot analyse because the given directory does not exists: ${directoryWithProjects.absolutePath}"
        }
        pomAnalysis = analyseDirectory(directoryWithProjects, pomFileLoader)
        val analysedProjects = getAnalysedProjects()
        require(analysedProjects.isNotEmpty()){
            "No pom files found in the given directory (which exists): ${directoryWithProjects.absolutePath}"
        }

        val pair = analyseSubmodules()
        submodulesOfProjectId = pair.first
        multiModulesOfSubmodule = pair.second

        val duplicates = collectDuplicates(pomAnalysis)
        //TODO maybe we should still emit a warning if missing parent analysis is turned off?
        // => introduce warn in addition to off maybe?
        val parentsNotInAnalysis = collectParentsNotInAnalysis(options, analysedProjects)
        //TODO error if a submodule is not part of the analysis
        //val submodulesNotInAnalysis = collectSubmodulesNotInAnalysis(options, analysedProjects)
        reportDuplicatesAndMissingParentsIfNecessary(directoryWithProjects, duplicates, parentsNotInAnalysis)

        dependents = analyseDependents(analysedProjects)
        projectIds = getInternalAnalysedGavs()
            .associateBy({ it.toMavenProjectId() }, { it.version })
    }

    private fun analyseDirectory(directoryWithProjects: File, pomFileLoader: PomFileLoader): PomAnalysis {
        val nullLogger = Log { }
        return PomAnalysis.runFullRecursiveAnalysis(
            directoryWithProjects.absolutePath,
            session,
            pomFileLoader,
            null,
            false,
            nullLogger
        )
    }

    private fun getAnalysedProjects(): Set<String> {
        return getInternalAnalysedGavs()
            .asSequence()
            .map { it.toMapKey() }
            .toSet()
    }

    private fun collectDuplicates(pomAnalysis: PomAnalysis): Map<String, List<Project>> {
        val sequence = pomAnalysis.duplicatedProjects.asSequence() + getInternalAnalysedProjects()
        return sequence
            .groupBy { it.gav.toMapKey() }
            .filterValues { it.size > 1 }
    }


    private fun collectParentsNotInAnalysis(options: Options, analysedProjects: Set<String>): Map<Project, Gav> {
        if (options.missingParentAnalysis) {
            return getInternalAnalysedProjects()
                .filter { it ->
                    val parentGav = it.parentGav
                    parentGav != null && !analysedProjects.contains(parentGav.toMapKey())
                }
                .associateBy({ it }, { it.parentGav })
        }
        logger.info("skipping missing parent analysis")
        return mapOf()
    }

    private fun analyseDependents(analysedProjects: Set<String>): Map<String, Set<Relation<MavenProjectId>>> {
        val dependents = hashMapOf<String, MutableSet<Relation<MavenProjectId>>>()
        getInternalAnalysedGavs().forEach { gav ->
            session.graph().read().relations(gav)
                .asSequence()
                .filter { analysedProjects.contains(it.targetToMapKey()) }
                .forEach { relation ->
                    val set = dependents.getOrPut(relation.targetToMapKey(), { mutableSetOf() })
                    when (relation) {
                        is DependencyLikeRelation ->
                            set.add(gav.toRelation(relation.dependency.isVersionSelfManaged.orElse(false)))

                        is ParentRelation ->
                            set.add(gav.toRelation(true))

                    //we ignore other relations at the moment (such as BuildDependencyRelation)
                    }
                }
        }
        return dependents
    }

    private fun getInternalAnalysedGavs() = session.projects()
        .keySet()
        .asSequence()
        .filter { it.toProject().isNotExternal }

    private fun getInternalAnalysedProjects() = session.projects()
        .keySet()
        .asSequence()
        .map { it.toProject() }
        .filter { it.isNotExternal }

    private fun Gav.toProject() = session.projects().forGav(this)
    private val Project.isNotExternal get() = !isExternal

    private fun Gav.toRelation(isVersionSelfManaged: Boolean): Relation<MavenProjectId> =
        Relation(toMavenProjectId(), version, isVersionSelfManaged)

    private fun fr.lteconsulting.pomexplorer.graph.relation.Relation.targetToMapKey() = target.toMapKey()
    private fun Gav.toMapKey() = toMavenProjectId().identifier
    private fun Gav.toMavenProjectId() = MavenProjectId(groupId, artifactId)


    private fun reportDuplicatesAndMissingParentsIfNecessary(
        directoryWithProjects: File,
        duplicates: Map<String, List<Project>>,
        parentsNotInAnalysis: Map<Project, Gav>
    ) {
        val sb = StringBuilder()

        if (duplicates.isNotEmpty()) {
            sb.append("Found duplicated projects in the given `directoryWithProjects`.\n")
                .append("directory: ${directoryWithProjects.canonicalPath}\n")
                .append("duplicates:\n\n")
            duplicates.values.appendToStringBuilder(sb, "\n\n") { projects, _ ->
                projects.appendToStringBuilder(sb, "\n") { project, _ ->
                    sb.append(projectToString(project))
                }
            }
        }

        if (parentsNotInAnalysis.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append("\n")
            sb.append("Found projects with parents where the parents are not part of this analysis.\n\n")
            parentsNotInAnalysis.entries.appendToStringBuilder(sb, "\n\n") { (project, parent), _ ->
                sb.append("project: ").append(projectToString(project)).append("\n")
                sb.append("parent: ").append(parent.groupId).append(":").append(parent.artifactId).append(":")
                    .append(parent.version)
            }
        }

        check(sb.isEmpty()) { sb.toString() }
    }

    private fun projectToString(project: Project): String = "${project.gav} (${project.pomFile.canonicalPath})"

    private fun analyseSubmodules(): kotlin.Pair<Map<MavenProjectId, Set<MavenProjectId>>, Map<MavenProjectId, Set<MavenProjectId>>> {
        val submodulesOfProjectId = hashMapOf<MavenProjectId, HashSet<MavenProjectId>>()
        val multiModulesOfSubmodule = hashMapOf<MavenProjectId, HashSet<MavenProjectId>>()
        getInternalAnalysedGavs().forEach { gav ->
            val gavsToVisit = linkedSetOf(gav)
            while (gavsToVisit.isNotEmpty()) {
                val multiModuleGav = gavsToVisit.iterator().next()
                gavsToVisit.remove(multiModuleGav)
                val multiModuleProjectId = multiModuleGav.toMavenProjectId()
                val submodules = submodulesOfProjectId.getOrPut(multiModuleProjectId, { hashSetOf() })

                session.projects().getSubmodulesAsStream(multiModuleGav).forEach { submoduleGav ->
                    val submoduleProjectId = submoduleGav.toMavenProjectId()
                    val multiModules = multiModulesOfSubmodule.getOrPut(submoduleProjectId, { hashSetOf() })
                    multiModules.add(multiModuleProjectId)
                    val notAlreadyContained = submodules.add(submoduleProjectId)
                    //just to prevent from maniac projects which have cyclic modules defined :)
                    if (notAlreadyContained && !gavsToVisit.contains(submoduleGav)) {
                        gavsToVisit.add(submoduleGav)
                    }
                }
            }
        }
        return submodulesOfProjectId to multiModulesOfSubmodule
    }

    /**
     * Returns the current version for the given [projectId] if it was involved in the analysis; `null` otherwise.
     * @return The current version or `null` if [projectId] was not part of the analysis.
     */
    fun getCurrentVersion(projectId: MavenProjectId): String? = projectIds[projectId]


    /**
     * Returns the [MavenProjectId]s of the analysed projects together with the current version.
     */
    fun getAnalysedProjectsAsString(): CharSequence {
        val sb = StringBuilder()
        projectIds.entries.joinTo(sb, transform = { (k, v) -> "$k:$v" })
        return sb
    }

    /**
     * Returns a set of dependent [MavenProjectId]s for the given [projectId] where only the
     * [MavenProjectId.groupId] and [MavenProjectId.artifactId] of the given [projectId] are considered.
     *
     * Meaning, if a project ch.loewenfels:A has a dependency on Project ch.loewenfels:B:1.0 and
     * the analysed project B is in version 2.0-SNAPSHOT then project A is still dependent of project B.
     */
    fun getDependentsOf(projectId: MavenProjectId): Set<Relation<MavenProjectId>> {
        return dependents[projectId.identifier] ?: emptySetOrThrow(projectId)
    }

    /**
     * Returns the number of analysed projects.
     */
    fun getNumberOfProjects(): Int = projectIds.size

    fun getErroneousPomFiles(): List<String> = pomAnalysis.erroneousPomFiles.map {
        "Error reading pom file.\nFile: ${it.pomFile.canonicalPath}\nMessage: ${it.cause!!.message}"
    }

    /**
     * Returns all modules of the given multi module project including nested submodules (submodules of submodules)
     * or an empty set if the project is not a multi module (has not any modules).
     */
    fun getSubmodulesInclNested(projectId: MavenProjectId): Set<MavenProjectId> {
        return submodulesOfProjectId[projectId] ?: emptySetOrThrow(projectId)
    }

    /**
     * Returns all multi modules of the given submodule project including super multi modules (multi module of multi module)
     * or an empty set if the project is not a submodule.
     */
    fun getMultiModules(projectId: MavenProjectId): Set<MavenProjectId> {
        return multiModulesOfSubmodule[projectId] ?: emptySetOrThrow(projectId)
    }

    /**
     * Indicates if the given [projectId] is a submodule of a multi module project or not.
     */
    fun isSubmodule(projectId: MavenProjectId): Boolean
        = getMultiModules(projectId).isNotEmpty()

    fun isSubmoduleOf(submoduleId: MavenProjectId, multiModuleId: MavenProjectId)
        = getSubmodulesInclNested(multiModuleId).contains(submoduleId)

    private fun <T> emptySetOrThrow(projectId: MavenProjectId): Set<T> {
        return if (projectIds.containsKey(projectId)) {
            emptySet()
        } else {
            throwProjectNotPartOfAnalysis(projectId)
        }
    }

    private fun throwProjectNotPartOfAnalysis(projectId: MavenProjectId): Nothing {
        throw IllegalArgumentException("project is not part of the analysis: $projectId")
    }

    /**
     * Options for the [Analyser].
     */
    data class Options(
        val missingParentAnalysis: Boolean = true
    )
}
