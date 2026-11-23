package io.github.nanunic9379.rideplan.profile

import io.github.nanunic9379.rideplan.ride.LatLon
import io.github.nanunic9379.rideplan.ride.Ride
import io.github.nanunic9379.rideplan.ride.Stop
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Chi usa l'app su questo telefono (#41). Niente account: tutto resta qui.
 * [id]: casuale, creato al primo avvio; servirà per riconoscersi quando i giri
 * si scambieranno tra persone (#7) o se arriverà un archivio centrale.
 */
@Serializable
data class Profile(
    val id: String,
    val name: String = "",
    val bike: String = "",
    /** Il benvenuto si mostra una volta sola, anche se lo salti. */
    val welcomed: Boolean = false,
    val createdAt: Long,
)

/** Una tappa salvata: [kind] è il nome di [Stop.Kind], così il file resta leggibile. */
@Serializable
data class SavedStop(val name: String?, val lat: Double, val lon: Double, val kind: String = Stop.Kind.STOP.name)

/**
 * Un giro nell'elenco "I tuoi giri".
 * [track]: la traccia di un GPX, lat e lon alternate. Vuota se il percorso lo calcola
 * RidePlan: si ricalcola dalle tappe quando si riapre.
 * [ridden]: quando l'hai fatto (millisecondi), una volta per ogni uscita.
 */
@Serializable
data class SavedRide(
    val id: String,
    val name: String?,
    val savedAt: Long,
    val km: Double,
    val seconds: Long? = null,
    val source: String,
    val stops: List<SavedStop>,
    val track: List<Double> = emptyList(),
    val ridden: List<Long> = emptyList(),
) {
    fun toRide(): Ride = Ride(
        name = name,
        track = track.chunked(2).filter { it.size == 2 }.map { LatLon(it[0], it[1]) },
        stops = stops.map { s -> Stop(s.name, LatLon(s.lat, s.lon), Stop.Kind.entries.firstOrNull { it.name == s.kind } ?: Stop.Kind.STOP) },
        source = Ride.Source.entries.firstOrNull { it.name == source } ?: Ride.Source.GPX,
    )

    companion object {
        fun from(ride: Ride, id: String, now: Long, ridden: List<Long> = emptyList()): SavedRide = SavedRide(
            id = id,
            name = ride.name,
            savedAt = now,
            km = ride.lengthKm,
            seconds = ride.route?.seconds?.toLong(),
            source = ride.source.name,
            stops = ride.stops.map { SavedStop(it.name, it.point.lat, it.point.lon, it.kind.name) },
            // Il percorso calcolato da RidePlan si rifà dalle tappe: il file resta piccolo.
            track = if (ride.route != null) emptyList() else ride.track.flatMap { listOf(it.lat, it.lon) },
            ridden = ridden,
        )
    }
}

/** Tutto quello che l'app ricorda di te. [version]: per cambiare il formato in futuro. */
@Serializable
data class ProfileData(
    val version: Int = 1,
    val profile: Profile,
    val rides: List<SavedRide> = emptyList(),
) {
    val stats: Stats get() = Stats(
        saved = rides.size,
        ridden = rides.sumOf { it.ridden.size },
        kmRidden = rides.sumOf { it.km * it.ridden.size },
        longestKm = rides.filter { it.ridden.isNotEmpty() }.maxOfOrNull { it.km },
        lastRidden = rides.flatMap { it.ridden }.maxOrNull(),
    )

    /** Il giro salvato (nuovo o aggiornato), in cima all'elenco. */
    fun save(ride: Ride, id: String?, now: Long): Pair<ProfileData, String> {
        val old = rides.firstOrNull { it.id == id }
        val newId = old?.id ?: newId()
        val saved = SavedRide.from(ride, newId, now, old?.ridden.orEmpty())
        return copy(rides = listOf(saved) + rides.filter { it.id != newId }) to newId
    }

    fun markRidden(id: String, now: Long): ProfileData =
        copy(rides = rides.map { if (it.id == id) it.copy(ridden = it.ridden + now) else it })

    /** Toglie l'ultima uscita segnata, per chi ha premuto per sbaglio. */
    fun undoRidden(id: String): ProfileData =
        copy(rides = rides.map { if (it.id == id) it.copy(ridden = it.ridden.dropLast(1)) else it })

    fun rename(id: String, name: String): ProfileData =
        copy(rides = rides.map { if (it.id == id) it.copy(name = name.trim().ifEmpty { null }) else it })

    fun delete(id: String): ProfileData = copy(rides = rides.filter { it.id != id })

    /**
     * Ripristino da una copia (#43): si unisce, non si sostituisce, così non si perde
     * niente di quello che hai già su questo telefono. Lo stesso giro (stesso id) non si
     * duplica: vince la versione salvata per ultima e le uscite si sommano senza doppioni.
     * Nome e moto della copia servono solo se qui sono ancora vuoti.
     * Restituisce anche quanti giri sono arrivati di nuovo.
     */
    fun merge(other: ProfileData): Pair<ProfileData, Int> {
        val merged = rides.associateBy { it.id }.toMutableMap()
        var added = 0
        for (r in other.rides) {
            val old = merged[r.id]
            if (old == null) added++
            val newest = if (old == null || r.savedAt > old.savedAt) r else old
            merged[r.id] = newest.copy(ridden = (old?.ridden.orEmpty() + r.ridden).distinct().sorted())
        }
        val p = profile.copy(
            name = profile.name.ifBlank { other.profile.name },
            bike = profile.bike.ifBlank { other.profile.bike },
            welcomed = true,
            createdAt = minOf(profile.createdAt, other.profile.createdAt),
        )
        return copy(profile = p, rides = merged.values.sortedByDescending { it.savedAt }) to added
    }

    companion object {
        fun fresh(now: Long) = ProfileData(profile = Profile(id = newId(), createdAt = now))

        @OptIn(ExperimentalStdlibApi::class)
        fun newId(): String = Random.nextBytes(16).toHexString()
    }
}

data class Stats(
    val saved: Int,
    val ridden: Int,
    val kmRidden: Double,
    val longestKm: Double?,
    val lastRidden: Long?,
)
