package app.ash.data.parser.dictionary

import app.ash.domain.model.Category

/** Canonical merchant names, their common spellings in notifications, and a default category. */
object MerchantDictionary {
    data class Merchant(val name: String, val aliases: List<String>, val category: Category)

    val all: List<Merchant> = listOf(
        // Shopping & food
        Merchant("Amazon", listOf("amazon", "amzn"), Category.SHOPPING),
        Merchant("Flipkart", listOf("flipkart", "ekart"), Category.SHOPPING),
        Merchant("Myntra", listOf("myntra"), Category.SHOPPING),
        Merchant("Meesho", listOf("meesho"), Category.SHOPPING),
        Merchant("Ajio", listOf("ajio"), Category.SHOPPING),
        Merchant("Nykaa", listOf("nykaa"), Category.SHOPPING),
        Merchant("Blinkit", listOf("blinkit", "grofers"), Category.SHOPPING),
        Merchant("Zepto", listOf("zepto"), Category.SHOPPING),
        Merchant("BigBasket", listOf("bigbasket", "bbnow"), Category.SHOPPING),
        Merchant("Swiggy", listOf("swiggy", "instamart"), Category.SHOPPING),
        Merchant("Zomato", listOf("zomato"), Category.SHOPPING),
        Merchant("DMart", listOf("dmart", "avenue supermarts"), Category.SHOPPING),
        // Subscriptions
        Merchant("Netflix", listOf("netflix"), Category.SUBSCRIPTIONS),
        Merchant("Spotify", listOf("spotify"), Category.SUBSCRIPTIONS),
        Merchant("JioHotstar", listOf("hotstar", "jiohotstar", "disney+"), Category.SUBSCRIPTIONS),
        Merchant("Prime Video", listOf("prime video", "amazon prime", "primevideo"), Category.SUBSCRIPTIONS),
        Merchant("YouTube Premium", listOf("youtube premium", "youtube"), Category.SUBSCRIPTIONS),
        Merchant("Google One", listOf("google one", "google storage"), Category.SUBSCRIPTIONS),
        Merchant("Apple", listOf("apple.com", "itunes", "apple services"), Category.SUBSCRIPTIONS),
        Merchant("SonyLIV", listOf("sonyliv", "sony liv"), Category.SUBSCRIPTIONS),
        Merchant("ZEE5", listOf("zee5"), Category.SUBSCRIPTIONS),
        Merchant("Google Play", listOf("google play", "play store"), Category.SUBSCRIPTIONS),
        // Telecom & utilities
        Merchant("Jio", listOf("jio", "reliance jio"), Category.BILLS),
        Merchant("Airtel", listOf("airtel"), Category.BILLS),
        Merchant("Vi", listOf("vodafone", "vi postpaid", "vodafone idea"), Category.BILLS),
        Merchant("BSNL", listOf("bsnl"), Category.BILLS),
        Merchant("ACT Fibernet", listOf("act fibernet", "atria convergence"), Category.BILLS),
        Merchant("BESCOM", listOf("bescom"), Category.BILLS),
        Merchant("Tata Power", listOf("tata power", "tpddl"), Category.BILLS),
        Merchant("Adani Electricity", listOf("adani electricity", "aeml"), Category.BILLS),
        Merchant("MSEDCL", listOf("msedcl", "mahavitaran"), Category.BILLS),
        Merchant("BSES", listOf("bses"), Category.BILLS),
        Merchant("TANGEDCO", listOf("tangedco", "tneb"), Category.BILLS),
        Merchant("Torrent Power", listOf("torrent power"), Category.BILLS),
        Merchant("CESC", listOf("cesc"), Category.BILLS),
        Merchant("Mahanagar Gas", listOf("mahanagar gas", "mgl"), Category.BILLS),
        Merchant("Indraprastha Gas", listOf("igl", "indraprastha gas"), Category.BILLS),
        // Travel
        Merchant("IRCTC", listOf("irctc"), Category.TRAVEL),
        Merchant("IndiGo", listOf("indigo", "interglobe"), Category.TRAVEL),
        Merchant("Air India", listOf("air india", "airindia"), Category.TRAVEL),
        Merchant("Akasa Air", listOf("akasa"), Category.TRAVEL),
        Merchant("SpiceJet", listOf("spicejet"), Category.TRAVEL),
        Merchant("MakeMyTrip", listOf("makemytrip", "mmt"), Category.TRAVEL),
        Merchant("Goibibo", listOf("goibibo"), Category.TRAVEL),
        Merchant("ixigo", listOf("ixigo"), Category.TRAVEL),
        Merchant("redBus", listOf("redbus"), Category.TRAVEL),
        Merchant("Uber", listOf("uber"), Category.TRAVEL),
        Merchant("Ola", listOf("olacabs", "ola cabs", "ani technologies"), Category.TRAVEL),
        Merchant("Rapido", listOf("rapido"), Category.TRAVEL),
        // Vehicle
        Merchant("Indian Oil", listOf("indian oil", "iocl"), Category.VEHICLE),
        Merchant("HP Petrol", listOf("hpcl", "hindustan petroleum"), Category.VEHICLE),
        Merchant("Bharat Petroleum", listOf("bpcl", "bharat petroleum"), Category.VEHICLE),
        Merchant("Shell", listOf("shell"), Category.VEHICLE),
        Merchant("FASTag", listOf("fastag", "netc"), Category.VEHICLE),
        // Healthcare
        Merchant("Apollo", listOf("apollo"), Category.HEALTHCARE),
        Merchant("PharmEasy", listOf("pharmeasy"), Category.HEALTHCARE),
        Merchant("Tata 1mg", listOf("1mg"), Category.HEALTHCARE),
        Merchant("Practo", listOf("practo"), Category.HEALTHCARE),
        // Insurance
        Merchant("ACKO", listOf("acko"), Category.INSURANCE),
        Merchant("Digit Insurance", listOf("digit insurance", "go digit", "godigit"), Category.INSURANCE),
        Merchant("HDFC ERGO", listOf("hdfc ergo"), Category.INSURANCE),
        Merchant("ICICI Lombard", listOf("icici lombard"), Category.INSURANCE),
        Merchant("LIC", listOf("lic of india", "life insurance corporation", "lic"), Category.INSURANCE),
        Merchant("Star Health", listOf("star health"), Category.INSURANCE),
        Merchant("Bajaj Allianz", listOf("bajaj allianz"), Category.INSURANCE),
        Merchant("Tata AIG", listOf("tata aig"), Category.INSURANCE),
        Merchant("Policybazaar", listOf("policybazaar"), Category.INSURANCE),
        // Finance
        Merchant("Zerodha", listOf("zerodha"), Category.FINANCE),
        Merchant("Groww", listOf("groww"), Category.FINANCE),
    )

    private val wordBoundaryCache = all.associateWith { m ->
        m.aliases.map { Regex("""(?i)(?<![a-z0-9])${Regex.escape(it)}(?![a-z0-9])""") }
    }

    /** The first dictionary merchant mentioned anywhere in [text]. */
    fun find(text: String): Merchant? = all.firstOrNull { m -> wordBoundaryCache.getValue(m).any { it.containsMatchIn(text) } }

    /** Canonicalises an extracted merchant string ("AMAZON PAY INDIA" -> "Amazon"). */
    fun canonical(raw: String): Merchant? = find(raw)

    fun categoryOf(name: String?): Category? = name?.let { find(it)?.category }
}
