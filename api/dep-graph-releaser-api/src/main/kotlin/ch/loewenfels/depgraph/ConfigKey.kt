package ch.loewenfels.depgraph

enum class ConfigKey(private val key: kotlin.String) {
    UPDATE_DEPENDENCY_JOB("updateDependencyJob"),
    REMOTE_REGEX("remoteRegex"),
    REMOTE_JOB("remoteJob"),
    REGEX_PARAMS("regexParams")
    ;

    override fun toString(): String = key
    fun asString(): String = key


    companion object {
        fun fromString(key: String): ConfigKey {
            return values().first { it.asString() == key  }
        }

        fun all() = listOf(UPDATE_DEPENDENCY_JOB, REMOTE_REGEX, REMOTE_JOB, REGEX_PARAMS)
    }
}
