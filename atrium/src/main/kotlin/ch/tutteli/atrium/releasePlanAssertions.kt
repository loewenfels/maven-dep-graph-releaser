package ch.tutteli.atrium

import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.ReleasePlan
import ch.tutteli.atrium.api.cc.en_UK.*
import ch.tutteli.atrium.assertions._method
import ch.tutteli.atrium.creating.Assert

fun Assert<ReleasePlan>.hasNoDependentsForProject(
    idAndVersions: IdAndVersions
) {
    returnValueOf(subject::getDependents, idAndVersions.id).isEmpty()
}

fun Assert<ReleasePlan>.hasDependentsForProject(
    idAndVersions: IdAndVersions,
    dependentIdAndVersion: IdAndVersions,
    vararg otherDependentIdAndVersion: IdAndVersions
) {
    returnValueOf(subject::getDependents, idAndVersions.id)
        .contains.inAnyOrder.only.values(
        dependentIdAndVersion.id,
        *otherDependentIdAndVersion.map { it.id }.toTypedArray()
    )
}

fun Assert<ReleasePlan>.iteratorReturnsRootAndStrictly(vararg otherProject: ProjectId) {
    val rootProject = subject.getRootProject()
    _method<ReleasePlan, List<ProjectId>>(this, "iterator", { iteratorProjectIdsToList() }) {
        containsStrictly(rootProject.id, *otherProject)
    }
}

private fun Assert<ReleasePlan>.iteratorProjectIdsToList() =
    subject.iterator().asSequence().map { it.id }.toList()

/**
 * The elements within the group can occur in any order but have all to be there
 */
fun Assert<ReleasePlan>.iteratorReturnsRootAndInOrderGrouped(vararg otherGroups: List<ProjectId>) {
    val rootProject = subject.getRootProject()
    _method<ReleasePlan, List<ProjectId>>(this, "iterator", { iteratorProjectIdsToList() }) {
        var index = 0
        (sequenceOf(listOf(rootProject.id)) + otherGroups.asSequence()).forEach {
            val tmpIndex = index + it.size
            val toIndex = if (tmpIndex < subject.size) {
                tmpIndex
            } else {
                subject.size
            }

            _method(this, "index $index to $toIndex", { subject.subList(index, toIndex) })
                .contains.inAnyOrder.only.values(it.first(), *it.drop(1).toTypedArray())

            index = toIndex
        }
        hasSize(index)
    }
}

