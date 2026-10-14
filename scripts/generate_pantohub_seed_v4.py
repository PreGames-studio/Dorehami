#!/usr/bin/env python3
"""Append the curated-bootstrap-v4 word bank (seed-2501..seed-5000) to the Pantohub CSV.

Generates 2,500 new, unique Persian entries — 125 per category
(50 easy / 45 medium / 30 hard) — from per-category items and actable templates,
deduplicated (normalized) against the existing bank. Totals become:
easy 2,000 / medium 1,800 / hard 1,200 = 5,000 records.

Idempotent: if the CSV already has 5,000 records it does nothing.
"""
import csv
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SEED = ROOT / "games/pantohub/ui/src/main/assets/pantohub-words-fa.csv"
TARGET_TOTAL = 5_000

CONTEXTS = [
    "در خانه", "در پارک", "در مدرسه", "در سفر", "کنار دریا", "در باغ",
    "در خیابان", "در مهمانی", "در آشپزخانه", "در کتابخانه", "در محل کار", "در روستا",
]
CHALLENGES = [
    "با عجله", "بدون صدا", "با یک دست", "در حالی که می‌خندد",
    "با چشم‌های بسته", "روی یک پا", "در حالی که دیرش شده است", "بدون اینکه بخندد",
]

# category -> (items, easy templates with {i})
DATA = {
    "animals": (["بز", "شتر", "خرگوش", "روباه", "گرگ", "خرس", "پلنگ", "زرافه", "اسب", "گاو", "گوسفند", "مرغ", "اردک", "قورباغه"],
                ["غذا دادن به {i}", "نوازش کردن {i}", "تقلید صدای {i}", "دویدن دنبال {i}"]),
    "nature": (["کوه بلند", "دریاچه", "آبشار", "جنگل انبوه", "صحرا", "رودخانه", "درخت بلوط", "گل رز", "برف سنگین", "باران تند", "رنگین‌کمان", "غروب خورشید", "جزیره", "غار تاریک"],
               ["تماشا کردن {i}", "عکس گرفتن از {i}", "رسیدن به {i}", "قدم زدن کنار {i}"]),
    "home": (["مبل راحتی", "یخچال", "جاروبرقی", "پرده", "فرش قرمز", "تختخواب", "کمد لباس", "آینه قدی", "ساعت دیواری", "چراغ مطالعه", "میز ناهارخوری", "صندلی چوبی"],
             ["تمیز کردن {i}", "جابه‌جا کردن {i}", "تعمیر کردن {i}", "گردگیری {i}"]),
    "objects": (["چتر", "عینک آفتابی", "کلاه", "دستکش", "کوله‌پشتی", "ساعت مچی", "دسته‌کلید", "قیچی", "چکش", "پیچ‌گوشتی", "سوزن و نخ", "خط‌کش"],
                ["گرفتن {i} در دست", "پیدا کردن {i}", "گم کردن {i}", "نشان دادن {i} به دوست"]),
    "food": (["پیتزا", "همبرگر", "سالاد شیرازی", "سوپ جو", "کباب کوبیده", "قرمه‌سبزی", "آش رشته", "ماکارونی", "تخم‌مرغ آب‌پز", "پنیر و سبزی", "هندوانه", "انار"],
             ["خوردن {i} با اشتها", "درست کردن {i}", "تعارف کردن {i} به مهمان", "بو کردن {i}"]),
    "jobs": (["پزشک", "معلم", "آشپز", "راننده تاکسی", "خلبان", "آتش‌نشان", "نجار", "خیاط", "کشاورز", "پلیس", "مهندس ساختمان", "نقاش"],
             ["تقلید کار {i}", "کمک گرفتن از {i}", "صحبت کردن با {i}", "لباس {i} را پوشیدن"]),
    "sports": (["فوتبال", "والیبال", "شنا", "دوچرخه‌سواری", "کوهنوردی", "شطرنج", "تنیس", "بوکس", "اسکی", "ژیمناستیک", "بسکتبال", "کشتی"],
               ["بازی کردن {i}", "تمرین {i}", "برنده شدن در {i}", "آموزش {i} به دوست"]),
    "actions": (["خندیدن بلند", "دویدن تند", "پریدن", "رقصیدن", "آواز خواندن", "نقاشی کشیدن", "شنا کردن", "خوابیدن", "بیدار شدن", "دست دادن", "سرفه کردن", "عطسه کردن"],
                ["تظاهر به {i}", "{i} با انرژی", "{i} آرام و آهسته", "{i} جلوی جمع"]),
    "emotions": (["خوشحالی زیاد", "ناراحتی", "تعجب", "ترسیدن", "خجالت", "عصبانیت", "خستگی", "هیجان", "نگرانی", "غرور", "حسادت", "آرامش"],
                 ["نشان دادن {i} با صورت", "تظاهر به {i}", "احساس {i} کردن ناگهانی", "پنهان کردن {i}"]),
    "places": (["کتابخانه بزرگ", "بیمارستان", "فرودگاه", "سینما", "موزه", "بازار شلوغ", "مدرسه", "ایستگاه قطار", "رستوران", "هتل", "پارک شهر", "استخر"],
               ["رفتن به {i}", "گشتن در {i}", "منتظر ماندن در {i}", "خارج شدن از {i}"]),
    "transport": (["اتوبوس", "قطار سریع", "هواپیما", "کشتی", "موتورسیکلت", "دوچرخه", "تاکسی زرد", "مترو", "کامیون بزرگ", "قایق", "هلیکوپتر", "اسب و گاری"],
                  ["سوار شدن به {i}", "پیاده شدن از {i}", "راندن {i}", "منتظر {i} ماندن"]),
    "cinema": (["فیلم کمدی", "بازیگر مشهور", "کارگردان", "دوربین فیلم‌برداری", "پرده سینما", "گریم سنگین", "دوبله فیلم", "تیتراژ", "بدلکاری", "انیمیشن", "فیلم ترسناک", "جشنواره فیلم"],
               ["تماشا کردن {i}", "بازی در {i}", "ساختن {i}", "نقد کردن {i}"]),
    "literature": (["شعر خواندن", "رمان قطور", "داستان کوتاه", "نمایشنامه", "دیوان شعر", "نویسنده معروف", "شاعر جوان", "کتابفروشی", "مجله قدیمی", "روزنامه صبح", "خاطره‌نویسی", "ترجمه کتاب"],
                   ["خواندن {i} با صدای بلند", "نوشتن {i}", "خریدن {i}", "هدیه دادن {i} به دوست"]),
    "idioms": (["از کاه کوه ساختن", "دست روی دست گذاشتن", "آب در هاون کوبیدن", "چشم و گوش بسته", "دل به دریا زدن", "سنگ بزرگ نشانه نزدن است", "کار نیکو کردن از پر کردن است", "جوجه را آخر پاییز می‌شمارند", "از این ستون به آن ستون فرج است", "ماه پشت ابر نمی‌ماند", "هر که بامش بیش برفش بیشتر", "نون به نرخ روز خوردن", "سر و گوش آب دادن", "دو دوتا چهارتا کردن"],
               ["{i}", "{i} را با مثال توضیح دادن", "{i} گفتن به دوست", "{i} را با حرکت نشان دادن"]),
    "technology": (["کامپیوتر", "گوشی هوشمند", "ربات خانگی", "اینترنت پرسرعت", "چاپگر", "دوربین عکاسی", "تلویزیون بزرگ", "لپ‌تاپ", "هدفون", "کنسول بازی", "پهپاد", "ساعت هوشمند"],
                   ["کار کردن با {i}", "روشن کردن {i}", "تعمیر {i}", "خریدن {i} جدید"]),
    "school": (["درس ریاضی", "آزمایش علوم", "درس تاریخ", "جغرافیا", "آزمایشگاه مدرسه", "تخته سیاه", "دفتر مشق", "مدادتراش", "آزمون سخت", "کتاب درسی", "زنگ تفریح", "کلاس درس"],
               ["درس دادن {i}", "یاد گرفتن {i}", "امتحان {i} دادن", "توضیح دادن {i} برای دوست"]),
    "travel": (["چمدان بزرگ", "بلیط هواپیما", "نقشه کاغذی", "گذرنامه", "رزرو هتل", "چادر مسافرتی", "دوربین سفر", "قطب‌نما", "سفر جاده‌ای", "گرفتن ویزا", "کیف مسافرتی", "راهنمای سفر"],
               ["آماده کردن {i}", "گم کردن {i}", "پیدا کردن {i}", "نشان دادن {i} به مأمور"]),
    "humor": (["دلقک سیرک", "پوست موز", "بادکنک بزرگ", "بوق ماشین", "عطسه بلند", "خنده بی‌موقع", "کلاه خنده‌دار", "جوراب سوراخ", "صندلی شکسته", "آینه خنده‌دار", "موز خوردن میمون", "سقلمه زدن به دوست"],
              ["تقلید {i}", "خندیدن به {i}", "بازی با {i}", "شوخی کردن با {i}"]),
    "culture": (["جشن نوروز", "شب یلدا", "سفره هفت‌سین", "موسیقی سنتی", "فرش ایرانی", "مینیاتور", "خطاطی", "چایخانه سنتی", "بازار سنتی", "چهارشنبه‌سوری", "عید دیدنی", "تعزیه"],
                ["شرکت در {i}", "آماده شدن برای {i}", "تماشا کردن {i}", "توضیح دادن {i} برای مهمان"]),
    "daily_life": (["خرید نان صبح", "شستن ظرف‌ها", "مرتب کردن اتاق", "آبیاری گل‌ها", "پختن ناهار", "اتو کردن لباس", "مسواک زدن", "آماده شدن برای مهمانی", "پرداخت قبض", "تماس تلفنی با مادر", "مرتب کردن کمد", "خرید میوه"],
                   ["انجام دادن {i}", "{i} با حوصله", "{i} قبل از مهمانی", "{i} در آخر شب"]),
}


def normalize_persian(text: str) -> str:
    text = unicodedata.normalize("NFKC", text)
    text = (
        text.replace("ي", "ی").replace("ى", "ی").replace("ك", "ک")
        .replace("‌", " ").replace("‏", " ").replace("‎", " ").lower()
    )
    out = []
    for char in text:
        code = ord(char)
        if 0x064B <= code <= 0x065F or code == 0x0670 or 0x06D6 <= code <= 0x06ED:
            continue
        out.append(" " if unicodedata.category(char).startswith("P") else char)
    return " ".join("".join(out).split())


def main() -> None:
    with SEED.open(encoding="utf-8", newline="") as file:
        rows = list(csv.reader(file))
    header, existing = rows[0], rows[1:]
    if len(existing) >= TARGET_TOTAL:
        print(f"Seed already has {len(existing)} records; nothing to do.")
        return

    seen = {normalize_persian(row[1]) for row in existing}
    new_rows: list[list[str]] = []
    next_id = len(existing) + 1

    def add(text: str, difficulty: str, category: str) -> bool:
        nonlocal next_id
        key = normalize_persian(text)
        if not key or key in seen or len(text) > 120:
            return False
        seen.add(key)
        new_rows.append([
            f"seed-{next_id:03d}", text, difficulty, category,
            "ACTION", "true", "true", "curated-bootstrap-v4",
        ])
        next_id += 1
        return True

    for category, (items, templates) in DATA.items():
        # Easy: plain templates (50)
        count = 0
        for template in templates:
            for item in items:
                if count >= 50:
                    break
                if add(template.format(i=item), "EASY", category):
                    count += 1
            if count >= 50:
                break
        if count < 50:  # short categories: add a light place context to reach 50
            for context in CONTEXTS[:3]:
                for template in templates:
                    for item in items:
                        if count >= 50:
                            break
                        if add(f"{template.format(i=item)} {context}", "EASY", category):
                            count += 1
                    if count >= 50:
                        break
                if count >= 50:
                    break
        # Medium: template + place context (45)
        count = 0
        for context in CONTEXTS:
            for template in templates:
                for item in items:
                    if count >= 45:
                        break
                    if add(f"{template.format(i=item)} {context}", "MEDIUM", category):
                        count += 1
                if count >= 45:
                    break
            if count >= 45:
                break
        # Hard: template + context + challenge (30)
        count = 0
        for challenge in CHALLENGES:
            for context in CONTEXTS:
                for template in templates:
                    for item in items:
                        if count >= 30:
                            break
                        if add(f"{template.format(i=item)} {context} {challenge}", "HARD", category):
                            count += 1
                    if count >= 30:
                        break
                if count >= 30:
                    break
            if count >= 30:
                break

    assert len(new_rows) == 2_500, f"generated {len(new_rows)} rows, expected 2500"
    with SEED.open("a", encoding="utf-8", newline="") as file:
        csv.writer(file).writerows(new_rows)
    print(f"Appended {len(new_rows)} v4 records; total is now {len(existing) + len(new_rows)}.")


if __name__ == "__main__":
    main()
