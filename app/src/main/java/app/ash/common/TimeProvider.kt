package app.ash.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Injectable clock so priority, reminders and parsers are deterministic under test. */
interface TimeProvider {
    fun nowMillis(): Long
    fun zone(): ZoneId

    fun today(): LocalDate = Instant.ofEpochMilli(nowMillis()).atZone(zone()).toLocalDate()
    fun now(): ZonedDateTime = Instant.ofEpochMilli(nowMillis()).atZone(zone())
}

@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

class FixedTimeProvider(
    private val millis: Long,
    private val zoneId: ZoneId = ZoneId.of("Asia/Kolkata"),
) : TimeProvider {
    override fun nowMillis(): Long = millis
    override fun zone(): ZoneId = zoneId

    companion object {
        fun at(date: LocalDate, hour: Int = 12, zone: ZoneId = ZoneId.of("Asia/Kolkata")) =
            FixedTimeProvider(date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(), zone)
    }
}

object Dates {
    const val DAY_MS = 24L * 60 * 60 * 1000

    fun startOfDay(date: LocalDate, zone: ZoneId): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun toLocalDate(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    /** Whole calendar days from [today] until [millis]; negative when in the past. */
    fun daysUntil(millis: Long, today: LocalDate, zone: ZoneId): Long =
        ChronoUnit.DAYS.between(today, toLocalDate(millis, zone))

    /** Default time for a date-only deadline: 9 AM local, so reminders don't fire at midnight. */
    fun morningOf(date: LocalDate, zone: ZoneId): Long = date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
}
