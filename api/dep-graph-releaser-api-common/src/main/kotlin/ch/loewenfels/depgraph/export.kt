package ch.loewenfels.depgraph

import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.ReleasePlan
import ch.tutteli.kbox.appendToStringBuilder

/**
 * Generates a list of projects, their [ProjectId.identifier] respectively, based on the projects which are part of
 * the given [releasePlan] but excluding the [ReleasePlan.rootProjectId], submodules and projects excluded
 * by the [excludeRegex].
 */
fun generateListOfDependentsWithoutSubmoduleAndExcluded(
    releasePlan: ReleasePlan,
    excludeRegex: Regex
) = projectsWithoutSubmodulesAndExcluded(releasePlan.iterator().asSequence().drop(1), excludeRegex)
    .map { it.id.identifier }
    .sorted()
    .joinToString("\n")

/**
 * Generates a list of git clone commands based on the projects which are part of the given [releasePlan] but
 * excluding submodules and projects excluded by the [excludeRegex].
 *
 * The git repository url is created with the help of [relativePathTransformerRegex] and
 * [relativePathTransformerReplacement] where the replacement takes place against [Project.relativePath].
 */
fun generateGitCloneCommands(
    releasePlan: ReleasePlan,
    excludeRegex: Regex,
    relativePathTransformerRegex: Regex,
    relativePathTransformerReplacement: String
) = gitRepoUrlsOfProjects(releasePlan, excludeRegex, relativePathTransformerRegex, relativePathTransformerReplacement)
    .joinToString("\n") { "git clone $it" }

/**
 * Generates an eclipse compatible psf-file including projects which are part of the given [releasePlan] but
 * excluding submodules and projects excluded by the [excludeRegex].
 *
 * The git repository url is created with the help of [relativePathTransformerRegex] and
 * [relativePathTransformerReplacement] where the replacement takes place against [Project.relativePath].
 */
fun generateEclipsePsf(
    releasePlan: ReleasePlan,
    excludeRegex: Regex,
    relativePathTransformerRegex: Regex,
    relativePathTransformerReplacement: String
): String {
    val sb = StringBuilder(
        """<?xml version="1.0" encoding="UTF-8"?>
        |<psf version="2.0">
        |  <provider id="org.eclipse.egit.core.GitProvider">
        |
        """.trimMargin()
    )
    gitRepoUrlsOfProjects(releasePlan, excludeRegex, relativePathTransformerRegex, relativePathTransformerReplacement)
        .appendToStringBuilder(sb, "\n") { gitRepoUrl ->
            sb.append("    <project reference=\"1.0,").append(gitRepoUrl).append(",master,.\"/>")
        }
    sb.append("\n  </provider>\n</psf>")
    return sb.toString()
}

private fun gitRepoUrlsOfProjects(
    releasePlan: ReleasePlan,
    excludeRegex: Regex,
    relativePathTransformerRegex: Regex,
    relativePathTransformerReplacement: String
): Sequence<String> {
    return projectsWithoutSubmodulesAndExcluded(releasePlan.iterator().asSequence(), excludeRegex)
        .map { it.turnIntoGitRepoUrl(relativePathTransformerRegex, relativePathTransformerReplacement) }
        .sorted()
}

private fun projectsWithoutSubmodulesAndExcluded(
    sequence: Sequence<Project>,
    excludeRegex: Regex
): Sequence<Project> = sequence
    .filter { !it.isSubmodule }
    .filter { !excludeRegex.matches(it.relativePath) }

private fun Project.turnIntoGitRepoUrl(
    relativePathTransformerRegex: Regex,
    relativePathTransformerReplacement: String
) = relativePathTransformerRegex.replace(relativePath, relativePathTransformerReplacement)
