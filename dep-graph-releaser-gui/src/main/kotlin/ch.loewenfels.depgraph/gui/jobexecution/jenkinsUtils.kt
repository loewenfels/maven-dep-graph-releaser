package ch.loewenfels.depgraph.gui.jobexecution

import ch.loewenfels.depgraph.regex.noneOrSomeChars

fun createParameterRegexPattern(parameters: Map<String, String>): String
    = parameters.entries.joinToString(noneOrSomeChars) { (k, v) ->
        "<parameter>$noneOrSomeChars" +
            "<name>$k</name>$noneOrSomeChars" +
            "<value>$v</value>$noneOrSomeChars" +
            "</parameter>$noneOrSomeChars"
    }

fun toQueryParameters(parameters: Map<String, String>): String
    = parameters.entries.joinToString("&") { (k, v) -> "$k=$v" }

const val endOfConsoleUrlSufix = "console/#footer"
