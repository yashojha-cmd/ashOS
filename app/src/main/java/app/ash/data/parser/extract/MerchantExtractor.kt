package app.ash.data.parser.extract

import app.ash.data.parser.dictionary.MerchantDictionary
import app.ash.domain.model.TxnDirection

/** Best-effort counterparty name from transaction text. Dictionary hits win over free-text extraction. */
object MerchantExtractor {
    private const val STOP = """(?=\s+(?:on|via|using|ref|upi|for|with|a/c|ac|avl|is|has|dated|thru|through|by|txn|rrn|info|towards|in)\b|\s*[.,;(]|\s*$)"""
    private val to = Regex("""(?i)\b(?:spent at|at|paid to|sent to|payment to|trf to|transferred to|to)\s+(?:vpa\s+)?([A-Za-z0-9&@.'_\- ]{2,40}?)$STOP""")
    private val from = Regex("""(?i)\b(?:received from|from|by)\s+(?:vpa\s+)?([A-Za-z0-9&@.'_\- ]{2,40}?)$STOP""")
    private val sentYou = Regex("""(?i)\b([A-Za-z][A-Za-z .'-]{1,30}?)\s+(?:has\s+)?(?:sent|paid)\s+you\b""")
    private val upiInfo = Regex("""(?i)\bUPI/(?:P2[AM]/)?\d*/?([A-Za-z][A-Za-z0-9 .&'-]{1,30})""")
    private val rejectWords = setOf("your", "you", "a/c", "ac", "account", "card", "bank", "the", "a", "an", "self", "rs", "inr", "upi", "vpa", "mobile")

    fun extract(text: String, direction: TxnDirection?): String? {
        MerchantDictionary.find(text)?.let { return it.name }
        val candidates = buildList {
            if (direction == TxnDirection.INCOME || direction == TxnDirection.REFUND) {
                sentYou.find(text)?.let { add(it.groupValues[1]) }
                from.findAll(text).forEach { add(it.groupValues[1]) }
            } else {
                to.findAll(text).forEach { add(it.groupValues[1]) }
            }
            upiInfo.find(text)?.let { add(it.groupValues[1]) }
        }
        return candidates.firstNotNullOfOrNull(::clean)
    }

    internal fun clean(raw: String): String? {
        var s = raw.trim().trimEnd('.', '-', ',')
        if (s.contains('@')) {
            val handle = s.substringBefore('@').replace(Regex("[0-9._-]+"), " ").trim()
            s = handle.ifBlank { return null }
        }
        s = s.replace(Regex("\\s+"), " ")
        if (s.length < 2) return null
        if (s.lowercase() in rejectWords) return null
        if (s.lowercase().split(" ").all { it in rejectWords }) return null
        if (Regex("""(?i)^(?:x+\d+|\d+|a/c.*|ac\s.*)$""").matches(s)) return null
        if (s == s.uppercase() && s.any { it.isLetter() }) {
            s = s.lowercase().split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
        }
        return s.take(40)
    }
}
