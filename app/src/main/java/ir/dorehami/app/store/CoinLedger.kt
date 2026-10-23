package ir.dorehami.app.store

/** A persistent, offline-owned cosmetic item and its price in Dorehami coins. */
internal data class StoreItem(
    val id: String,
    val title: String,
    val description: String,
    val emoji: String,
    val price: Long,
)

/** The local wallet snapshot. Coin purchases from a server may only replace it after verification. */
internal data class WalletSnapshot(
    val balance: Long = 0,
    val ownedItemIds: Set<String> = emptySet(),
)

/** Storage boundary that keeps the ledger logic free of Android and straightforward to test. */
internal interface CoinStorage {
    fun read(): WalletSnapshot
    fun write(snapshot: WalletSnapshot)
}

internal sealed interface PurchaseResult {
    data class Purchased(val item: StoreItem, val balance: Long) : PurchaseResult
    data class AlreadyOwned(val item: StoreItem) : PurchaseResult
    data class InsufficientCoins(val item: StoreItem, val balance: Long) : PurchaseResult
}

/**
 * Atomic local coin ledger for cosmetic purchases.
 *
 * No network or payment call exists here. [credit] is the future entry point for a verified server
 * receipt; the current demo coin packs deliberately never call it. Item purchases and ownership
 * work entirely offline and never allow a negative or overflowing balance.
 */
internal class CoinLedger(private val storage: CoinStorage) {
    @Synchronized
    fun snapshot(): WalletSnapshot = storage.read().sanitized()

    /** Adds coins only after a verified payment/reward; returns the new balance. */
    @Synchronized
    fun credit(amount: Long): Long {
        require(amount > 0) { "Coin credit must be positive" }
        val current = snapshot()
        require(current.balance <= Long.MAX_VALUE - amount) { "Coin balance would overflow" }
        val updated = current.copy(balance = current.balance + amount)
        storage.write(updated)
        return updated.balance
    }

    /** Purchases a non-consumable item once, fully on-device. */
    @Synchronized
    fun purchase(item: StoreItem): PurchaseResult {
        require(item.price >= 0) { "Item price cannot be negative" }
        val current = snapshot()
        if (item.id in current.ownedItemIds) return PurchaseResult.AlreadyOwned(item)
        if (current.balance < item.price) return PurchaseResult.InsufficientCoins(item, current.balance)
        val updated = current.copy(
            balance = current.balance - item.price,
            ownedItemIds = current.ownedItemIds + item.id,
        )
        storage.write(updated)
        return PurchaseResult.Purchased(item, updated.balance)
    }

    private fun WalletSnapshot.sanitized(): WalletSnapshot = copy(
        balance = balance.coerceAtLeast(0),
        ownedItemIds = ownedItemIds.filter { it.isNotBlank() }.toSet(),
    )
}
