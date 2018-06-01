package ch.loewenfels.depgraph.gui

import ch.loewenfels.depgraph.ConfigKey
import ch.loewenfels.depgraph.data.ReleasePlan


fun parseRemoteRegex(releasePlan: ReleasePlan) = parseRemoteRegex(releasePlan.getConfig(ConfigKey.REMOTE_REGEX))
fun parseRemoteRegex(regex: String): List<Pair<Regex, String>> {
    return parseRegex(regex, ';', "remoteRegex", ::checkUrlDefined)
}

fun parseRegexParameters(releasePlan: ReleasePlan) = parseRegexParameters(releasePlan.getConfig(ConfigKey.REGEX_PARAMS))
fun parseRegexParameters(regex: String): List<Pair<Regex, String>> {
    return parseRegex(regex, '$', "regexParameters", ::checkAtLeastOneParameter)
}

private fun parseRegex(
    configValue: String,
    splitChar: Char,
    name: String,
    checkRightSide: (String, String) -> Unit
): List<Pair<Regex, String>> {
    return if (configValue.isNotEmpty()) {
        configValue.splitToSequence(splitChar)
            .map { pair ->
                val index = checkRegexNotEmpty(pair, name, configValue)
                val rightSide = pair.substring(index + 1)
                checkRightSide(rightSide, configValue)
                Regex(pair.substring(0, index)) to rightSide
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
    check(jenkinsBaseUrl.isNotBlank()){
        "A remoteRegex requires a related jenkins base url.\remoteRegex: $remoteRegex"
    }
}

private fun checkAtLeastOneParameter(pair: String, regexParameters: String) {
    val index = pair.indexOf('=')
    check(index > 0) {
        "A regexParam requires at least one parameter.\nregexParameters: $regexParameters"
    }
}
