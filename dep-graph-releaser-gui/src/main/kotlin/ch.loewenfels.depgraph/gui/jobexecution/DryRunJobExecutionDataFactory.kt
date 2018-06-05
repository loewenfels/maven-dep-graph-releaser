package ch.loewenfels.depgraph.gui.jobexecution

import ch.loewenfels.depgraph.ConfigKey
import ch.loewenfels.depgraph.data.*
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency
import ch.loewenfels.depgraph.data.maven.jenkins.M2ReleaseCommand
import ch.loewenfels.depgraph.gui.components.Pipeline

private typealias GroupIdArtifactIdAndNewVersion = Triple<String, String, String>

class DryRunJobExecutionDataFactory(
    defaultJenkinsBaseUrl: String,
    releasePlan: ReleasePlan
) : BaseJobExecutionDataFactory(defaultJenkinsBaseUrl, releasePlan) {

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
        val (params, identifyingParams) = createUpdateDependencyParams(project, command)
        return createJobExecutionData(jobName, params, identifyingParams)
    }

    private fun triggerRelease(project: Project): JobExecutionData {
        val jobName = "dry release ${project.id.identifier}"
        val (params, identifyingParams) = createReleaseParams(project)
        return createJobExecutionData(jobName, params, identifyingParams)
    }

    private fun createUpdateDependencyParams(
        project: Project,
        command: JenkinsUpdateDependency
    ): Pair<String, Map<String, String>> {
        val releaseVersion = ""
        val triple = determineGroupIdArtifactIdAndNewVersion(command)
        return createParams("update", project, releaseVersion, triple) to mapOf(
            "releaseId" to releasePlan.releaseId,
            "groupId" to triple.first,
            "artifactId" to triple.second,
            "newVersion" to triple.third
        )
    }

    private fun determineGroupIdArtifactIdAndNewVersion(command: JenkinsUpdateDependency): GroupIdArtifactIdAndNewVersion {
        val dependency = releasePlan.getProject(command.projectId)
        val dependencyMavenProjectId = dependency.id as MavenProjectId
        val groupId = dependencyMavenProjectId.groupId
        val artifactId = dependencyMavenProjectId.artifactId
        val newVersion = "${dependency.releaseVersion}-${releasePlan.releaseId}"
        return Triple(groupId, artifactId, newVersion)
    }

    private fun createReleaseParams(project: Project): Pair<String, Map<String, String>> {
        val releaseVersion = "${project.releaseVersion}-${releasePlan.releaseId}"
        return createParams("release", project, releaseVersion, GroupIdArtifactIdAndNewVersion("", "", "")) to mapOf(
            "releaseId" to releasePlan.releaseId,
            "releaseVersion" to releaseVersion
        )
    }

    private fun createParams(
        commandName: String,
        project: Project,
        releaseVersion: String,
        groupIdArtifactIdAndNewVersion: GroupIdArtifactIdAndNewVersion
    ): String {
        val (groupId, artifactId, newVersion) = groupIdArtifactIdAndNewVersion
        val skipCheckout = if (isFirstTriggeredCommand(project)) "false" else "true"
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

    private fun isFirstTriggeredCommand(project: Project): Boolean {
        if(project.isSubmodule) return isFirstTriggeredCommand(searchTopMultiModule(project.id))
        return !commandRanOnProjectOrSubmodules(project)
    }

    private fun searchTopMultiModule(projectId: ProjectId): Project
        = releasePlan.getAllSubmodules().entries
            .find { (_, v) -> v.contains(projectId) }
            .let { if(it != null) searchTopMultiModule(it.key) else releasePlan.getProject(projectId) }

    private fun commandRanOnProjectOrSubmodules(project: Project): Boolean {
        var commandAlreadyRan = project.commands.withIndex().any { (index, _) ->
            val state = Pipeline.getCommandState(project.id, index)
            state === CommandState.Succeeded || state === CommandState.Failed
        }
        val submodules = releasePlan.getSubmodules(project.id)
        commandAlreadyRan = commandAlreadyRan || (submodules.isNotEmpty() && submodules.any {
            commandRanOnProjectOrSubmodules(releasePlan.getProject(it))
        })
        return commandAlreadyRan
    }

    private fun createJobExecutionData(
        jobName: String,
        params: String,
        identifyingParams: Map<String, String>
    ): JobExecutionData {
        val jobUrl = getJobUrl(ConfigKey.DRY_RUN_JOB)
        return JobExecutionData.buildWithParameters(jobName, jobUrl, params, identifyingParams)
    }
}
