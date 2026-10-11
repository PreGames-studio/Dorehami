# سرور دورهمی — `server/`

> **خلاصه:** سرور **مرجع** اتاق‌های آنلاین است: همان `HostController` که روی گوشی میزبان بلوتوثی اجرا
> می‌شود، اینجا بدون صندلی اجرا می‌شود. کلاینت‌ها فقط حرکت می‌فرستند و view می‌گیرند. بدون دیتابیس هم
> کامل کار می‌کند (درون‌حافظه).

## ساختار

| مسیر | چیست | وابستگی به Ktor |
|---|---|---|
| `rooms/RelayTransport` | سوکت‌ها به‌شکل sink؛ `GameTransport` برای کنترلر | ندارد |
| `rooms/RoomHub` | ساخت/یافتن اتاق، اتصال سوکت به اتاق، جمع‌کردن اتاق‌های تمام‌شده | ندارد |
| `rooms/Matchmaker` | صف حریف تصادفی؛ بعد از ۲۵ ثانیه اتاق با ربات | ندارد |
| `rooms/MatchStore` | ذخیرهٔ رکورد بازی (درون‌حافظه؛ Postgres همین رابط را پیاده می‌کند) | ندارد |
| `Tokens` | توکن مهمان بدون حالت (`base64url(player).hmac`) | ندارد |
| `Application` | HTTP + WebSocket | دارد |

## API (docs/PROTOCOL.md §۶)

```
GET  /healthz
GET  /v1/games
POST /v1/auth/guest            {displayName}            → {player, token}
POST /v1/rooms                 {gameId, partySize, options?, name?}   (Bearer token) → اتاق
GET  /v1/rooms/{code}
POST /v1/rooms/{code}/join     (Bearer token)           → {ok, room, wsPath}
POST /v1/matchmaking           {gameId, partySize}      → {ticketId, roomCode?}
GET  /v1/matchmaking/{id}?wait=20                       (long-poll)
DELETE /v1/matchmaking/{id}
GET  /v1/matches/{code}                                 → رکورد کامل قابل بازپخش
WS   /v1/ws/room/{code}?ticket=<token>                  → از اینجا همه‌چیز Envelope است
```

اولین نفری که به اتاق می‌پیوندد «صاحب اتاق» است و با `lobby.start` بازی را شروع می‌کند؛ صندلی‌های
خالی ربات می‌شوند. قطع‌شدن یک بازیکن ⇒ ربات جایش بازی می‌کند تا با همان توکن برگردد (`session.resync`).

## اجرا

```bash
export DOREHAMI_TOKEN_SECRET="$(openssl rand -hex 32)"
./gradlew :server:run            # 0.0.0.0:8080
./gradlew :server:test           # RoomHubTest (بدون Ktor) + ApplicationTest (Ktor test host)
```

متغیرها: `DOREHAMI_HOST`, `DOREHAMI_PORT`, `DOREHAMI_TOKEN_SECRET` (docs/DEPLOY.md).
