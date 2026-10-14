package opal.dev.wynnoverhaul.client

class StableOrder {
    private val order = HashMap<Any, Int>()
    private var next = 0

    fun <T> sort(items: List<T>, key: (T) -> Any, group: (T) -> Comparable<*> = { 0 }): List<T> {
        val live = HashSet<Any>()
        for (item in items) {
            val k = key(item)
            live.add(k)
            order.getOrPut(k) { next++ }
        }
        order.keys.retainAll(live)
        @Suppress("UNCHECKED_CAST")
        return items.sortedWith(compareBy<T> { group(it) as Comparable<Any> }.thenBy { order.getValue(key(it)) })
    }

    fun clear() {
        order.clear()
    }
}
