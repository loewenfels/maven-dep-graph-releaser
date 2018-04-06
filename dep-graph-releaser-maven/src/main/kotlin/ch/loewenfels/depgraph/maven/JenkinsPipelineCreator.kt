package ch.loewenfels.depgraph.maven

import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ReleasePlan
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.JenkinsUpdateDependency
import ch.loewenfels.depgraph.data.maven.jenkins.M2ReleaseCommand
import ch.loewenfels.depgraph.hasNextOnTheSameLevel
import ch.loewenfels.depgraph.toPeekingIterator
import java.lang.StringBuilder

class JenkinsPipelineCreator(
    private val updateDependencyJobName: String,
    private val remoteProjectRegex: Regex,
    private val remoteReleaseJobName: String,
    private val regexParametersList: List<Pair<Regex, String>>
) {

    fun create(releasePlan: ReleasePlan): CharSequence {
        val sb = StringBuilder("node(params.label) {\n\n")
        val itr = releasePlan.iterator().toPeekingIterator()
        var level: Int
        while (itr.hasNext()) {
            val project = itr.next()
            level = project.level

            sb.append("stage('level $level'){ parallel(\n")

            val paramObject = ParamObject(sb, project, releasePlan)
            var appended = appendIfNotSubmoduleOrNotMavenProject(paramObject, false)
            while (itr.hasNextOnTheSameLevel(level)) {
                val nextParamObject = ParamObject(paramObject, itr.next())
                appended = appendIfNotSubmoduleOrNotMavenProject(nextParamObject, appended)
            }

            sb.append("\n)}\n\n")
        }
        sb.append("}")
        return sb
    }

    private fun appendIfNotSubmoduleOrNotMavenProject(
        paramObject: ParamObject,
        appended: Boolean
    ): Boolean {
        val (sb, project) = paramObject
        val mavenProjectId = project.id
        if (project.isSubmodule || mavenProjectId !is MavenProjectId) return appended

        if (appended) {
            sb.append(",\n")
        }
        sb.append("  ['${mavenProjectId.artifactId}': {\n")
        project.commands.forEach {
            when (it) {
                is JenkinsUpdateDependency -> appendUpdateDependency(paramObject, it)
                is M2ReleaseCommand -> appendRelease(paramObject, it)
                else -> throw UnsupportedOperationException("We do not (yet) support the command: $it")
            }
        }
        sb.append("  }]")
        return true
    }

    private fun appendUpdateDependency(paramObject: ParamObject, it: JenkinsUpdateDependency) {
        paramObject.sb.appendJob(updateDependencyJobName) {
            appendStringParam("pathToProject", paramObject.mavenProjectId.artifactId).append(",")
            appendStringParam("groupId", paramObject.mavenProjectId.groupId).append(",")
            appendStringParam("artifactId", paramObject.mavenProjectId.artifactId).append(",")
            appendStringParam("newVersion", paramObject.releasePlan.getProject(it.projectId).releaseVersion)
        }
    }

    private fun appendRelease(paramObject: ParamObject, command: M2ReleaseCommand) {
        val jobName = if (remoteProjectRegex.matches(paramObject.mavenProjectId.identifier)) {
            remoteReleaseJobName
        } else {
            paramObject.mavenProjectId.artifactId
        }
        paramObject.sb.appendJob(jobName) {
            val mavenProjectId = paramObject.mavenProjectId
            appendStringParam("jobName", mavenProjectId.artifactId).append(",")
            appendStringParam("releaseVersion", paramObject.project.releaseVersion).append(",")
            appendStringParam("nextDevVersion", command.nextDevVersion).append(",")

            val relevantParams = regexParametersList.asSequence()
                .filter { (regex, _) -> regex.matches(mavenProjectId.identifier) }
                .map { it.second }
                .joinToString(";")
            appendStringParam("parameters", relevantParams)
        }
    }

    private fun StringBuilder.appendJob(jobName: String, parameters: StringBuilder.() -> Unit) {
        append("    build job: '").append(jobName).append("', parameters: [")
        this.parameters()
        append("\n    ]\n")
    }

    private fun StringBuilder.appendStringParam(name: String, value: String): StringBuilder {
        return append("\n      [").append(STRING_PARAM).append("name: '$name', value: '").append(value).append("']")
    }

    companion object {
        private const val STRING_PARAM = "\$class: 'StringParameterValue', "
    }

    data class ParamObject(
        val sb: StringBuilder,
        val project: Project,
        val releasePlan: ReleasePlan
    ) {
        constructor(paramObject: ParamObject, nextProject: Project) : this(
            paramObject.sb,
            nextProject,
            paramObject.releasePlan
        )

        val mavenProjectId = project.id as MavenProjectId
    }
}
