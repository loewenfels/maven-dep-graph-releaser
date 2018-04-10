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
        iterateCommands(prefix) { checkbox, i ->
            //do nothing if command is disabled
            if (checkbox.disabled) return@iterateCommands

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
            if (notAllCommandsActive(prefix, id) || notAllSubmodulesActive(prefix)) {
                // cannot reactivate release command if not all commands are active
                // setting checked again to false
                checkbox.checked = false
                showInfo("Cannot reactivate the ReleaseCommand for project $prefix because some commands (of submodules) are deactivated.")
            }
            menu.activateSaveButton()
        }
    }

    private fun deactivateReleaseCommands(prefix: String, id: String) {
        iterateCommands(prefix) { checkbox, _ ->
            if (checkbox.id != id && checkbox.isReleaseCommand()) {
                checkbox.checked = false
            }
        }
    }

    private fun deactivateDependents(prefix: String) {
        val project = getProject(prefix)
        deactivateDependents(project.id)
    }

    private fun getProject(prefix: String) = elementById(prefix).asDynamic().project as Project

    private fun deactivateDependents(projectId: ProjectId) {
        releasePlan.getDependents(projectId).forEach(this::disableProject)
        releasePlan.getSubmodules(projectId).forEach {
            val id = "${it.identifier}:disableAll"
            if (document.getElementById(id) != null) {
                disableProject(it)
            } else {
                deactivateDependents(it)
            }
        }
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

    private fun notAllCommandsActive(prefix: String, id: String): Boolean {
        var notAllChecked = false
        iterateCommands(prefix) { checkbox, _ ->
            if (checkbox.id != id && !checkbox.checked) {
                notAllChecked = true
            }
        }
        return notAllChecked
    }

    private fun notAllSubmodulesActive(prefix: String): Boolean {
        val project = getProject(prefix)
        releasePlan.getSubmodules(project.id).forEach {
            val id = "${it.identifier}:disableAll"
            if (document.getElementById(id) != null) {
                if (notAllCommandsActive(it.identifier, "notACommandOfTheSubmodule")) {
                    return true
                }
            }
        }
        return false
    }

    private fun iterateCommands(prefix: String, act: (HTMLInputElement, Int) -> Unit) {
        var i = 0
        do {
            val checkbox = getCheckboxOrNull("$prefix:$i:disable")
            val found = checkbox != null
            if (found) {
                act(checkbox!!, i)
                ++i
            }
        } while (found)
    }


}
