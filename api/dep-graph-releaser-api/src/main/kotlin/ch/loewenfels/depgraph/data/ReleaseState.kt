package ch.loewenfels.depgraph.data

enum class ReleaseState {
    Ready,
    InProgress,
    Succeeded,
    Failed,
    ;

    fun checkTransitionAllowed(newState: ReleaseState): ReleaseState {
        when (newState) {
            ReleaseState.Ready -> check(false) {
                getErrorMessage(newState, "there is no way to transition to ${Ready::class.simpleName}")
            }
            ReleaseState.InProgress -> check(this == Ready || this == Failed) {
                getErrorMessage(
                    newState, "state was neither ${Ready::class.simpleName} nor ${Failed::class.simpleName}"
                )
            }
            ReleaseState.Succeeded -> checkNewState(newState, InProgress)
            ReleaseState.Failed -> checkNewState(newState, InProgress)
        }
        return newState
    }

    private fun checkNewState(newState: ReleaseState, expectedState: ReleaseState) {
        check(this == expectedState) {
            getErrorMessage(newState, "state is not ${expectedState::class.simpleName}")
        }
    }

    private fun getErrorMessage(newState: ReleaseState, reason: String) {
        "Cannot transition to ${newState::class.simpleName} because $reason." +
            //TODO use $this in stead of $getToStringRepresentation(...) once https://youtrack.jetbrains.com/issue/KT-23970 is fixed
            "\nState was: ${this.getToStringRepresentation()}"
    }
}
