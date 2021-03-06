package ch.loewenfels.depgraph.runner

import ch.loewenfels.depgraph.runner.commands.*
import ch.loewenfels.depgraph.runner.console.*

object Main {
    @JvmStatic
    @Suppress("MemberNameEqualsClassName")
    fun main(vararg args: String?) {
        val commands = listOf(
            DependentProjects,
            Json,
            PrintReleasableProjects,
            UpdateDependency
        )
        dispatch(args, errorHandler, commands)
    }

    internal var errorHandler: ErrorHandler = SystemExitErrorHandler
    internal var pathVerifier: PathVerifier = OnlyFolderAndSubFolderPathVerifier
}
