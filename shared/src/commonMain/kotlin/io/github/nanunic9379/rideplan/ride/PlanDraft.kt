package io.github.nanunic9379.rideplan.ride

/**
 * A ride being planned on the map from scratch (#33). The first tap sets the start, each further
 * tap adds the next stop at the end. Every change can be undone, one step at a time.
 *
 * Only points are kept: names come with place search, which is not there yet. The stop kinds
 * follow the import rules: start and finish are stops, the points in between exact pass-throughs.
 */
data class PlanDraft(
    val points: List<LatLon> = emptyList(),
    /** The name typed in the sheet; applied when the ride is saved. */
    val name: String = "",
    /** Earlier versions of [points], last = the one before the current. */
    private val history: List<List<LatLon>> = emptyList(),
) {
    val canUndo: Boolean get() = history.isNotEmpty()

    /** A route needs a start and at least one more stop. */
    val canFinish: Boolean get() = points.size >= 2

    /** Already back at the start. */
    val isRoundTrip: Boolean get() = points.size >= 3 && points.first() == points.last()

    /** "Round trip" makes sense once the ride goes somewhere and does not come back yet. */
    val canRoundTrip: Boolean get() = points.size >= 2 && points.first() != points.last()

    /** A tap on the map: the start, then the next stop. Tapping the last point again does nothing. */
    fun withTap(point: LatLon): PlanDraft = if (points.lastOrNull() == point) this else changed(points + point)

    /** "Use my position": where the rider is becomes the start, before anything already tapped. */
    fun withStartHere(point: LatLon): PlanDraft = if (points.firstOrNull() == point) this else changed(listOf(point) + points)

    /** Adds the start again as the finish. */
    fun withRoundTrip(): PlanDraft = if (!canRoundTrip) this else changed(points + points.first())

    fun withMoved(index: Int, point: LatLon): PlanDraft =
        if (index !in points.indices || points[index] == point) this
        else changed(points.toMutableList().also { it[index] = point })

    fun withReordered(from: Int, to: Int): PlanDraft =
        if (from !in points.indices || to !in points.indices || from == to) this
        else changed(points.toMutableList().also { it.add(to, it.removeAt(from)) })

    /** While planning any point can go, down to an empty map. */
    fun withRemoved(index: Int): PlanDraft =
        if (index !in points.indices) this else changed(points.toMutableList().also { it.removeAt(index) })

    /** Back to the points before the last change (a tap, a drag, "Round trip"...). */
    fun undone(): PlanDraft = if (!canUndo) this else copy(points = history.last(), history = history.dropLast(1))

    fun withName(name: String): PlanDraft = copy(name = name.take(MAX_NAME))

    val stops: List<Stop> get() = Stop.guessKinds(points.map { Stop(null, it) }, towns = false)

    /**
     * The ride to show and route while planning. Its name is applied only by [finished]: typing
     * the name does not route the ride again.
     */
    fun ride(): Ride = Ride(null, emptyList(), stops, Ride.Source.PLANNED)

    /** "Done": [current] (the planned ride, routed or still being routed) with the typed name. */
    fun finished(current: Ride): Ride {
        val clean = name.trim().ifEmpty { null }
        return if (current.name == clean) current else current.copy(name = clean)
    }

    private fun changed(fresh: List<LatLon>) = copy(points = fresh, history = (history + listOf(points)).takeLast(MAX_UNDO))

    companion object {
        const val MAX_UNDO = 50
        const val MAX_NAME = 80
    }
}
