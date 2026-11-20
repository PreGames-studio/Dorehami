# برنامهٔ مستقل حکم

این ماژول یک APK جداگانه می‌سازد که صفحهٔ آغازش مستقیماً وارد بازی حکم می‌شود و تنها به `:games:hokm:ui` (و وابستگی‌های خود آن) وصل است؛ بازی‌های دیگر و پوستهٔ اصلی دورهمی در این برنامه وارد نمی‌شوند.

ساخت APK دیباگ از ریشهٔ مخزن:

```sh
./gradlew :hokm-app:assembleDebug
```

خروجی:

```text
hokm-app/build/outputs/apk/debug/hokm-app-debug.apk
```

برای ساخت، JDK 17 و Android SDK مطابق `compileSdk` تعریف‌شده در `gradle.properties` لازم است. این APK دیباگ برای نصب/آزمایش است و امضای انتشار ندارد.
