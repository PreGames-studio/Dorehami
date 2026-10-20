package ir.dorehami.games.hokm.ui.room

import java.net.URI

/** Mutable, testable configuration for the endpoint the next online connection will use. */
internal class OnlineServerAddress(initialUrl: String) {
    private var rawUrl: String = initialUrl

    fun update(url: String) {
        rawUrl = url
    }

    /** Returns the normalized HTTP base URL, or `null` before any network resources are created. */
    fun validatedBaseUrl(): String? {
        val candidate = rawUrl.trim()
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank() || uri.rawAuthority.isNullOrBlank()) return null
        if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
        if (uri.port != -1 && uri.port !in 1..65_535) return null

        val path = uri.rawPath.orEmpty().trimEnd('/')
        return "$scheme://${uri.rawAuthority}$path"
    }

    companion object {
        const val INVALID_ADDRESS_MESSAGE_FA =
            "آدرس سرور معتبر نیست؛ نشانی کامل با http:// یا https:// و میزبان معتبر وارد کن."
    }
}
