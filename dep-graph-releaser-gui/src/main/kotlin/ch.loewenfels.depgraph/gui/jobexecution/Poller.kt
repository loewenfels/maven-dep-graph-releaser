package ch.loewenfels.depgraph.gui.jobexecution

import ch.loewenfels.depgraph.gui.sleep
import kotlin.browser.window
import kotlin.js.Promise

object Poller {

    fun pollAndExtract(
        authData: AuthData,
        url: String,
        regex: Regex,
        pollEverySecond: Int,
        maxWaitingTimeInSeconds: Int,
        errorHandler: (PollTimeoutException) -> Nothing
    ): Promise<String> {
        return poll(PollData(authData, url, pollEverySecond, maxWaitingTimeInSeconds) { body ->
                val matchResult = regex.find(body)
                if (matchResult != null) {
                    true to matchResult.groupValues[1]
                } else {
                    false to null
                }
            }).catch { t ->
                if (t is PollTimeoutException) {
                    errorHandler(t)
                } else {
                    throw t
                }
            }
    }

    private fun <T : Any> poll(pollData: PollData<T>): Promise<T> {
        val headers = createHeaderWithAuthAndCrumb(pollData.authData)
        val init = createGetRequest(headers)

        val rePoll: (String) -> T = { body ->
            if (pollData.numberOfTries * pollData.pollEverySecond >= pollData.maxWaitingTimeInSeconds) {
                throw PollTimeoutException(
                    "Waited at least ${pollData.maxWaitingTimeInSeconds} seconds",
                    body
                )
            }
            val p = sleep(pollData.pollEverySecond * 1000) {
                poll(pollData.newWithIncreasedNumberOfTimes())
            }
            // unsafeCast is used because javascript resolves the result automatically on return
            // will not result in Promise<Promise<T>> but T
            p.unsafeCast<T>()
        }

        return window.fetch(pollData.pollUrl, init)
            .then(::checkStatusOk)
            .then { body: String ->
                val (success, result) = pollData.action(body)
                if (success) {
                    if (result == null) {
                        throw Error("Result was null even though success flag during polling was true." +
                            "\nPlease report a bug: $GITHUB_NEW_ISSUE")
                    }
                    result
                } else {
                    rePoll(body)
                }
            }.catch { t ->
                if (t is Exception) {
                    rePoll("")
                } else {
                    throw t
                }
            }
    }

    @Suppress("DataClassPrivateConstructor")
    data class PollData<T> private constructor(
        val authData: AuthData,
        val pollUrl: String,
        val pollEverySecond: Int,
        val maxWaitingTimeInSeconds: Int,
        val action: (String) -> Pair<Boolean, T?>,
        val numberOfTries: Int
    ) {
        constructor(
            authData: AuthData,
            pollUrl: String,
            pollEverySecond: Int,
            maxWaitingTimeInSeconds: Int,
            action: (String) -> Pair<Boolean, T?>
        ) : this(
            authData,
            pollUrl,
            pollEverySecond,
            maxWaitingTimeInSeconds,
            action,
            numberOfTries = 0
        )


        fun newWithIncreasedNumberOfTimes(): PollData<T> = PollData(
            authData,
            pollUrl,
            pollEverySecond,
            maxWaitingTimeInSeconds,
            action,
            numberOfTries + 1
        )
    }

}
