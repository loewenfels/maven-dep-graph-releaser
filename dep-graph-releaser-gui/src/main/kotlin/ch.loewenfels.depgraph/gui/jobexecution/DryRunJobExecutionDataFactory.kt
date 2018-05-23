package ch.loewenfels.depgraph.gui.jobexecution

import ch.loewenfels.depgraph.ConfigKey
import ch.loewenfels.depgraph.data.Command
import ch.loewenfels.depgraph.data.CommandState
import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ReleasePlan
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency
import ch.loewenfels.depgraph.data.maven.jenkins.M2ReleaseCommand
import ch.loewenfels.depgraph.gui.components.Pipeline

class DryRunJobExecutionDataFactory(
    jenkinsUrl: String,
    releasePlan: ReleasePlan
) : BaseJobExecutionDataFactory(jenkinsUrl, releasePlan) {

    init {
        checkConfig(releasePlan.config)
    }

    private fun checkConfig(config: Map<ConfigKey, String>) {
        requireConfigEntry(config, ConfigKey.UPDATE_DEPENDENCY_JOB)
        requireConfigEntry(config, ConfigKey.DRY_RUN_JOB)
    }

    override fun create(project: Project, command: Command): JobExecutionData {
        return when (command) {
            is JenkinsUpdateDependency -> triggerUpdateDependency(project, command)
            is M2ReleaseCommand -> triggerRelease(project)
            else -> throw UnsupportedOperationException("We do not (yet) support the command: $command")
        }
    }

    private fun triggerUpdateDependency(project: Project, command: JenkinsUpdateDependency): JobExecutionData {
        val jobName = "dry update dependency of ${project.id.identifier}"
        val params = createUpdateDependencyParams(project, command)
        return createJobExecutionData(jobName, params)
    }
    private fun triggerRelease(project: Project): JobExecutionData {
        val jobName = "dry release ${project.id.identifier}"
        val params = createReleaseParams(project)
        return createJobExecutionData(jobName, params)
    }

    private fun createUpdateDependencyParams(project: Project, command: JenkinsUpdateDependency): String {
        val dependency = releasePlan.getProject(command.projectId)
        val dependencyMavenProjectId = dependency.id as MavenProjectId
        val releaseVersion = ""
        val groupId = dependencyMavenProjectId.groupId
        val artifactId = dependencyMavenProjectId.artifactId
        val newVersion = "${dependency.releaseVersion}-${releasePlan.releaseId}"
        return createParams("update", project, releaseVersion, groupId, artifactId, newVersion)
    }

    private fun createReleaseParams(project: Project): String {
        val releaseVersion = "${project.releaseVersion}-${releasePlan.releaseId}"
        val groupId = ""
        val artifactId = ""
        val newVersion = ""
        return createParams("release", project, releaseVersion, groupId, artifactId, newVersion)
    }

    private fun createParams(
        commandName: String,
        project: Project,
        releaseVersion: String,
        groupId: String,
        artifactId: String,
        newVersion: String
    ): String {
        val skipCheckout = if (isFirstCommandAndNotSubmodule(project)) "false" else "true"
        return "command=$commandName" +
                "&pathToProject=${project.relativePath}" +
                "&skipCheckout=$skipCheckout" +
                "&releaseId=${releasePlan.releaseId}" +
                //release specific
                "&releaseVersion=$releaseVersion" +
                //update specific
                "&groupId=$groupId" +
                "&artifactId=$artifactId" +
                "&newVersion=$newVersion"
    }

    private fun isFirstCommandAndNotSubmodule(project: Project): Boolean {
        if (project.isSubmodule) return false

        project.commands.forEachIndexed { index, _ ->
            val state = Pipeline.getCommandState(project.id, index)
            if (state === CommandState.Succeeded || state === CommandState.Failed) {
                return false
            }
        }
        return true
    }

    private fun createJobExecutionData(jobName: String, params: String): JobExecutionData {
        val jobUrl = getJobUrl(ConfigKey.DRY_RUN_JOB)
        return JobExecutionData.buildWithParameters(jobName, jobUrl, params)
    }
}
