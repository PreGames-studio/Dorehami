package ir.dorehami.games.dejban.ui

import org.godotengine.godot.Godot
import org.godotengine.godot.GodotActivity

/**
 * Hosts the embedded Godot 4.3 runtime for Dejban.
 *
 * The game itself remains a GDScript project in `games/dejban/godot`; CI
 * exports it to `dezhban.pck`, which this Activity asks Godot to load from
 * the library assets. The Activity runs in the `:dejban` process (declared in
 * the manifest) so a native Godot crash or shutdown cannot take down the
 * Kotlin/Compose host process.
 */
class DejbanActivity : GodotActivity() {

    override fun getCommandLine(): List<String> =
        listOf("--main-pack", MAIN_PACK)

    /**
     * Dejban's menus call `get_tree().quit()`. In an embedded game that
     * should mean "leave this game", not GodotActivity's default behaviour
     * of force-quitting the whole application process. Finishing only this
     * Activity returns the user to Dorehami's game picker.
     */
    override fun onGodotForceQuit(instance: Godot) {
        runOnUiThread { finish() }
    }

    private companion object {
        const val MAIN_PACK = "dezhban.pck"
    }
}
