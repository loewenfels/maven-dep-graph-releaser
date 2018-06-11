package ch.loewenfels.depgraph

import ch.loewenfels.depgraph.data.Project
import ch.tutteli.kbox.PeekingIterator

fun PeekingIterator<Project>.hasNextOnTheSameLevel(level: Int) = hasNext() && level == peek().level
