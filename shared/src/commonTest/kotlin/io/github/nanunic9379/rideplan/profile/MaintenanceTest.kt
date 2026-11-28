package io.github.nanunic9379.rideplan.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MaintenanceTest {
    @Test
    fun drivetrainAndCoolingOnlyShowApplicableItems() {
        val chain = defaultMaintenanceRules(FinalDrive.CHAIN, Cooling.LIQUID).map { it.task }.toSet()
        assertTrue(MaintenanceTask.CHAIN_LUBE in chain)
        assertTrue(MaintenanceTask.COOLANT in chain)
        assertFalse(MaintenanceTask.SHAFT_OIL in chain)
        assertFalse(MaintenanceTask.BELT_CHECK in chain)

        val shaft = defaultMaintenanceRules(FinalDrive.SHAFT, Cooling.AIR).map { it.task }.toSet()
        assertTrue(MaintenanceTask.SHAFT_OIL in shaft)
        assertFalse(MaintenanceTask.CHAIN_LUBE in shaft)
        assertFalse(MaintenanceTask.COOLANT in shaft)

        val electric = defaultMaintenanceRules(FinalDrive.ELECTRIC, Cooling.ELECTRIC).map { it.task }.toSet()
        assertFalse(MaintenanceTask.ENGINE_OIL in electric)
        assertFalse(MaintenanceTask.COOLANT in electric)
        assertTrue(MaintenanceTask.BRAKE_FLUID in electric)
    }

    @Test
    fun mileageAndCalendarIntervalsBecomeDue() {
        val mileage = MaintenanceRule(MaintenanceTask.CHAIN_LUBE, everyKm = 800, lastKm = 1200, lastAt = 1_000L)
        assertFalse(mileage.isDue(1999, 1_000L))
        assertTrue(mileage.isDue(2000, 1_000L))

        val timed = MaintenanceRule(MaintenanceTask.BRAKE_FLUID, everyMonths = 24, lastKm = 500, lastAt = 0L)
        assertFalse(timed.isDue(500, 730L * 86_400_000L))
        assertTrue(timed.isDue(500, 731L * 86_400_000L))
        assertFalse(MaintenanceRule(MaintenanceTask.CHAIN_LUBE).isDue(99_999, 99_999L))
    }

    @Test
    fun maintenanceAndOdometerSurviveProfileBackupRoundTrip() {
        val bike = Bike(
            id = "bike-1", name = "Moto", finalDrive = FinalDrive.SHAFT, cooling = Cooling.LIQUID,
            odometerKm = 42_000,
            maintenance = defaultMaintenanceRules(FinalDrive.SHAFT, Cooling.LIQUID).map {
                if (it.task == MaintenanceTask.SHAFT_OIL) it.copy(lastKm = 40_000, lastAt = 2_000L) else it
            },
            maintenanceLog = listOf(MaintenanceRecord("entry-1", MaintenanceTask.SHAFT_OIL, 40_000, 2_000L, "gear oil")),
        )
        val profile = ProfileData.fresh(0).saveBike(bike, default = true)
        val restored = ProfileStore.decode(ProfileStore.encode(profile)).defaultBike!!
        assertEquals(42_000, restored.odometerKm)
        assertEquals(FinalDrive.SHAFT, restored.finalDrive)
        assertEquals(20_000, restored.maintenance.single { it.task == MaintenanceTask.SHAFT_OIL }.everyKm)
        assertEquals(1, restored.maintenanceLog.size)
    }
}
