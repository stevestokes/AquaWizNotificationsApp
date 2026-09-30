package app.aquawiznotifier

object HistoryMerge {
    fun merge(items: List<Pair<String, Measurement>>): List<Pair<String, Measurement>> {
        val merged = linkedMapOf<String, Pair<String, Measurement>>()
        items.forEach { (serial, measurement) ->
            val key = serial.uppercase() + "|" + measurement.measuredAt.toEpochMilli()
            val previous = merged[key]?.second
            merged[key] = serial to if (previous == null) measurement else previous.copy(
                ph = previous.ph ?: measurement.ph,
                phOpenAir = previous.phOpenAir ?: measurement.phOpenAir,
                deltaPh = previous.deltaPh ?: measurement.deltaPh,
                doseMl = previous.doseMl ?: measurement.doseMl,
            )
        }
        return merged.values.sortedByDescending { it.second.measuredAt }.take(2000)
    }
}
