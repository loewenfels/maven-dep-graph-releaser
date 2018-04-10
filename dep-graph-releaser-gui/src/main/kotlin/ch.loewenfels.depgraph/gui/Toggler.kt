package ch.loewenfels.depgraph.gui

import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.ReleasePlan
import org.w3c.dom.HTMLInputElement
import kotlin.browser.document

class Toggler(private val releasePlan: ReleasePlan, private val menu: Menu) {

    @JsName("toggle")
    fun toggle(id: String) {
        val checkbox = getCheckbox(id)
        when {
            id.endsWith(":disableAll") -> toggleProject(checkbox, id)
            else -> toggleCommand(checkbox, id)
        }
    }

    private fun toggleProject(disableAllCheckbox: HTMLInputElement, id: String) {
        val checked = disableAllCheckbox.checked
        val prefix = id.substring(0, id.indexOf(":disableAll"))
        iterate(prefix) { checkbox, i ->
            //do nothing if command is disabled
            if (checkbox.disabled) return@iterate

            menu.activateSaveButton()
            checkbox.checked = checked
            if (checkbox.isReleaseCommand() && !checked) {
                toggleCommand(checkbox, "$prefix:$i:disable")
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
            menu.activateSaveButton()
            deactivateReleaseCommands(prefix, id)
            deactivateDependents(prefix)
        } else if (checkbox.isReleaseCommand()) {
            //can only activate release if all checkboxes are activated
            if (notAllChecked(prefix, id)) {
                checkbox.checked = false
                menu.activateSaveButton()
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
        val project = elementById(prefix).asDynamic().project as Project
        deactivateDependents(project.id)
    }

    private fun deactivateDependents(projectId: ProjectId){
        releasePlan.getDependents(projectId).forEach(this::disableProject)
        releasePlan.getSubmodules(projectId).forEach(this::deactivateDependents)
    }

    private fun disableProject(it: ProjectId) {
        val id = "${it.identifier}:disableAll"
        val checkbox = document.getElementById(id)
        //could be a sub module without commands
        if (checkbox is HTMLInputElement) {
            checkbox.checked = false
            toggle(id)
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
