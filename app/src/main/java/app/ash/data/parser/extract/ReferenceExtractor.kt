package app.ash.data.parser.extract

/** Identifiers that commonly appear in Indian notifications and documents. */
object ReferenceExtractor {
    private val pnr = Regex("""(?i)\bPNR\s*(?:no\.?|number|#)?\s*[:-]?\s*([A-Z0-9]{6,10})\b""")
    private val policy = Regex("""(?i)\bpolicy\s*(?:no\.?|number|#)\s*[:-]?\s*([A-Z0-9][A-Z0-9/-]{5,})""")
    private val policyLoose = Regex("""(?i)\bpolicy\s+([A-Z0-9]*\d[A-Z0-9/-]{5,})""")
    private val order = Regex("""(?i)\border\s*(?:id|no\.?|number|#)\s*[:-]?\s*#?([A-Z0-9][A-Z0-9-]{5,})""")
    private val invoice = Regex("""(?i)\b(?:invoice|bill)\s*(?:no\.?|number|#)\s*[:-]?\s*([A-Z0-9][A-Z0-9/-]{3,})""")
    private val account = Regex("""(?i)\b(?:a/c|acct|account|card|ac)\s*(?:no\.?)?\s*(?:ending(?:\s*with)?|ending\s*in)?\s*[xX*.]*\s*(\d{3,6})\b""")
    private val vehicle = Regex("""\b([A-Z]{2})[ -]?(\d{1,2})[ -]?([A-Z]{1,3})[ -]?(\d{4})\b""")
    private val bharat = Regex("""\b(\d{2})[ -]?BH[ -]?(\d{4})[ -]?([A-Z]{1,2})\b""")
    private val upiRef = Regex("""(?i)\b(?:upi\s*ref(?:\.|erence)?\s*(?:no\.?|id)?|ref(?:erence)?\s*(?:no\.?|id)|rrn|txn\s*(?:id|no\.?))\s*[:.-]?\s*([A-Z0-9]{8,})""")
    private val vpa = Regex("""\b([A-Za-z0-9.\-_]{2,}@[A-Za-z]{2,})\b""")

    fun pnr(text: String): String? = pnr.find(text)?.groupValues?.get(1)
    fun policyNumber(text: String): String? =
        (policy.find(text) ?: policyLoose.find(text))?.groupValues?.get(1)?.trimEnd('.', '-', '/')
    fun orderId(text: String): String? = order.find(text)?.groupValues?.get(1)
    fun invoiceNumber(text: String): String? = invoice.find(text)?.groupValues?.get(1)
    fun upiRef(text: String): String? = upiRef.find(text)?.groupValues?.get(1)
    fun vpa(text: String): String? = vpa.find(text)?.groupValues?.get(1)?.takeUnless { it.contains("..") }

    /** Masked account reference, e.g. "XX1234". Only the last 4 digits are ever kept. */
    fun account(text: String): String? = account.find(text)?.groupValues?.get(1)?.takeLast(4)?.let { "XX$it" }

    /** Normalised registration number, e.g. "MH12AB1234" or "22BH1234AA". */
    fun vehicleReg(text: String): String? {
        bharat.find(text)?.let { return "${it.groupValues[1]}BH${it.groupValues[2]}${it.groupValues[3]}" }
        val m = vehicle.find(text) ?: return null
        val state = m.groupValues[1]
        if (state !in STATE_CODES) return null
        return state + m.groupValues[2].padStart(2, '0') + m.groupValues[3] + m.groupValues[4]
    }

    private val STATE_CODES = setOf(
        "AN", "AP", "AR", "AS", "BR", "CG", "CH", "DD", "DL", "DN", "GA", "GJ", "HP", "HR", "JH", "JK", "KA", "KL",
        "LA", "LD", "MH", "ML", "MN", "MP", "MZ", "NL", "OD", "OR", "PB", "PY", "RJ", "SK", "TN", "TR", "TS", "UK",
        "UP", "WB",
    )
}
