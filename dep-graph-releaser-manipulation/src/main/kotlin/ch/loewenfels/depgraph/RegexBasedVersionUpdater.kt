package ch.loewenfels.depgraph

import java.io.File

class RegexBasedVersionUpdater {

    fun updateDependency(pom: File, groupId: String, artifactId: String, newVersion: String) {
        val groupIdPattern = "<groupId>$groupId</groupId>"
        val artifactIdPattern = "<artifactId>$artifactId</artifactId>"
        val groupIdArtifactIdRegex =
            Regex("(?:$groupIdPattern[\\S\\s]*?$artifactIdPattern)|(?:$artifactIdPattern[\\S\\s]*?$groupIdPattern)")
        val content = pom.readText()

        val paramObject = ParamObject(groupId, artifactId, newVersion, content, dependencyRegex.find(content, 0))
        while (paramObject.matchResult != null) {
            val matchResult = paramObject.matchResult!!
            paramObject.appendSubstring(paramObject.startIndex, matchResult.range.start)
            paramObject.startIndex = matchResult.range.start
            if (groupIdArtifactIdRegex.containsMatchIn(matchResult.value)) {
                appendDependency(paramObject)
            }
            paramObject.matchResult = matchResult.next()
        }
        paramObject.appendSubstring(paramObject.startIndex)

        //TODO deal with properties

        if (paramObject.updated) {
            pom.writeText(paramObject.modifiedPom.toString())
        } else {
            throw IllegalStateException(
                "cannot update dependency $groupId:$artifactId because " +
                    "it is either not managed by the given pom or " +
                    "not in there at all.\npom: ${pom.absolutePath}"
            )
        }
    }

    private fun appendDependency(paramObject: ParamObject) {
        val matchResult = paramObject.matchResult!!

        val versionMatchResult = versionRegex.find(matchResult.value)
        if (versionMatchResult != null) {
            //append everything before <version>
            paramObject.appendSubstring(paramObject.startIndex, paramObject.startIndex + versionMatchResult.range.start)

            appendVersion(paramObject, versionMatchResult)

            //append everything after </version>
            paramObject.appendSubstring(
                paramObject.startIndex + versionMatchResult.range.endInclusive + 1, matchResult.range.endInclusive
            )
            paramObject.startIndex = matchResult.range.endInclusive

            check(versionMatchResult.next() == null) {
                "<dependency> has two <version>: ${paramObject.groupId}:${paramObject.artifactId}"
            }
        }
    }

    private fun appendVersion(paramObject: ParamObject, versionMatchResult: MatchResult) {
        paramObject.modifiedPom.append("<version>")
        val version = versionMatchResult.groupValues[1]
        when {
            propertyRegex.matches(version) -> {
                paramObject.properties.add(version)
                paramObject.modifiedPom.append(version)
            }
            version.contains("$") -> throw UnsupportedOperationException("Version was neither static nor a reference to a single property. Given: $version")
            else -> {
                paramObject.modifiedPom.append(paramObject.newVersion)
                paramObject.updated = true
            }
        }
        paramObject.modifiedPom.append("</version>")
    }

    class ParamObject(
        val groupId: String,
        val artifactId: String,
        val newVersion: String,
        private val content: String,
        var matchResult: MatchResult?
    ) {
        val modifiedPom = StringBuilder()
        var startIndex: Int = 0
        val properties = hashSetOf<String>()
        var updated = false

        fun appendSubstring(startIndex: Int) {
            modifiedPom.append(content.substring(startIndex))
        }

        fun appendSubstring(startIndex: Int, endIndex: Int) {
            modifiedPom.append(content.substring(startIndex, endIndex))
        }
    }

    companion object {
        private val dependencyRegex = Regex("<dependency>[\\S\\s]+?</dependency>")
        private val versionRegex = Regex("<version>([^<]+)</version>")
        private val propertyRegex = Regex("\\$\\{[^}]+}")
    }
}
