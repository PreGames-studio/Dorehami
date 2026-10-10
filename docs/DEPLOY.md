# نصب سرور روی هاست (VPS ایرانی)

> **خلاصهٔ فارسی:** سرور یک فایل اجرایی جاوا است روی همان VPSی که خودت می‌خری. بدون دیتابیس هم کار
> می‌کند (همه‌چیز درون‌حافظه)؛ برای آمار دائمی، Postgres روشن می‌کنی. Nginx جلوی آن می‌نشیند و
> HTTPS را انجام می‌دهد. همهٔ رمزها از متغیرهای محیطی می‌آیند، هیچ رمزی در گیت نیست.

---

## ۱. پیش‌نیازها

* Ubuntu 22.04 یا بالاتر (اکثر سرورهای ایرانی همین را می‌دهند)
* JDK 17 → `sudo apt install openjdk-17-jre-headless`
* (اختیاری) Postgres 16 → `sudo apt install postgresql`
* (اختیاری) Nginx + گواهی SSL

## ۲. ساخت و انتقال

روی ماشین خودت:

```bash
./gradlew :server:installDist
# خروجی: server/build/install/server/   (پوشهٔ bin + lib)
```

روی سرور:

```bash
sudo useradd --system --home /opt/dorehami dorehami
sudo mkdir -p /opt/dorehami
# پوشهٔ server/ را با scp/rsync به /opt/dorehami منتقل کن
sudo chown -R dorehami:dorehami /opt/dorehami
```

## ۳. تنظیمات (بدون رمز در فایل گیت)

`/etc/dorehami/server.env`:

```bash
DOREHAMI_HOST=127.0.0.1            # فقط Nginx از بیرون می‌بیند
DOREHAMI_PORT=8080
DOREHAMI_TOKEN_SECRET=<یک رشته تصادفی حداقل ۳۲ کاراکتری>
DOREHAMI_PUBLIC_URL=https://api.dorehami.example
DOREHAMI_TELEGRAM_BOT_TOKEN=<اگر ورود تلگرام می‌خواهی>

DOREHAMI_DB_ENABLED=true
DOREHAMI_DB_URL=jdbc:postgresql://localhost:5432/dorehami
DOREHAMI_DB_USER=dorehami
DOREHAMI_DB_PASSWORD=<رمز دیتابیس>
```

```bash
sudo chmod 600 /etc/dorehami/server.env
sudo chown root:root /etc/dorehami/server.env
```

جدول‌های دیتابیس خودکار ساخته می‌شوند (Flyway فقط برای جدول `matches`).

## ۴. سرویس systemd

`/etc/systemd/system/dorehami.service`:

```ini
[Unit]
Description=Dorehami game server
After=network.target postgresql.service

[Service]
Type=simple
User=dorehami
WorkingDirectory=/opt/dorehami/server
EnvironmentFile=/etc/dorehami/server.env
ExecStart=/opt/dorehami/server/bin/server
Restart=always
RestartSec=3
NoNewPrivileges=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now dorehami
sudo systemctl status dorehami
curl -s http://127.0.0.1:8080/healthz
```

## ۵. Nginx (HTTPS + وب‌سوکت)

```nginx
server {
    listen 443 ssl http2;
    server_name api.dorehami.example;

    ssl_certificate     /etc/letsencrypt/live/api.dorehami.example/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.dorehami.example/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;

        # وب‌سوکت اتاق‌ها: این دو خط حیاتی‌اند
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";

        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        proxy_read_timeout 3600s;    # یک دست بازی طولانی قطع نشود
        proxy_send_timeout 3600s;
    }
}
```

فایروال: فقط ۸۰/۴۴۳ باز باشد؛ پورت ۸۰۸۰ روی `127.0.0.1` می‌ماند (`DOREHAMI_HOST`).

## ۶. آدرس سرور در اپ

در `gradle.properties` پروژهٔ اپ:

```properties
dorehami.server.baseUrl=https://api.dorehami.example
```

(`http://10.0.2.2:8080` فقط برای شبیه‌ساز اندروید است.)

## ۷. به‌روزرسانی نسخه

```bash
sudo systemctl stop dorehami
# پوشهٔ جدید را جای پوشهٔ قبلی بگذار (rsync --delete)
sudo systemctl start dorehami
curl -s http://127.0.0.1:8080/healthz     # باید {"status":"ok",…} بدهد
```

## ۸. نگهداری و پشتیبان

* **لاگ:** `journalctl -u dorehami -f`؛ صفحهٔ وضعیت `https://api.dorehami.example/` را هم می‌شود
  مرورگر باز کرد.
* **پشتیبان دیتابیس:** `pg_dump dorehami > backup-$(date +%F).sql` (روزی/هفتگی با cron).
* نکتهٔ خوب معماری: هر رکورد بازی «خوداثبات» است (سید + لاگ + هش)، پس حتی اگر بکاپ قدیمی باشد،
  نتیجه‌ها دوباره قابل تأییدند.
* **نظارت:** یک درخواست هر دقیقه به `/healthz` (UptimeRobot داخلی یا cron).

## ۹. چک‌لیست بعد از نصب

- [ ] `curl /healthz` جواب `ok` می‌دهد.
- [ ] صفحهٔ وضعیت در مرورگر باز می‌شود و عددها زنده‌اند.
- [ ] `POST /v1/auth/guest` توکن می‌دهد.
- [ ] ساخت اتاق + اتصال سوکت با `wscat`/اپ موفق است.
- [ ] اگر تلگرام فعال است، ورود با `initData` تست شده.
- [ ] پشتیبان دیتابیس تنظیم شده.
