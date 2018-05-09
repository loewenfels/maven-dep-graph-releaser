package ch.loewenfels.depgraph.gui

import ch.loewenfels.depgraph.ConfigKey
import ch.loewenfels.depgraph.data.ReleasePlan

abstract class BaseJobExecutionDataFactory(
    private val jenkinsUrl: String,
    protected val releasePlan: ReleasePlan
) : JobExecutionDataFactory {

    protected fun requireConfigEntry(config: Map<ConfigKey, String>, key: ConfigKey) {
        require(config.containsKey(key)) {
            "$key is not defined in settings"
        }
    }

    protected fun getConfig(key: ConfigKey) = releasePlan.getConfig(key)

    protected fun getJobUrl(key: ConfigKey) = getJobUrl(getConfig(key))
    protected fun getJobUrl(jobName: String) = "$jenkinsUrl/job/$jobName"
}
