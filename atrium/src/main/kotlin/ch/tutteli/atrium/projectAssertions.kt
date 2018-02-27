package ch.tutteli.atrium

import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.tutteli.atrium.api.cc.en_UK.property
import ch.tutteli.atrium.api.cc.en_UK.toBe
import ch.tutteli.atrium.creating.Assert

data class IdAndVersions(val id: MavenProjectId, val currentVersion: String, val releaseVersion: String, val nextDevVersion: String) {
    constructor(id: MavenProjectId, parent: IdAndVersions) : this(id, parent.currentVersion, parent.releaseVersion, parent.nextDevVersion)
}

fun Assert<Project>.idAndVersions(idAndVersions: IdAndVersions): Assert<Project> {
    property(subject::id).toBe(idAndVersions.id)
    property(subject::currentVersion).toBe(idAndVersions.currentVersion)
    property(subject::releaseVersion).toBe(idAndVersions.releaseVersion)
    return this
}

fun Assert<Project>.hasSameVersionsAs(otherProject: Project): Assert<Project> {
    property(subject::currentVersion).toBe(otherProject.currentVersion)
    property(subject::releaseVersion).toBe(otherProject.releaseVersion)
    return this
}
