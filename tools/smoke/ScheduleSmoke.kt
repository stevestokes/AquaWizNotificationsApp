import app.aquawiznotifier.PollCadence
import java.time.Instant

fun main() {
    check(PollCadence.probeOffsetsMinutes(60) == listOf(17L, 32L, 47L, 62L))
    val t = Instant.parse("2026-09-29T12:00:00Z")
    check(PollCadence.nextRun(t.plusSeconds(48 * 60), t, 60) == t.plusSeconds(62 * 60))
    check(PollCadence.nextRun(t.plusSeconds(63 * 60), t, 60) == t.plusSeconds(77 * 60))
    println("PollCadence smoke test passed: ${PollCadence.probeOffsetsMinutes(60)}")
}
