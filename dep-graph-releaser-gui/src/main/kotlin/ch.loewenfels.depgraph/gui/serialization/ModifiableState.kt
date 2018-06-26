package ch.loewenfels.depgraph.gui.serialization

import ch.loewenfels.depgraph.data.ReleasePlan
import ch.loewenfels.depgraph.gui.jobexecution.DryRunJobExecutionDataFactory
import ch.loewenfels.depgraph.gui.jobexecution.JobExecutionDataFactory
import ch.loewenfels.depgraph.gui.jobexecution.ReleaseJobExecutionDataFactory

class ModifiableState(defaultJenkinsBaseUrl: String?, json: String) {
    private val defaultJenkinsBaseUrl: String

    private var _json: String
    var json: String
        get() = _json
        private set(value) {
            _json = value
        }

    private lateinit var _releaseJobExecutionDataFactory: JobExecutionDataFactory
    var releaseJobExecutionDataFactory: JobExecutionDataFactory
        get() = _releaseJobExecutionDataFactory
        private set(value) {
            _releaseJobExecutionDataFactory = value
        }

    private lateinit var _dryRunExecutionDataFactory: JobExecutionDataFactory
    var dryRunExecutionDataFactory: JobExecutionDataFactory
        get() = _dryRunExecutionDataFactory
        private set(value) {
            _dryRunExecutionDataFactory = value
        }

    private lateinit var _releasePlan: ReleasePlan
    var releasePlan: ReleasePlan
        get() = _releasePlan
        private set(value) {
            _releasePlan = value
        }

    init{
        val fakeJenkinsBaseUrl = "https://github.com/loewenfels/"
        this.defaultJenkinsBaseUrl = defaultJenkinsBaseUrl ?: fakeJenkinsBaseUrl
        _json = json
        initJsonDependentFields()
    }

    private fun initJsonDependentFields() {
        _releasePlan = deserialize(_json)
        _releaseJobExecutionDataFactory = ReleaseJobExecutionDataFactory(defaultJenkinsBaseUrl, releasePlan)
        _dryRunExecutionDataFactory = DryRunJobExecutionDataFactory(defaultJenkinsBaseUrl, releasePlan)
    }

    /**
     * Applies the changes made in the GUI to [json] and indicates whether changes where made or not.
     *
     * @return `true` in case changes were made; `false` otherwise.
     */
    fun applyChanges(): Boolean {
        val (changed, newJson) = ChangeApplier.createReleasePlanJsonWithChanges(releasePlan, json)
        this.json = newJson
        initJsonDependentFields()
        return changed
    }

    /**
     * Applies the changes made in the GUI to a **copy** of [json] and returns it.
     */
    fun getJsonWithAppliedChanges(): String {
        val (_, newJson) = ChangeApplier.createReleasePlanJsonWithChanges(releasePlan, json)
        return newJson
    }
}
