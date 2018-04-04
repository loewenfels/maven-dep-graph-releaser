package ch.loewenfels.depgraph

import java.io.File

class RegexBasedVersionUpdater {

    fun updateDependency(pom: File, groupId: String, artifactId: String, newVersion: String) {
        val groupIdArtifactIdRegex = createGroupIdArtifactIdRegex(groupId, artifactId)
        val content = pom.readText()

        val dependenciesParamObject = ParamObject(groupId, artifactId, newVersion, hashSetOf(), content, dependencyRegex)
        updateDependencies(dependenciesParamObject, groupIdArtifactIdRegex)

        val parentParamObject = ParamObject(dependenciesParamObject, parentRegex)
        updateParentRelation(parentParamObject, groupIdArtifactIdRegex, pom)

        val propertiesParamObject = ParamObject(parentParamObject, propertiesRegex)
        updateProperties(propertiesParamObject, pom)

        if (dependenciesParamObject.updated || parentParamObject.updated || propertiesParamObject.updated) {
            pom.writeText(propertiesParamObject.getNewContent())
        } else {
            throw IllegalStateException(
                "cannot update (parent) dependency $groupId:$artifactId because " +
                    "it is either not managed by the given pom or " +
                    "not in there at all.\npom: ${pom.absolutePath}"
            )
        }
    }

    private fun createGroupIdArtifactIdRegex(groupId: String, artifactId: String): Regex {
        val groupIdPattern = "<groupId>$groupId</groupId>"
        val artifactIdPattern = "<artifactId>$artifactId</artifactId>"
        return Regex("(?:$groupIdPattern[\\S\\s]*?$artifactIdPattern)|(?:$artifactIdPattern[\\S\\s]*?$groupIdPattern)")
    }

    private fun updateParentRelation(parentParamObject: ParamObject, groupIdArtifactIdRegex: Regex, pom: File) {
        val matchResult = parentParamObject.matchResult
        if (matchResult != null && groupIdArtifactIdRegex.containsMatchIn(matchResult.value)) {
            parentParamObject.appendBeforeMatchAndUpdateStartIndex()
            appendDependency(parentParamObject)
            check(matchResult.next() == null) {
                "pom has two <parent> -- file: ${pom.absolutePath}"
            }
        }
        parentParamObject.appendAfterMatchIfUpdated()
    }

    private fun updateProperties(propertiesParamObject: ParamObject, pom: File) {
        val matchResult = propertiesParamObject.matchResult
        if (matchResult != null) {
            propertiesParamObject.appendBeforeMatchAndUpdateStartIndex()
            propertiesParamObject.modifiedPom.append("<").append(PROPERTIES).append(">")
            propertiesParamObject.startIndex += PROPERTIES.length + 2
            var tagMatchResult = tagRegex.find(matchResult.groupValues[1])
            while (tagMatchResult != null) {
                val (start, value, end) = tagMatchResult.destructured
                check(start == end) {
                    "Property seems to be malformed, start and end tag were different.\nStart: $start\nEnd: $end\nValue: $value"
                }
                propertiesParamObject.appendBeforeSubMatch(tagMatchResult)
                propertiesParamObject.modifiedPom.append("<").append(start).append(">")
                if (propertiesParamObject.properties.contains(start)) {
                    appendVersion(propertiesParamObject, value)
                } else {
                    propertiesParamObject.modifiedPom.append(value)
                }
                propertiesParamObject.modifiedPom.append("</").append(start).append(">")
                propertiesParamObject.startIndex += tagMatchResult.range.endInclusive + 1

                tagMatchResult = tagMatchResult.next()

            }
            check(matchResult.next() == null) {
                "pom has two <$PROPERTIES> -- file: ${pom.absolutePath}"
            }
        }
        //appends everything after the last property including </properties>
        propertiesParamObject.appendAfterMatchIfUpdated()
    }

    private fun updateDependencies(dependenciesParamObject: ParamObject, groupIdArtifactIdRegex: Regex) {
        while (dependenciesParamObject.matchResult != null) {
            val matchResult = dependenciesParamObject.matchResult!!
            if (groupIdArtifactIdRegex.containsMatchIn(matchResult.value)) {
                dependenciesParamObject.appendBeforeMatchAndUpdateStartIndex()
                appendDependency(dependenciesParamObject)
            }
            dependenciesParamObject.matchResult = matchResult.next()
        }
        dependenciesParamObject.appendAfterMatchIfUpdated()
    }

    private fun appendDependency(paramObject: ParamObject) {
        val matchResult = paramObject.matchResult!!

        val versionMatchResult = versionRegex.find(matchResult.value)
        if (versionMatchResult != null) {

            paramObject.appendBeforeSubMatch(versionMatchResult)

            paramObject.modifiedPom.append("<$VERSION>")
            appendVersion(paramObject, versionMatchResult.groupValues[1])
            paramObject.modifiedPom.append("</$VERSION>")

            paramObject.appendAfterSubMatchAndSetStartIndex(versionMatchResult)

            check(versionMatchResult.next() == null) {
                "<dependency> has two <$VERSION>: ${paramObject.groupId}:${paramObject.artifactId}"
            }
        }
    }

    private fun appendVersion(paramObject: ParamObject, version: String) {
        val propertyMatchResult = mavenPropertyRegex.find(version)
        when {
            propertyMatchResult != null -> {
                paramObject.properties.add(propertyMatchResult.groupValues[1])
                paramObject.modifiedPom.append(version)
            }
            version.contains("$") -> throw UnsupportedOperationException("Version was neither static nor a reference to a single property. Given: $version")
            else -> {
                paramObject.modifiedPom.append(paramObject.newVersion)
                paramObject.updated = true
            }
        }
    }

    class ParamObject(
        val groupId: String,
        val artifactId: String,
        val newVersion: String,
        val properties: HashSet<String>,
        private val content: String,
        regex: Regex
    ) {

        /**
         * Copy constructor where [getNewContent] is used as new [content] and [newRegex] as [newRegex]
         */
        constructor(paramObject: ParamObject, newRegex: Regex) : this(
            paramObject.groupId,
            paramObject.artifactId,
            paramObject.newVersion,
            paramObject.properties,
            paramObject.getNewContent(),
            newRegex
        )

        val modifiedPom = StringBuilder()
        var matchResult = regex.find(content, 0)
        var startIndex: Int = 0
        var updated = false

        fun appendBeforeMatchAndUpdateStartIndex(){
            val nonNullMatchResult = matchResult!!
            appendSubstring(startIndex, nonNullMatchResult.range.start)
            startIndex = nonNullMatchResult.range.start
        }

        fun appendAfterMatchIfUpdated() {
            if (updated) {
                appendSubstring(startIndex)
            }
        }

        fun appendBeforeSubMatch(subMatchResult: MatchResult) {
            appendSubstring(startIndex, startIndex + subMatchResult.range.start)
        }

        fun appendAfterSubMatchAndSetStartIndex(subMatchResult: MatchResult) {
            val nonNullMatchResult = matchResult!!
            appendSubstring(
                startIndex + subMatchResult.range.endInclusive + 1, nonNullMatchResult.range.endInclusive
            )
            startIndex = nonNullMatchResult.range.endInclusive
        }

        private fun appendSubstring(startIndex: Int) {
            modifiedPom.append(content.substring(startIndex))
        }

        private fun appendSubstring(startIndex: Int, endIndex: Int) {
            modifiedPom.append(content.substring(startIndex, endIndex))
        }

        fun getNewContent() = if (updated) modifiedPom.toString() else content
    }

    companion object {
        private val dependencyRegex = Regex("<dependency>[\\S\\s]+?</dependency>")
        private val parentRegex = Regex("<parent>[\\S\\s]+?</parent>")
        private const val PROPERTIES = "properties"
        private val propertiesRegex = Regex("<$PROPERTIES>([\\S\\s]+?)</$PROPERTIES>")
        private const val VERSION = "version"
        private val versionRegex = Regex("<$VERSION>([^<]+)</$VERSION>")
        private val mavenPropertyRegex = Regex("\\$\\{([^}]+)}")
        private val tagRegex = Regex("<([^<]+)>([^<]+)</([^<]+)>")
    }
}
