package ir.dorehami.games.dejban.ui

import android.os.Bundle
import android.os.Process
import android.util.Log
import java.io.File
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
 *
 * Lifecycle contract: a Godot engine instance is one-shot per process — it
 * cannot be torn down and re-initialized in the same process (Godot's own
 * [org.godotengine.godot.GodotActivity.onGodotForceQuit] default therefore
 * kills the process). Because this Activity lives in the dedicated `:dejban`
 * process, we keep that default: quitting the game kills only the game
 * process, and the next launch starts a fresh process with a fresh engine.
 * Simply finish()-ing the Activity instead would leave a spent engine behind
 * and the next launch would crash.
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

    // NOTE: onGodotForceQuit is intentionally NOT overridden. GodotActivity's
    // default terminates the engine by killing the hosting process, which here
    // is only the dedicated `:dejban` process — the Dorehami host process keeps
    // running and the user lands back on the game picker.

    override fun onDestroy() {
        super.onDestroy()
        // Belt and braces for exits that finish this Activity without going
        // through Godot's force-quit path (e.g. the engine never came up):
        // never leave a spent engine process behind for the next launch.
        if (isFinishing) {
            Process.killProcess(Process.myPid())
        }
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
