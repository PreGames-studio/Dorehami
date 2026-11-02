// SANDBOX-ONLY: the real `GamePhase` lives in PantohubModels.kt next to the @Serializable models,
// which a bare kotlinc cannot parse (no serialization plugin). Same five values, so the pure UI
// helpers and their tests can still be compiled locally. Gradle/CI always uses the real enum.
package ir.dorehami.games.pantohub

enum class GamePhase {
    READY,
    RUNNING,
    PAUSED,
    FINAL_READY,
    FINISHED,
}
