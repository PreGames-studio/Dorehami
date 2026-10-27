package ir.dorehami.games.hokm.ui.room

import ir.dorehami.transport.online.socketUrlFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * HK-006 regression: the address typed in the lobby must be the endpoint consumed by the next
 * connection — for HTTP and for the WebSocket — and a bad address must stop before any resource
 * is created. `RoomFlow.online()` resolves [OnlineConnectionPlan] once and uses both URLs from
 * the returned [OnlineConnectionPlan.Endpoint].
 */
class OnlineConnectionPlanTest {

    @Test
    fun `latest lobby address becomes both the http base and the socket host`() {
        val plan = OnlineConnectionPlan("http://old.example:8080")
        plan.update("  https://new.example:9443/hokm/  ")

        val endpoint = plan.resolve()
        assertEquals("https://new.example:9443/hokm", endpoint?.httpBase)
        assertEquals(
            "wss://new.example:9443/hokm/v1/ws/room/AB12?ticket=tok",
            endpoint?.socketUrl("AB12", "tok"),
        )
    }

    @Test
    fun `a second edit replaces the first instead of keeping the remembered address`() {
        val plan = OnlineConnectionPlan("http://old.example:8080")
        plan.update("http://first.example:8081")
        plan.update("https://second.example:9443/base")

        assertEquals("https://second.example:9443/base", plan.resolve()?.httpBase)
        assertEquals(
            "wss://second.example:9443/base/v1/ws/room/ACDE3?ticket=v1.abc.def",
            plan.resolve()?.socketUrl("ACDE3", "v1.abc.def"),
        )
    }

    @Test
    fun `http maps to ws and https to wss while preserving port path code and ticket`() {
        val cases = listOf(
            "http://10.0.2.2:8080" to "ws://10.0.2.2:8080/v1/ws/room/XY34Z?ticket=t2",
            "https://example.test/hokm/" to "wss://example.test/hokm/v1/ws/room/XY34Z?ticket=t2",
            "https://example.test:9443/hokm" to "wss://example.test:9443/hokm/v1/ws/room/XY34Z?ticket=t2",
        )
        cases.forEach { (raw, expected) ->
            val plan = OnlineConnectionPlan("http://unused.example")
            plan.update(raw)
            assertEquals(expected, plan.resolve()?.socketUrl("XY34Z", "t2"), "for $raw")
        }
    }

    @Test
    fun `invalid address has no endpoint at all and a clear persian error`() {
        val plan = OnlineConnectionPlan("http://good.example:8080")
        val invalid = listOf(
            "",
            "example.com",
            "ftp://example.com",
            "https://",
            "https://example.com:70000",
            "https://user@example.com",
            "https://example.com?room=1",
            "https://example.com/#fragment",
        )
        invalid.forEach { bad ->
            plan.update(bad)
            assertNull(plan.resolve(), "must not connect for $bad")
        }
        assertEquals(
            "آدرس سرور معتبر نیست؛ نشانی کامل با http:// یا https:// و میزبان معتبر وارد کن.",
            OnlineConnectionPlan.ERROR_FA,
        )
    }

    @Test
    fun `a bad edit cannot fall back to a previously valid endpoint and a good edit recovers`() {
        val plan = OnlineConnectionPlan("http://good.example:8080")
        assertEquals("http://good.example:8080", plan.resolve()?.httpBase)
        plan.update("not-a-url")
        assertNull(plan.resolve())
        plan.update("https://recovered.example/base")
        assertEquals("https://recovered.example/base", plan.resolve()?.httpBase)
    }

    @Test
    fun `plan uses the same socket formula as the shared transport helper`() {
        val plan = OnlineConnectionPlan("http://unused.example")
        plan.update("https://shared.example:9443/hokm")
        val endpoint = plan.resolve()!!

        assertEquals(
            socketUrlFor(endpoint.httpBase, "AB12", "tok"),
            endpoint.socketUrl("AB12", "tok"),
        )
    }

    @Test
    fun `resolving is deterministic and does not mutate the plan`() {
        val plan = OnlineConnectionPlan("http://unused.example")
        plan.update("  http://stable.example:8080  ")
        val first = plan.resolve()
        assertEquals(first, plan.resolve())
        assertEquals(first?.socketUrl("AB12", "tok"), plan.resolve()?.socketUrl("AB12", "tok"))
    }
}
