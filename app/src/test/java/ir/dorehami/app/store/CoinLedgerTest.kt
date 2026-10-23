package ir.dorehami.app.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CoinLedgerTest {
    private class MemoryStorage(initial: WalletSnapshot = WalletSnapshot()) : CoinStorage {
        var value = initial
        override fun read(): WalletSnapshot = value
        override fun write(snapshot: WalletSnapshot) {
            value = snapshot
        }
    }

    private val item = StoreItem("board", "تخته", "طرح تخته", "♟", 250)

    @Test
    fun verifiedCreditThenOfflinePurchaseDeductsOnceAndPersistsOwnership() {
        val storage = MemoryStorage()
        val ledger = CoinLedger(storage)

        assertEquals(500L, ledger.credit(500))
        val purchased = ledger.purchase(item)
        assertIs<PurchaseResult.Purchased>(purchased)
        assertEquals(250L, purchased.balance)
        assertEquals(WalletSnapshot(250, setOf(item.id)), storage.value)

        assertIs<PurchaseResult.AlreadyOwned>(ledger.purchase(item))
        assertEquals(250L, ledger.snapshot().balance)
    }

    @Test
    fun insufficientBalanceNeverBecomesNegativeAndDoesNotUnlockItem() {
        val storage = MemoryStorage(WalletSnapshot(balance = 100))
        val ledger = CoinLedger(storage)

        val result = ledger.purchase(item)
        assertIs<PurchaseResult.InsufficientCoins>(result)
        assertEquals(100L, result.balance)
        assertEquals(100L, storage.value.balance)
        assertTrue(storage.value.ownedItemIds.isEmpty())
    }

    @Test
    fun invalidCreditAndOverflowAreRejectedWithoutChangingWallet() {
        val storage = MemoryStorage(WalletSnapshot(balance = Long.MAX_VALUE - 1))
        val ledger = CoinLedger(storage)

        assertFailsWith<IllegalArgumentException> { ledger.credit(0) }
        assertFailsWith<IllegalArgumentException> { ledger.credit(2) }
        assertEquals(Long.MAX_VALUE - 1, storage.value.balance)
    }
}
