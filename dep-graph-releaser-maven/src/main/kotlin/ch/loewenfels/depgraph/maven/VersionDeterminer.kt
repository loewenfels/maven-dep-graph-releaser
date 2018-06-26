package ch.loewenfels.depgraph.maven

import java.util.regex.Pattern

class VersionDeterminer {
    fun releaseVersion(currentVersion: String): String = when {
        currentVersion.endsWith(SNAPSHOT_SUFFIX) -> currentVersion.substringBefore(SNAPSHOT_SUFFIX)
        else -> updateLastNumber(currentVersion)
    }

    fun nextDevVersion(currentVersion: String): String {
        val releaseVersion = releaseVersion(currentVersion)
        return updateLastNumber(releaseVersion) + SNAPSHOT_SUFFIX
    }

    private fun updateLastNumber(version: String): String {
        val matcher = LAST_NUMBER_PATTERN.matcher(version)
        return if (matcher.find()) {
            val incrementedNumber = matcher.group(2).toInt() + 1
            matcher.replaceFirst("$1$incrementedNumber")
        } else {
            "$version.2"
        }
    }

    companion object {
        private const val SNAPSHOT_SUFFIX = "-SNAPSHOT"
        private val LAST_NUMBER_PATTERN = Pattern.compile("(.*\\.)?(\\d+)(?:.*)")
    }
}
