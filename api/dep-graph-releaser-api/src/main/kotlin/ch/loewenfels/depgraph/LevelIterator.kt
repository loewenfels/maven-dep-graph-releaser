package ch.loewenfels.depgraph

/**
 * Not thread safe
 */
class LevelIterator<in K, V>(
    private val startingPoint: Pair<K, V>
) : Iterator<V> {
    private val itemsToVisit:  MutableList<LinkedHashMap<K, V>> = mutableListOf(linkedMapOf(startingPoint))

    override fun hasNext() : Boolean {
        cleanupCurrentLevel()
        return itemsToVisit.isNotEmpty()
    }

    private fun cleanupCurrentLevel() {
        if (itemsToVisit.isNotEmpty() && itemsToVisit[0].isEmpty()) {
            itemsToVisit.removeAt(0)
        }
    }

    override fun next(): V {
        if (itemsToVisit.isEmpty()) {
            throw NoSuchElementException("No item left; starting point was $startingPoint")
        }
        cleanupCurrentLevel()
        val itemsOnTheSameLevel = itemsToVisit[0]
        return itemsOnTheSameLevel.remove(itemsOnTheSameLevel.iterator().next().key)!!
    }

    fun addToCurrentLevel(pair: Pair<K, V>) {
        itemsToVisit[0][pair.first] = pair.second
    }

    fun addToNextLevel(pair: Pair<K, V>) {
        if (itemsToVisit.size <= 1) {
            itemsToVisit.add(linkedMapOf())
        }
        val nextLevelProjects = itemsToVisit.last()
        nextLevelProjects[pair.first] = pair.second
    }

    fun removeIfOnSameLevelAndReAddOnNext(pair: Pair<K, V>){
        val itemsOnTheSameLevel = itemsToVisit[0]
        itemsOnTheSameLevel.remove(pair.first)
        addToNextLevel(pair)
    }
}
