package ch.loewenfels.depgraph.maven

import ch.loewenfels.depgraph.data.ProjectId
import java.util.regex.Matcher
import java.util.regex.Pattern

class VersionDeterminer {
    fun releaseVersion(projectId: ProjectId): String = when {
        projectId.version.endsWith(SNAPSHOT_SUFFIX) -> projectId.version.substringBefore(SNAPSHOT_SUFFIX)
        else -> updateLastNumber(projectId.version)
    }

    private fun updateLastNumber(version: String): String {
        val matcher = LAST_NUMBER_PATTERN.matcher(version)
        return if (matcher.find()) {
            matcher.group(1) + incrementNumber(matcher, 2)
        } else {
            version + ".2"
        }
    }

    private fun incrementNumber(matcher: Matcher, index: Int): Int = matcher.group(index).toInt() + 1

    fun nextDevVersion(projectId: ProjectId): String {
        val releaseVersion = releaseVersion(projectId)
        val matcher = MAJOR_MINOR_PATCH_PATTERN.matcher(releaseVersion)
        matcher.find()
        val incrementedLastDigit = incrementNumber(matcher, PATCH_GROUP).toString()
        return optionalPrefix(matcher) + when {
            onlyMajor(matcher) -> incrementedLastDigit + SNAPSHOT_SUFFIX
            onlyMajorAndMinor(matcher) -> matcher.group(MAJOR_GROUP) + incrementedLastDigit + SNAPSHOT_SUFFIX
            else -> matcher.group(MAJOR_GROUP) + matcher.group(MINOR_GROUP) + incrementNumber(matcher, PATCH_GROUP) + SNAPSHOT_SUFFIX
        }
    }

    private fun onlyMajor(matcher: Matcher) = matcher.group(MAJOR_GROUP) == null
    private fun onlyMajorAndMinor(matcher: Matcher) = matcher.group(MINOR_GROUP) == null
    private fun optionalPrefix(matcher: Matcher): String = matcher.group(PREFIX_GROUP) ?: ""

    companion object {
        private const val SNAPSHOT_SUFFIX = "-SNAPSHOT"
        private val LAST_NUMBER_PATTERN = Pattern.compile("(.*)([0-9]+)[^0-9]*")
        private val MAJOR_MINOR_PATCH_PATTERN = Pattern.compile("([^0-9]+)?([0-9]+[^0-9]+)?([0-9]+[^0-9]+)?([0-9]+)")
        private const val PREFIX_GROUP = 1
        private const val MAJOR_GROUP = 2
        private const val MINOR_GROUP = 3
        private const val PATCH_GROUP = 4
    }
}
