package pt.birds.meteo

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real network integration checks; no mocked responses and no cached success. */
@RunWith(AndroidJUnit4::class)
class LiveSourcesTest {
    @Test fun forecastAndAirForNineLocations() = runBlocking {
        val repo = Repo(InstrumentationRegistry.getInstrumentation().targetContext)
        val places = repo.places()
        assertEquals(9, places.size)
        val results = places.map { place -> async { place.name to repo.refresh(place,true) } }.awaitAll()
        val failures = mutableListOf<String>()
        for ((name, data) in results) {
            val weather = data.weather
            val air = data.air
            val obj = weather?.obj()
            if (weather == null || weather.error != null || obj?.optJSONObject("current").number("temperature_2m") == null || obj?.optJSONObject("hourly").times().isEmpty()) failures.add("$name forecast: ${weather?.error ?: "invalid data"}")
            if (air == null || air.error != null || air.obj()?.optJSONObject("hourly").times().isEmpty()) failures.add("$name air: ${air?.error ?: "invalid data"}")
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun sharedLiveSources() = runBlocking {
        val repo = Repo(InstrumentationRegistry.getInstrumentation().targetContext)
        val checks = listOf(
            async { "IPMA avisos" to repo.warnings(true) },
            async { "IPMA estações" to repo.observations(true) },
            async { "IPMA sismos" to repo.seismic(true) },
            async { "IPMA risco hoje" to repo.fire(0,true) },
            async { "RainViewer" to repo.radar(true) },
            async { "NOAA Kp" to repo.geomagnetic(true) }
        ).awaitAll()
        val failures = checks.filter { (_,packet) -> packet.error != null || packet.saved <= 0 || packet.raw.isBlank() }
        assertTrue(failures.joinToString("\n") { (name,p) -> "$name: ${p.error ?: "invalid data"}" }, failures.isEmpty())
    }
}
