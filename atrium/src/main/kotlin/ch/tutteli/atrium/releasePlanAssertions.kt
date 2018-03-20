package ch.tutteli.atrium

import ch.loewenfels.depgraph.data.ReleasePlan
import ch.tutteli.atrium.api.cc.en_UK.*
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
        .contains.inAnyOrder.only.values(dependentIdAndVersion.id, *otherDependentIdAndVersion.map { it.id }.toTypedArray())
}
