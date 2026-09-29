package app.aquawiznotifier

import java.time.Instant

data class Session(
    val username: String,
    val password: String,
    val accessToken: String,
    val devices: List<String>,
)

data class Measurement(
    val kh: Double,
    val measuredAt: Instant,
    val rawId: String? = null,
    val ph: Double? = null,
    val phOpenAir: Double? = null,
    val deltaPh: Double? = null,
    val doseMl: Double? = null,
) {
    val fingerprint: String get() = rawId ?: "${measuredAt.toEpochMilli()}:${"%.4f".format(kh)}"
}
