package opal.dev.wynnoverhaul.client

import net.minecraft.world.item.ItemStack

object SearchSyntax {
    class Token(val negated: Boolean, val key: String?, val op: String, val value: String)

    private val LEVEL = Regex("""^(lvl|level)(>=|<=|>|<|=|:)(.*)$""")

    fun tokenize(query: String, keys: Set<String>): List<Token> {
        val out = ArrayList<Token>()
        for (chunk in split(query)) {
            var text = chunk
            var negated = false
            if (text.length > 1 && text[0] == '-') {
                negated = true
                text = text.substring(1)
            }
            val level = LEVEL.matchEntire(text)
            if (level != null && level.groupValues[1] in keys && level.groupValues[3].isNotEmpty()) {
                out.add(Token(negated, "lvl", level.groupValues[2], level.groupValues[3]))
                continue
            }
            val colon = text.indexOf(':')
            if (colon > 0) {
                val key = text.substring(0, colon)
                val value = text.substring(colon + 1).trim('"')
                if (key in keys && value.isNotEmpty()) {
                    out.add(Token(negated, key, ":", value))
                    continue
                }
            }
            val word = text.trim('"')
            if (word.isNotEmpty()) out.add(Token(negated, null, "", word))
        }
        return out
    }

    private fun split(query: String): List<String> {
        val parts = ArrayList<String>()
        val current = StringBuilder()
        var quoted = false
        for (ch in query) {
            when {
                ch == '"' -> {
                    quoted = !quoted
                    current.append(ch)
                }
                ch.isWhitespace() && !quoted -> {
                    if (current.isNotEmpty()) parts.add(current.toString())
                    current.setLength(0)
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotEmpty()) parts.add(current.toString())
        return parts
    }

    fun numberTest(op: String, raw: String): ((Int) -> Boolean)? {
        val range = Regex("""^(\d+)-(\d+)$""").matchEntire(raw)
        if (range != null && (op == ":" || op == "=")) {
            val low = range.groupValues[1].toInt()
            val high = range.groupValues[2].toInt()
            return { it in low..high }
        }
        val n = raw.toIntOrNull() ?: return null
        return when (op) {
            ">=" -> { v -> v >= n }
            "<=" -> { v -> v <= n }
            ">" -> { v -> v > n }
            "<" -> { v -> v < n }
            else -> { v -> v == n }
        }
    }
}

object ItemSearch {
    private val KEYS = setOf("tier", "rarity", "type", "cat", "name", "lore", "lvl", "level")
    private val LEVEL_LINE = Regex("""combat (?:lv\.?|level):?\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val cache = LinkedHashMap<String, List<(ItemStack) -> Boolean>>()

    fun matches(stack: ItemStack, query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return true
        if (stack.isEmpty) return false
        return compiled(trimmed).all { it(stack) }
    }

    fun levelOf(stack: ItemStack): Int? {
        for (line in WynnItemRarity.loreLines(stack)) {
            LEVEL_LINE.find(line)?.let { return it.groupValues[1].toIntOrNull() }
        }
        return null
    }

    private fun compiled(query: String): List<(ItemStack) -> Boolean> {
        cache[query]?.let { return it }
        val tests = SearchSyntax.tokenize(query, KEYS).mapNotNull { token -> test(token) }
        if (cache.size >= CACHE_LIMIT) cache.remove(cache.keys.first())
        cache[query] = tests
        return tests
    }

    private fun test(token: SearchSyntax.Token): ((ItemStack) -> Boolean)? {
        val value = token.value.lowercase()
        val base: (ItemStack) -> Boolean = when (token.key) {
            "tier", "rarity" -> { stack -> WynnItemRarity.of(stack)?.name?.lowercase()?.startsWith(value) == true }
            "type", "cat" -> { stack ->
                val category = WynnItemCategory.of(stack)
                category.name.lowercase().startsWith(value) || category.displayName.lowercase().contains(value)
            }
            "name" -> { stack -> stack.hoverName.string.lowercase().contains(value) }
            "lore" -> { stack -> WynnItemRarity.loreLines(stack).any { it.lowercase().contains(value) } }
            "lvl" -> {
                val check = SearchSyntax.numberTest(token.op, value) ?: return null
                val positive: (ItemStack) -> Boolean = { stack -> levelOf(stack)?.let(check) == true }
                return if (token.negated) { stack -> !positive(stack) } else positive
            }
            else -> { stack ->
                stack.hoverName.string.lowercase().contains(value) || WynnItemRarity.loreLines(stack).any { it.lowercase().contains(value) }
            }
        }
        return if (token.negated) { stack -> !base(stack) } else base
    }

    private const val CACHE_LIMIT = 24
}

object ActivitySearch {
    private val KEYS = setOf("type", "status", "dist", "distance", "len", "length", "diff", "difficulty", "lvl", "level", "name", "desc")
    private val cache = LinkedHashMap<String, List<(ActivityInfo) -> Boolean>>()

    fun matches(info: ActivityInfo, query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return true
        return compiled(trimmed).all { it(info) }
    }

    private fun compiled(query: String): List<(ActivityInfo) -> Boolean> {
        cache[query]?.let { return it }
        val tests = SearchSyntax.tokenize(query, KEYS).mapNotNull { test(it) }
        if (cache.size >= CACHE_LIMIT) cache.remove(cache.keys.first())
        cache[query] = tests
        return tests
    }

    private fun test(token: SearchSyntax.Token): ((ActivityInfo) -> Boolean)? {
        val value = token.value.lowercase()
        val base: (ActivityInfo) -> Boolean = when (token.key) {
            "type" -> { info -> info.type.displayName.lowercase().contains(value) }
            "status" -> { info -> info.status.name.lowercase().replace('_', ' ').startsWith(value) }
            "dist", "distance" -> { info -> info.distance?.name?.lowercase()?.replace('_', ' ')?.startsWith(value) == true }
            "len", "length" -> { info -> info.length?.name?.lowercase()?.startsWith(value) == true }
            "diff", "difficulty" -> { info -> info.difficulty?.name?.lowercase()?.startsWith(value) == true }
            "lvl" -> {
                val check = SearchSyntax.numberTest(token.op, value) ?: return null
                val positive: (ActivityInfo) -> Boolean = { info -> check(info.levelReq) }
                return if (token.negated) { info -> !positive(info) } else positive
            }
            "desc" -> { info -> info.description?.lowercase()?.contains(value) == true }
            "name" -> { info -> info.name.lowercase().contains(value) }
            else -> { info -> info.name.lowercase().contains(value) || info.description?.lowercase()?.contains(value) == true }
        }
        return if (token.negated) { info -> !base(info) } else base
    }

    private const val CACHE_LIMIT = 24
}
