package ch.loewenfels.depgraph.gui.jobexecution

import ch.loewenfels.depgraph.ConfigKey
import ch.loewenfels.depgraph.data.Command
import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ReleasePlan
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency
import ch.loewenfels.depgraph.data.maven.jenkins.M2ReleaseCommand

class ReleaseJobExecutionDataFactory(
    jenkinsUrl: String,
    releasePlan: ReleasePlan
) : BaseJobExecutionDataFactory(jenkinsUrl, releasePlan) {

    private val regexParametersList: List<Pair<Regex, String>>
    private val jobMapping: Map<String, String>

    init {
        checkConfig(releasePlan.config)
        regexParametersList = parseRegexParameters()
        jobMapping = parseJobMapping()
    }

    private fun checkConfig(config: Map<ConfigKey, String>) {
        requireConfigEntry(config, ConfigKey.UPDATE_DEPENDENCY_JOB)
        requireConfigEntry(config, ConfigKey.REMOTE_REGEX)
        requireConfigEntry(config, ConfigKey.REMOTE_JOB)
        requireConfigEntry(config, ConfigKey.COMMIT_PREFIX)
    }

    private fun parseRegexParameters(): List<Pair<Regex, String>> {
        val regexParameters = getConfig(ConfigKey.REGEX_PARAMS)
        return if (regexParameters.isNotEmpty()) {
            regexParameters.splitToSequence("$")
                .map { pair ->
                    val index = checkRegexNotEmpty(pair, regexParameters)
                    val parameters = pair.substring(index + 1)
                    checkAtLeastOneParameter(parameters, regexParameters)
                    Regex(pair.substring(0, index)) to parameters
                }
                .toList()
        } else {
            emptyList()
        }
    }

    private fun checkRegexNotEmpty(pair: String, regexParameters: String): Int {
        val index = pair.indexOf('#')
        check(index > 0) {
            "regex requires at least one character.\nregexParameters: $regexParameters"
        }
        return index
    }

    private fun checkAtLeastOneParameter(pair: String, regexParameters: String): Int {
        val index = pair.indexOf('=')
        check(index > 0) {
            "A regexParam requires at least one parameter.\nregexParameters: $regexParameters"
        }
        return index
    }

    private fun parseJobMapping(): Map<String, String> {
        val mapping = releasePlan.getConfig(ConfigKey.JOB_MAPPING)
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

    override fun create(project: Project, command: Command, index: Int): JobExecutionData {
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
        return JobExecutionData.buildWithParameters(jobName, jobUrl, params)
    }

    private fun createUpdateDependencyParams(project: Project, command: JenkinsUpdateDependency): String {
        val dependency = releasePlan.getProject(command.projectId)
        val dependencyMavenProjectId = dependency.id as MavenProjectId
        return "pathToProject=${project.relativePath}" +
            "&groupId=${dependencyMavenProjectId.groupId}" +
            "&artifactId=${dependencyMavenProjectId.artifactId}" +
            "&newVersion=${dependency.releaseVersion}" +
            "&commitPrefix=${getConfig(ConfigKey.COMMIT_PREFIX)}" +
            "&releaseId=${releasePlan.releaseId}"
    }

    private fun triggerRelease(project: Project, command: M2ReleaseCommand): JobExecutionData {
        val (jobUrl, params) = determineJobUrlAndParams(project, command)
        //TODO that is actually wrong, if it is a local build, then we should use m2 trigger and not buildWithParameters
        return JobExecutionData.buildWithParameters(
            "release ${project.id.identifier}",
            jobUrl,
            params
        )
    }

    private fun determineJobUrlAndParams(project: Project, command: M2ReleaseCommand): Pair<String, String> {
        val mavenProjectId = project.id as MavenProjectId
        val regex = Regex(getConfig(ConfigKey.REMOTE_REGEX))
        val relevantParams = regexParametersList.asSequence()
            .filter { (regex, _) -> regex.matches(mavenProjectId.identifier) }
            .map { it.second }

        val params = "releaseVersion=${project.releaseVersion}" +
            "&nextDevVersion=${command.nextDevVersion}"

        val jobName = getJobName(project)
        return if (regex.matches(project.id.identifier)) {
            getJobUrl(ConfigKey.REMOTE_JOB) to
                "$params&jobName=$jobName&parameters=${relevantParams.joinToString(";")}"
        } else {
            getJobUrl(jobName) to
                "$params&${relevantParams.joinToString("&")}}"
        }
    }


}
