"""بررسیِ سناریوهای دست‌سازِ تست‌های کاتلین (تا در CI سبز بمانند)."""
from hokm import HokmGame, card_code

S = {"S": "SPADES", "H": "HEARTS", "D": "DIAMONDS", "C": "CLUBS"}


MAP = {"A": 14, "K": 13, "Q": 12, "J": 11, "10": 10}  # همان Rank.label در کاتلین


def rank_of(label):
    return MAP[label] if label in MAP else int(label)


def parse(code):
    return (S[code[-1]], rank_of(code[:-1]))


def hand(*codes):
    return [parse(c) for c in codes]


def make(hands, **kw):
    return HokmGame(1, scripted_hands=[hands], skip_flip=True, **kw)


def seq(game, *codes):
    for code in codes:
        game.play_card(game.turn, parse(code))


def trick(game, a, b, c, d):
    seq(game, a, b, c, d)


# ---------------------------------------------------------------------------
single = [
    hand(*"AS KS QS JS 10S 9S 8S 7S 6S 5S 4S 3S 2S".split()),
    hand(*"AH KH QH JH 10H 9H 8H 7H 6H 5H 4H 3H 2H".split()),
    hand(*"AD KD QD JD 10D 9D 8D 7D 6D 5D 4D 3D 2D".split()),
    hand(*"AC KC QC JC 10C 9C 8C 7C 6C 5C 4C 3C 2C".split()),
]

# ۱) دوی حکم از آسِ خالِ دیگر قوی‌تر است
g = make(single)
assert [len(h) for h in g.hands] == [5, 5, 0, 5], [len(h) for h in g.hands]
g.declare_trump(0, "HEARTS")
assert [len(h) for h in g.hands] == [13, 13, 13, 13]
trick(g, "AS", "2H", "2D", "2C")
assert g.tricks_won == [0, 1], g.tricks_won
print("✅ ۱) ۲ِ حکم، آسِ پیک را می‌برد")

# ۲) جواب دادنِ خال
follow = [
    hand(*"AH KH QH JH 10H 9H 8H 7H 6H 5H 4H 3H 2S".split()),
    hand(*"AS KS QS JS 10S 9S 8S 7S 6S 5S 4S 3S 2H".split()),
    hand(*"AD KD QD JD 10D 9D 8D 7D 6D 5D 4D 3D 2D".split()),
    hand(*"AC KC QC JC 10C 9C 8C 7C 6C 5C 4C 3C 2C".split()),
]
g = make(follow)
g.declare_trump(0, "CLUBS")
seq(g, "5H")
legal = g.legal_cards(1)
assert legal == [(S["H"], 2)], [card_code(c) for c in legal]
assert (S["S"], 14) not in legal, "پیک نباید مجاز باشد وقتی دل دارد"
seq(g, "2H", "2D", "2C")
assert g.tricks_won == [0, 1], g.tricks_won
print("✅ ۲) وقتی خالِ زمین را دارد باید همان را بازی کند (و برنده با حکم است)")

# ۳) کوتِ تیمِ حاکم: ۷-۰ ⇒ ۲ امتیاز، حاکم سر جایش
scoring = [
    hand(*"AS KS QS JS 10S 9S 8S AC KC QC JC 10C 9C".split()),
    hand(*"8C 7C 6C 5C 4C 3C 2C 7D 6D 5D 4D 3D 2D".split()),
    hand(*"AD KD QD JD 10D 9D 8D 7S 6S 5S 4S 3S 2S".split()),
    hand(*"AH KH QH JH 10H 9H 8H 7H 6H 5H 4H 3H 2H".split()),
]
g = make(scoring)
g.declare_trump(0, "SPADES")
for i in range(6):
    trick(g, "AS KS QS JS 10S 9S".split()[i], "2C 3C 4C 5C 6C 7C".split()[i],
          "2S 3S 4S 5S 6S 7S".split()[i], "2H 3H 4H 5H 6H 7H".split()[i])
assert g.tricks_won == [6, 0], g.tricks_won
trick(g, "8S", "8C", "AD", "8H")
assert g.last_hand == {"number": 1, "winner": 0, "tricks": [7, 0], "points": 2,
                       "kot": True, "hakem_koti": False}, g.last_hand
assert g.team_scores == [2, 0], g.team_scores
assert g.hakem == 0
assert [len(h) for h in g.hands] == [5, 5, 0, 5], [len(h) for h in g.hands]
print("✅ ۳) هفتِ اول ⇒ کوت ۲ امتیاز؛ حاکم عوض نمی‌شود و یارش کارت نمی‌گیرد")

# ۴) حاکم‌کُتی: ۰-۷ ⇒ ۳ امتیاز، حاکم به نفرِ بعدی
koti = [
    hand(*"AH KH QH JH 10H 9H 8H 7H 6H 5H 4H 3H 2H".split()),
    hand(*"AS KS QS JS 10S 9S 8S AC KC QC JC 10C 9C".split()),
    hand(*"8C 7C 6C 5C 4C 3C 2C AD KD QD JD 10D 9D".split()),
    hand(*"7S 6S 5S 4S 3S 2S 8D 7D 6D 5D 4D 3D 2D".split()),
]
g = make(koti)
g.declare_trump(0, "SPADES")
trick(g, "2H", "AS", "2C", "2D")
assert g.tricks_won == [0, 1], g.tricks_won
for i in range(6):
    trick(g, "KS QS JS 10S 9S 8S".split()[i], "3C 4C 5C 6C 7C 8C".split()[i],
          "2S 3S 4S 5S 6S 7S".split()[i], "3H 4H 5H 6H 7H 8H".split()[i])
assert g.last_hand == {"number": 1, "winner": 1, "tricks": [0, 7], "points": 3,
                       "kot": True, "hakem_koti": True}, g.last_hand
assert g.team_scores == [0, 3], g.team_scores
assert g.hakem == 1, g.hakem
partner = (g.hakem + 2) % 4
counts = [len(h) for h in g.hands]
assert counts[partner] == 0 and counts == [5, 5, 5, 0], counts
print("✅ ۴) حاکم‌کُتی ۳ امتیاز؛ حاکم به نفرِ بعد رفت و یارِ جدیدش کارت نگرفت")

# ۵) بردِ معمولی: ۷-۱ ⇒ ۱ امتیاز
normal = [
    hand(*"KS QS JS 10S 9S 8S 7S AC KC QC JC 10C 9C".split()),
    hand(*"AS AH KH QH JH 10H 9H 8H 7H 6H 5H 4H 3H".split()),
    hand(*"6S 5S 4S 3S 2S AD KD QD JD 10D 9D 8D 7D".split()),
    hand(*"8C 7C 6C 5C 4C 3C 2C 6D 5D 4D 3D 2D 2H".split()),
]
g = make(normal)
g.declare_trump(0, "SPADES")
trick(g, "KS", "AS", "2S", "3C")                 # تیمِ حریف با آسِ حکم می‌برد
assert g.tricks_won == [0, 1], g.tricks_won
trick(g, "AH", "3S", "2H", "7S")                 # حاکم با حکم پس می‌گیرد
trick(g, "QS", "KH", "4S", "4C")
trick(g, "JS", "QH", "5S", "5C")
trick(g, "10S", "JH", "6S", "6C")
trick(g, "9S", "10H", "KD", "7C")
trick(g, "8S", "9H", "QD", "8C")
trick(g, "AC", "8H", "AD", "2C")
assert g.last_hand == {"number": 1, "winner": 0, "tricks": [7, 1], "points": 1,
                       "kot": False, "hakem_koti": False}, g.last_hand
assert g.team_scores == [1, 0]
assert g.hakem == 0
print("✅ ۵) بردِ ۷-۱ ⇒ کوت نیست و ۱ امتیاز دارد")

# ۶) حکم لازم
lazim = [
    hand(*"AH KH QH JH 10H 9H 8H 7H 6H 5H 4H 3H 2H".split()),
    hand(*"AC KC QC JC 10C 9C 8C 7C 6C 5C 4C 3C 3S".split()),
    hand(*"AS KS QS JS 10S 9S 8S 7S 6S 5S 4S 2S 2C".split()),
    hand(*"AD KD QD JD 10D 9D 8D 7D 6D 5D 4D 3D 2D".split()),
]
g = make(lazim, hokm_lazim="overtrump")
g.declare_trump(0, "SPADES")
seq(g, "5H", "3S")
legal = sorted(card_code(c) for c in g.legal_cards(2))
assert legal == sorted(["4S", "5S", "6S", "7S", "8S", "9S", "10S", "JS", "QS", "KS", "AS"]), legal
g2 = make(lazim)
g2.declare_trump(0, "SPADES")
seq(g2, "5H", "3S")
assert len(g2.legal_cards(2)) == 13
print("✅ ۶) حکمِ لازم فقط حکم‌های بالاتر را مجاز می‌کند (و در حالت عادی همه مجازند)")

print("\nهمهٔ سناریوهای تست‌های کاتلین درست از آب درآمدند ✅")
