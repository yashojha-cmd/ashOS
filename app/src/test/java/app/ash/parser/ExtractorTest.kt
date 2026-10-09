package app.ash.parser

import app.ash.data.parser.ParseContext
import app.ash.data.parser.extract.AmountExtractor
import app.ash.data.parser.extract.DateExtractor
import app.ash.data.parser.extract.MerchantExtractor
import app.ash.data.parser.extract.ReferenceExtractor
import app.ash.domain.model.TxnDirection
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ExtractorTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 10)
    private val ctx = ParseContext(today, zone, today.atStartOfDay(zone).toInstant().toEpochMilli())

    @Test fun amountFormats() {
        assertThat(AmountExtractor.primary("₹1,499 paid")).isEqualTo(149_900)
        assertThat(AmountExtractor.primary("Rs.1499.50 debited")).isEqualTo(149_950)
        assertThat(AmountExtractor.primary("INR 1,23,456.78 spent")).isEqualTo(12_345_678)
        assertThat(AmountExtractor.primary("Rs 25000/- received")).isEqualTo(2_500_000)
        assertThat(AmountExtractor.primary("Amount 999/- paid")).isEqualTo(99_900)
        assertThat(AmountExtractor.primary("no money here")).isNull()
    }

    @Test fun balanceIsNotThePrimaryAmount() {
        val text = "Avl Bal Rs 50,000.00. Rs 200 debited"
        assertThat(AmountExtractor.primary(text)).isEqualTo(20_000)
        assertThat(AmountExtractor.all(text).first().isBalance).isTrue()
    }

    @Test fun datesWithAndWithoutYear() {
        assertThat(DateExtractor.first("expires on 21 October", ctx)).isEqualTo(LocalDate.of(2026, 10, 21))
        assertThat(DateExtractor.first("due 5 Jan", ctx)).isEqualTo(LocalDate.of(2027, 1, 5))
        assertThat(DateExtractor.first("on 21/10/26", ctx)).isEqualTo(LocalDate.of(2026, 10, 21))
        assertThat(DateExtractor.first("on 2026-10-21", ctx)).isEqualTo(LocalDate.of(2026, 10, 21))
        assertThat(DateExtractor.first("Oct 21, 2026", ctx)).isEqualTo(LocalDate.of(2026, 10, 21))
        assertThat(DateExtractor.first("21-OCT-26", ctx)).isEqualTo(LocalDate.of(2026, 10, 21))
        assertThat(DateExtractor.first("21st October 2027", ctx)).isEqualTo(LocalDate.of(2027, 10, 21))
    }

    @Test fun relativeDates() {
        assertThat(DateExtractor.first("arriving tomorrow", ctx)).isEqualTo(today.plusDays(1))
        assertThat(DateExtractor.first("delivered today", ctx)).isEqualTo(today)
        assertThat(DateExtractor.first("in 3 days", ctx)).isEqualTo(today.plusDays(3))
        assertThat(DateExtractor.first("pay by Friday", ctx)).isEqualTo(LocalDate.of(2026, 10, 16))
        assertThat(DateExtractor.first("aaj delivery hogi", ctx)).isEqualTo(today)
    }

    @Test fun pastBiasForTransactions() {
        assertThat(DateExtractor.first("on 25 Dec", ctx, preferFuture = false)).isEqualTo(LocalDate.of(2025, 12, 25))
    }

    @Test fun invalidDatesIgnored() {
        assertThat(DateExtractor.first("31/02/2026", ctx)).isNull()
        assertThat(DateExtractor.first("call 1800-123-4567", ctx)).isNull()
    }

    @Test fun dateNearKeyword() {
        val text = "Bill dated 01-10-2026 is due on 15-10-2026"
        assertThat(DateExtractor.near(text, ctx, Regex("(?i)due"))).isEqualTo(LocalDate.of(2026, 10, 15))
    }

    @Test fun references() {
        assertThat(ReferenceExtractor.pnr("PNR: 4512345678 CNF")).isEqualTo("4512345678")
        assertThat(ReferenceExtractor.policyNumber("Policy No: ACK-1234567")).isEqualTo("ACK-1234567")
        assertThat(ReferenceExtractor.orderId("Order ID OD123456789012 shipped")).isEqualTo("OD123456789012")
        assertThat(ReferenceExtractor.account("debited from A/c XX4321 on")).isEqualTo("XX4321")
        assertThat(ReferenceExtractor.account("Card ending 9876")).isEqualTo("XX9876")
        assertThat(ReferenceExtractor.vehicleReg("for MH 12 AB 1234")).isEqualTo("MH12AB1234")
        assertThat(ReferenceExtractor.vehicleReg("bike KA01MN4567")).isEqualTo("KA01MN4567")
        assertThat(ReferenceExtractor.vehicleReg("22BH1234AA")).isEqualTo("22BH1234AA")
        assertThat(ReferenceExtractor.vehicleReg("ZZ12AB1234")).isNull()
        assertThat(ReferenceExtractor.upiRef("UPI Ref No 628312345678")).isEqualTo("628312345678")
    }

    @Test fun merchants() {
        assertThat(MerchantExtractor.extract("Rs 100 debited to VPA zomato@hdfc", TxnDirection.EXPENSE)).isEqualTo("Zomato")
        assertThat(MerchantExtractor.extract("You paid ₹250 to Raju Kirana", TxnDirection.EXPENSE)).isEqualTo("Raju Kirana")
        assertThat(MerchantExtractor.extract("Received ₹500 from Amit Sharma", TxnDirection.INCOME)).isEqualTo("Amit Sharma")
        assertThat(MerchantExtractor.extract("spent at SHREE GANESH STORES on 05-10", TxnDirection.EXPENSE)).isEqualTo("Shree Ganesh Stores")
        assertThat(MerchantExtractor.clean("your a/c")).isNull()
    }
}
