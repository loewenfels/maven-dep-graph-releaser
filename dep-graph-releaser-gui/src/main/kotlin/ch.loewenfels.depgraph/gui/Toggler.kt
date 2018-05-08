package ch.loewenfels.depgraph.gui

import ch.loewenfels.depgraph.data.*
import ch.loewenfels.depgraph.gui.Gui.Companion.disableUnDisableForReleaseStartAndEnd
import ch.tutteli.kbox.mapWithIndex
import org.w3c.dom.CustomEvent
import org.w3c.dom.CustomEventInit
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import kotlin.browser.document
import kotlin.js.Promise

class Toggler(private val releasePlan: ReleasePlan, private val menu: Menu) {

    fun registerToggleEvents() {
        releasePlan.getProjects().forEach { project ->
            val allToggle = getAllToggle(project) ?: return@forEach

            registerAllToggleEvents(allToggle, project)
            registerCommandToggleEvents(project)
            registerReleaseUncheckEventForDependentsAndSubmodules(project)
        }
    }

    private fun getAllToggle(project: Project): HTMLInputElement? = getAllToggle(project.id)
    private fun getAllToggle(projectId: ProjectId): HTMLInputElement? =
        elementByIdOrNull(projectId.identifier + Gui.DEACTIVATE_ALL_SUFFIX)

    private fun registerAllToggleEvents(allToggle: HTMLInputElement, project: Project) {
        allToggle.addChangeEventListener {
            val event = if (allToggle.checked) EVENT_ALL_TOGGLE_CHECKED else EVENT_ALL_TOGGLE_UNCHECKED
            dispatchToggleEvent(project, allToggle, event)
        }
        Gui.disableUnDisableForReleaseStartAndEnd(allToggle, elementById("${allToggle.id}${Gui.SLIDER_SUFFIX}"))
    }

    private fun registerCommandToggleEvents(project: Project) {
        project.commands.forEachIndexed { index, command ->
            val toggle = getToggle(project, index)

            if (command is ReleaseCommand) {
                toggle.addChangeEventListener { toggleCommand(project, index, EVENT_RELEASE_TOGGLE_UNCHECKED) }
                disallowClickIfNotAllCommandsOrSubmodulesActive(project, toggle)
                val projectAndSubmodules = sequenceOf(project) +
                    releasePlan.getSubmodules(project.id).asSequence().map { releasePlan.getProject(it) }

                projectAndSubmodules.forEach {
                    registerForProjectEvent(it, EVENT_TOGGLE_UNCHECKED) { toggle.uncheck() }
                }
            } else {
                toggle.addChangeEventListener { toggleCommand(project, index, EVENT_TOGGLE_UNCHECKED) }
            }

            disableUnDisableForReleaseStartAndEnd(toggle, elementById("${toggle.id}${Gui.SLIDER_SUFFIX}"))
            registerForProjectEvent(project, EVENT_ALL_TOGGLE_CHECKED) {
                if (inCorrectStateForToggling(project, index)) {
                    toggle.check()
                }
            }
            registerForProjectEvent(project, EVENT_ALL_TOGGLE_UNCHECKED) {
                if (inCorrectStateForToggling(project, index)) {
                    toggle.uncheck()
                }
            }
        }
    }

    private fun inCorrectStateForToggling(project: Project, index: Int): Boolean {
        val commandState = Gui.getCommandState(project.id, index)
        return commandState === CommandState.Ready ||
            commandState is CommandState.Waiting ||
            commandState is CommandState.Deactivated ||
            commandState === CommandState.Failed //maybe a user does not want to re-trigger a failed command
    }

    private fun getToggle(project: Project, index: Int): HTMLInputElement =
        elementById<HTMLInputElement>(Gui.getCommandId(project, index) + Gui.DEACTIVATE_SUFFIX)

    private fun toggleCommand(project: Project, index: Int, uncheckedEvent: String) {
        val toggle = getToggle(project, index)
        val command = Gui.getCommand(project, index).asDynamic()
        val slider = elementById("${toggle.id}${Gui.SLIDER_SUFFIX}")
        val currentTitle = elementById("${Gui.getCommandId(project, index)}${Gui.STATE_SUFFIX}").title
        if (!toggle.checked) {
            dispatchToggleEvent(project, toggle, uncheckedEvent)
            val previous = command.state as CommandState
            Gui.changeStateOfCommand(project, index, CommandState.Deactivated(previous), currentTitle)
            slider.title = "Click to activate command."
        } else {
            val oldState = command.state as CommandState.Deactivated
            Gui.changeStateOfCommand(project, index, oldState.previous, currentTitle)
            slider.title = "Click to deactivate command."
        }
        menu.activateSaveButton()
    }

    private fun disallowClickIfNotAllCommandsOrSubmodulesActive(project: Project, toggle: HTMLInputElement) {
        toggle.addClickEventListener { e ->
            if (toggle.checked && notAllCommandsOrSubmodulesActive(project, toggle)) {
                // cannot reactivate release command if not all commands are active
                // setting checked again to false
                //toggle.checked = false
                e.preventDefault()
                showInfo(
                    "Cannot reactivate the ReleaseCommand for project ${project.id.identifier} " +
                        "because some commands (of submodules) are deactivated.",
                    4000
                )
            }
        }
    }

    private fun notAllCommandsOrSubmodulesActive(project: Project, toggle: HTMLInputElement): Boolean {
        return notAllCommandsActive(project, { it.id != toggle.id }) || notAllSubmodulesActive(project)
    }

    private fun registerReleaseUncheckEventForDependentsAndSubmodules(project: Project) {
        if (!project.isSubmodule) {
            val projectIds = releasePlan.collectDependentsInclDependentsOfAllSubmodules(project.id)

            projectIds.forEach { (projectId, dependentId) ->
                releasePlan.getProject(dependentId).commands
                    .mapWithIndex()
                    .filter { (_, command) ->
                        // release command will get deactivated automatically via deactivation dependency update
                        if (command is ReleaseCommand) return@filter false
                        val state = command.state
                        state is CommandState.Waiting && state.dependencies.contains(projectId)
                    }
                    .forEach { (index, _) ->
                        registerForProjectEvent(project, EVENT_RELEASE_TOGGLE_UNCHECKED) {
                            getToggle(releasePlan.getProject(dependentId), index).uncheck()
                        }
                    }
            }
        }
    }

    private fun HTMLInputElement.uncheck() = changeChecked(this, false)
    private fun HTMLInputElement.check() = changeChecked(this, true)

    private fun changeChecked(toggle: HTMLInputElement, checked: Boolean) {
        if (toggle.checked == checked) return

        toggle.checked = checked
        Promise.resolve(0).then {
            //used to avoid stack-overflow
            toggle.dispatchEvent(Event("change"))
        }
    }

    private fun notAllCommandsActive(project: Project, predicate: (HTMLInputElement) -> Boolean): Boolean {
        return project.commands.asSequence()
            .mapIndexed { index, _ -> getCheckbox(project.id.identifier, index) }
            .filter(predicate)
            .any { checkbox -> !checkbox.checked }
    }

    private fun notAllSubmodulesActive(project: Project): Boolean {
        return releasePlan.getSubmodules(project.id).any { submoduleId ->
            val submodulesHasCommands = document.getElementById(getDeactivateAllId(project.id)) != null
            submodulesHasCommands &&
                //cannot be the same command, hence we do not filter commands at all => thus we use `{ true }`
                notAllCommandsActive(releasePlan.getProject(submoduleId), { true })
        }
    }

    private fun getDeactivateAllId(projectId: ProjectId) = "${projectId.identifier}${Gui.DEACTIVATE_ALL_SUFFIX}"
    private fun getCheckbox(identifier: String, index: Int) = getCheckbox("$identifier:$index${Gui.DEACTIVATE_SUFFIX}")


    private fun dispatchToggleEvent(project: Project, toggle: HTMLInputElement, event: String) {
        projectElement(project).dispatchEvent(CustomEvent(event, CustomEventInit(toggle)))
    }

    private fun registerForProjectEvent(project: Project, event: String, callback: (Event) -> Unit) {
        projectElement(project).addEventListener(event, callback)
    }

    private fun projectElement(project: Project) = elementById(project.id.identifier)

    companion object {
        private const val EVENT_TOGGLE_UNCHECKED = "toggle.unchecked"
        private const val EVENT_RELEASE_TOGGLE_UNCHECKED = "release.toggle.unchecked"
        private const val EVENT_ALL_TOGGLE_CHECKED = "all.toggle.checked"
        private const val EVENT_ALL_TOGGLE_UNCHECKED = "all.toggle.unchecked"
    }
}
