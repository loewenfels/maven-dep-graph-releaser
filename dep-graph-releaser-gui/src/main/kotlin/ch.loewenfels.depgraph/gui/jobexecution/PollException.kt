package ch.loewenfels.depgraph.gui.jobexecution

class PollException(message: String, val body: String) : RuntimeException(message)
