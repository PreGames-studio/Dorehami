package ir.dorehami.games.pantohub.ui

import ir.dorehami.games.pantohub.WordBankRules

/**
 * The pure state of the two-step team wizard: how many people play, then one name per seat.
 *
 * This file has no Compose or Android dependency on purpose — the seat maths, the name
 * normalisation and the on-device text format are all unit-tested on the JVM.
 */
internal data class TeamDraft(
    val name: String,
    val players: List<String>,
    val colorArgb: Int,
)

/** Step 1 counts the people, step 2 names them. There is no comma-separated typing any more. */
internal enum class SetupStep { COUNT, PLAYERS }

internal const val MIN_TEAMS = 2
internal const val MAX_TEAMS = 8

/** Shared-preferences file that keeps the last wizard state on this device. */
internal const val SETUP_PREFS = "pantohub-setup"
internal const val MIN_PLAYERS_PER_TEAM = 1
/**
 * Seats per team. Raised from 6 to 12: a real party table turned up with ten people on one side and
 * the wizard simply refused the seventh seat. Twelve keeps even a big family in one team while the
 * chip grid below stays two rows of six.
 */
internal const val MAX_PLAYERS_PER_TEAM = 12

/** 1-based seat label, e.g. «بازیکن ۲». */
internal fun defaultPlayerName(seat: Int): String = "بازیکن ${seat.toPersianDigits()}"

/** Grows or shrinks a team to exactly [perTeam] seats, keeping the names already typed. */
internal fun TeamDraft.withSeatCount(perTeam: Int): TeamDraft {
    val seats = perTeam.coerceIn(MIN_PLAYERS_PER_TEAM, MAX_PLAYERS_PER_TEAM)
    return copy(
        players = List(seats) { index ->
            players.getOrNull(index)?.takeIf { it.isNotBlank() } ?: defaultPlayerName(index + 1)
        },
    )
}

/** Resizes every team at once — the only way the wizard changes the shape of the table. */
internal fun resizeTeams(teams: List<TeamDraft>, perTeam: Int): List<TeamDraft> =
    teams.map { it.withSeatCount(perTeam) }

/** The two teams (and their colours) a first-time setup starts with. */
internal fun defaultTeams(perTeam: Int, colors: List<Int>): List<TeamDraft> = listOf(
    TeamDraft("تیم ۱", emptyList(), colors[0]),
    TeamDraft("تیم ۲", emptyList(), colors[1 % colors.size]),
).map { it.withSeatCount(perTeam) }

/** Names of one team, trimmed, de-duplicated and in roster order. */
internal fun sanitizePlayerNames(players: List<String>): List<String> = players
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .distinctBy(WordBankRules::normalizePersian)

/** First (team, seat) pair left empty, or `null` when every seat is filled. */
internal fun firstBlankSeat(teams: List<TeamDraft>): Pair<Int, Int>? {
    teams.forEachIndexed { teamIndex, team ->
        team.players.forEachIndexed { seatIndex, name ->
            if (name.isBlank()) return teamIndex to seatIndex
        }
    }
    return null
}

/**
 * On-device format: one team per line, tab separated — `name`, `colour`, then one name per seat.
 *
 * Plain text instead of JSON keeps the setup screen free of an `org.json` dependency and lets the
 * codec be tested on the JVM (the old comma-separated blob is simply not read any more).
 */
internal fun encodeTeams(teams: List<TeamDraft>): String = teams.joinToString("\n") { team ->
    val cells = buildList {
        add(team.name.oneLine())
        add(team.colorArgb.toString())
        team.players.forEach { add(it.oneLine()) }
    }
    cells.joinToString("\t")
}

/** Reads [encodeTeams] output; `null` when there is nothing sane to restore. */
internal fun decodeTeams(raw: String?, perTeam: Int): List<TeamDraft>? {
    if (raw.isNullOrBlank()) return null
    val teams = raw.lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val cells = line.split('\t')
            if (cells.size < 2) return@mapNotNull null
            val color = cells[1].toIntOrNull() ?: return@mapNotNull null
            val name = cells[0].ifBlank { "تیم" }
            TeamDraft(name, cells.drop(2).filter { it.isNotBlank() }, color).withSeatCount(perTeam)
        }
        .toList()
    return teams.takeIf { it.size >= MIN_TEAMS }
}

private fun String.oneLine(): String = replace('\t', ' ').replace('\n', ' ').trim()
