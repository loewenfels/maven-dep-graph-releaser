package ch.loewenfels.depgraph.gui.jobexecution

import ch.loewenfels.depgraph.ConfigKey
import ch.loewenfels.depgraph.data.ReleasePlan

abstract class BaseJobExecutionDataFactory(
    protected val jenkinsUrl: String,
    protected val releasePlan: ReleasePlan
) : JobExecutionDataFactory {

    protected fun requireConfigEntry(config: Map<ConfigKey, String>, key: ConfigKey) {
        require(config.containsKey(key)) {
            "$key is not defined in settings"
        }
    }

    protected fun getConfig(key: ConfigKey) = releasePlan.getConfig(key)

    protected fun getJobUrl(key: ConfigKey) = getJobUrl(jenkinsUrl, getConfig(key))
    protected fun getJobUrl(jenkinsBaseUrl: String, jobName: String) = "$jenkinsBaseUrl/job/$jobName"
}
