package ir.dorehami.app.store

import android.content.Context
import android.content.SharedPreferences

/** Android persistence adapter for the offline coin ledger. */
internal class CoinWallet(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val ledger = CoinLedger(SharedPreferencesCoinStorage(preferences))

    fun snapshot(): WalletSnapshot = ledger.snapshot()

    fun purchase(item: StoreItem): PurchaseResult = ledger.purchase(item)

    private class SharedPreferencesCoinStorage(
        private val preferences: SharedPreferences,
    ) : CoinStorage {
        override fun read(): WalletSnapshot = WalletSnapshot(
            balance = preferences.getLong(KEY_BALANCE, 0L),
            ownedItemIds = preferences.getStringSet(KEY_OWNED_ITEMS, emptySet())?.toSet().orEmpty(),
        )

        override fun write(snapshot: WalletSnapshot) {
            preferences.edit()
                .putLong(KEY_BALANCE, snapshot.balance)
                .putStringSet(KEY_OWNED_ITEMS, snapshot.ownedItemIds.toSet())
                .apply()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "dorehami-coin-wallet"
        const val KEY_BALANCE = "balance"
        const val KEY_OWNED_ITEMS = "owned-items"
    }
}
