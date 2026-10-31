"""The Kit Ladder's data files, read and written the one way every kit tool and the game agree on (kit-ladder.md, Data).

    mod/data/marines/kit-ladder.json      the stations in campaign order, each with its reach condition, and one
                                          placement per card, machine pattern or shuttle (boat) part:
                                          {"item": <game id>, "station": <id>}
    mod/data/marines/loadout-cards.json   the weapon cards (twelve seats each) and tactic sheets (their twelve seated
                                          suits, or a set of suits the game deals by role)

Item ids are the game's own store ids (LoadoutCard.id, MechPattern.id, and "boat-part:<part id>" for a BoatFitting or
BoatGun the store can sell). The Kit Ladder page keys the same items by its older page ids (a card's definition id,
"chassis:HOUND", "boat:<part id>" and so on); page_id/game_id translate between the two. Every machine item (every
machine pattern and every sellable shuttle part) must be placed; a loadout card may sit on "none".

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
BOAT_PREFIX = "boat-part:"
BOAT_PAGE = "boat:"
# Placement blocks, in file order: a game-id prefix each.
BLOCKS = [CARD_PREFIX + "weapon:", CARD_PREFIX + "sheet:", PATTERN_PREFIX + "chassis:", PATTERN_PREFIX + "weapon:",
          PATTERN_PREFIX + "internal:", PATTERN_PREFIX + "vehicle:", BOAT_PREFIX]
# The guns a new boat leaves the yard with (BoatGun.standardFor: the A2G sortie kits ShuttleType.kitFor names for the
# hardpoint counts the shuttle patterns have). Known to every company and never sold, like the tier-1 fittings and
# BoatParts.COMPANY_ISSUE. A constant rather than parsed, because each kit depends on a hull's hardpoint count; keep it
# in step with BoatParts.starterIds (KitLadderCatalog refuses a placement naming a starter part, so a drift shows).
STANDARD_BOAT_GUNS = {"door_heavy_mg", "door_arbalest", "door_grenade_launcher"}
# The page's pattern kinds and the Java enum each names.
PATTERN_ENUMS = {"chassis": "battle/mech/MechVariant.java", "weapon": "battle/mech/MechWeaponComponent.java",
                 "internal": "marine/MechInternal.java", "vehicle": "battle/vehicle/VehicleType.java"}
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
# Page ids and weapon-reel clip ids a saved page state, a published page or its weapon notes may still carry from before
# the game renamed the lasers to BL, PL and LL (mechs-nouns.md, Weapon names): old id -> current id. ladder.load_state,
# ladder.load_base and export_state.load_notes read them as their successors, so a rename is never an orphan.
RETIRED_PAGE_IDS = {
    "weapon:LIGHT_PULSE_LASER": "weapon:BL_1", "weapon:PD_LASER": "weapon:BL_2",
    "weapon:LIGHT_BURST_LASER": "weapon:PL_1", "weapon:MEDIUM_BURST_LASER": "weapon:PL_2",
    "weapon:BURST_LASER": "weapon:PL_3", "weapon:PULSE_LASER": "weapon:PL_2_AP",
    "weapon:HEAVY_PD_LASER": "weapon:PL_3_SWEEP", "weapon:SHOULDER_LASER_CANNON": "weapon:LL_3",
}
RETIRED_CLIPS = {
    "mech-light-pulse-laser": "mech-bl-1", "mech-pd-laser": "mech-bl-2", "mech-light-burst-laser": "mech-pl-1",
    "mech-medium-burst-laser": "mech-pl-2", "mech-burst-laser": "mech-pl-3", "mech-pulse-laser": "mech-pl-2-ap",
    "mech-heavy-pd-laser": "mech-pl-3-sweep", "mech-shoulder-laser-cannon": "mech-ll-3",
    "vehicle-laser-tank-heavy-pd-laser": "vehicle-laser-tank-pl-3-sweep",
}
# Weapon ids the same rename retired, for a note or clip that still names one.
RETIRED_WEAPONS = {
    "weapon.mech-light-pulse-laser": "weapon.mech-bl-1", "weapon.mech-pd-laser": "weapon.mech-bl-2",
    "weapon.mech-light-burst-laser": "weapon.mech-pl-1", "weapon.mech-medium-burst-laser": "weapon.mech-pl-2",
    "weapon.mech-burst-laser": "weapon.mech-pl-3", "weapon.mech-pulse-laser": "weapon.mech-pl-2-ap",
    "weapon.mech-heavy-pd-laser": "weapon.mech-pl-3-sweep", "weapon.mech-shoulder-laser": "weapon.mech-ll-3",
}


def current_key(key):
    """A page id, "clip:<clip id>" key or bare clip id read as its current id (RETIRED_PAGE_IDS, RETIRED_CLIPS)."""
    if key in RETIRED_PAGE_IDS:
        return RETIRED_PAGE_IDS[key]
    if key.startswith("clip:") and key[5:] in RETIRED_CLIPS:
        return "clip:" + RETIRED_CLIPS[key[5:]]
    return RETIRED_CLIPS.get(key, key)


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
    """{page kind: {ENUM: game definition id}} read from the four machine enums."""
    out = {}
    for kind, rel in PATTERN_ENUMS.items():
        text = read(java / rel)
        if kind == "vehicle":
            body = text[text.index("public enum VehicleType"):]
            out[kind] = {e: e.lower() for e in re.findall(r"^    ([A-Z][A-Z_0-9]*)\s*\(", body, re.M)}
        else:
            out[kind] = dict(re.findall(r'^    ([A-Z][A-Z_0-9]*)\("([a-z][^"]*)"', text, re.M))
    return out


def boat_parts(java=JAVA):
    """{part id: {const, nameKey, tier, slot (a fitting's BoatFittingSlot, else None), family (a gun's, else None),
    starter}} for every BoatFitting and BoatGun constant, in declaration order."""
    fit_src = read(java / "marine/BoatFitting.java")
    gun_src = read(java / "marine/BoatGun.java")
    out = {}
    for const, pid, key, slot, tier in re.findall(
            r'public static final BoatFitting (\w+) = new BoatFitting\(\s*"([a-z0-9_]+)",\s*"(@[^"]+)",'
            r'\s*BoatFittingSlot\.(\w+),\s*(\d+)', fit_src):
        out[pid] = {"const": const, "nameKey": key, "tier": int(tier), "slot": slot, "family": None,
                    "starter": int(tier) == 1}
    if not out:
        raise DataError("BoatFitting.java: no 'new BoatFitting(\"id\", \"@key\", BoatFittingSlot.X, tier' constants")
    issue = re.search(r"COMPANY_ISSUE\s*=\s*List\.of\(([^)]*)\)", read(java / "marine/BoatParts.java"))
    if not issue:
        raise DataError("BoatParts.java: no 'COMPANY_ISSUE = List.of(...)' line")
    by_const = {v["const"]: pid for pid, v in out.items()}
    for const in re.findall(r"BoatFitting\.(\w+)", issue.group(1)):
        if const not in by_const:
            raise DataError("BoatParts.java: COMPANY_ISSUE names %s, which BoatFitting.java does not declare" % const)
        out[by_const[const]]["starter"] = True
    guns = re.findall(r'public static final BoatGun (\w+) = new BoatGun\(\s*"([a-z0-9_]+)",\s*"(@[^"]+)",\s*[\w.]+,'
                      r'\s*(\d+),[^;]*?Family\.(\w+)', gun_src)
    if not guns:
        raise DataError("BoatGun.java: no 'new BoatGun(\"id\", \"@key\", <structure>, tier, ... Family.X' constants")
    for const, pid, key, tier, family in guns:
        out[pid] = {"const": const, "nameKey": key, "tier": int(tier), "slot": None, "family": family,
                    "starter": pid in STANDARD_BOAT_GUNS}
    missing = STANDARD_BOAT_GUNS - {g[1] for g in guns}
    if missing:
        raise DataError("kitdata.STANDARD_BOAT_GUNS names %s, which BoatGun.java does not declare" % sorted(missing))
    return out


def offerable_boat_parts(parts=None):
    """The part ids the store may sell (BoatParts.offerable): every part but the starter set, in declaration order."""
    parts = parts if parts is not None else boat_parts()
    return [pid for pid, p in parts.items() if not p["starter"]]


def boat_access(tier):
    """BoatParts.accessTier: tier 2 or below is Common, 3 Advanced, 4 Prestige."""
    return "COMMON" if tier <= 2 else "ADVANCED" if tier == 3 else "PRESTIGE"


def page_id(game, enums):
    """The page's id for a game item id, or None for an id in no known shape."""
    if game.startswith(BOAT_PREFIX):
        part = game[len(BOAT_PREFIX):]
        return BOAT_PAGE + part if part else None
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
    if page.startswith(BOAT_PAGE):
        return BOAT_PREFIX + page[len(BOAT_PAGE):]
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


def sheet_suits(sheet):
    """The suits a tactic sheet teaches: its seats' distinct suits in seat order, else its ``suits``."""
    if sheet.get("seats") is not None:
        out = []
        for s in sheet["seats"]:
            if s not in out:
                out.append(s)
        return out
    return list(sheet.get("suits") or [])


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
    elif card.get("seats") is not None:
        lines.append('      "seats": %s' % _line(card["seats"]))
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
    {"primaries", "specials", "thrown", "armor", "patterns", "boatParts" (sellable part ids, no prefix), "machines" (every
    machine game id that must be placed: patterns and sellable boat parts), "strings", "missions"} (sets); a missing key
    skips that check."""
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
            elif card.get("seats") is not None:
                seats = card["seats"]
                if len(seats) != SQUAD:
                    errs.append("%s: seats has %d suits; a squad has %d" % (where, len(seats), SQUAD))
                for derived in ("suits", "armorClass", "seatClasses"):
                    if derived in card:
                        errs.append("%s: give seats or %s, not both (a seated sheet's %s come from its seats)"
                                    % (where, derived, derived))
                for n, s in enumerate(seats):
                    if "armor" in known and s not in known["armor"]:
                        errs.append("%s seat %d: suit %r is not an armour id" % (where, n + 1, s))
                for key in ("tradition", "mix"):
                    if not card.get(key):
                        errs.append("%s: needs a %s" % (where, key))
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
        elif item and item.startswith(BOAT_PREFIX):
            if "boatParts" in known and item[len(BOAT_PREFIX):] not in known["boatParts"]:
                errs.append("%s: names no boat part the store can sell" % where)
        else:
            errs.append("%s: is not a %s, %s or %s id" % (where, CARD_PREFIX, PATTERN_PREFIX, BOAT_PREFIX))
    for card in sorted(cards):
        if card not in seen:
            errs.append("%s: card %s has no placement (place it, or on 'none')" % (L, card))
    for machine in sorted(known.get("machines") or ()):
        if machine not in seen:
            errs.append("%s: machine %s has no placement (place it, or on 'none')" % (L, machine))
    return errs
