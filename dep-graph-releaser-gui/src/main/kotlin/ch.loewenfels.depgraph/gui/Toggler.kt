package ch.loewenfels.depgraph.gui

import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.ReleaseCommand
import ch.loewenfels.depgraph.data.ReleasePlan
import ch.loewenfels.depgraph.gui.Gui.Companion.disableUnDisableForReleaseStartAndEnd
import org.w3c.dom.CustomEvent
import org.w3c.dom.CustomEventInit
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import kotlin.browser.document

class Toggler(private val releasePlan: ReleasePlan, private val menu: Menu) {

    fun registerToggleEvents() {
        releasePlan.getProjects().forEach { project ->
            val allToggle = getAllToggle(project) ?: return@forEach

            allToggle.addChangeEventListener { toggle(allToggle.id) }
            project.commands.forEachIndexed { index, command ->
                val toggle = getToggle(project, index)
                toggle.addChangeEventListener { toggleCommand(project, toggle) }
                disableUnDisableForReleaseStartAndEnd(toggle, elementById("${toggle.id}:slider"))
                if (command is ReleaseCommand) {
                    registerReleaseCommandEvents(toggle, project)
                }
            }
        }
    }

    private fun registerReleaseCommandEvents(toggle: HTMLInputElement, project: Project) {
        toggle.addClickEventListener { e ->
            if (toggle.checked && notAllCommandsOrSubmodulesActive(project, toggle)) {
                // cannot reactivate release command if not all commands are active
                // setting checked again to false
                //toggle.checked = false
                e.preventDefault()
                showInfo("Cannot reactivate the ReleaseCommand for project ${project.id.identifier} because some commands (of submodules) are deactivated.", 5000)
            }
        }
        registerForProjectEvent(project, EVENT_TOGGLE_UNCHECKED) {
            if (toggle.checked) {
                toggle.checked = false
            }
        }
    }

    private fun getAllToggle(project: Project): HTMLInputElement? =
        elementByIdOrNull(project.id.identifier + Gui.DISABLE_ALL_SUFFIX)

    private fun getToggle(project: Project, index: Int): HTMLInputElement =
        elementById<HTMLInputElement>(Gui.getCommandId(project, index) + Gui.DISABLE_SUFFIX)

    private fun toggleCommand(project: Project, toggle: HTMLInputElement) {
        if (!toggle.checked) {
            dispatchToggleEvent(project, toggle, EVENT_TOGGLE_UNCHECKED)
            toggle(toggle.id)
        } else {
            toggle(toggle.id)
        }
        menu.activateSaveButton()
    }

    private fun notAllCommandsOrSubmodulesActive(project: Project, toggle: HTMLInputElement): Boolean {
        return notAllCommandsActive(project, { it.id != toggle.id }) || notAllSubmodulesActive(project)
    }

    private fun dispatchToggleEvent(project: Project, toggle: HTMLInputElement, event: String) {
        projectElement(project).dispatchEvent(CustomEvent(event, CustomEventInit(toggle)))
    }

    private fun registerForProjectEvent(project: Project, event: String, callback: (Event) -> Unit) {
        projectElement(project).addEventListener(event, callback)
    }

    private fun projectElement(project: Project) = elementById(project.id.identifier)

    @JsName("toggle")
    fun toggle(id: String) {
        val checkbox = getCheckbox(id)
        when {
            id.endsWith(Gui.DISABLE_ALL_SUFFIX) -> toggleProject(checkbox)
            else -> toggleCommand(checkbox)
        }
    }

    private fun toggleProject(disableAllCheckbox: HTMLInputElement) {
        val checked = disableAllCheckbox.checked
        val identifier = disableAllCheckbox.id.substring(0, disableAllCheckbox.id.indexOf(Gui.DISABLE_ALL_SUFFIX))
        val project = getProject(identifier)

        project.commands.forEachIndexed { index, _ ->
            val checkbox = getCheckbox(identifier, index)
            //do nothing if command is disabled
            if (checkbox.disabled) return@forEachIndexed

            menu.activateSaveButton()
            checkbox.checked = checked
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

    private fun getDisableAllId(projectId: ProjectId) = "${projectId.identifier}${Gui.DISABLE_ALL_SUFFIX}"
    private fun getCheckbox(identifier: String, index: Int) = getCheckbox("$identifier:$index${Gui.DISABLE_SUFFIX}")

    private fun toggleCommand(checkbox: HTMLInputElement) {
        val project = getProjectOfCommand(checkbox)
        if (!checkbox.checked) {
            menu.activateSaveButton()
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
        val regex = Regex("(.*):[0-9]+${Gui.DISABLE_SUFFIX}")
        val result = regex.find(checkbox.id)
        require(result != null) {
            "the given id ($checkbox.id) did not match the pattern $regex"
        }
        val identifier = result!!.groups[1]!!.value
        return getProject(identifier)
    }

    private fun getProject(identifier: String) = elementById(identifier).asDynamic().project as Project


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
        private const val EVENT_TOGGLE_UNCHECKED = "toggle.unchecked"
    }
}
