package ch.loewenfels.depgraph

import ch.loewenfels.depgraph.data.ReleasePlan


fun parseRemoteRegex(releasePlan: ReleasePlan) =
    parseRemoteRegex(releasePlan.getConfig(ConfigKey.REMOTE_REGEX))

fun parseRemoteRegex(regex: String): List<Pair<Regex, String>> {
    return parseRegex(regex, ';', "remoteRegex", ::checkUrlDefined) { it }
}

fun parseRegexParameters(releasePlan: ReleasePlan) =
    parseRegexParameters(releasePlan.getConfig(ConfigKey.REGEX_PARAMS))

fun parseRegexParameters(regex: String): List<Pair<Regex, List<String>>> {
    return parseRegex(regex, '$', "regexParameters", ::checkAtLeastOneParameter) { params ->
        params.split(';')
    }
}

private fun <T> parseRegex(
    configValue: String,
    splitChar: Char,
    name: String,
    checkRightSide: (String, String) -> Unit,
    rightSideConverter: (String) -> T
): List<Pair<Regex, T>> {
    return if (configValue.isNotEmpty()) {
        configValue.splitToSequence(splitChar)
            .map { pair ->
                val index = checkRegexNotEmpty(pair, name, configValue)
                val rightSide = pair.substring(index + 1)
                checkRightSide(rightSide, configValue)
                Regex(pair.substring(0, index).replace("\n", "")) to rightSideConverter(rightSide)
            }
            .toList()
    } else {
        emptyList()
    }
}

private fun checkRegexNotEmpty(pair: String, name: String, input: String): Int {
    val index = pair.indexOf('#')
    check(index > 0) {
        "regex requires at least one character.\n$name: $input"
    }
    return index
}

private fun checkUrlDefined(jenkinsBaseUrl: String, remoteRegex: String) {
    check(jenkinsBaseUrl.isNotBlank()) {
        "A remoteRegex requires a related jenkins base url.\remoteRegex: $remoteRegex"
    }
}

private fun checkAtLeastOneParameter(pair: String, regexParameters: String) {
    val index = pair.indexOf('=')
    check(index > 0) {
        "A regexParam requires at least one parameter.\nregexParameters: $regexParameters"
    }
}
