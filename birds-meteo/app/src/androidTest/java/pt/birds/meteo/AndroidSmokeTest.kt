package pt.birds.meteo

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// These tests exercise actual Android classes and Compose, not just the JVM core.
// Animation clocks are frozen during UI assertions to avoid infinite animation waits.
@RunWith(AndroidJUnit4::class)
class AndroidSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun launchAndNavigate() {
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText("BIRDS METEO", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Locais", useUnmergedTree = true).performClick()
        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithText("Localidades", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Vale de Touros", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Opções", useUnmergedTree = true).performClick()
        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithText("Atualizar todas", useUnmergedTree = true).assertExists()
    }

    @Test fun actualAndroidJsonPreservesNullAndZero() {
        val json = JSONObject("""{"zero":0,"missing":null,"values":[0,null,2],"time":["2026-11-26T10:00","2026-11-26T11:00","2026-11-26T12:00"]}""")
        assertEquals(0.0, json.number("zero")!!, 0.0)
        assertNull(json.number("missing"))
        assertNull(json.number("absent"))
        assertEquals(0.0, json.at("values",0)!!, 0.0)
        assertNull(json.at("values",1))
        assertNull(json.at("values",9))
        assertEquals(3, json.times().size)
        assertNull(Packet("not json",0).obj())
    }

    @Test fun originalLocationAssetsAndRepository() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val places = Repo(context).places()
        assertEquals(listOf("palmela","carnaxide","oriente","centro","restelo","castro_verde","almeida","porto","setubal"), places.map { it.id })
        assertEquals(9, places.map { it.latitude to it.longitude }.distinct().size)
        assertEquals("STB", places.first().district)
    }
}
