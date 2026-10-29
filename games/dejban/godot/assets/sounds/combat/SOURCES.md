# منبع و مجوز صداهای مبارزه

فایل‌های OGG این پوشه از خروجی‌های پردازش‌شدهٔ مخزن [Conquestoria، PR #1228](https://github.com/a1flecke/conquestoria/pull/1228)، در commit `41879da455371673dc39d907cb23711ec927ab69` انتخاب و **بدون تغییر** کپی شده‌اند. مانیفست همان commit، برای هر کلیپ منشأ، مدت، بلندی و اوج واقعی را ثبت می‌کند: [`unit-sfx-manifest.md`](https://github.com/a1flecke/conquestoria/blob/41879da455371673dc39d907cb23711ec927ab69/docs/audio/unit-sfx-manifest.md). همهٔ فایل‌های استفاده‌شده در بازی از منابع CC0 انتخاب شده‌اند؛ اعتباردهی زیر برای حفظ منشأ و قدردانی است.

## نگاشت و مشخصات

| فایل | کاربرد در دژبان | منشأ ثبت‌شده در مانیفست | مدت | بلندی یکپارچه / اوج واقعی | SHA-256 |
|---|---|---|---:|---:|---|
| `rifle-semi.ogg` | شلیک سبک/سریع | Free Firearm Sound Library: `AR-15/D_32P.wav` | ۰٫۵۰ ثانیه | ‎−25.5 LUFS / ‎−1.3 dBTP | `e5882d8d5c0d74f433fe4d330f0906721df61d022eae9bfbd9a011eb695b9b9b` |
| `rifle-bolt.ogg` | شلیک سبک/میانه | Free Firearm Sound Library: `Mosin Nagant/M_21P.wav` | ۰٫۶۰ ثانیه | ‎−23.5 LUFS / ‎−1.3 dBTP | `63f692d1ecbeba14da190d05f0d74ee8e94c6fe48265354edaa3f170f8e2446c` |
| `field-gun-fire.ogg` | شلیک سنگین/انفجاری | Free Firearm Sound Library: `Nova/O_21P.wav`؛ بریده و با افت سرعت/اکو پردازش شده | ۱٫۱۰ ثانیه | ‎−22.2 LUFS / ‎−1.4 dBTP | `493a63892d4e89b1aba8f261ceab21a4fbf2793c86b5074bb8090106802561fa` |
| `tank-gun-fire.ogg` | شلیک سنگین | Free Firearm Sound Library: `Arisaka/E_25P.wav` | ۰٫۷۰ ثانیه | ‎−23.1 LUFS / ‎−1.3 dBTP | `894717df0737f84dacb6a6a30140300ce9260fb68871ae9859753d27d36ef090` |
| `soldier-defeat.ogg` | فریاد/نالهٔ کوتاه دشمن انسان‌نما | `AR-15/D_32P.wav` + `yell7.wav` از مجموعهٔ HaelDB | ۰٫۷۹ ثانیه | ‎−16.5 LUFS / ‎−4.8 dBTP | `d3c3ac4518c074cef19093c9e7eeb36606b8c31ce0e64bb9917eedb613e478c8` |
| `cannon-wreck.ogg` | صدای مرگ دشمن زرهی/غول | Sci-Fi RTS War Unit Sounds: `artil/artil.die.wav` | ۰٫۸۰ ثانیه | ‎−17.2 LUFS / ‎−2.4 dBTP | `480526ca9374665ff00d8265b23db35790674b51f075d555aa6fed630e9b8b0f` |
| `aircraft-hit.ogg` | برخورد کوتاه برای گلوله‌های ناحیه‌ای | Tiny Naval Battle Sounds Set: `ExplosionMetal.wav` | ۰٫۳۰ ثانیه | کوتاه‌تر از پنجرهٔ اندازه‌گیری LUFS / ‎−3.2 dBTP | `4fac88cec39615f3cec311c9cc3815f5694edc01c7ed41c03bbb9e81f4a4846b` |

## منابع و مجوز

- [The Free Firearm Sound Library](https://opengameart.org/content/the-free-firearm-sound-library) — ضبط‌های Ben Jaszczak، Brian Nelson، Kevin Heras و Matthew Nanney؛ CC0 1.0.
- [Male Grunt/Yelling Sounds](https://opengameart.org/content/male-gruntyelling-sounds) — HaelDB؛ مجموعه دوگانه‌مجوز CC0 / OGA-BY 3.0 است؛ برای کلیپ استفاده‌شده CC0 انتخاب شده است.
- [Tiny Naval Battle Sounds Set](https://opengameart.org/content/tiny-naval-battle-sounds-set) — Iwan Gabovitch (qubodup)؛ CC0 1.0.
- [Sci-Fi RTS War Unit Sounds](https://opengameart.org/content/sci-fi-rts-war-unit-sounds) — Iwan Gabovitch (qubodup)؛ مانیفست منبع، CC0 1.0 را پس از بازاعطای مجوز در ۲۸ اوت ۲۰۲۴ ثبت می‌کند.

بلندی فایل‌ها از مانیفست upstream نقل شده است؛ سطح پخش نهایی در بازی با افت صدای اختصاصی برای هر رویداد و محدودیت هم‌پوشانی تنظیم می‌شود، نه با تغییر فایل اصلی. شلیک‌ها دست‌کم ۸۰ میلی‌ثانیه فاصله دارند و کل صدای رزمی از استخر ۴ پخش‌کننده استفاده می‌کند؛ انفجار و مرگ دشمن هم محدودکنندهٔ تکرار جدا دارند. صدای شلیک با ‎−10 dB، نالهٔ سرباز با ‎−10 dB، برخورد/مرگ زرهی با ‎−13 dB پخش می‌شوند.

## صدای برق تسلا

`tesla-zap-a.wav` و `tesla-zap-b.wav` ضبط آماده یا فایل upstream نیستند؛ هر دو با
`godot/tools/generate_tesla_audio.py` از نوسان و نویز مصنوعی به‌شکل قطعی تولید می‌شوند.
برای بازتولید، از ریشهٔ مخزن `python3 games/dejban/godot/tools/generate_tesla_audio.py`
را اجرا کنید. این دو نمونه در گروه جداگانهٔ `shot_tesla` همان محدودکنندهٔ استخر رزمی را دارند.

## صدای پرتو لیزر

`laser-beam-a.wav` و `laser-beam-b.wav` از ضبط یا فایل upstream نیستند؛ هر دو با
`godot/tools/generate_laser_audio.py` به‌شکل قطعی از موج‌های مصنوعی ساخته می‌شوند.
آن‌ها در گروه جداگانهٔ `shot_laser` از همان استخر محدود صدا استفاده می‌کنند.
