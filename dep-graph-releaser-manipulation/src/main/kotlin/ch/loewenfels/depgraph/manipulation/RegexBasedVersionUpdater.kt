package ch.loewenfels.depgraph.manipulation

import java.io.File

object RegexBasedVersionUpdater {

    private const val DEPENDENCY = "dependency"
    private val dependencyRegex = Regex("<$DEPENDENCY>[\\S\\s]+?</$DEPENDENCY>")
    private const val PARENT = "parent"
    private val parentRegex = Regex("<$PARENT>[\\S\\s]+?</$PARENT>")
    private const val PROPERTIES = "properties"
    private val propertiesRegex = Regex("<$PROPERTIES>[\\S\\s]+?</$PROPERTIES>")
    private const val VERSION = "version"
    private val versionRegex = Regex("<$VERSION>([^<]+)</$VERSION>")
    private val mavenPropertyRegex = Regex("\\$\\{([^}]+)}")
    private val tagRegex = Regex("<([a-zA-Z0-9_.-]+)>([^<]+)</([a-zA-Z0-9_.-]+)>")

    fun updateDependency(pom: File, groupId: String, artifactId: String, newVersion: String) {
        require(pom.exists()) {
            "pom file does not exist, cannot update dependency." +
                "\npom: ${pom.absolutePath}" +
                "\ndependency: $groupId:$artifactId:$newVersion"
        }

        val groupIdArtifactIdRegex =
            createGroupIdArtifactIdRegex(
                groupId,
                artifactId
            )
        val content = pom.readText()

        val dependenciesParamObject = ParamObject(
            groupId,
            artifactId,
            newVersion,
            hashSetOf(),
            content,
            dependencyRegex
        )
        updateDependencies(
            dependenciesParamObject,
            groupIdArtifactIdRegex
        )

        val parentParamObject = ParamObject(
            dependenciesParamObject,
            parentRegex
        )
        updateParentRelation(
            parentParamObject,
            groupIdArtifactIdRegex,
            pom
        )

        val propertiesParamObject = ParamObject(
            parentParamObject,
            propertiesRegex
        )
        updateProperties(propertiesParamObject)

        checkAndUpdatePomIfOk(
            dependenciesParamObject,
            parentParamObject,
            propertiesParamObject,
            pom
        )
    }

    private fun createGroupIdArtifactIdRegex(groupId: String, artifactId: String): Regex {
        val groupIdPattern = "<groupId>$groupId</groupId>"
        val artifactIdPattern = "<artifactId>$artifactId</artifactId>"
        return Regex("(?:$groupIdPattern[\\S\\s]*?$artifactIdPattern)|(?:$artifactIdPattern[\\S\\s]*?$groupIdPattern)")
    }

    private fun updateParentRelation(parentParamObject: ParamObject, groupIdArtifactIdRegex: Regex, pom: File) {
        val matchResult = parentParamObject.nullableMatchResult
        if (matchResult != null && groupIdArtifactIdRegex.containsMatchIn(matchResult.value)) {
            parentParamObject.appendBeforeMatchAndUpdateStartIndex()
            appendDependency(parentParamObject)
            check(matchResult.next() == null) {
                "pom has two <$PARENT> -- file: ${pom.absolutePath}"
            }
        }
        parentParamObject.appendRestIfUpdated()
    }

    private fun updateProperties(propertiesParamObject: ParamObject) {
        while (propertiesParamObject.nullableMatchResult != null) {
            propertiesParamObject.appendBeforeMatchAndUpdateStartIndex()
            appendProperties(propertiesParamObject)
            propertiesParamObject.nullableMatchResult = propertiesParamObject.nonNullMatchResult.next()
        }
        propertiesParamObject.appendRestIfUpdated()
    }

    private fun appendProperties(propertiesParamObject: ParamObject) {
        val initialStartIndex = propertiesParamObject.startIndex

        var tagMatchResult = tagRegex.find(propertiesParamObject.nonNullMatchResult.value)
        if (tagMatchResult != null) {
            propertiesParamObject.appendBeforeSubMatch(tagMatchResult)
            propertiesParamObject.startIndex += tagMatchResult.range.start
        }

        while (tagMatchResult != null) {
            propertiesParamObject.appendSubstring(
                propertiesParamObject.startIndex,
                initialStartIndex + tagMatchResult.range.start
            )
            val (propertyName, version) = getTagNameAndVersion(
                tagMatchResult
            )
            propertiesParamObject.modifiedPom.append("<").append(propertyName).append(">")
            appendVersionInProperty(
                propertiesParamObject,
                propertyName,
                version
            )
            propertiesParamObject.modifiedPom.append("</").append(propertyName).append(">")

            val nextMatchResult = tagMatchResult.next()
            if (nextMatchResult != null) {
                propertiesParamObject.startIndex = initialStartIndex + tagMatchResult.range.endInclusive + 1
            } else {
                propertiesParamObject.startIndex = initialStartIndex //simulates that we matched all properties together
                propertiesParamObject.appendAfterSubMatchAndSetStartIndex(tagMatchResult)
            }
            tagMatchResult = nextMatchResult
        }
    }

    private fun getTagNameAndVersion(tagMatchResult: MatchResult): Pair<String, String> {
        val (start, version, end) = tagMatchResult.destructured
        check(start == end) {
            "Property seems to be malformed, start and end tag were different.\nStart: $start\nEnd: $end\nValue: $version"
        }
        return start to version
    }

    private fun updateDependencies(dependenciesParamObject: ParamObject, groupIdArtifactIdRegex: Regex) {
        while (dependenciesParamObject.nullableMatchResult != null) {
            if (groupIdArtifactIdRegex.containsMatchIn(dependenciesParamObject.nonNullMatchResult.value)) {
                dependenciesParamObject.appendBeforeMatchAndUpdateStartIndex()
                appendDependency(dependenciesParamObject)
            }
            dependenciesParamObject.nullableMatchResult = dependenciesParamObject.nonNullMatchResult.next()
        }
        dependenciesParamObject.appendRestIfUpdated()
    }

    private fun appendDependency(paramObject: ParamObject) {
        val versionMatchResult = versionRegex.find(paramObject.nonNullMatchResult.value)
        if (versionMatchResult != null) {

            paramObject.appendBeforeSubMatch(versionMatchResult)

            paramObject.modifiedPom.append("<$VERSION>")
            appendVersion(
                paramObject,
                versionMatchResult.groupValues[1]
            )
            paramObject.modifiedPom.append("</$VERSION>")

            paramObject.appendAfterSubMatchAndSetStartIndex(versionMatchResult)

            check(versionMatchResult.next() == null) {
                "<$DEPENDENCY> has two <$VERSION>: ${paramObject.groupId}:${paramObject.artifactId}"
            }
        }
    }

    private fun appendVersion(paramObject: ParamObject, version: String) {
        val resolvedVersion =
            resolveVersion(paramObject, version)
        val propertyMatchResult = mavenPropertyRegex.matchEntire(resolvedVersion)
        when {
            propertyMatchResult != null -> {
                paramObject.properties.add(propertyMatchResult.groupValues[1])
                paramObject.modifiedPom.append(resolvedVersion)
            }
            resolvedVersion.contains("$") -> throw UnsupportedOperationException(
                "Version was neither static nor a reference to a single property. Given: $version"
            )
            paramObject.newVersion == version -> throw IllegalArgumentException(
                "Version is already up-to-date; did you pass wrong argument for newVersion? Given: $version"
            )
            else -> appendNewVersionAndSetUpdated(
                paramObject
            )
        }
    }

    private fun resolveVersion(paramObject: ParamObject, version: String): String {
        return if ("\${project.version}" == version) {
            paramObject.newVersion
        } else {
            version
        }
    }

    private fun appendVersionInProperty(propertiesParamObject: ParamObject, propertyName: String, version: String) {
        when {
            resolveVersion(propertiesParamObject, version).contains("$") ->
                throw UnsupportedOperationException(
                    "Property contains another property.\nProperty: $propertyName\nValue: $version"
                )

            propertiesParamObject.properties.contains(propertyName) ->
                appendNewVersionAndSetUpdated(
                    propertiesParamObject
                )

            else -> propertiesParamObject.modifiedPom.append(version)
        }
    }

    private fun appendNewVersionAndSetUpdated(paramObject: ParamObject) {
        paramObject.modifiedPom.append(paramObject.newVersion)
        paramObject.updated = true
    }

    private fun checkAndUpdatePomIfOk(
        dependenciesParamObject: ParamObject,
        parentParamObject: ParamObject,
        propertiesParamObject: ParamObject,
        pom: File
    ) {
        val wasModified = dependenciesParamObject.updated || parentParamObject.updated || propertiesParamObject.updated
        when {
            wasModified ->
                pom.writeText(propertiesParamObject.getModifiedContent())

            propertiesParamObject.properties.isNotEmpty() ->
                throw IllegalStateException(
                    "Cannot update (parent) dependency: The dependency's version is managed via one or more properties" +
                        " but they are not present in the pom." +
                        "\ndependency: ${propertiesParamObject.groupId}:${propertiesParamObject.artifactId}" +
                        "\npom: ${pom.absolutePath}" +
                        "\nproperties:${propertiesParamObject.properties.joinToString()}"
                )

            else ->
                throw IllegalStateException(
                    "Cannot update (parent) dependency: the dependency was not found." +
                        "\ndependency: ${propertiesParamObject.groupId}:${propertiesParamObject.artifactId}" +
                        "\npom: ${pom.absolutePath}"
                )
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
         * Copy constructor where [getModifiedContent] is used as new [content] and [newRegex] as [newRegex]
         */
        constructor(paramObject: ParamObject, newRegex: Regex) : this(
            paramObject.groupId,
            paramObject.artifactId,
            paramObject.newVersion,
            paramObject.properties,
            paramObject.getModifiedContent(),
            newRegex
        )

        val modifiedPom = StringBuilder()
        var nullableMatchResult = regex.find(content, 0)
        var startIndex: Int = 0
        var updated = false
        val nonNullMatchResult get() = nullableMatchResult!!

        fun appendBeforeMatchAndUpdateStartIndex() {
            appendSubstring(startIndex, nonNullMatchResult.range.start)
            startIndex = nonNullMatchResult.range.start
        }

        fun appendRestIfUpdated() {
            if (updated) {
                appendSubstring(startIndex)
            }
        }

        fun appendBeforeSubMatch(subMatchResult: MatchResult) {
            appendSubstring(startIndex, startIndex + subMatchResult.range.start)
        }

        fun appendAfterSubMatchAndSetStartIndex(subMatchResult: MatchResult) {
            appendSubstring(
                startIndex + subMatchResult.range.endInclusive + 1, nonNullMatchResult.range.endInclusive + 1
            )
            startIndex = nonNullMatchResult.range.endInclusive + 1
        }

        private fun appendSubstring(startIndex: Int) {
            modifiedPom.append(content.substring(startIndex))
        }

        fun appendSubstring(startIndex: Int, endIndex: Int) {
            modifiedPom.append(content.substring(startIndex, endIndex))
        }

        fun getModifiedContent() = if (updated) modifiedPom.toString() else content
    }
}
