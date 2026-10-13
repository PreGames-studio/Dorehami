package ir.dorehami.games.hokm.ui.room

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OnlineServerAddressTest {
    @Test
    fun `updated lobby address becomes the endpoint base for the next connection`() {
        val address = OnlineServerAddress("http://old.example:8080")
        address.update("  https://new.example:9443/hokm/  ")

        assertEquals("https://new.example:9443/hokm", address.validatedBaseUrl())
    }

    @Test
    fun `invalid server addresses are rejected before connecting`() {
        val address = OnlineServerAddress("http://10.0.2.2:8080")
        val invalidAddresses = listOf(
            "",
            "example.com",
            "ftp://example.com",
            "https://",
            "https://example.com:70000",
            "https://user@example.com",
            "https://example.com?room=1",
            "https://example.com/#fragment",
        )

        invalidAddresses.forEach { invalid ->
            address.update(invalid)
            assertNull(address.validatedBaseUrl(), "should reject $invalid")
        }
        assertEquals(
            "آدرس سرور معتبر نیست؛ نشانی کامل با http:// یا https:// و میزبان معتبر وارد کن.",
            OnlineServerAddress.INVALID_ADDRESS_MESSAGE_FA,
        )
    }
}
