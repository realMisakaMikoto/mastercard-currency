package com.vibecoding.mcfx

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The failure labels the WebView layer writes must stay distinguishable, because the
 * UI turns them into very different advice: "your edge session was refused" vs
 * "you appear to be offline" are not the same problem and must not share wording.
 */
class PageFailureClassificationTest {

    private val markers = listOf(
        "PAGE_REFUSED_MARKER" to com.vibecoding.mcfx.net.RateEngine.PAGE_REFUSED_MARKER,
        "PAGE_ERROR_MARKER" to com.vibecoding.mcfx.net.RateEngine.PAGE_ERROR_MARKER,
        "PAGE_TIMEOUT_MARKER" to com.vibecoding.mcfx.net.RateEngine.PAGE_TIMEOUT_MARKER,
    )

    @Test
    fun `markers are distinct and non-empty`() {
        for ((name, value) in markers) {
            assertTrue("$name must not be empty", value.isNotBlank())
        }
        val values = markers.map { it.second }
        assertTrue("markers must be distinguishable", values.toSet().size == values.size)
    }

    @Test
    fun `one marker is not a substring of another`() {
        // Otherwise a substring check would classify every failure as the broader one.
        for (a in markers) {
            for (b in markers) {
                if (a.first == b.first) continue
                assertFalse(
                    "${a.first} must not contain ${b.first}",
                    a.second.contains(b.second),
                )
            }
        }
    }

    @Test
    fun `offline advice never claims the edge blocked the client`() {
        val hint = com.vibecoding.mcfx.net.RateEngine.OFFLINE_HINT
        assertTrue("should mention the network", hint.contains("网络"))
        assertFalse(
            "an offline user must not be told their fingerprint was blocked",
            hint.contains("指纹"),
        )
        assertFalse(hint.contains("Akamai"))
    }

    @Test
    fun `edge-refusal advice is about the session, not the network`() {
        val hint = com.vibecoding.mcfx.net.RateEngine.PAGE_BLOCKED_HINT
        assertTrue(hint.contains("Akamai"))
        assertTrue("should point at diagnostics", hint.contains("诊断"))
    }
}
