"""The Kit Ladder's data files, read and written the one way every kit tool and the game agree on (kit-ladder.md, Data).

    mod/data/marines/kit-ladder.json      the stations in campaign order, each with its reach condition, and one
                                          placement per card or machine pattern: {"item": <game id>, "station": <id>}
    mod/data/marines/loadout-cards.json   the weapon cards (twelve seats each) and tactic sheets (their suits)

Item ids are the game's own store ids (LoadoutCard.id, MechPattern.id). The Kit Ladder page keys the same items by its
older page ids (a card's definition id, "chassis:HOUND" and so on); page_id/game_id translate between the two.

write_ladder and write_cards are deterministic: one placement or seat per line, placements kept in their kind's block
and in campaign order (a stable sort, so an unmoved row never moves), LF endings and a trailing newline. Writing what
was read changes nothing.
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "src/main/java/com/dillon/starsectormarines"
LADDER_JSON = ROOT / "mod/data/marines/kit-ladder.json"
CARDS_JSON = ROOT / "mod/data/marines/loadout-cards.json"

CARD_PREFIX = "loadout-card:"
PATTERN_PREFIX = "mech-pattern:"
# Placement blocks, in file order: a game-id prefix each.
BLOCKS = [CARD_PREFIX + "weapon:", CARD_PREFIX + "sheet:", PATTERN_PREFIX + "chassis:", PATTERN_PREFIX + "weapon:",
          PATTERN_PREFIX + "internal:", PATTERN_PREFIX + "vehicle:", PATTERN_PREFIX + "kit:"]
# The page's pattern kinds and the Java enum each names.
PATTERN_ENUMS = {"chassis": "battle/mech/MechVariant.java", "weapon": "battle/mech/MechWeaponComponent.java",
                 "internal": "marine/MechInternal.java", "vehicle": "battle/vehicle/VehicleType.java",
                 "kit": "battle/vehicle/VehicleKit.java"}
# The closed set of reach conditions the game reads (UnlockStation.Reach), with the parameters each needs.
REACH_TYPES = {"always": (), "storyMission": ("mission",), "arcBeat": ("arc", "beat")}
ARC_BEATS = {
    "broken-supply-route": {"junction-won", "closed"},
    "corvus-account": {"dust-ledger", "blind-watch", "cold-account"},
    "shepherd-recovery": {"claim-secured", "repaired", "departed"},
    "wellhollow-bloom": {"colony-breach", "cordon-road", "the-burn", "broad-front"},
    "agreus": {"small-war-settled"},
}
# Special ids a saved page state may still carry from before the game renamed them: old id -> current id.
RETIRED_SPECIALS = {"special.rocket-launcher": "special.srm-1", "special.scrap-rocket": "special.srm-1-qs",
                    "special.missile-pod": "special.srm-3-pod"}
GRADES = ("SURPLUS", "SERVICE", "MILSPEC", "MASTERWORK")
SQUAD = 12
WEAPON_KEYS = ("role", "primary", "grade", "special", "belt")


class DataError(Exception):
    """A data file the game would refuse at load; the message names the file and the entry."""


def read(path):
    return Path(path).read_text(encoding="utf-8")


def load(path):
    try:
        return json.loads(read(path))
    except json.JSONDecodeError as e:
        raise DataError("%s: not valid JSON (%s)" % (Path(path).name, e))


def load_ladder(path=LADDER_JSON):
    return load(path)


def load_cards(path=CARDS_JSON):
    return load(path)


# ---------------------------------------------------------------------------------------------------------------
# Ids.

def pattern_ids(java=JAVA):
    """{page kind: {ENUM: game definition id}} read from the five machine enums."""
    out = {}
    for kind, rel in PATTERN_ENUMS.items():
        text = read(java / rel)
        if kind == "vehicle":
            body = text[text.index("public enum VehicleType"):]
            out[kind] = {e: e.lower() for e in re.findall(r"^    ([A-Z][A-Z_0-9]*)\s*\(", body, re.M)}
        else:
            out[kind] = dict(re.findall(r'^    ([A-Z][A-Z_0-9]*)\("([a-z][^"]*)"', text, re.M))
    return out


def page_id(game, enums):
    """The page's id for a game item id, or None for an id in no known shape."""
    if game.startswith(CARD_PREFIX + "weapon:"):
        return game[len(CARD_PREFIX + "weapon:"):]
    if game.startswith(CARD_PREFIX + "sheet:"):
        return game[len(CARD_PREFIX + "sheet:"):]
    if game.startswith(PATTERN_PREFIX):
        kind, _, def_id = game[len(PATTERN_PREFIX):].partition(":")
        for enum, value in (enums.get(kind) or {}).items():
            if value == def_id:
                return "%s:%s" % (kind, enum)
    return None


def game_id(page, enums, sheet_ids=()):
    """The game's id for a page id; None for a page id naming nothing the game has."""
    kind, _, rest = page.partition(":")
    if kind in enums:
        def_id = enums[kind].get(rest)
        return PATTERN_PREFIX + "%s:%s" % (kind, def_id) if def_id else None
    if page in sheet_ids or kind == "armor":
        return CARD_PREFIX + "sheet:" + page
    return CARD_PREFIX + "weapon:" + page


# ---------------------------------------------------------------------------------------------------------------
# Writing.

def block(item):
    for i, prefix in enumerate(BLOCKS):
        if item.startswith(prefix):
            return i
    return len(BLOCKS)


def ordered(placements, station_ids):
    """Placements in their kind's block, each block in campaign order, stable; 'none' last."""
    rank = {sid: i for i, sid in enumerate(station_ids)}
    rank["none"] = len(station_ids)
    return sorted(placements, key=lambda p: (block(p["item"]), rank.get(p["station"], len(station_ids) + 1)))


def _line(obj):
    return json.dumps(obj, ensure_ascii=False)


def dump_ladder(doc):
    station_ids = [s["id"] for s in doc["stations"]]
    out = ["{", '  "stations": [']
    out += ["    %s%s" % (_line(s), "," if i < len(doc["stations"]) - 1 else "") for i, s in enumerate(doc["stations"])]
    out += ["  ],", '  "placements": [']
    rows = ordered(doc["placements"], station_ids)
    out += ["    %s%s" % (_line({"item": p["item"], "station": p["station"]}), "," if i < len(rows) - 1 else "")
            for i, p in enumerate(rows)]
    out += ["  ]", "}", ""]
    return "\n".join(out)


def _seat(member, keys):
    return {k: member[k] for k in keys if member.get(k) is not None}


def _card(card, head, keys, list_key):
    lines = ["    {"]
    fields = [(k, card[k]) for k in head if card.get(k) is not None]
    for k, v in fields:
        lines.append("      %s: %s," % (_line(k), _line(v)))
    if list_key == "members":
        lines.append('      "members": [')
        ms = card.get("members") or []
        lines += ["        %s%s" % (_line(_seat(m, keys)), "," if i < len(ms) - 1 else "") for i, m in enumerate(ms)]
        lines.append("      ]")
    else:
        lines.append('      "suits": %s' % _line(card.get("suits") or []))
    lines.append("    }")
    return lines


SHEET_HEAD = ("id", "name", "description", "grant", "tradition", "mix", "armorClass", "seatClasses")
WEAPON_HEAD = ("id", "name", "description", "grant")


def dump_cards(doc):
    out = ["{", '  "weaponCards": [']
    for i, card in enumerate(doc["weaponCards"]):
        lines = _card(card, WEAPON_HEAD, WEAPON_KEYS, "members")
        if i < len(doc["weaponCards"]) - 1:
            lines[-1] += ","
        out += lines
    out += ["  ],", '  "tacticSheets": [']
    for i, sheet in enumerate(doc["tacticSheets"]):
        lines = _card(sheet, SHEET_HEAD, (), "suits")
        if i < len(doc["tacticSheets"]) - 1:
            lines[-1] += ","
        out += lines
    out += ["  ]", "}", ""]
    return "\n".join(out)


def write_ladder(doc, path=LADDER_JSON):
    Path(path).write_text(dump_ladder(doc), encoding="utf-8", newline="\n")


def write_cards(doc, path=CARDS_JSON):
    Path(path).write_text(dump_cards(doc), encoding="utf-8", newline="\n")


# ---------------------------------------------------------------------------------------------------------------
# Validation: the same rules KitLadderCatalog enforces when the game loads, so a tool run finds them first.

def validate(ladder_doc, cards_doc, known=None):
    """Error strings, each naming the file and the entry. ``known`` (optional) holds the real ids the data may name:
    {"primaries", "specials", "thrown", "armor", "patterns", "strings", "missions"} (sets); a missing key skips that check."""
    known = known or {}
    errs = []
    L, C = "kit-ladder.json", "loadout-cards.json"
    stations = ladder_doc.get("stations") or []
    if not stations:
        errs.append("%s: no stations" % L)
    station_ids = []
    for i, s in enumerate(stations):
        sid = s.get("id")
        where = "%s: stations[%d] (%s)" % (L, i, sid)
        if not sid or sid == "none":
            errs.append("%s: a station needs an id other than 'none'" % where)
        if sid in station_ids:
            errs.append("%s: station declared twice" % where)
        station_ids.append(sid)
        reach = s.get("reach") or {}
        rtype = reach.get("type")
        if rtype not in REACH_TYPES:
            errs.append("%s: reach type %r is not one of %s" % (where, rtype, sorted(REACH_TYPES)))
            continue
        for p in REACH_TYPES[rtype]:
            if not reach.get(p):
                errs.append("%s: reach %s needs %r" % (where, rtype, p))
        if rtype == "arcBeat" and reach.get("arc") and reach.get("beat"):
            if reach["arc"] not in ARC_BEATS:
                errs.append("%s: arc %r is not one of %s" % (where, reach["arc"], sorted(ARC_BEATS)))
            elif reach["beat"] not in ARC_BEATS[reach["arc"]]:
                errs.append("%s: arc %s has no beat %r (%s)" % (where, reach["arc"], reach["beat"], sorted(ARC_BEATS[reach["arc"]])))
        if rtype == "storyMission" and "missions" in known and reach.get("mission") not in known["missions"]:
            errs.append("%s: story mission %r is not a story mission id" % (where, reach.get("mission")))
        if i == 0 and (sid != "start" or rtype != "always"):
            errs.append("%s: the first station must be 'start', reached always" % where)
    valid_stations = set(station_ids) | {"none"}

    cards = set()
    for lst, kind in (("weaponCards", "weapon"), ("tacticSheets", "sheet")):
        for i, card in enumerate(cards_doc.get(lst) or []):
            cid = card.get("id")
            where = "%s: %s[%d] (%s)" % (C, lst, i, cid)
            if not cid:
                errs.append("%s: a card needs an id" % where)
                continue
            game = CARD_PREFIX + kind + ":" + cid
            if game in cards:
                errs.append("%s: card declared twice" % where)
            cards.add(game)
            for key in ("name", "description"):
                text = card.get(key)
                if not text:
                    errs.append("%s: needs a %s" % (where, key))
                elif not text.startswith("@"):
                    errs.append("%s: %s must be a string key (@key), not literal text" % (where, key))
                elif "strings" in known and text[1:] not in known["strings"]:
                    errs.append("%s: %s key %s is not in strings.json" % (where, key, text))
            if card.get("grant", "sale") not in ("sale", "free-pick"):
                errs.append("%s: grant %r is not sale or free-pick" % (where, card.get("grant")))
            if kind == "weapon":
                members = card.get("members") or []
                if len(members) != SQUAD:
                    errs.append("%s: has %d seats; a squad has %d" % (where, len(members), SQUAD))
                for n, m in enumerate(members):
                    seat = "%s seat %d" % (where, n + 1)
                    extra = set(m) - set(WEAPON_KEYS)
                    if extra:
                        errs.append("%s: unknown field(s) %s" % (seat, sorted(extra)))
                    if not m.get("role"):
                        errs.append("%s: needs a role" % seat)
                    if m.get("grade") not in GRADES:
                        errs.append("%s: grade %r is not one of %s" % (seat, m.get("grade"), list(GRADES)))
                    if "primaries" in known and m.get("primary") not in known["primaries"]:
                        errs.append("%s: primary %r is not a marine primary weapon" % (seat, m.get("primary")))
                    if m.get("special") and "specials" in known and m["special"] not in known["specials"]:
                        errs.append("%s: special %r is not a special-equipment id" % (seat, m["special"]))
                    if m.get("belt") and "thrown" in known and m["belt"] not in known["thrown"]:
                        errs.append("%s: belt %r is not a thrown special" % (seat, m["belt"]))
            else:
                suits = card.get("suits") or []
                if not suits:
                    errs.append("%s: has no suits" % where)
                if len(set(suits)) != len(suits):
                    errs.append("%s: names a suit twice" % where)
                for s in suits:
                    if "armor" in known and s not in known["armor"]:
                        errs.append("%s: suit %r is not an armour id" % (where, s))
                for key in ("tradition", "mix"):
                    if not card.get(key):
                        errs.append("%s: needs a %s" % (where, key))
                if card.get("armorClass", "STANDARD") not in ("STANDARD", "POWER_SUIT"):
                    errs.append("%s: armorClass %r is not STANDARD or POWER_SUIT" % (where, card.get("armorClass")))
                seats = card.get("seatClasses")
                if seats is not None:
                    if len(seats) != SQUAD or any(c not in ("STANDARD", "POWER_SUIT") for c in seats):
                        errs.append("%s: seatClasses must be %d of STANDARD or POWER_SUIT" % (where, SQUAD))
                    if "armorClass" in card:
                        errs.append("%s: give armorClass or seatClasses, not both" % where)

    seen = set()
    for i, p in enumerate(ladder_doc.get("placements") or []):
        item, st = p.get("item"), p.get("station")
        where = "%s: placements[%d] (%s)" % (L, i, item)
        if item in seen:
            errs.append("%s: placed twice" % where)
        seen.add(item)
        if st not in valid_stations:
            errs.append("%s: station %r is not a declared station or 'none'" % (where, st))
        if item and item.startswith(CARD_PREFIX):
            if item not in cards:
                errs.append("%s: names no card in %s" % (where, C))
        elif item and item.startswith(PATTERN_PREFIX):
            if "patterns" in known and item not in known["patterns"]:
                errs.append("%s: names no machine pattern the store can sell" % where)
        else:
            errs.append("%s: is neither a loadout-card: nor a mech-pattern: id" % where)
    for card in sorted(cards):
        if card not in seen:
            errs.append("%s: card %s has no placement (place it, or on 'none')" % (L, card))
    return errs
