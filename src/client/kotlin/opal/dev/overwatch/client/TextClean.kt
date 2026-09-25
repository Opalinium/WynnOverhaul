package opal.dev.overwatch.client

object TextClean {
    private const val LEGACY_CODE_PREFIX = '§'
    private const val PRIVATE_USE_START = 0xE000
    private const val REPLACEMENT_CHAR = 0xFFFD

    fun clean(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            if (text[i] == LEGACY_CODE_PREFIX) {
                i += 2
                continue
            }
            val cp = text.codePointAt(i)
            if (cp < PRIVATE_USE_START && cp != REPLACEMENT_CHAR) sb.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        return sb.toString().trim()
    }
}
