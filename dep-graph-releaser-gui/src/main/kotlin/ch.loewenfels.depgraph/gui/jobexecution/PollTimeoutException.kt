package ch.loewenfels.depgraph.gui.jobexecution

class PollTimeoutException(message: String, val body: String, cause: Throwable?) : RuntimeException(message, cause) {
    constructor(message: String, body: String): this(message, body, null)
}
