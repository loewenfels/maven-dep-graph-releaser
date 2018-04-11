package ch.loewenfels.depgraph.gui

import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.ReleaseCommand
import ch.loewenfels.depgraph.data.ReleasePlan
import org.w3c.dom.HTMLInputElement
import kotlin.browser.document

class Toggler(private val releasePlan: ReleasePlan, private val menu: Menu) {

    @JsName("toggle")
    fun toggle(id: String) {
        val checkbox = getCheckbox(id)
        when {
            id.endsWith(DISABLE_ALL_SUFFIX) -> toggleProject(checkbox)
            else -> toggleCommand(checkbox)
        }
    }

    private fun toggleProject(disableAllCheckbox: HTMLInputElement) {
        val checked = disableAllCheckbox.checked
        val identifier = disableAllCheckbox.id.substring(0, disableAllCheckbox.id.indexOf(DISABLE_ALL_SUFFIX))
        val project = getProject(identifier)

        project.commands.forEachIndexed { index, _ ->
            val checkbox = getCheckbox(identifier, index)
            //do nothing if command is disabled
            if (checkbox.disabled) return@forEachIndexed

            menu.activateSaveButton()
            checkbox.checked = checked
            if (checkbox.isReleaseCommand() && !checked) {
                toggleCommand(checkbox)
            }
        }
        releasePlan.getSubmodules(project.id)
            .asSequence()
            .map { submoduleId -> getCheckboxOrNull(getDisableAllId(submoduleId)) }
            .filterNotNull()
            .forEach { checkbox ->
                checkbox.checked = checked
                toggleProject(checkbox)
            }
    }

    private fun getDisableAllId(projectId: ProjectId) = "${projectId.identifier}$DISABLE_ALL_SUFFIX"
    private fun getCheckbox(identifier: String, index: Int) = getCheckbox("$identifier:$index:disable")

    private fun toggleCommand(checkbox: HTMLInputElement) {
        val project = getProjectOfCommand(checkbox)
        if (!checkbox.checked) {
            menu.activateSaveButton()
            deactivateReleaseCommands(project, checkbox.id)
            deactivateDependents(project.id)
        } else if (checkbox.isReleaseCommand()) {
            if (notAllCommandsActive(project, { it.id != checkbox.id }) || notAllSubmodulesActive(project)) {
                // cannot reactivate release command if not all commands are active
                // setting checked again to false
                checkbox.checked = false
                showInfo("Cannot reactivate the ReleaseCommand for project ${project.id.identifier} because some commands (of submodules) are deactivated.")
            }
            menu.activateSaveButton()
        }
    }

    private fun getProjectOfCommand(checkbox: HTMLInputElement): Project {
        val regex = Regex("(.*):[0-9]+:disable")
        val result = regex.find(checkbox.id)
        require(result != null) {
            "the given id ($checkbox.id) did not match the pattern $regex"
        }
        val identifier = result!!.groups[1]!!.value
        return getProject(identifier)
    }

    private fun getProject(identifier: String) = elementById(identifier).asDynamic().project as Project

    private fun deactivateReleaseCommands(project: Project, id: String) {
        project.commands.asSequence()
            .mapIndexed { index, it -> index to it }
            .filter { it.second is ReleaseCommand }
            .map { (index, _) -> getCheckbox(project.id.identifier, index) }
            .filter { checkbox -> checkbox.id != id }
            .forEach { checkbox -> checkbox.checked = false }
    }

    private fun deactivateDependents(projectId: ProjectId) {
        releasePlan.getDependents(projectId).forEach(this::disableProject)
        releasePlan.getSubmodules(projectId).forEach { submoduleId ->
            val id = getDisableAllId(submoduleId)
            if (document.getElementById(id) != null) {
                disableProject(submoduleId)
            } else {
                deactivateDependents(submoduleId)
            }
        }
    }

    private fun disableProject(projectId: ProjectId) {
        val id = getDisableAllId(projectId)
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

    private fun notAllCommandsActive(project: Project, predicate: (HTMLInputElement) -> Boolean): Boolean {
        return project.commands.asSequence()
            .mapIndexed { index, _ -> getCheckbox(project.id.identifier, index) }
            .filter(predicate)
            .any { checkbox -> !checkbox.checked }
    }

    private fun notAllSubmodulesActive(project: Project): Boolean {
        return releasePlan.getSubmodules(project.id).any { submoduleId ->
            val submodulesHasCommands = document.getElementById(getDisableAllId(project.id)) != null
            submodulesHasCommands &&
                //cannot be the same command, hence we do not filter commands at all => use `{ true }`
                notAllCommandsActive(releasePlan.getProject(submoduleId), { true })
        }
    }

    companion object {
        private const val DISABLE_ALL_SUFFIX = ":disableAll"
    }
}
