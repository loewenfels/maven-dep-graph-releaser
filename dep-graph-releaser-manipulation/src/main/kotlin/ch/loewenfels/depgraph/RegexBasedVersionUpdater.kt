package ch.loewenfels.depgraph

import java.io.File

class RegexBasedVersionUpdater {

    fun updateDependency(pom: File, groupId: String, artifactId: String, newVersion: String) {
        val groupIdPattern = "<groupId>$groupId</groupId>"
        val artifactIdPattern = "<artifactId>$artifactId</artifactId>"
        val groupIdArtifactIdRegex =
            Regex("(?:$groupIdPattern[\\S\\s]*?$artifactIdPattern)|(?:$artifactIdPattern[\\S\\s]*?$groupIdPattern)")
        val content = pom.readText()

        val dependenciesParamObject = ParamObject(groupId, artifactId, newVersion, content, dependencyRegex)
        updateDependencies(dependenciesParamObject, groupIdArtifactIdRegex)

        val parentParamObject = ParamObject(dependenciesParamObject, parentRegex)
        updateParentRelation(parentParamObject, groupIdArtifactIdRegex, pom)

        //TODO deal with properties

        if (dependenciesParamObject.updated || parentParamObject.updated) {
            pom.writeText(parentParamObject.getNewContent())
        } else {
            throw IllegalStateException(
                "cannot update (parent) dependency $groupId:$artifactId because " +
                    "it is either not managed by the given pom or " +
                    "not in there at all.\npom: ${pom.absolutePath}"
            )
        }
    }

    private fun updateParentRelation(
        parentParamObject: ParamObject,
        groupIdArtifactIdRegex: Regex,
        pom: File
    ) {
        val matchResult = parentParamObject.matchResult
        if (matchResult != null && groupIdArtifactIdRegex.containsMatchIn(matchResult.value)) {
            parentParamObject.appendBeforeMatchAndUpdateStartIndex()
            appendDependency(parentParamObject)
            check(matchResult.next() == null) {
                "pom has two <parent> -- file: ${pom.absolutePath}"
            }
        }
        if (parentParamObject.updated) {
            parentParamObject.appendSubstring(parentParamObject.startIndex)
        }
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
        if (dependenciesParamObject.updated) {
            dependenciesParamObject.appendSubstring(dependenciesParamObject.startIndex)
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
        regex: Regex
    ) {
        /**
         * Copy constructor where [getNewContent] is used as new [content] and [newRegex] as [newRegex]
         */
        constructor(paramObject: ParamObject, newRegex: Regex) : this(
            paramObject.groupId,
            paramObject.artifactId,
            paramObject.newVersion,
            paramObject.getNewContent(),
            newRegex
        )

        val modifiedPom = StringBuilder()
        var matchResult = regex.find(content, 0)
        var startIndex: Int = 0
        val properties = hashSetOf<String>()
        var updated = false

        fun appendBeforeMatchAndUpdateStartIndex(){
            val nonNullMatchResult = matchResult!!
            appendSubstring(startIndex, nonNullMatchResult.range.start)
            startIndex = nonNullMatchResult.range.start
        }

        fun appendSubstring(startIndex: Int) {
            modifiedPom.append(content.substring(startIndex))
        }

        fun appendSubstring(startIndex: Int, endIndex: Int) {
            modifiedPom.append(content.substring(startIndex, endIndex))
        }

        fun getNewContent() = if (updated) modifiedPom.toString() else content
    }

    companion object {
        private val dependencyRegex = Regex("<dependency>[\\S\\s]+?</dependency>")
        private val parentRegex = Regex("<parent>[\\S\\s]+?</parent>")
        private val versionRegex = Regex("<version>([^<]+)</version>")
        private val propertyRegex = Regex("\\$\\{[^}]+}")
    }
}
