package app.ash.common

import app.ash.logging.Redactor
import app.ash.security.BackupCrypto
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class CommonTest {

    // ---- Money ----

    @Test fun indianGrouping() {
        assertThat(Money.format(214_000)).isEqualTo("₹2,140")
        assertThat(Money.format(12_345_678)).isEqualTo("₹1,23,456.78")
        assertThat(Money.format(1_000_000_000)).isEqualTo("₹1,00,00,000")
        assertThat(Money.format(99)).isEqualTo("₹0.99")
        assertThat(Money.format(-50_000)).isEqualTo("-₹500")
    }

    @Test fun parseInput() {
        assertThat(Money.parseInput("2,140")).isEqualTo(214_000)
        assertThat(Money.parseInput("₹ 99.5")).isEqualTo(9_950)
        assertThat(Money.parseInput("abc")).isNull()
        assertThat(Money.parseInput("-5")).isNull()
        assertThat(Money.toInput(9_950)).isEqualTo("99.50")
        assertThat(Money.toInput(214_000)).isEqualTo("2140")
    }

    // ---- Log redaction ----

    @Test fun redactsPersonalData() {
        val raw = "Rs.1,499.00 debited from A/c 123456789012 PAN ABCDE1234F for MH12AB1234 mail me@x.com https://bank.in/x Aadhaar 1234 5678 9012"
        val out = Redactor.redact(raw)
        assertThat(out).doesNotContain("1,499")
        assertThat(out).doesNotContain("123456789012")
        assertThat(out).doesNotContain("ABCDE1234F")
        assertThat(out).doesNotContain("MH12AB1234")
        assertThat(out).doesNotContain("me@x.com")
        assertThat(out).doesNotContain("bank.in")
        assertThat(out).doesNotContain("9012")
        assertThat(out).contains("₹[amt]")
    }

    @Test fun keepsUsefulLogText() {
        assertThat(Redactor.redact("Parsed com.phonepe.app -> upi:0.95")).isEqualTo("Parsed com.phonepe.app -> upi:0.95")
    }

    // ---- Backup encryption ----

    @Test fun backupRoundTrip() {
        val data = Random(42).nextBytes(3 * 1024 * 1024 + 17)
        val out = ByteArrayOutputStream()
        BackupCrypto.encryptingStream(out, "correct horse".toCharArray(), iterations = 20_000).use { it.write(data) }
        val encrypted = out.toByteArray()
        assertThat(encrypted.size).isGreaterThan(data.size)
        val decrypted = BackupCrypto.decryptingStream(ByteArrayInputStream(encrypted), "correct horse".toCharArray()).use { it.readBytes() }
        assertThat(decrypted).isEqualTo(data)
    }

    @Test fun backupWrongPasswordFails() {
        val out = ByteArrayOutputStream()
        BackupCrypto.encryptingStream(out, "right password".toCharArray(), iterations = 20_000).use { it.write("secret".toByteArray()) }
        assertThrows(Exception::class.java) {
            BackupCrypto.decryptingStream(ByteArrayInputStream(out.toByteArray()), "wrong password".toCharArray()).use { it.readBytes() }
        }
    }

    @Test fun backupTamperingDetected() {
        val out = ByteArrayOutputStream()
        BackupCrypto.encryptingStream(out, "pw-123456".toCharArray(), iterations = 20_000).use { it.write(ByteArray(5000) { 7 }) }
        val bytes = out.toByteArray()
        bytes[bytes.size - 100] = (bytes[bytes.size - 100].toInt() xor 1).toByte()
        assertThrows(Exception::class.java) {
            BackupCrypto.decryptingStream(ByteArrayInputStream(bytes), "pw-123456".toCharArray()).use { it.readBytes() }
        }
    }

    @Test fun notABackup() {
        assertThrows(BackupCrypto.NotABackup::class.java) {
            BackupCrypto.decryptingStream(ByteArrayInputStream("hello world, not a backup".toByteArray()), "x".toCharArray())
        }
    }
}
