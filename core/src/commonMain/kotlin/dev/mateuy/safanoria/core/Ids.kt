package dev.mateuy.safanoria.core

/** Ticket id suggestions (SPEC §3). A suggestion only: humans and agents confirm or change it. */
public object Ids {
    public const val MAX_LENGTH: Int = 40
    private const val MAX_WORDS = 4

    /**
     * A short id from a title, close to the ids people pick: "Andalucia Dual-File Import" →
     * `andalucia-dual-file-import`. camelCase is split, accents folded, filler words dropped, at most
     * [MAX_WORDS] words. With [parent], the id is `<parent>-<words>` (§3: children are named
     * after their parent), trimmed by whole words to [MAX_LENGTH]. Null when nothing usable is left.
     */
    public fun suggest(title: String, parent: String? = null): String? {
        val parentWords = parent?.split('-')?.toSet() ?: emptySet()
        // Words the parent id already has would only repeat it.
        val words = words(title).filter { it !in parentWords }
        val significant = words.filter { it !in FILLER }.ifEmpty { words }
        val prefix = parent?.let { "$it-" } ?: ""
        val chosen = mutableListOf<String>()
        for (w in significant) {
            if (chosen.size == MAX_WORDS) break
            val candidate = prefix + (chosen + w).joinToString("-")
            if (candidate.length > MAX_LENGTH) break
            chosen += w
        }
        if (chosen.isEmpty()) return null
        // Ids start with a letter (§3).
        val id = (prefix + chosen.joinToString("-")).trimStart { !it.isLetter() }
        return id.takeIf { isValidId(it) }
    }

    /** Lowercase ASCII words of a title: camelCase split, accents folded, other characters dropped. */
    internal fun words(title: String): List<String> {
        val spaced = title
            .replace(Regex("([lL])[·.•]([lL])"), "$1$2")         // Catalan col·lecció → collecció
            .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")      // applyMovement → apply Movement
            .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1 $2")   // HTTPServer → HTTP Server
        return fold(spaced.lowercase())
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotEmpty() }
    }

    private fun fold(text: String): String = buildString {
        for (c in text) append(ACCENTS[c] ?: c.toString())
    }

    private val ACCENTS: Map<Char, String> = buildMap {
        "àáâäãå".forEach { put(it, "a") }
        "èéêë".forEach { put(it, "e") }
        "ìíîï".forEach { put(it, "i") }
        "òóôöõ".forEach { put(it, "o") }
        "ùúûü".forEach { put(it, "u") }
        put('ç', "c"); put('ñ', "n"); put('ŀ', "l"); put('ß', "ss"); put('æ', "ae"); put('œ', "oe")
    }

    /** Short words that rarely help name a ticket, in English, Spanish and Catalan. */
    private val FILLER: Set<String> = setOf(
        // en
        "a", "an", "the", "of", "for", "to", "in", "on", "at", "by", "with", "from", "and", "or",
        "is", "are", "be", "as", "using", "via", "when", "into",
        // es
        "el", "la", "los", "las", "un", "una", "de", "del", "al", "en", "con", "por", "para", "y", "o", "que",
        // ca
        "els", "les", "l", "d", "amb", "per", "i", "dels", "als", "quan",
    )
}
