package ch.loewenfels.depgraph.maven

import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.ReleaseCommand
import ch.loewenfels.depgraph.data.ReleasePlan
import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.loewenfels.depgraph.data.maven.jenkins.M2ReleaseCommand
import ch.loewenfels.depgraph.hasNextOnTheSameLevel
import ch.loewenfels.depgraph.toPeekingIterator
import java.lang.StringBuilder

class JenkinsPipelineCreator(
    private val remoteProjectRegex: Regex,
    private val remoteReleaseJobName: String,
    private val regexParametersList: List<Pair<Regex, String>>
) {

    fun create(releasePlan: ReleasePlan): CharSequence {
        val sb = StringBuilder()
        val itr = releasePlan.iterator().toPeekingIterator()
        var level: Int
        while (itr.hasNext()) {
            val project = itr.next()
            level = project.level

            sb.append("parallel(\n")
            var appended = appendIfNotSubmoduleOrNotMavenProject(sb, project, false)
            while (itr.hasNextOnTheSameLevel(level)) {
                appended = appendIfNotSubmoduleOrNotMavenProject(sb, itr.next(), appended)
            }
            sb.append("\n)\n")
        }
        return sb
    }

    private fun appendIfNotSubmoduleOrNotMavenProject(
        sb: StringBuilder,
        project: Project,
        appended: Boolean
    ): Boolean {
        val mavenProjectId = project.id
        if (project.isSubmodule || mavenProjectId !is MavenProjectId) return appended

        if(appended) {
            sb.append(",\n")
        }
        sb.append("  ['${mavenProjectId.artifactId}': {\n")
        when {
            remoteProjectRegex.matches(mavenProjectId.identifier) -> appendBuildRemoteRelease(sb, project, mavenProjectId)
            else -> appendBuildLocalRelease(sb, project, mavenProjectId)
        }
        sb.append("  }]")
        return true
    }

    private fun appendBuildRemoteRelease(
        sb: StringBuilder,
        project: Project,
        mavenProjectId: MavenProjectId
    ) {
        project.commands.asSequence().filterIsInstance<ReleaseCommand>().forEach {
            if (it !is M2ReleaseCommand) {
                throw UnsupportedOperationException(
                    "We do not yet support other ReleaseCommands than a ${M2ReleaseCommand::class.simpleName}"
                )
            }
            sb.appendJob(remoteReleaseJobName) {
                appendStringParam("jobName", mavenProjectId.artifactId).append(",")
                appendStringParam("releaseVersion", project.releaseVersion).append(",")
                appendStringParam("nextDevVersion", it.nextDevVersion).append(",")

                val relevantParams = regexParametersList.asSequence()
                    .filter { (regex, _) -> regex.matches(mavenProjectId.identifier) }
                    .map { it.second }
                    .joinToString(";")
                appendStringParam("parameters", relevantParams)
            }
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

    private fun appendBuildLocalRelease(
        sb: StringBuilder,
        project: Project,
        mavenProjectId: ProjectId
    ) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    companion object {
        private const val STRING_PARAM = "\$class: 'StringParameterValue', "
    }
}
