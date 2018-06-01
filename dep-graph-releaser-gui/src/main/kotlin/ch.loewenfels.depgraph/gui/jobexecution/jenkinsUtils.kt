package ch.loewenfels.depgraph.gui.jobexecution

fun createParameterRegexPattern(parameters: Map<String, String>): String
    = parameters.entries.joinToString("[\\S\\s]*?") { (k, v) ->
        "<parameter>[\\S\\s]*?" +
            "<name>$k</name>[\\S\\s]*?" +
            "<value>$v</value>[\\S\\s]*?" +
            "</parameter>[\\S\\s]*?"
    }

fun toQueryParameters(parameters: Map<String, String>): String
    = parameters.entries.joinToString("&") { (k, v) -> "$k=$v" }
