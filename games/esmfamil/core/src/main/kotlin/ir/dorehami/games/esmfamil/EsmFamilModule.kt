package ir.dorehami.games.esmfamil

import ir.dorehami.gamesdk.Capability
import ir.dorehami.gamesdk.GameConfig
import ir.dorehami.gamesdk.GameConfigException
import ir.dorehami.gamesdk.GameMeta
import ir.dorehami.gamesdk.GameModule
import ir.dorehami.gamesdk.GameSession
import ir.dorehami.protocol.ErrCodes
import ir.dorehami.protocol.GameId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * اسم فامیل (Name–Family): a paper game moved onto the phone. Everybody writes one answer per
 * column for the drawn letter, the sheets are opened together, and only the answers nobody else
 * wrote pay full points.
 *
 * Two seats minimum because a sheet needs somebody to compete with; up to eight because that is
 * how many people fit around one phone in a pass-and-play round.
 *
 * There is **no bot**: a robot cannot invent answers a human table would accept. A room therefore
 * needs real players, and the rules never fill a seat with a machine.
 */
object EsmFamilModule : GameModule {

    const val ID = "esmfamil"

    /** Hand in my sheet. Payload: `{ "answers": { "name": "سارا", … } }`. */
    const val ACTION_SUBMIT = "submit"

    /** Close the round without waiting for the missing sheets (timer over / a seat went quiet). */
    const val ACTION_FINISH_ROUND = "finish_round"

    /** Read the scores and deal the next letter. */
    const val ACTION_NEXT_ROUND = "next_round"

    const val PAYLOAD_ANSWERS = "answers"

    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 8

    const val DEFAULT_ROUNDS = 5

    /** Round counts offered in the room settings. */
    val ALLOWED_ROUNDS = listOf(3, 5, 7)

    /** Fewest columns a round may ask for (a two-column sheet is not a party game). */
    const val MIN_CATEGORIES = 3

    override val meta = GameMeta(
        id = GameId(ID),
        titleFa = "اسم فامیل",
        titleEn = "Name, Family",
        taglineFa = "یک حرف می‌آید، همه برگه پر می‌کنند، امتیازها رو می‌شود",
        minPlayers = MIN_PLAYERS,
        maxPlayers = MAX_PLAYERS,
        iconKey = "esmfamil",
        estimatedMinutes = 12,
        capabilities = setOf(
            // Both modes the app promises for a party game that needs no content bank.
            Capability.BLUETOOTH_OFFLINE.name,
            Capability.ONLINE.name,
        ),
        defaultOptions = buildJsonObject {
            put(EsmFamilSession.Options.ROUNDS, DEFAULT_ROUNDS)
            put(
                EsmFamilSession.Options.CATEGORIES,
                buildJsonArray { EsmFamilSheet.DEFAULT_IDS.forEach { add(JsonPrimitive(it)) } },
            )
            put(EsmFamilSession.Options.LETTERS, EsmFamilSheet.LETTERS_COMMON)
        },
    )

    /** The columns a normalised options object asks for (also used by [EsmFamilSession]). */
    fun categoriesOf(options: JsonObject): List<String> {
        val chosen = (options[EsmFamilSession.Options.CATEGORIES] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.filter { EsmFamilSheet.byId(it) != null }
            .orEmpty()
        return chosen.ifEmpty { EsmFamilSheet.DEFAULT_IDS }
    }

    override fun validateOptions(options: JsonObject): JsonObject {
        val rounds = (options[EsmFamilSession.Options.ROUNDS] as? JsonPrimitive)?.intOrNull ?: DEFAULT_ROUNDS
        if (rounds !in ALLOWED_ROUNDS) {
            throw GameConfigException(
                ErrCodes.BAD_OPTIONS,
                "تعداد دور باید یکی از ${ALLOWED_ROUNDS.joinToString("، ")} باشد",
            )
        }
        val categories = categoriesOf(options)
        if (categories.size < MIN_CATEGORIES) {
            throw GameConfigException(
                ErrCodes.BAD_OPTIONS,
                "دست‌کم ${MIN_CATEGORIES} ستون لازم است",
            )
        }
        val letters = (options[EsmFamilSession.Options.LETTERS] as? JsonPrimitive)?.contentOrNull
            ?: EsmFamilSheet.LETTERS_COMMON
        if (letters != EsmFamilSheet.LETTERS_COMMON && letters != EsmFamilSheet.LETTERS_ALL) {
            throw GameConfigException(ErrCodes.BAD_OPTIONS, "مجموعهٔ حروف نامعتبر است")
        }
        return buildJsonObject {
            put(EsmFamilSession.Options.ROUNDS, rounds)
            put(
                EsmFamilSession.Options.CATEGORIES,
                buildJsonArray { categories.forEach { add(JsonPrimitive(it)) } },
            )
            put(EsmFamilSession.Options.LETTERS, letters)
        }
    }

    override fun create(config: GameConfig): GameSession = EsmFamilSession(config)

    /** A Bluetooth table is a table of friends: four seats is the size a sheet is fun at. */
    override fun preferredPartySize(): Int = 4

    /** No bot policy — see the class comment. */
    override val botAction: ((GameSession, ir.dorehami.protocol.PlayerId) -> ir.dorehami.gamesdk.GameAction?)? = null
}
