// SANDBOX-ONLY: the real `WordBankRules` lives with `WordRecord`, which needs kotlinx.serialization.
// The bare-kotlinc harness cannot parse that, so it compiles this stand-in with the same
// normalisation rules instead. Gradle/CI always compiles the real file.
package ir.dorehami.games.pantohub

import java.text.Normalizer
import java.util.Locale

object WordBankRules {
    fun normalizePersian(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKC)
            .replace('ي', 'ی')
            .replace('ى', 'ی')
            .replace('ك', 'ک')
            .lowercase(Locale("fa", "IR"))
            .replace("\u200c", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
