package ch.loewenfels.depgraph.gui.jobexecution

import ch.loewenfels.depgraph.ConfigKey
import ch.loewenfels.depgraph.data.Command
import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ReleasePlan
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency
import ch.loewenfels.depgraph.data.maven.jenkins.M2ReleaseCommand
import ch.loewenfels.depgraph.parseRegexParameters
import ch.loewenfels.depgraph.parseRemoteRegex
import ch.tutteli.kbox.appendToStringBuilder

class ReleaseJobExecutionDataFactory(
    defaultJenkinsBaseUrl: String,
    releasePlan: ReleasePlan
) : BaseJobExecutionDataFactory(defaultJenkinsBaseUrl, releasePlan) {

    private val remoteRegex : List<Pair<Regex, String>>
    private val regexParametersList: List<Pair<Regex, String>>
    private val jobMapping: Map<String, String>

    init {
        checkConfig(releasePlan.config)
        remoteRegex = parseRemoteRegex(releasePlan)
        regexParametersList = parseRegexParameters(releasePlan)
        jobMapping = parseJobMapping()
    }

    private fun checkConfig(config: Map<ConfigKey, String>) {
        requireConfigEntry(config, ConfigKey.UPDATE_DEPENDENCY_JOB)
        requireConfigEntry(config, ConfigKey.REMOTE_REGEX)
        requireConfigEntry(config, ConfigKey.COMMIT_PREFIX)
    }

    private fun parseJobMapping(): Map<String, String> {
        val mapping = getConfig(ConfigKey.JOB_MAPPING)
        return mapping.split("|").associate { pair ->
            val index = pair.indexOf('=')
            check(index > 0) {
                "At least one mapping has no groupId and artifactId defined.\njobMapping: $mapping"
            }
            val groupIdAndArtifactId = pair.substring(0, index)
            check(groupIdAndArtifactId.contains(':')) {
                "At least one groupId and artifactId is erroneous, does not contain a `:`.\njobMapping: $mapping"
            }
            val jobName = pair.substring(index + 1)
            check(jobName.isNotBlank()) {
                "At least one groupId and artifactId is erroneous, has no job name defined.\njobMapping: $mapping"
            }
            groupIdAndArtifactId to jobName
        }
    }

    private fun getJobName(project: Project): String {
        val mavenProjectId = project.id as MavenProjectId
        return jobMapping[mavenProjectId.identifier] ?: mavenProjectId.artifactId
    }

    override fun create(project: Project, command: Command): JobExecutionData {
        return when (command) {
            is JenkinsUpdateDependency -> triggerUpdateDependency(project, command)
            is M2ReleaseCommand -> triggerRelease(project, command)
            else -> throw UnsupportedOperationException("We do not (yet) support the command: $command")
        }
    }

    private fun triggerUpdateDependency(project: Project, command: JenkinsUpdateDependency): JobExecutionData {
        val jobUrl = getJobUrl(ConfigKey.UPDATE_DEPENDENCY_JOB)
        val jobName = "update dependency of ${project.id.identifier}"
        val params = createUpdateDependencyParams(project, command)
        return JobExecutionData.buildWithParameters(jobName, jobUrl, toQueryParameters(params), params)
    }

    private fun createUpdateDependencyParams(project: Project, command: JenkinsUpdateDependency): Map<String,String> {
        val dependency = releasePlan.getProject(command.projectId)
        val dependencyMavenProjectId = dependency.id as MavenProjectId
        return mapOf(
            "pathToProject" to project.relativePath,
            "&groupId" to dependencyMavenProjectId.groupId,
            "&artifactId" to dependencyMavenProjectId.artifactId,
            "&newVersion" to dependency.releaseVersion,
            "&commitPrefix" to getConfig(ConfigKey.COMMIT_PREFIX),
            "&releaseId" to releasePlan.releaseId
        )
    }

    private fun triggerRelease(project: Project, command: M2ReleaseCommand): JobExecutionData {
        val mavenProjectId = project.id as MavenProjectId
        val jobName = getJobName(project)
        val jenkinsBaseUrl = getMatchingEntries(remoteRegex, mavenProjectId).firstOrNull() ?: defaultJenkinsBaseUrl
        val jobUrl = getJobUrl(jenkinsBaseUrl, jobName)
        val relevantParams = getMatchingEntries(regexParametersList, mavenProjectId)
        val parameters = StringBuilder()
        relevantParams.appendToStringBuilder(parameters, ",") {
            val (name, value) = it.split('=')
            parameters.append("{\"name\":\"$name\",\"value\":\"$value\"}")
        }
        val params = "releaseVersion=${project.releaseVersion}" +
            "&developmentVersion=${command.nextDevVersion}" +
            "&json={parameter=[$parameters]}"
        return JobExecutionData.m2ReleaseSubmit(
            "release ${project.id.identifier}",
            jobUrl,
            params,
            project.releaseVersion,
            command.nextDevVersion
        )
    }

    private fun getMatchingEntries(
        regex: List<Pair<Regex, String>>,
        mavenProjectId: MavenProjectId
    ) = regex.asSequence().filter { (regex, _) -> regex.matches(mavenProjectId.identifier) }.map { it.second }
}
