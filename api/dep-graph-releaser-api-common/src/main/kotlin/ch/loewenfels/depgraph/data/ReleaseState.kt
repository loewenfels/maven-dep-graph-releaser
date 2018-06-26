package ch.loewenfels.depgraph.data

enum class ReleaseState {
    READY,
    IN_PROGRESS,
    SUCCEEDED,
    FAILED,
    /**
     * State indicating that a user only observes the process (we could disable all toggles etc.)
     */
    WATCHING,
    ;

    fun checkTransitionAllowed(newState: ReleaseState): ReleaseState {
        return when (newState) {
            ReleaseState.READY -> checkNewState(newState, SUCCEEDED)
            ReleaseState.IN_PROGRESS -> {
                check(this == READY || this == FAILED) {
                    getErrorMessage(
                        newState, "state was neither ${READY::class.simpleName} nor ${FAILED::class.simpleName}"
                    )
                }
                newState
            }
            ReleaseState.SUCCEEDED -> checkNewState(newState, IN_PROGRESS)
            ReleaseState.FAILED -> checkNewState(newState, IN_PROGRESS)
            ReleaseState.WATCHING -> newState //it's always allowed to watch
        }
    }

    private fun checkNewState(newState: ReleaseState, expectedState: ReleaseState): ReleaseState {
        check(this == expectedState) {
            getErrorMessage(newState, "state is not ${expectedState::class.simpleName}")
        }
        return newState
    }

    private fun getErrorMessage(newState: ReleaseState, reason: String) {
        "Cannot transition to ${newState::class.simpleName} because $reason." +
            //TODO use $this in stead of $getToStringRepresentation(...) once https://youtrack.jetbrains.com/issue/KT-23970 is fixed
            "\nState was: ${this.getToStringRepresentation()}"
    }
}
