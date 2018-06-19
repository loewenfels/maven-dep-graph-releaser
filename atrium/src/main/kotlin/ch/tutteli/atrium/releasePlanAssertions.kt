package ch.tutteli.atrium

import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.ReleasePlan
import ch.tutteli.atrium.api.cc.en_GB.*
import ch.tutteli.atrium.creating.Assert
import ch.tutteli.atrium.domain.builders.AssertImpl

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
    AssertImpl.changeSubject(this) { iteratorProjectIdsToList() }.containsStrictly(rootProject.id, *otherProject)
}

private fun Assert<ReleasePlan>.iteratorProjectIdsToList() =
    subject.iterator().asSequence().map { it.id }.toList()

/**
 * The elements within the group can occur in any order but have all to be there
 */
fun Assert<ReleasePlan>.iteratorReturnsRootAndInOrderGrouped(vararg otherGroups: List<ProjectId>) {
    val rootProject = subject.getRootProject()
    val builder = AssertImpl.changeSubject(this) { iteratorProjectIdsToList() }.contains.inOrder.only.grouped.within
    AssertImpl.iterable.contains.valuesInOrderOnlyGrouped(builder, listOf(listOf(rootProject)) + otherGroups)
}
