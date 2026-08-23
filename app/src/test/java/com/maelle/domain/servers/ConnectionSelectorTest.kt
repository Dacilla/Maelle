package com.maelle.domain.servers

import com.maelle.domain.servers.model.PlexConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionSelectorTest {

    private val selector = ConnectionSelector()

    private fun connection(uri: String, local: Boolean) = PlexConnection(
        protocol = "http",
        address = "10.0.0.9",
        port = 32400,
        uri = uri,
        local = local,
    )

    @Test
    fun `returns null when no connections exist`() {
        assertNull(selector.chooseBest(emptyList(), emptyMap()))
    }

    @Test
    fun `returns null when every connection is unreachable`() {
        val connections = listOf(connection("http://a", true), connection("http://b", false))
        val latencies = mapOf("http://a" to -1, "http://b" to -1)

        assertNull(selector.chooseBest(connections, latencies))
    }

    @Test
    fun `ignores connections without a measured latency`() {
        val measured = connection("http://measured", false)
        val unmeasured = connection("http://unmeasured", true)

        assertEquals(measured, selector.chooseBest(listOf(unmeasured, measured), mapOf("http://measured" to 50)))
    }

    @Test
    fun `prefers the fastest connection`() {
        val slow = connection("http://slow", true)
        val fast = connection("http://fast", false)

        val best = selector.chooseBest(
            listOf(slow, fast),
            mapOf("http://slow" to 300, "http://fast" to 20),
        )

        assertEquals(fast, best)
    }

    @Test
    fun `prefers local connections on latency ties`() {
        val remote = connection("http://remote", false)
        val local = connection("http://local", true)

        val best = selector.chooseBest(
            listOf(remote, local),
            mapOf("http://remote" to 100, "http://local" to 100),
        )

        assertEquals(local, best)
    }
}
