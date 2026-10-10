# مشارکت در دورهمی

> خلاصه: تست بنویس، PR کوچک بده، در کار بازی‌های دیگر دخالت نکن، سرعت بگیر ولی از قرارداد بیرون نزن.
> اگر عامل هوش مصنوعی هستی، اول `AGENTS.md` را بخوان.

## راه‌اندازی

```bash
git clone <repo> && cd dorehami
./gradlew checkAll        # همهٔ تست‌های JVM
```

نیازمندی: JDK 17 + Android SDK (compileSdk 36). هیچ سرویس بیرونی لازم نیست.

## گردش کار

1. **ایده/تسک:** از `docs/ROADMAP.md` یک تسک بردار یا ایشو باز کن (قالب‌ها در `.github/ISSUE_TEMPLATE`
   هستند).
2. **شاخه:** `game/<بازی>/<موضوع>`، `core/<موضوع>`، `server/<موضوع>`، `app/<موضوع>`، `docs/<موضوع>`.
3. **کد + تست:** هر تغییر رفتاری تست دارد (جدول تست‌ها در `AGENTS.md` بخش ۵).
4. **PR:** قالب PR را پر کن. PRهای `core/*` و `server/*` به دو تأیید نیاز دارند؛ بقیه یک تأیید.
5. **ادغام:** فقط با CI سبز. پیام ادغام همان قواعد کامیت را دارد.

## قواعد کامیت

```
feat(hokm): add trump selection for hakem
fix(session): resend private view after reconnect
test(pantohub): cover skip penalty edge case
docs: explain the game contract in ADD_A_GAME
```

## مالکیت (CODEOWNERS)

| مسیر | مالک |
|---|---|
| `games/<بازی>/**` | توسعه‌دهندهٔ همان بازی |
| `core/**`, `transport/**` | هسته (الف) |
| `server/**` | سرور (ب) |
| `app/**`, `core/uikit/**` | اپ و رابط کاربری (ج) |
| `docs/**`, `AGENTS.md` | همه (تأیید دو نفره) |

## ممنوعیت‌ها (در بازبینی رد می‌شود)

* وابستگی بازی به بازی دیگر، یا `core/*` به بازی/سرور.
* ساعت/تصادف سیستم داخل قوانین بازی (به‌جای `Clock`/`Rng`ی که تزریق می‌شود).
* ارسال اطلاعات خصوصی بازیکنی برای کسی غیر از خودش.
* کامیت رمز/توکن. رمزها فقط از متغیرهای محیطی `DOREHAMI_*`.
* تست با `Thread.sleep` یا موک‌فریم‌ورک (فیک‌های `core/testkit` وجود دارند).

## سؤال؟

اول `AGENTS.md`، بعد `docs/ARCHITECTURE.md` و `docs/PROTOCOL.md`. اگر جواب نبود، ایشو بزن با برچسب
`question`.
