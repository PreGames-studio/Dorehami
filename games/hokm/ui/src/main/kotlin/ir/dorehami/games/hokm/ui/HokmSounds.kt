package ir.dorehami.games.hokm.ui

/** Sound names used by the Hokm table → bundled raw resources (all synthesized in-house, no licences). */
object HokmSounds {
    val map: Map<String, Int> = mapOf(
        "card_play" to R.raw.hokm_card_play,
        "card_deal" to R.raw.hokm_card_deal,
        "shuffle" to R.raw.hokm_shuffle,
        "trick_won" to R.raw.hokm_trick_won,
        "trick_lost" to R.raw.hokm_trick_lost,
        "hokm_chosen" to R.raw.hokm_hokm_chosen,
        "hand_won" to R.raw.hokm_hand_won,
        "hand_lost" to R.raw.hokm_hand_lost,
        "kot" to R.raw.hokm_kot,
        "game_won" to R.raw.hokm_game_won,
        "game_lost" to R.raw.hokm_game_lost,
        "tap" to R.raw.hokm_tap,
        "your_turn" to R.raw.hokm_your_turn,
        "sticker" to R.raw.hokm_sticker,
        "card_cut" to R.raw.hokm_card_cut,
        "card_slide" to R.raw.hokm_card_slide,
        "timer_tick" to R.raw.hokm_timer_tick,
        "timer_warn" to R.raw.hokm_timer_warn,
        "whoosh" to R.raw.hokm_whoosh,
    )
}
