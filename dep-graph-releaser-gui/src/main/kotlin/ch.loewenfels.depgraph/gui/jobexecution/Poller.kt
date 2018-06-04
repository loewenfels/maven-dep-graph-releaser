package ch.loewenfels.depgraph.gui.jobexecution

import ch.loewenfels.depgraph.gui.sleep
import kotlin.browser.window
import kotlin.js.Promise

object Poller {

    fun pollAndExtract(
        usernameAndApiToken: UsernameAndApiToken,
        crumbWithId: CrumbWithId?,
        url: String,
        regex: Regex,
        errorHandler: (PollException) -> Nothing
    ): Promise<String> {
        val pollData = PollData(usernameAndApiToken, crumbWithId, url, pollEverySecond = 2, maxWaitingTimeInSeconds = 20) { body ->
            val matchResult = regex.find(body)
            if (matchResult != null) {
                true to matchResult.groupValues[1]
            } else {
                false to null
            }
        }

        return poll(pollData).catch { t ->
            if (t is PollException) {
                errorHandler(t)
            } else {
                throw t
            }
        }
    }

    fun <T : Any> poll(pollData: PollData<T>): Promise<T> {
        val headers = createHeaderWithAuthAndCrumb(pollData.usernameAndApiToken, pollData.crumbWithId)
        val init = createGetRequest(headers)

        val rePoll: (String) -> T = { body ->
            if (pollData.numberOfTries * pollData.pollEverySecond >= pollData.maxWaitingTimeInSeconds) {
                throw PollException(
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
                        throw Error("Result was null even though success flag during polling was true, please report a bug.")
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
        val usernameAndApiToken: UsernameAndApiToken,
        val crumbWithId: CrumbWithId?,
        val pollUrl: String,
        val pollEverySecond: Int,
        val maxWaitingTimeInSeconds: Int,
        val action: (String) -> Pair<Boolean, T?>,
        val numberOfTries: Int
    ) {
        constructor(
            usernameAndApiToken: UsernameAndApiToken,
            crumbWithId: CrumbWithId?,
            pollUrl: String,
            pollEverySecond: Int,
            maxWaitingTimeInSeconds: Int,
            action: (String) -> Pair<Boolean, T?>
        ) : this(
            usernameAndApiToken,
            crumbWithId,
            pollUrl,
            pollEverySecond,
            maxWaitingTimeInSeconds,
            action,
            numberOfTries = 0
        )


        fun newWithIncreasedNumberOfTimes(): PollData<T> = PollData(
            usernameAndApiToken,
            crumbWithId,
            pollUrl,
            pollEverySecond,
            maxWaitingTimeInSeconds,
            action,
            numberOfTries + 1
        )
    }

}
