package ch.loewenfels.depgraph.gui.jobexecution

import kotlin.js.Promise

/**
 * Responsible to extract a build number from a triggered job.
 */
interface BuilderNumberExtractor {

    /**
     * Eventually returns the build number.
     */
    fun extract(): Promise<Int>
}
