"""شبیه‌سازِ پایتونیِ قواعد حکم — آینهٔ دقیقِ HokmSession.kt برای راستی‌آزمایی منطق.

چرا؟ در این محیط JDK/Gradle در دسترس نیست، پس برای اینکه مطمئن شویم قواعد واقعاً درست‌اند،
همان منطق را اینجا پیاده و روی هزاران دست اجرا می‌کنیم (تستِ واقعی، نه حدس).
"""
from __future__ import annotations

M64 = (1 << 64) - 1
SUITS = ["SPADES", "HEARTS", "DIAMONDS", "CLUBS"]  # ordinal 0..3 (همان ترتیب enum)
SUIT_OF_CODE = {"S": "SPADES", "H": "HEARTS", "D": "DIAMONDS", "C": "CLUBS"}
RED = {"HEARTS", "DIAMONDS"}
RANKS = list(range(2, 15))  # 2..14 (14 = آس)
LABEL = {11: "J", 12: "Q", 13: "K", 14: "A", 10: "10"}  # همان Rank.label در کاتلین


def full_deck():
    return [(s, r) for s in SUITS for r in RANKS]


def card_code(card):
    suit, rank = card
    return f"{LABEL.get(rank, str(rank))}{suit[0]}"


class SeededRng:
    """splitmix64 — همان پیاده‌سازی SeededRng.kt."""

    def __init__(self, seed):
        self.state = seed & M64

    def next_long(self):
        self.state = (self.state + 0x9E3779B97F4A7C15) & M64
        z = self.state
        z = ((z ^ (z >> 30)) * 0xBF58476D1CE4E5B9) & M64
        z = ((z ^ (z >> 27)) * 0x94D049BB133111EB) & M64
        return z ^ (z >> 31)

    def next_int(self, bound):
        return ((self.next_long() >> 1) % bound)

    def shuffled(self, items):
        result = list(items)
        for i in range(len(result) - 1, 0, -1):
            j = self.next_int(i + 1)
            result[i], result[j] = result[j], result[i]
        return result


def strength(card, trump, led):
    suit, rank = card
    base = 100 if suit == trump else (0 if suit == led else -100)
    return base + rank


class HokmGame:
    """قواعدِ حکم، آینهٔ HokmSession."""

    PLAYERS = 4
    TEAMS = 2
    TRICKS_IN_HAND = 13
    TRICKS_TO_WIN = 7
    FIRST_ROUND = 5
    LATER_ROUNDS = 4

    def __init__(self, seed, target_points=7, hand_end="seven", hokm_lazim="off",
                 kot_hakem=2, kot_opponent=3, scripted_hands=None, skip_flip=False):
        self.rng = SeededRng(seed)
        self.target_points = target_points
        self.hand_end = hand_end
        self.hokm_lazim = hokm_lazim
        self.kot_hakem = kot_hakem
        self.kot_opponent = kot_opponent
        self.players = [f"p{i}" for i in range(4)]
        self.team_scores = [0, 0]
        self.hand_number = 1
        self.tricks_won = [0, 0]
        self.trump = None
        self.trick = []
        self.turn = 0
        self.hands = [[] for _ in range(4)]
        self.last_hand = None
        self.finished = False
        self.winner_team = None
        self.stats = {"hands": 0, "kots": 0, "hakem_kotis": 0}

        self.scripted_hands = scripted_hands or []
        self.scripted_index = 0
        if skip_flip:
            self.seats = list(self.players)
            self.deck = self.rng.shuffled(full_deck())
            self.deck_cursor = 0
        else:
            flip = self._determine_hakem()
            self.seats = self._build_seating(flip["hakem"], flip["partner"])
            self.deck = flip["deck"]
            self.deck_cursor = 0
        self.hakem = 0
        self._deal_first_round()

    # --- setup -------------------------------------------------------------
    def _determine_hakem(self):
        shuffled = self.rng.shuffled(full_deck())
        cursor = 0
        pointer = 0
        aces = []
        revealed = []
        while len(aces) < 2 and cursor < len(shuffled) and pointer < 500:
            player_index = pointer % 4
            pointer += 1
            if player_index in aces:
                continue
            card = shuffled[cursor]
            cursor += 1
            revealed.append(card)
            if card[1] == 14:
                aces.append(player_index)
        hakem = aces[0] if aces else 0
        partner = aces[1] if len(aces) > 1 else (hakem + 2) % 4
        rest = shuffled[cursor:]
        full = rest + revealed
        return {"hakem": hakem, "partner": partner, "deck": self.rng.shuffled(full)}

    def _build_seating(self, hakem_index, partner_index):
        others = [(hakem_index + i) % 4 for i in range(1, 4) if (hakem_index + i) % 4 != partner_index]
        order = [hakem_index, others[0], partner_index, others[1]]
        return [self.players[i] for i in order]

    def _draw(self):
        card = self.deck[self.deck_cursor]
        self.deck_cursor += 1
        return card

    def _deal_to(self, seat, count):
        if self.scripted_hands and self.scripted_index < len(self.scripted_hands):
            target = self.scripted_hands[self.scripted_index][seat]
            already = len(self.hands[seat])
            self.hands[seat].extend(target[already:already + count])
            return
        for _ in range(count):
            self.hands[seat].append(self._draw())

    def _deal_order(self):
        return [(self.hakem + i) % 4 for i in range(4)]

    def _deal_first_round(self):
        partner = (self.hakem + 2) % 4
        for seat in self._deal_order():
            if seat == partner:
                continue
            self._deal_to(seat, self.FIRST_ROUND)
        self.stage = "trump"

    def _deal_rest(self):
        partner = (self.hakem + 2) % 4
        for _ in range(2):
            for seat in self._deal_order():
                self._deal_to(seat, self.LATER_ROUNDS)
        self._deal_to(partner, self.FIRST_ROUND)

    # --- actions -----------------------------------------------------------
    def declare_trump(self, seat, suit):
        assert self.stage == "trump", "wrong phase"
        assert seat == self.hakem, "only hakem"
        self.trump = suit
        self._deal_rest()
        self.stage = "playing"
        self.turn = self.hakem
        self.trick = []

    def legal_cards(self, seat):
        hand = self.hands[seat]
        if not self.trick:
            return list(hand)
        led = self.trick[0][1][0]
        following = [c for c in hand if c[0] == led]
        if following:
            return following
        if self.hokm_lazim == "overtrump":
            played_trumps = [c for (_, c) in self.trick if c[0] == self.trump]
            if played_trumps:
                highest = max(c[1] for c in played_trumps)
                over = [c for c in hand if c[0] == self.trump and c[1] > highest]
                if over:
                    return over
        return list(hand)

    def play_card(self, seat, card):
        assert self.stage == "playing"
        assert seat == self.turn, f"turn is {self.turn}, got {seat}"
        assert card in self.hands[seat], "card not in hand"
        legal = self.legal_cards(seat)
        assert card in legal, f"illegal card {card_code(card)}; legal={[card_code(c) for c in legal]}"
        self.hands[seat].remove(card)
        self.trick.append((seat, card))
        if len(self.trick) == 4:
            self._resolve_trick()
        else:
            self.turn = (seat + 1) % 4

    def _resolve_trick(self):
        led = self.trick[0][1][0]
        winner = max(self.trick, key=lambda p: strength(p[1], self.trump, led))[0]
        team = winner % 2
        self.tricks_won[team] += 1
        self.trick = []
        self.trick_number = getattr(self, "trick_number", 1) + 1
        if self.hand_end == "seven":
            hand_over = self.tricks_won[0] >= 7 or self.tricks_won[1] >= 7
        else:
            hand_over = self.trick_number > 13
        if hand_over:
            self._score_hand()
        else:
            self.turn = winner

    def _score_hand(self):
        winner_team = 0 if self.tricks_won[0] > self.tricks_won[1] else 1
        loser_team = 1 - winner_team
        hakem_team = self.hakem % 2
        is_kot = self.tricks_won[loser_team] == 0 and (
            self.hand_end == "seven" or self.tricks_won[winner_team] == 13
        )
        is_hakem_koti = is_kot and winner_team != hakem_team
        if not is_kot:
            points = 1
        elif winner_team == hakem_team:
            points = self.kot_hakem
        else:
            points = self.kot_opponent
        self.team_scores[winner_team] += points
        self.last_hand = {
            "number": self.hand_number,
            "winner": winner_team,
            "tricks": list(self.tricks_won),
            "points": points,
            "kot": is_kot,
            "hakem_koti": is_hakem_koti,
        }
        self.stats["hands"] += 1
        if is_kot:
            self.stats["kots"] += 1
        if is_hakem_koti:
            self.stats["hakem_kotis"] += 1
        if self.team_scores[winner_team] >= self.target_points:
            self.finished = True
            self.winner_team = winner_team
            return
        if winner_team != hakem_team:
            self.hakem = (self.hakem + 1) % 4
        self.hand_number += 1
        self.tricks_won = [0, 0]
        self.trick = []
        self.trick_number = 1
        self.trump = None
        self.stage = "trump"
        self.hands = [[] for _ in range(4)]
        if self.scripted_hands and self.scripted_index < len(self.scripted_hands):
            self.scripted_index += 1
        self.deck = self.rng.shuffled(full_deck())
        self.deck_cursor = 0
        self._deal_first_round()
