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
        elementByIdOrNull(projectId.identifier + Gui.DISABLE_ALL_SUFFIX)

    private fun registerAllToggleEvents(allToggle: HTMLInputElement, project: Project) {
        allToggle.addChangeEventListener {
            val event = if (allToggle.checked) EVENT_ALL_TOGGLE_CHECKED else EVENT_ALL_TOGGLE_UNCHECKED
            dispatchToggleEvent(project, allToggle, event)
        }
    }

    private fun registerCommandToggleEvents(project: Project) {
        project.commands.forEachIndexed { index, command ->
            val toggle = getToggle(project, index)

            if (command is ReleaseCommand) {
                toggle.addChangeEventListener { toggleCommand(project, toggle, EVENT_RELEASE_TOGGLE_UNCHECKED) }
                disallowClickIfNotAllCommandsOrSubmodulesActive(project, toggle)
                registerForProjectEvent(project, EVENT_TOGGLE_UNCHECKED) { toggle.uncheck() }
            } else {
                toggle.addChangeEventListener { toggleCommand(project, toggle, EVENT_TOGGLE_UNCHECKED) }
            }

            disableUnDisableForReleaseStartAndEnd(toggle, elementById("${toggle.id}:slider"))
            registerForProjectEvent(project, EVENT_ALL_TOGGLE_CHECKED) {
                toggle.check()
            }
            registerForProjectEvent(project, EVENT_ALL_TOGGLE_UNCHECKED) {
                toggle.uncheck()
            }
        }
    }

    private fun getToggle(project: Project, index: Int): HTMLInputElement =
        elementById<HTMLInputElement>(Gui.getCommandId(project, index) + Gui.DISABLE_SUFFIX)

    private fun toggleCommand(project: Project, toggle: HTMLInputElement, event: String) {
        if (!toggle.checked) {
            dispatchToggleEvent(project, toggle, event)
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
            val projectIds = collectDependentsOnNextLevelAndSubmodulesAndTheirDependents(project)

            projectIds.forEach { projectId ->
                registerForProjectEvent(project, EVENT_RELEASE_TOGGLE_UNCHECKED) {
                    getAllToggle(projectId)?.uncheck()
                }
            }
        }
    }

    private fun collectDependentsOnNextLevelAndSubmodulesAndTheirDependents(project: Project): HashSet<ProjectId> {
        val projectIds = hashSetOf<ProjectId>()
        val projectsToVisit = mutableListOf(project.id)
        do {
            val projectId = projectsToVisit.removeAt(0)
            projectIds.addAll(getDependentsOnNextLevel(project, projectId))
            val submodules = releasePlan.getSubmodules(projectId)
            projectIds.addAll(submodules)
            projectsToVisit.addAll(submodules)
        } while(projectsToVisit.isNotEmpty())
        return projectIds
    }

    private fun getDependentsOnNextLevel(mainProject: Project, projectId: ProjectId) =
        releasePlan.getDependents(projectId).filter { mainProject.level + 1 == releasePlan.getProject(it).level }


    private fun HTMLInputElement.uncheck() = changeChecked(this, false)
    private fun HTMLInputElement.check() = changeChecked(this, true)

    private fun changeChecked(toggle: HTMLInputElement, checked: Boolean) {
        if (toggle.checked == checked) return

        toggle.checked = checked
        Promise.resolve(0).then { //used to avoid stack-overflow
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
            val submodulesHasCommands = document.getElementById(getDisableAllId(project.id)) != null
            submodulesHasCommands &&
                //cannot be the same command, hence we do not filter commands at all => thus we use `{ true }`
                notAllCommandsActive(releasePlan.getProject(submoduleId), { true })
        }
    }

    private fun getDisableAllId(projectId: ProjectId) = "${projectId.identifier}${Gui.DISABLE_ALL_SUFFIX}"
    private fun getCheckbox(identifier: String, index: Int) = getCheckbox("$identifier:$index${Gui.DISABLE_SUFFIX}")


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
