package ch.loewenfels.depgraph

enum class ConfigKey(private val key: kotlin.String) {
    COMMIT_PREFIX("commitPrefix"),
    UPDATE_DEPENDENCY_JOB("updateDependencyJob"),
    REMOTE_REGEX("remoteRegex"),
    REMOTE_JOB("remoteJob"),
    REGEX_PARAMS("regexParams"),
    JOB_MAPPING("jobMapping"),
    ;

    override fun toString(): String = key
    fun asString(): String = key


    companion object {
        fun fromString(key: String): ConfigKey {
            return values().first { it.asString() == key  }
        }

        fun all() = listOf(COMMIT_PREFIX, UPDATE_DEPENDENCY_JOB, REMOTE_REGEX, REMOTE_JOB, REGEX_PARAMS, JOB_MAPPING)
    }
}
