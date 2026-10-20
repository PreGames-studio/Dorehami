package ir.dorehami.games.dejban.ui

import android.os.Bundle
import android.util.Log
import java.io.File
import org.godotengine.godot.Godot
import org.godotengine.godot.GodotActivity

/**
 * Hosts the embedded Godot 4.3 runtime for Dejban.
 *
 * The game itself remains a GDScript project in `games/dejban/godot`; CI
 * exports it to `dezhban.pck`, which is packaged as an APK asset. The engine
 * cannot open that asset through a bare relative path (Godot's Android
 * storage layer rejects non-absolute paths), so on first launch the pack is
 * copied into app-private storage and the engine is given the absolute path.
 *
 * The Activity runs in the `:dejban` process (declared in the manifest) so a
 * native Godot crash or shutdown cannot take down the Kotlin/Compose host
 * process.
 */
class DejbanActivity : GodotActivity() {

    private lateinit var mainPackFile: File

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must happen before super.onCreate(): the Godot fragment created
        // there immediately asks getCommandLine() for the pack location.
        mainPackFile = ensureMainPackExtracted()
        super.onCreate(savedInstanceState)
    }

    override fun getCommandLine(): List<String> =
        listOf("--main-pack", mainPackFile.absolutePath)

    /**
     * Dejban's menus call `get_tree().quit()`. In an embedded game that
     * should mean "leave this game", not GodotActivity's default behaviour
     * of force-quitting the whole application process. Finishing only this
     * Activity returns the user to Dorehami's game picker.
     */
    override fun onGodotForceQuit(instance: Godot) {
        runOnUiThread { finish() }
    }

    /**
     * Copies `assets/dezhban.pck` to `filesDir/dejban/dezhban-v<version>.pck`
     * exactly once per app version and returns the extracted file.
     */
    private fun ensureMainPackExtracted(): File {
        val packDir = File(filesDir, "dejban").apply { mkdirs() }
        val target = File(packDir, "dezhban-v${appVersionCode()}.pck")
        if (target.isFile && target.length() > 0L) {
            return target
        }
        // Drop packs extracted for older app versions.
        packDir.listFiles()?.forEach { if (it != target) it.delete() }

        val tmp = File(packDir, target.name + ".tmp")
        assets.open(ASSET_PACK).use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        }
        check(tmp.renameTo(target)) { "Could not finalize extracted Dejban pack: $target" }
        Log.i(TAG, "Extracted Dejban pack to ${target.absolutePath} (${target.length()} bytes)")
        return target
    }

    @Suppress("DEPRECATION")
    private fun appVersionCode(): Int =
        runCatching { packageManager.getPackageInfo(packageName, 0).versionCode }
            .getOrDefault(0)

    private companion object {
        const val TAG = "DejbanActivity"
        const val ASSET_PACK = "dezhban.pck"
    }
}
