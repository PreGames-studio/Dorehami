package ir.dorehami.games.pantohub

/** Aggregates scores and outcomes from saved match and turn events. */
object GameStatistics {
    fun teams(games: Iterable<GameState>): List<TeamStatistics> {
        val accumulators = linkedMapOf<String, MutableTeamStats>()
        games.filter { it.phase == GamePhase.FINISHED }.forEach { game ->
            game.teams.forEach { team ->
                val key = WordBankRules.normalizePersian(team.name)
                val aggregate = accumulators.getOrPut(key) { MutableTeamStats(team.name) }
                aggregate.gamesPlayed++
                aggregate.totalPoints += game.teamScores[team.id] ?: 0
                when {
                    game.winnerTeamIds.size > 1 && team.id in game.winnerTeamIds -> aggregate.draws++
                    game.winnerTeamIds.size == 1 && team.id in game.winnerTeamIds -> aggregate.wins++
                    team.id !in game.winnerTeamIds -> aggregate.losses++
                }
                val teamTurns = game.turns.filter { it.teamId == team.id }
                aggregate.turnsPlayed += teamTurns.size
                teamTurns.flatMap { it.attempts }
                    .filter { it.outcome == WordOutcome.CORRECT }
                    .forEach { attempt ->
                        aggregate.correctAnswers++
                        when (attempt.difficulty) {
                            Difficulty.EASY -> aggregate.easyCorrect++
                            Difficulty.MEDIUM -> aggregate.mediumCorrect++
                            Difficulty.HARD -> aggregate.hardCorrect++
                        }
                    }
            }
        }
        return accumulators.values
            .map(MutableTeamStats::toImmutable)
            .sortedWith(compareByDescending<TeamStatistics> { it.wins }.thenByDescending { it.totalPoints }.thenBy { it.name })
    }

    fun players(games: Iterable<GameState>): List<PlayerStatistics> {
        val accumulators = linkedMapOf<String, MutablePlayerStats>()
        games.filter { it.phase == GamePhase.FINISHED }.forEach { game ->
            if (game.settings.individualStatisticsEnabled) {
                game.teams.flatMap { it.players }.forEach { player ->
                    val aggregate = accumulators.getOrPut(player.id) { MutablePlayerStats(player.id, player.name) }
                    aggregate.gameIds += game.id
                }
            }
            game.turns.forEach turnLoop@{ turn ->
                val playerId = turn.playerId ?: return@turnLoop
                val aggregate = accumulators.getOrPut(playerId) { MutablePlayerStats(playerId, turn.playerName) }
                aggregate.gameIds += game.id
                aggregate.turnsPerformed++
                aggregate.pointsEarned += turn.scoreEarned
                if (turn.attempts.any { it.outcome == WordOutcome.CORRECT }) aggregate.successfulTurns++
                turn.attempts.forEach { attempt ->
                    aggregate.wordsUsed++
                    if (attempt.outcome == WordOutcome.CORRECT) aggregate.correctAnswers++
                }
            }
        }
        return accumulators.values
            .map(MutablePlayerStats::toImmutable)
            .sortedWith(compareByDescending<PlayerStatistics> { it.pointsEarned }.thenBy { it.name })
    }

    private class MutableTeamStats(val name: String) {
        var gamesPlayed = 0
        var wins = 0
        var losses = 0
        var draws = 0
        var totalPoints = 0
        var correctAnswers = 0
        var turnsPlayed = 0
        var easyCorrect = 0
        var mediumCorrect = 0
        var hardCorrect = 0

        fun toImmutable() = TeamStatistics(
            name = name,
            gamesPlayed = gamesPlayed,
            wins = wins,
            losses = losses,
            draws = draws,
            totalPoints = totalPoints,
            correctAnswers = correctAnswers,
            turnsPlayed = turnsPlayed,
            easyCorrect = easyCorrect,
            mediumCorrect = mediumCorrect,
            hardCorrect = hardCorrect,
        )
    }

    private class MutablePlayerStats(val id: String, val name: String) {
        val gameIds = mutableSetOf<String>()
        var turnsPerformed = 0
        var correctAnswers = 0
        var wordsUsed = 0
        var successfulTurns = 0
        var pointsEarned = 0

        fun toImmutable() = PlayerStatistics(
            playerId = id,
            name = name,
            gamesPlayed = gameIds.size,
            turnsPerformed = turnsPerformed,
            correctAnswers = correctAnswers,
            wordsUsed = wordsUsed,
            successfulTurns = successfulTurns,
            pointsEarned = pointsEarned,
        )
    }
}
