package ch.loewenfels.depgraph.gui.serialization

class ModifiableJson(json: String) {

    private var _json: String = json
    var json: String
        get() = _json
        private set(value) {
            _json = value
        }

    /**
     * Applies the changes made in the GUI to [json] and indicates whether changes where made or not.
     *
     * @return `true` in case changes were made; `false` otherwise.
     */
    fun applyChanges(): Boolean {
        val (changed, newJson) = ChangeApplier.createReleasePlanJsonWithChanges(
            json
        )
        this.json = newJson
        return changed
    }

    /**
     * Applies the changes made in the GUI to a copy of [json] and returns it.
     */
    fun getJsonWithAppliedChanges(): String {
        val (_, newJson) = ChangeApplier.createReleasePlanJsonWithChanges(json)
        return newJson
    }
}
