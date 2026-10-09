package app.ash.data.parser.dictionary

/** Keyword dictionaries shared by parsers. English plus common Hinglish forms. */
object Keywords {
    val SENSITIVE = Regex(
        """(?i)\b(otp|one[- ]time[- ]password|verification code|security code|login code|passcode|2fa|cvv|do not share|never share|don't share|mpin)\b""",
    )
    val PROMO = Regex(
        """(?i)(\boffer\b|cashback up to|upto \d+% off|\d+% off|discount|\bsale\b|\bwin\b|congratulations|pre-?approved|eligible for|apply now|limited time|use code|coupon|lowest price|\bdeal\b|hurry|exclusive)""",
    )
    val FUTURE_DEBIT = Regex("""(?i)(will be debited|will be deducted|to be debited|scheduled to be|is due|due on|due date|payment due)""")

    val REFUND = Regex("""(?i)\b(refund(?:ed)?|reversed|reversal|cashback (?:of|credited))\b""")
    val DEBIT = Regex("""(?i)\b(debited|debit of|spent|withdrawn|deducted|paid|sent|purchase|charged|txn of|used for|transferred to|kat gaya|katauti)\b""")
    val CREDIT = Regex("""(?i)\b(credited|received|deposited|credit of|added to your|jama|prapt)\b""")
    val TRANSFER_SELF = Regex("""(?i)\b(self transfer|own account|to self|between your accounts)\b""")
    val FAILED = Regex("""(?i)\b(failed|declined|unsuccessful|could not be processed|rejected)\b""")
    val REQUEST = Regex("""(?i)\b(requested|request(?:ing)? (?:money|payment)|collect request)\b""")

    val BANK_NAMES = Regex(
        """(?i)\b(hdfc|icici|sbi|state bank|axis|kotak|yes bank|idfc|indusind|pnb|punjab national|bank of baroda|bob|canara|union bank|federal|au bank|rbl|idbi|bank)\b""",
    )
    val UPI = Regex("""(?i)\b(upi|vpa|bhim|imps|neft|rtgs)\b""")

    val ORDER = Regex("""(?i)\b(order (?:placed|confirmed|received|is confirmed|has been placed)|your order|thank you for (?:your )?order|order #|order id)\b""")
    val ORDER_CANCELLED = Regex("""(?i)\b(order (?:has been |is )?cancell?ed|cancellation)\b""")
    val DELIVERY = Regex(
        """(?i)\b(shipped|dispatched|out for delivery|arriving|will be delivered|delivered|delivery (?:attempt|scheduled|expected)|on (?:its|the) way|picked up|in transit|expected delivery)\b""",
    )
    val DELIVERY_DATE_HINT = Regex("""(?i)(arriv\w*|deliver\w*|expected|by)""")

    val TRAVEL = Regex(
        """(?i)\b(pnr|ticket (?:is )?(?:confirmed|booked)|booking (?:is )?confirmed|booking id|boarding|flight|train|departure|check-?in|e-?ticket|journey|bus|cnf|rac|wl\d*|coach|berth)\b""",
    )
    val TRAVEL_DATE_HINT = Regex("""(?i)(doj|date of journey|journey|departure|dep\.?|departs|travel|on)""")
    val FLIGHT = Regex("""(?i)\b(flight|airline|indigo|air india|akasa|spicejet|boarding pass|gate|terminal|pnr [A-Z0-9]{6}\b)""")
    val BUS = Regex("""(?i)\b(bus|redbus|volvo)\b""")

    val INSURANCE = Regex("""(?i)\b(policy|insurance|premium|insured|mediclaim)\b""")
    val INSURANCE_EVENT = Regex("""(?i)\b(expir\w*|renew\w*|due|lapse\w*|valid till|valid upto|issued|active|grace period)\b""")
    val EXPIRY_HINT = Regex("""(?i)(expir\w*|renew\w*|due|lapse\w*|valid (?:till|upto|until)|till|upto|before|on or before)""")
    val VEHICLE_INSURANCE = Regex("""(?i)\b(bike|car|motor|vehicle|two[- ]wheeler|four[- ]wheeler|auto)\b""")
    val HEALTH_INSURANCE = Regex("""(?i)\b(health|mediclaim|medical|hospital)\b""")
    val LIFE_INSURANCE = Regex("""(?i)\b(life|term plan|term insurance|lic)\b""")

    val BILL = Regex(
        """(?i)\b(bill|due date|due on|pay by|payment due|amount due|total due|minimum (?:amount )?due|emi|statement|outstanding|recharge (?:due|expires))\b""",
    )
    val BILL_PAID = Regex("""(?i)\b(payment (?:of .{0,20})?(?:received|successful)|bill (?:is )?paid|thank you for (?:the |your )?payment|paid successfully)\b""")
    val DUE_HINT = Regex("""(?i)(due(?: date| on)?|pay by|before|by|on or before|last date|expires on)""")
    val OVERDUE = Regex("""(?i)\b(overdue|past due|late (?:fee|payment)|disconnect\w*|immediately)\b""")
    val UTILITY = Regex("""(?i)\b(electricity|power|water|gas|broadband|postpaid|prepaid|dth|credit card|landline|fibre|fiber)\b""")

    val SUBSCRIPTION = Regex(
        """(?i)\b(subscription|membership|renew(?:al|ed|s)?|auto-?pay|autopay|mandate|recurring|plan (?:renewed|activated)|will be charged|charged for|billing cycle)\b""",
    )
    val YEARLY = Regex("""(?i)\b(annual|yearly|12 months|1 year|per year|/yr)\b""")
    val QUARTERLY = Regex("""(?i)\b(quarterly|3 months)\b""")
    val RENEWAL_HINT = Regex("""(?i)(renew\w*|next (?:billing|payment|charge)|charged on|due|on)""")

    val VEHICLE = Regex(
        """(?i)\b(puc|pollution under control|service (?:is )?due|service reminder|vehicle service|fastag (?:balance|low|recharge|blacklist)|challan|registration (?:certificate|renewal)|fitness certificate)\b""",
    )
    val VEHICLE_DATE_HINT = Regex("""(?i)(expir\w*|valid\w*|due|till|upto|on)""")

    val GOVERNMENT = Regex(
        """(?i)\b(income tax|itr|tax refund|refund (?:issued|processed|determined)|aadhaar|aadhar|\bpan\b|passport|digilocker|umang|epfo|\bpf\b|gst|voter id|e-?challan|traffic challan|parivahan|ration card)\b""",
    )
}
