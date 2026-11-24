package io.github.nanunic9379.rideplan.route

/** The areas index, outlines and cells shared by the tests of road data areas (#14). */
internal object AreaFixtures {
    /** The real outline of Geofabrik's Alps extract, as routing_areas.py encodes it (3 decimals). */
    val alpsRing = """ihyAoy\`HvcA~OvXxDjFuCtMuJ[sDp@oClFZ`QdCzTxB|CpAbBvErD~C`KdAbIlAbV`@fDtFt@fIzKt@xQi@dFo@~P}@tR{@|Jq@fEcF~Gg@rEe@vCUvD]`F[fHgBlMyA~DL`IzHdNxD`MvG`RdGdRdPpEnStElGhA|CiDl@aJ^kGD{AGmBIcFdAmHlAmJdIqOv^fSlMzl@nl@laAJvj@_RzdAi}@wGcQpBeZ~AmMgFkXw]sQwMol@mEkAm_@oE}VcO}XuBcT}QgMgHk}@qBmEmIwCkJ}y@wOo~@\ueBgHct@tBug@qEyf@e@gF{GuZqDqXNi\RuLhDcNpAgAza@aBxBiArGkFpIrM|w@fv@hArArFdI`@vC"""

    /** Encodes a ring of lon, lat pairs for a coarse test outline. */
    fun ring(vararg lonLat: Double): String {
        // Same as routing_areas.py: latitude first, 3 decimals.
        val sb = StringBuilder()
        var lastLat = 0L
        var lastLon = 0L
        for (i in lonLat.indices step 2) {
            val lat = kotlin.math.round(lonLat[i + 1] * 1000).toLong()
            val lon = kotlin.math.round(lonLat[i] * 1000).toLong()
            for (d in longArrayOf(lat - lastLat, lon - lastLon)) {
                var v = if (d < 0) (d shl 1).inv() else d shl 1
                while (v >= 0x20) {
                    sb.append(((0x20 or (v and 0x1F).toInt()) + 63).toChar())
                    v = v shr 5
                }
                sb.append((v.toInt() + 63).toChar())
            }
            lastLat = lat
            lastLon = lon
        }
        return sb.toString()
    }

    fun json(s: String) = s.replace("\\", "\\\\")

    val italyOuter = ring(6.6, 44.0, 7.0, 46.0, 10.0, 45.9, 11.0, 47.0, 13.0, 47.1, 18.5, 40.0, 15.5, 37.0, 12.0, 37.5, 12.0, 43.0)
    val italyHole = ring(12.4, 43.9, 12.5, 43.9, 12.5, 44.0, 12.4, 44.0)

    val index = """
        {"format": 1, "areas": [
          {"id": "it", "name": "Italy", "release": "routing-data", "bbox": [6.59, 35.25, 18.82, 47.13],
           "outline": {"precision": 3, "rings": ["${json(italyOuter)}", "${json(italyHole)}"]}},
          {"id": "alps", "name": "Alps", "release": "routing-alps", "bbox": [4.842, 42.699, 16.762, 48.401],
           "outline": {"precision": 3, "rings": ["${json(alpsRing)}"]}}
        ]}
    """.trimIndent()

    val areas = RoutingAreas.parse(index)
    val italy = areas.first { it.id == "it" }
    val alps = areas.first { it.id == "alps" }

    fun cells(build: String, vararg lonLat: Pair<Int, Int>, bytes: Long = 10) = RoutingCells(
        build = build,
        osmDate = "d",
        valhalla = "3.9.2",
        packs = lonLat.map { (lon, lat) ->
            val id = (lat + 90) * 360 + (lon + 180)
            RoutingPack("l1-$id", 1, listOf(lon.toDouble(), lat.toDouble(), lon + 1.0, lat + 1.0), "l1-$id-$build.zip", bytes, bytes * 2, "ff")
        },
    )

    /** Italy's cells: the north (including border cells reaching into Switzerland) and Rome. */
    val italyCells = cells("it1", 6 to 45, 7 to 45, 8 to 45, 9 to 45, 10 to 45, 11 to 45, 11 to 46, 8 to 46, 9 to 46, 12 to 41, 7 to 43, bytes = 10)
    val alpsCells = cells("al1", 6 to 46, 7 to 43, 11 to 46, 11 to 47, 8 to 45, 9 to 45, 8 to 47, 8 to 46, bytes = 20)
}
