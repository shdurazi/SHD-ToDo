package com.shdurazi.todo.core

/** A block together with the column it occupies when blocks overlap in time. */
data class Placed(val block: Block, val column: Int, val columns: Int)

object TimelineLayout {
    /** Lays blocks out side by side where their time ranges overlap. */
    fun place(blocks: List<Block>): List<Placed> {
        val sorted = blocks.sortedWith(compareBy<Block>({ it.startMin }, { -it.durationMin }))
        val result = ArrayList<Placed>()
        var cluster = ArrayList<Pair<Block, Int>>()
        var columnEnds = ArrayList<Int>()
        var clusterEnd = -1

        fun flush() {
            val cols = columnEnds.size
            for ((b, c) in cluster) result.add(Placed(b, c, cols))
            cluster = ArrayList()
            columnEnds = ArrayList()
            clusterEnd = -1
        }

        for (b in sorted) {
            if (cluster.isNotEmpty() && b.startMin >= clusterEnd) flush()
            var col = columnEnds.indexOfFirst { it <= b.startMin }
            if (col == -1) {
                col = columnEnds.size
                columnEnds.add(b.endMin)
            } else {
                columnEnds[col] = b.endMin
            }
            cluster.add(Pair(b, col))
            clusterEnd = maxOf(clusterEnd, b.endMin)
        }
        if (cluster.isNotEmpty()) flush()
        return result
    }
}
