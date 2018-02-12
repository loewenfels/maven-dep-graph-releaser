package ch.loewenfels.depgraph.maven

import ch.loewenfels.depgraph.data.ProjectId
import java.util.regex.Pattern

class VersionDeterminer {
    fun determineNextVersion(projectId: ProjectId): String = when {
        projectId.version.endsWith(SNAPSHOT_SUFFIX) -> projectId.version.substringBefore(SNAPSHOT_SUFFIX)
        else -> updateLastNumber(projectId.version)
    }

    private fun updateLastNumber(version: String): String {
        val matcher = Pattern.compile("(.*)([0-9]+)[^0-9]*").matcher(version)
        return if (matcher.find()) {
            matcher.group(1) + (matcher.group(2).toInt() + 1)
        } else {
            version + ".2"
        }
    }

    companion object {
        const val SNAPSHOT_SUFFIX = "-SNAPSHOT"
    }
}
