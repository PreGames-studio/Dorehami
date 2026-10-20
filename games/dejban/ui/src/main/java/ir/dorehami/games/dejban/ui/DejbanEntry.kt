package ir.dorehami.games.dejban.ui

import android.content.Context
import android.content.Intent

/** Entry point used by the Dorehami app shell to open Dejban. */
object DejbanEntry {

    fun createIntent(context: Context): Intent =
        Intent(context, DejbanActivity::class.java)

    fun launch(context: Context) {
        context.startActivity(createIntent(context))
    }
}
