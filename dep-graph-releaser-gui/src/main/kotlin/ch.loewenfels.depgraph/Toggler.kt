package ch.loewenfels.depgraph

import ch.loewenfels.depgraph.data.ProjectId
import org.w3c.dom.HTMLInputElement
import kotlin.browser.document

class Toggler {

    @JsName("toggle")
    fun toggle(id: String) {
        val checkbox = getCheckbox(id)
        when {
            id.endsWith(":disableAll") -> toggleProject(checkbox, id)
            else -> toggleCommand(checkbox, id)
        }
    }

    private fun toggleProject(checkbox: HTMLInputElement, id: String) {
        val checked = checkbox.checked
        val prefix = id.substring(0, id.indexOf(":disableAll"))
        iterate(prefix) { it, i ->
            it.checked = checked
            if (it.isReleaseCommand() && !checked) {
                toggleCommand(it, "$prefix:$i:disable")
            }
        }
    }

    private fun toggleCommand(checkbox: HTMLInputElement, id: String) {
        val regex = Regex("(.*):[0-9]+:disable")
        val result = regex.find(id)
        require(result != null) {
            "the given id ($id) did not match the pattern $regex"
        }
        val prefix = result!!.groups[1]!!.value
        if (!checkbox.checked) {
            deactivateReleaseCommands(prefix, id)
            deactivateDependents(prefix)
        } else if (checkbox.isReleaseCommand()) {
            //can only activate release if all checkboxes are activated
            if (notAllChecked(prefix, id)) {
                checkbox.checked = false
            }
        }
    }

    private fun deactivateReleaseCommands(prefix: String, id: String) {
        iterate(prefix) { checkbox, _ ->
            if (checkbox.id != id && checkbox.isReleaseCommand()) {
                checkbox.checked = false
            }
        }
    }

    private fun deactivateDependents(prefix: String) {
        val dependents : Set<ProjectId> = elementById(prefix).asDynamic().dependents as Set<ProjectId>
        dependents.forEach {
            val disableAll = "${it.identifier}:disableAll"
            getCheckbox(disableAll).checked = false
            toggle(disableAll)
        }
    }


    private fun HTMLInputElement.isReleaseCommand(): Boolean {
        return classList.contains("release")
    }

    private fun notAllChecked(prefix: String, id: String): Boolean {
        var notAllChecked = false
        iterate(prefix) { checkbox, _ ->
            if (checkbox.id != id && !checkbox.checked) {
                notAllChecked = true
            }
        }
        return notAllChecked
    }

    private fun iterate(prefix: String, act: (HTMLInputElement, Int) -> Unit) {
        var found = true
        var i = 0
        while (found) {
            val checkbox = getCheckboxOrNull("$prefix:$i:disable")
            found = checkbox != null
            if (found) {
                act(checkbox!!, i)
                ++i
            }
        }
    }

    private fun getCheckbox(id: String): HTMLInputElement = getCheckboxOrNull(id)
        ?: throw IllegalStateException("no checkbox found for id $id")

    private fun getCheckboxOrNull(id: String): HTMLInputElement? {
        val element = document.getElementById(id) ?: return null
        require((element is HTMLInputElement && element.type == "checkbox")) {
            "$id was not a checkbox but $element"
        }
        return element as HTMLInputElement
    }
}
