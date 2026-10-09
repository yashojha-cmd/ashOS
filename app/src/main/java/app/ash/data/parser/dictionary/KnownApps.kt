package app.ash.data.parser.dictionary

import app.ash.domain.model.SourceKind

/**
 * Apps ASH recognises and suggests as notification sources. Users still opt in to each one.
 * Unknown apps are recorded (disabled) the first time they post, so the user can enable them later.
 */
object KnownApps {
    data class App(val packageName: String, val label: String, val kind: SourceKind)

    val all: List<App> = listOf(
        App("com.google.android.apps.messaging", "Messages (SMS)", SourceKind.SMS),
        App("com.samsung.android.messaging", "Samsung Messages (SMS)", SourceKind.SMS),
        App("com.google.android.apps.nbu.paisa.user", "Google Pay", SourceKind.UPI),
        App("com.phonepe.app", "PhonePe", SourceKind.UPI),
        App("net.one97.paytm", "Paytm", SourceKind.UPI),
        App("in.org.npci.upiapp", "BHIM", SourceKind.UPI),
        App("com.snapwork.hdfc", "HDFC Bank", SourceKind.BANK),
        App("com.csam.icici.bank.imobile", "ICICI iMobile", SourceKind.BANK),
        App("com.sbi.lotusintouch", "SBI YONO", SourceKind.BANK),
        App("com.axis.mobile", "Axis Mobile", SourceKind.BANK),
        App("com.msf.kbank.mobile", "Kotak Bank", SourceKind.BANK),
        App("com.amazon.mShop.android.shopping", "Amazon", SourceKind.SHOPPING),
        App("com.flipkart.android", "Flipkart", SourceKind.SHOPPING),
        App("com.myntra.android", "Myntra", SourceKind.SHOPPING),
        App("com.meesho.supply", "Meesho", SourceKind.SHOPPING),
        App("com.grofers.customerapp", "Blinkit", SourceKind.SHOPPING),
        App("com.zeptoconsumerapp", "Zepto", SourceKind.SHOPPING),
        App("in.swiggy.android", "Swiggy", SourceKind.FOOD),
        App("com.application.zomato", "Zomato", SourceKind.FOOD),
        App("cris.org.in.prs.ima", "IRCTC Rail Connect", SourceKind.TRAVEL),
        App("in.goindigo.android", "IndiGo", SourceKind.TRAVEL),
        App("com.makemytrip", "MakeMyTrip", SourceKind.TRAVEL),
        App("com.ixigo.train.ixitrain", "ixigo Trains", SourceKind.TRAVEL),
        App("com.jio.myjio", "MyJio", SourceKind.TELECOM),
        App("com.myairtelapp", "Airtel Thanks", SourceKind.TELECOM),
        App("com.netflix.mediaclient", "Netflix", SourceKind.OTT),
        App("com.spotify.music", "Spotify", SourceKind.OTT),
        App("com.acko.android", "ACKO", SourceKind.INSURANCE),
        App("com.digit.insurance", "Digit Insurance", SourceKind.INSURANCE),
        App("com.policybazaar", "Policybazaar", SourceKind.INSURANCE),
        App("in.gov.umang.negd.g2c", "UMANG", SourceKind.GOVERNMENT),
        App("com.digilocker.android", "DigiLocker", SourceKind.GOVERNMENT),
        App("com.nic.mparivahan", "mParivahan", SourceKind.GOVERNMENT),
    )

    private val byPackage = all.associateBy { it.packageName }

    fun find(packageName: String): App? = byPackage[packageName]
    fun kindOf(packageName: String): SourceKind = byPackage[packageName]?.kind ?: SourceKind.OTHER
}
