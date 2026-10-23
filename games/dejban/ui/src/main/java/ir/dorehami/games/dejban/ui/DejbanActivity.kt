package ir.dorehami.games.dejban.ui

import android.os.Bundle
import android.os.Process
import android.util.Log
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import org.godotengine.godot.GodotActivity

/**
 * Hosts the embedded Godot 4.3 runtime for Dejban.
 *
 * The game itself remains a GDScript project in `games/dejban/godot`; CI
 * exports it to `dezhban.pck`, which is packaged as an APK asset. The engine
 * cannot open that asset through a bare relative path (Godot's Android
 * storage layer rejects non-absolute paths), so on launch the pack is synced
 * into app-private storage keyed by its SHA-256 content digest and the engine
 * is given the absolute path.
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
     * Syncs `assets/dezhban.pck` to `filesDir/dejban/dezhban-<sha256>.pck`
     * whenever the packaged asset bytes change — even when an updated debug APK
     * reuses the same `versionCode`.
     */
    private fun ensureMainPackExtracted(): File {
        val packDir = File(filesDir, "dejban")
        val extracted = DejbanPackExtractor.extractIfNeeded(packDir) { assets.open(ASSET_PACK) }
        Log.i(TAG, "Using Dejban pack ${extracted.absolutePath} (${extracted.length()} bytes)")
        return extracted
    }

    private companion object {
        const val TAG = "DejbanActivity"
        const val ASSET_PACK = "dezhban.pck"
    }
}

/**
 * Extracts the embedded Godot `.pck` into app-private storage using a content
 * hash filename so in-place APK updates with an unchanged `versionCode` never
 * keep serving a stale extracted pack.
 */
internal object DejbanPackExtractor {

    private const val HEX_CHARS = "0123456789abcdef"

    /**
     * Ensures [packDir] holds the exact bytes supplied by [openPack] under a
     * content-addressed filename (`dezhban-<hash>.pck`), removing stale packs.
     */
    fun extractIfNeeded(packDir: File, openPack: () -> InputStream): File {
        packDir.mkdirs()
        val (digestHex, expectedLength) = digestAndSize(openPack)
        val target = File(packDir, "dezhban-$digestHex.pck")
        if (target.isFile && target.length() == expectedLength && expectedLength > 0L) {
            packDir.listFiles()?.forEach { if (it != target) it.delete() }
            return target
        }

        packDir.listFiles()?.forEach { if (it != target) it.delete() }

        val tmp = File(packDir, "${target.name}.tmp")
        openPack().use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        }
        check(tmp.renameTo(target)) { "Could not finalize extracted Dejban pack: $target" }
        return target
    }

    internal fun digestAndSize(openPack: () -> InputStream): Pair<String, Long> {
        val md = MessageDigest.getInstance("SHA-256")
        var total = 0L
        val buffer = ByteArray(64 * 1024)
        openPack().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                md.update(buffer, 0, read)
                total += read
            }
        }
        val hashBytes = md.digest()
        val hex = buildString(16) {
            for (i in 0 until 8) {
                val v = hashBytes[i].toInt() and 0xff
                append(HEX_CHARS[v ushr 4])
                append(HEX_CHARS[v and 0x0f])
            }
        }
        return hex to total
    }
}
