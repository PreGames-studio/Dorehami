package ir.dorehami.games.esmfamil.ui

import ir.dorehami.session.ClientState
import ir.dorehami.session.Member

/**
 * Turns the shared session state into the sheet screen: no Compose, no Android, so the mapping from
 * `ClientState` to what a player sees is unit-tested on the JVM like the rules.
 */
object EsmFamilRoomModel {

    fun state(clientState: ClientState, modeLabel: String): EsmFamilUiState {
        val public = EsmFamilUiModel.public(clientState.publicView)
        val members: List<Member> = clientState.room?.members?.sortedBy { it.seat }.orEmpty()
        val humans = members.filter { !it.isBot }
        val me = clientState.me.value
        val seats = if (humans.isEmpty()) {
            listOf(EsmFamilSeat(id = me, name = "من", isMe = true))
        } else {
            humans.map { member ->
                EsmFamilSeat(
                    id = member.player.id.value,
                    name = member.player.displayName,
                    isMe = member.player.id.value == me,
                )
            }
        }
        val mySeatIndex = seats.indexOfFirst { it.isMe }.coerceAtLeast(0)
        val mySubmitted = me in public.submitted
        val standings = public.standings.map { (id, pair) ->
            EsmFamilStanding(
                name = seats.firstOrNull { it.id == id }?.name ?: id,
                rank = pair.first,
                score = pair.second,
                isMe = id == me,
            )
        }
        val waiting = humans.count { it.player.id.value !in public.submitted }
        return EsmFamilUiState(
            round = public.round,
            rounds = public.rounds,
            letter = public.letter,
            categories = public.categories,
            phase = public.phase,
            seats = seats,
            writingSeat = mySeatIndex,
            draft = emptyMap(),
            mine = EsmFamilUiModel.mine(clientState.privateView),
            submittedIds = public.submitted,
            rows = EsmFamilUiModel.rows(public, seats),
            totals = public.totals,
            standings = standings,
            modeLabel = modeLabel,
            isHost = clientState.isHost,
            error = null,
            waitingForOthers = waiting,
        )
    }

    /** True when every human at the table has handed a sheet in and the round can be opened early. */
    fun allHumansSubmitted(clientState: ClientState): Boolean {
        val public = EsmFamilUiModel.public(clientState.publicView)
        if (public.phase != EsmFamilPhase.WRITING) return false
        val humans = clientState.room?.members?.filter { !it.isBot && it.connected }.orEmpty()
        if (humans.isEmpty()) return false
        return humans.all { it.player.id.value in public.submitted }
    }
}
