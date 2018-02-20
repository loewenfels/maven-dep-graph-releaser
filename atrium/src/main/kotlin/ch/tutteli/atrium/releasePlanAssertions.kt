package ch.tutteli.atrium

import ch.loewenfels.depgraph.data.ReleasePlan
import ch.tutteli.atrium.api.cc.en_UK.*
import ch.tutteli.atrium.creating.Assert

fun Assert<ReleasePlan>.hasNotDependentsForProject(
    idAndVersions: IdAndVersions
) {
    property(subject::dependents) {
        returnValueOf(subject::get, idAndVersions.id).isNotNull { isEmpty() }
    }
}

fun Assert<ReleasePlan>.hasDependentsForProject(
    idAndVersions: IdAndVersions,
    dependentIdAndVersion: IdAndVersions,
    vararg otherDependentIdAndVersion: IdAndVersions
) {
    property(subject::dependents) {
        returnValueOf(subject::get, idAndVersions.id).isNotNull {
            contains.inAnyOrder.only.values(dependentIdAndVersion.id, *otherDependentIdAndVersion.map { it.id }.toTypedArray())
        }
    }
}
