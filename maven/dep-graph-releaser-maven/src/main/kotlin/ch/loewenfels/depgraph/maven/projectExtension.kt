package ch.loewenfels.depgraph.maven

import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import fr.lteconsulting.pomexplorer.model.Gav

fun MavenProjectId.toGav(): Gav = Gav(groupId, artifactId, version)
