package app.ash.logging

/**
 * Masks personal data before anything reaches a log file.
 * Order matters: specific identifiers first, generic digit runs last.
 */
object Redactor {
    private data class Rule(val regex: Regex, val replacement: String)

    private val rules = listOf(
        Rule(Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"""), "[email]"),
        Rule(Regex("""(?i)\b(?:https?://|www\.)\S+"""), "[url]"),
        Rule(Regex("""\b[A-Z]{5}\d{4}[A-Z]\b"""), "[pan]"),
        Rule(Regex("""\b[A-Z]{2}[ -]?\d{1,2}[ -]?[A-Z]{1,3}[ -]?\d{4}\b"""), "[vehicle]"),
        Rule(Regex("""\b\d{4}[ -]?\d{4}[ -]?\d{4}\b"""), "[aadhaar]"),
        Rule(Regex("""(?i)(?:₹|\bRs\.?|\bINR)\s?[\d,]+(?:\.\d{1,2})?"""), "₹[amt]"),
        Rule(Regex("""(?<!\[)\b\d[\d -]{4,}\d\b"""), "[num]"),
    )

    fun redact(input: String): String = rules.fold(input) { acc, rule -> rule.regex.replace(acc, rule.replacement) }
}
