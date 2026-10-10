package ir.dorehami.protocol

/**
 * کدهای خطای مشترک کل پلتفرم.
 *
 * این کدها **بخشی از قرارداد** هستند: رابط کاربری روی همین کدها پیام فارسی نشان می‌دهد و لاگِ
 * هر بازی هم با همین کدها ذخیره می‌شود. بنابراین مقدار رشته‌ای آن‌ها را عوض نکن؛ اگر کد تازه‌ای
 * لازم است، اینجا اضافه کن.
 *
 * نکته: کدها عمداً «نقطه‌دار» و snake-free هستند (`game.not_your_turn`) تا در سیم و در لاگ یکدست
 * بمانند.
 */
object ErrCodes {
    /** بازیکن خارج از نوبت خودش حرکت فرستاده است. */
    const val NOT_YOUR_TURN = "game.not_your_turn"

    /** حرکت از نظر قواعد آن بازی مجاز نیست (مثلاً خال نداری و داری خال دیگر بازی می‌کنی). */
    const val ILLEGAL_ACTION = "game.illegal_action"

    /** نوع حرکت ناشناس است (بازی این `type` را نمی‌شناسد). */
    const val UNKNOWN_ACTION = "game.unknown_action"

    /** بدنهٔ حرکت ناقص یا نامعتبر است (مثل کارتی که در دست بازیکن نیست). */
    const val BAD_PAYLOAD = "game.bad_payload"

    /** حرکت درست است ولی در این مرحله از بازی معنا ندارد (مثلاً تعیین حکم بعد از شروع بازی). */
    const val WRONG_PHASE = "game.wrong_phase"

    /** بازی تمام شده است؛ حرکت دیگری پذیرفته نمی‌شود. */
    const val GAME_FINISHED = "game.finished"

    /** بازیکن عضو این میز/دست نیست. */
    const val NOT_IN_MATCH = "game.not_in_match"

    /** تنظیمات میزبان نامعتبر است (فقط در `validateOptions`). */
    const val BAD_OPTIONS = "game.bad_options"
}

/** نسخهٔ قالب پیام. کلاینت و سرور باید یکی باشند وگرنه اتصال رد می‌شود. */
const val PROTOCOL_VERSION = 1
