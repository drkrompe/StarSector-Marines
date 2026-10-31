"""Check the Kit Ladder: the game's data (kit-ladder.json, loadout-cards.json), the items it places, and the page
built from it.

    python tools/kits/check.py [--page <page.html>] [--reel <weapon-reel/index.json>] [--md <out.md>]
    python tools/kits/check.py --self-test

Pure Python over the repository's files (and, when given, a page HTML an Artifact read saved); about a second.
Exits 1 on an error, 0 otherwise; warnings feed the owner's decisions (kit-ladder.md, Checks).

Errors: anything the game refuses when it loads the data (kitdata.validate: an unknown station, reach condition,
item, weapon, special, belt item or suit, an item placed twice, a card with no placement, a squad not of twelve, a
sheet with no suits, a missing string key), a real item with no placement, a build.py station list out of step with
kit-ladder.json, a Wayline free pick off Relief, a reel clip firing a weapon no *.weapon.json defines.

Warnings (codes, so tools/kits/check-allow.json can accept one with a reason as "<code> <id>"):
  carrier      a machine part sold before any machine (a mech chassis or vehicle hull) can carry it, or a mech weapon
               before any chassis
  restricted   a Restricted card placed on a station: the store still needs a warrant naming it
  start-tier   a start item above Common
  not-sold     an item on "none" with no story or encounter route found in code (hint: where it is referenced)
  stale        an item whose name, stats or contents changed since the page was published, or whose station the
               published page seeds differently (republish); needs --page or tools/kits/review/published.json
  no-clip      a weapon in reel scope (marine primaries and secondaries, the weapons mech parts fire) with no clip
               and not left out
  no-upload    a clip with no uploaded asset url on the published page
  orphan-note  a weapon note (tools/kits/review/weapon-notes.json) for a clip the page no longer has
  gap          a known Kit Ladder gap still open in code (the collection curve and reachability ignore the ladder)
"""
import argparse
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import kitdata  # noqa: E402
import ladder  # noqa: E402
import build  # noqa: E402

ALLOW = Path(__file__).resolve().parent / "check-allow.json"
NOTES = ladder.REVIEW / "weapon-notes.json"
RANK = {"COMMON": 0, "ADVANCED": 1, "PRESTIGE": 2, "RESTRICTED": 3}
NOT_KIT_PRIMARY = {"weapon.drone-pulse", "weapon.drone-ion"}
# Files that only declare or place an item; a reference anywhere else is a route worth naming.
DECLARING = {"SquadEquipmentDoctrines.java", "MechPattern.java", "MechVariant.java", "MechWeaponComponent.java",
             "MechInternal.java", "VehicleType.java", "FirstRefit.java"}


class Report:
    def __init__(self, allow):
        self.errors, self.warnings, self.accepted, self.info = [], [], [], []
        self.allow = allow

    def error(self, msg):
        self.errors.append(msg)

    def warn(self, code, item, msg):
        key = "%s %s" % (code, item)
        (self.accepted if key in self.allow else self.warnings).append((code, item, msg + (" [accepted: %s]" % self.allow[key] if key in self.allow else "")))

    def markdown(self):
        out = ["# Kit Ladder check", "", "%d errors, %d warnings, %d accepted." % (len(self.errors), len(self.warnings), len(self.accepted)), ""]
        if self.errors:
            out += ["## Errors", ""] + ["- " + e for e in self.errors] + [""]
        if self.warnings:
            out += ["## Warnings", "", "| Code | Item | Finding |", "|---|---|---|"]
            out += ["| %s | `%s` | %s |" % w for w in self.warnings] + [""]
        if self.accepted:
            out += ["## Accepted", ""] + ["- %s `%s`: %s" % a for a in self.accepted] + [""]
        if self.info:
            out += ["## Notes", ""] + ["- " + i for i in self.info] + [""]
        return "\n".join(out)


def card_tier(card, kind, primaries, specials, armors):
    r = 0
    for m in card.get("members") or []:
        if kind == "weapon":
            p = primaries.get(m.get("primary"))
            tiers = [p and p["access"].get(m.get("grade"))] + [specials.get(s, {}).get("access") for s in (m.get("special"), m.get("belt")) if s]
        else:
            tiers = [armors.get(m.get("armor"), {}).get("access")]
        r = max([r] + [RANK[t] for t in tiers if t in RANK])
    return [t for t, v in RANK.items() if v == r][0]


def known_ids(data, game):
    """The ids the data may name, as the game's registries hold them (kitdata.validate's ``known``)."""
    weapons = data["WEAPONS"]
    specials = {}
    for e in build_specials():
        specials[e["id"]] = e
    thrown = {i for i, e in specials.items() if e["activation"]["type"] == "utility-smoke"
              or (e["activation"]["type"] == "arc-explosive" and (weapons.get(e["activation"].get("weaponId")) or {}).get("sim", {}).get("indirectFire"))}
    strings = build.flat(build.load(build.MOD / "data/strings/strings.json"))
    return {"primaries": {w for w, d in weapons.items() if d["mount"] == "marine-primary"}, "specials": set(specials),
            "thrown": thrown, "armor": {a["id"] for a in data["ARMORS"]}, "patterns": {game["gameIds"][i] for i in game["patterns"]},
            "strings": set(strings)}


def build_specials():
    return build.load(build.MOD / "data/marines/marine-special-equipment.equipment.json")["equipment"]


def java_sources():
    return {f: f.read_text(encoding="utf-8") for f in ladder.JAVA.rglob("*.java")}


def check(page=None, reel=None):
    allow = json.loads(ALLOW.read_text(encoding="utf-8")) if ALLOW.exists() else {}
    allow = {k: v for k, v in allow.items() if not k.startswith("_")}
    rep = Report(allow)
    try:
        game = ladder.game(reel_index=reel)
    except (ladder.LadderError, SystemExit) as e:
        rep.error(str(e))
        return rep
    data, stations = game["data"], game["stations"]
    rank = {sid: i for i, (_, sid) in enumerate(stations)}
    rank["none"] = len(stations) + 1
    names = game["names"]

    # The data loads: every rule the game enforces at load, then every real item placed.
    for e in kitdata.validate(game["ladder"], kitdata.load_cards(), known_ids(data, game)):
        rep.error(e)
    for item in sorted(game["items"]):
        if item not in game["rowStations"]:
            rep.error("%s (%s) has no placement in kit-ladder.json: the page cannot seed it and the store never sells it" % (item, names.get(item)))
    for k in data["KITS"] + data["ARMORKITS"]:
        if k.get("grant") == "free-pick" and k.get("station") != "relief":
            rep.error("%s is a Wayline free pick (FirstRefit) placed on %s; free picks exist only at relief" % (k["id"], k.get("station")))

    # Prerequisites: a part is no use before something can carry it.
    hulls = [it for it in data["ITEMS"]["hull"] if it.get("station")]
    first = lambda prefix: min([rank[it["station"]] for it in hulls if it["id"].startswith(prefix) and it["station"] != "none"] or [rank["none"]])
    first_vehicle, first_chassis = first("vehicle:"), first("chassis:")
    station_name = {i: sid for sid, i in rank.items()}
    for it in data["ITEMS"]["mweapon"] + data["ITEMS"]["mpart"]:
        st = it.get("station")
        if not st or st == "none":
            continue
        # One machine, one part catalog: an internal goes in a mech or a hull alike.
        part = it["id"].startswith("internal:")
        need = min(first_vehicle, first_chassis) if part else first_chassis
        if rank[st] < need:
            what = "mech chassis or vehicle hull" if part else "mech chassis"
            rep.warn("carrier", it["id"], "%s sells after %s, but no %s is sold before %s" % (it["name"], st, what, station_name.get(need, "never")))

    # Tiers against stations.
    primaries = {p["id"]: p for p in data["PRIMARIES"]}
    specials = {s["id"]: s for s in data["SPECIALS"]}
    armors = {a["id"]: a for a in data["ARMORS"]}
    tiers = {}
    for kind, lst in (("weapon", data["KITS"]), ("armor", data["ARMORKITS"])):
        for k in lst:
            tiers[k["id"]] = card_tier(k, kind, primaries, specials, armors)
    for lst in data["ITEMS"].values():
        for it in lst:
            tiers[it["id"]] = it["access"]
    for item, tier in sorted(tiers.items()):
        st = game["rowStations"].get(item)
        if tier == "RESTRICTED" and st not in (None, "none"):
            rep.warn("restricted", item, "%s is Restricted and placed on %s: the store sells it there only to a company holding a warrant naming it"
                     % (names.get(item), st))
        if st == "start" and RANK.get(tier, 0) > 0:
            rep.warn("start-tier", item, "%s is %s but a new company holds it" % (names.get(item), tier.title()))

    # Never sold: say where else the code names it, so the owner can tell a story route from a dead end.
    sources = None
    doctrines = ladder.constants(ladder.read(ladder.JAVA / "marine/SquadEquipmentDoctrines.java"))
    for item in sorted(i for i, st in game["rowStations"].items() if st == "none"):
        if sources is None:
            sources = java_sources()
        row = next(r for r in game["rows"] if r["id"] == item)
        if row["kind"] == "pattern":
            const = item.split(":", 1)[1]
            value = row["item"].split(":", 2)[2]
        else:
            const = next((c for c, v in doctrines.items() if v == item), item)
            value = item
        refs = sorted({f.stem for f, text in sources.items() if f.name not in DECLARING
                       and (re.search(r"\b%s\b" % re.escape(const), text) or '"%s"' % value in text)})
        where = ("named in " + ", ".join(refs[:6]) + (" and %d more" % (len(refs) - 6) if len(refs) > 6 else "")) if refs else "no reference outside its declaration"
        rep.warn("not-sold", item, "%s (%s) is never sold: patrons and recovery by tier only; %s. Mark it story/encounter-only in check-allow.json, or place it"
                 % (names.get(item), tiers.get(item, "?").title(), where))

    # Known gaps that clear themselves once the code consults the ladder.
    for cls, what in (("EquipmentCollectionCurve", "grants cards by its own curve, regardless of stations"),
                      ("FactionEquipmentCatalog", "validateReachability still uses the tier rules, not the ladder")):
        path = next(ladder.JAVA.rglob(cls + ".java"), None)
        if path and not re.search(r"\b(KitLadder|UnlockStation)\b", path.read_text(encoding="utf-8")):
            rep.warn("gap", cls, what)

    # The published page: what changed since, and the Weapons tab.
    base = ladder.load_base(page)
    fresh = ladder.manifest(data)
    if base is None:
        rep.warn("stale", "page", "no published manifest: pass --page <html from an Artifact read>, or record one (refresh.py record-published)")
    else:
        for key, h in sorted(fresh["fingerprints"].items()):
            if key.startswith("clip:") and not reel:
                continue
            if key not in base["fingerprints"]:
                rep.warn("stale", key, "%s is new since the page was published" % fresh["names"].get(key, key))
            elif base["fingerprints"][key] != h:
                rep.warn("stale", key, "%s changed (name, stats or contents) since the page was published" % fresh["names"].get(key, key))
        for key in sorted(set(base["fingerprints"]) - set(fresh["fingerprints"])):
            if not key.startswith("clip:") or reel:
                rep.warn("stale", key, "%s is on the published page but no longer in the game" % base["names"].get(key, key))
        for item, st in sorted(game["rowStations"].items()):
            b = base["stations"].get(item)
            if b is not None and b != st:
                rep.warn("stale", item, "the published page seeds %s on %s; the game has %s" % (names.get(item), b, st))
    clips = {c["id"]: {"weaponId": c["weaponId"], "url": c.get("url")} for c in data["WEAPONREEL"]["cards"]} if reel else (base or {}).get("clips", {})
    left = " ".join(data["WEAPONREEL"]["leftOut"] if reel else (base or {}).get("leftOut", []))
    weapons = data["WEAPONS"]
    if clips:
        fired = {c["weaponId"] for c in clips.values()}
        for cid, c in sorted(clips.items()):
            if c["weaponId"] not in weapons:
                rep.error("clip %s fires %s, which no *.weapon.json defines" % (cid, c["weaponId"]))
            if not c.get("url"):
                rep.warn("no-upload", cid, "no uploaded clip on the page")
        # Reel scope: every marine primary and secondary, and every weapon a mech part fires with its levers as shipped
        # (a lever's control variant is not in scope).
        scope = {wid for wid, w in weapons.items() if w["mount"] in ("marine-primary", "marine-secondary") and wid not in NOT_KIT_PRIMARY}
        scope |= {p["weaponId"] for p in data["PARTS"]}
        for wid in sorted(scope - fired):
            if wid not in left:
                rep.warn("no-clip", wid, "%s (%s) has no clip and the reel does not say why" % (wid, weapons[wid]["mount"]))
    else:
        rep.info.append("Weapons tab not checked: no reel (pass --reel <index.json>) and no published clips.")
    if NOTES.exists():
        notes = {kitdata.current_key(k): v for k, v in json.loads(NOTES.read_text(encoding="utf-8")).items()}
        for cid, n in sorted(notes.items()):
            if clips and cid not in clips:
                rep.warn("orphan-note", cid, "a note for a clip the page no longer has: %s" % (n.get("text") or "(empty)")[:80])
        open_notes = [cid for cid, n in notes.items() if n.get("text") and n.get("status") not in ("ok", "done")]
        rep.info.append("%d weapon notes, %d open (text and no ok status): %s" % (len(notes), len(open_notes), ", ".join(sorted(open_notes))))
    rep.info.append("Mod-added cards: a submod's cards have no placement and so read as none (never sold in the store).")
    return rep


# ---------------------------------------------------------------------------------------------------------------

SAMPLE_LADDER = {
    "stations": [{"id": "start", "reach": {"type": "always"}}, {"id": "relief", "reach": {"type": "storyMission", "mission": "m"}},
                 {"id": "junction", "reach": {"type": "arcBeat", "arc": "broken-supply-route", "beat": "junction-won"}}],
    "placements": [{"item": "loadout-card:weapon:weapons:a", "station": "start"},
                   {"item": "loadout-card:weapon:wayline:steady", "station": "start"},
                   {"item": "loadout-card:weapon:weapons:b", "station": "none"},
                   {"item": "loadout-card:sheet:armor:d", "station": "start"},
                   {"item": "mech-pattern:internal:internal.liner", "station": "start"}]}
SAMPLE_ENUMS = {"internal": {"LINER": "internal.liner"}, "chassis": {}, "weapon": {}, "vehicle": {}}


def sample_cards():
    seat = {"role": "R", "primary": "p", "grade": "SURPLUS"}
    weapon = lambda cid: {"id": cid, "name": "@k", "description": "@k", "members": [dict(seat) for _ in range(12)]}
    return {"weaponCards": [weapon("weapons:a"), weapon("wayline:steady"), weapon("weapons:b"), weapon("weapons:c")],
            "tacticSheets": [{"id": "armor:d", "name": "@k", "description": "@k", "tradition": "T", "mix": "M", "suits": ["s1"]},
                             {"id": "armor:c", "name": "@k", "description": "@k", "tradition": "T", "mix": "M", "suits": ["s1"]}]}


def self_test():
    """The pure logic on small inputs: row parsing, patching (and its idempotence), the diff and the page reader."""
    failures = []

    def expect(name, cond):
        if not cond:
            failures.append(name)

    stations = ladder.stations(SAMPLE_LADDER)
    rows = ladder.rows(SAMPLE_LADDER, SAMPLE_ENUMS, stations)
    expect("rows read page ids", [r["id"] for r in rows] == ["weapons:a", "wayline:steady", "weapons:b", "armor:d", "internal:LINER"])
    expect("rows read stations", [r["station"] for r in rows] == ["start", "start", "none", "start", "start"])
    bad = json.loads(json.dumps(SAMPLE_LADDER))
    bad["placements"][2]["station"] = "later"
    try:
        ladder.rows(bad, SAMPLE_ENUMS, stations)
        expect("unknown station refused", False)
    except ladder.LadderError:
        pass

    once = ladder.patch(SAMPLE_LADDER, {"loadout-card:weapon:weapons:a": "junction", "loadout-card:weapon:weapons:b": "relief"},
                        {"loadout-card:sheet:armor:c": "relief"})
    twice = ladder.patch(once, {"loadout-card:weapon:weapons:a": "junction", "loadout-card:weapon:weapons:b": "relief"}, {})
    after = {r["id"]: r["station"] for r in ladder.rows(once, SAMPLE_ENUMS, stations)}
    expect("patch moves rows", after["weapons:a"] == "junction" and after["weapons:b"] == "relief")
    expect("patch adds rows", after.get("armor:c") == "relief")
    expect("patch is idempotent", kitdata.dump_ladder(once) == kitdata.dump_ladder(twice))
    expect("patch keeps blocks in campaign order",
           [r["id"] for r in ladder.rows(once, SAMPLE_ENUMS, stations)] == ["wayline:steady", "weapons:b", "weapons:a", "armor:d", "armor:c", "internal:LINER"])
    expect("patch with nothing changes nothing", kitdata.dump_ladder(ladder.patch(SAMPLE_LADDER, {}, {})) == kitdata.dump_ladder(SAMPLE_LADDER))
    expect("dump is LF, one placement a line", "\r" not in kitdata.dump_ladder(once)
           and kitdata.dump_ladder(once).count("\n") == len(once["stations"]) + len(once["placements"]) + 6)
    cards = sample_cards()
    expect("cards dump round-trips", kitdata.dump_cards(json.loads(kitdata.dump_cards(cards))) == kitdata.dump_cards(cards))
    expect("page and game ids translate", kitdata.page_id("mech-pattern:internal:internal.liner", SAMPLE_ENUMS) == "internal:LINER"
           and kitdata.game_id("internal:LINER", SAMPLE_ENUMS) == "mech-pattern:internal:internal.liner"
           and kitdata.game_id("armor:d", SAMPLE_ENUMS) == "loadout-card:sheet:armor:d"
           and kitdata.game_id("wayline:steady", SAMPLE_ENUMS) == "loadout-card:weapon:wayline:steady")

    full = ladder.patch(SAMPLE_LADDER, {}, {"loadout-card:weapon:weapons:c": "none", "loadout-card:sheet:armor:c": "none"})
    known = {"primaries": {"p"}, "specials": {"sp"}, "thrown": set(), "armor": {"s1"}, "strings": {"k"}}
    expect("valid data validates", kitdata.validate(full, cards, known) == [])
    broken = sample_cards()
    broken["weaponCards"][0]["members"].pop()
    broken["weaponCards"][2]["members"][0]["special"] = "nothing"
    broken["weaponCards"][3]["members"][0]["belt"] = "sp"
    broken["tacticSheets"][0]["suits"] = []
    broken["tacticSheets"][1]["name"] = "@missing"
    dup = ladder.patch(SAMPLE_LADDER, {}, {})
    dup["placements"].append({"item": "loadout-card:weapon:weapons:a", "station": "relief"})
    dup["stations"][2]["reach"] = {"type": "moon"}
    errs = "\n".join(kitdata.validate(dup, broken, known))
    for needle in ("has 11 seats", "special 'nothing'", "belt 'sp' is not a thrown", "has no suits", "@missing is not in strings",
                   "placed twice", "reach type 'moon'", "card loadout-card:weapon:weapons:c has no placement"):
        expect("validate finds: " + needle, needle in errs)
    page = {"weapon": [{"id": "weapons:a", "members": [{"role": "R", "primary": "p", "grade": "SURPLUS", "special": "special.rocket-launcher", "belt": None}] * 12}],
            "armor": [{"id": "armor:d", "members": [{"armor": "s2" if i % 3 == 0 else "s1"} for i in range(12)]}]}
    ladder.translate_retired(page)
    expect("retired specials translate", page["weapon"][0]["members"][0]["special"] == "special.srm-1")
    edited = ladder.patch_cards(cards, {"weapons:a": page["weapon"][0], "armor:d": page["armor"][0]})
    expect("patch_cards writes seats", edited["weaponCards"][0]["members"][0] == {"role": "R", "primary": "p", "grade": "SURPLUS", "special": "special.srm-1"})
    expect("patch_cards seats a sheet", edited["tacticSheets"][0].get("seats") == ["s2", "s1", "s1"] * 4
           and "suits" not in edited["tacticSheets"][0] and kitdata.sheet_suits(edited["tacticSheets"][0]) == ["s2", "s1"])
    seated = ladder.patch_cards(cards, {"armor:d": page["armor"][0]})
    expect("a seated sheet validates", kitdata.validate(full, seated, dict(known, armor={"s1", "s2"})) == [])
    seated_bad = json.loads(json.dumps(seated))
    seated_bad["tacticSheets"][0]["seats"].pop()
    seated_bad["tacticSheets"][0]["suits"] = ["s1"]
    errs2 = " | ".join(kitdata.validate(full, seated_bad, dict(known, armor={"s1", "s2"})))
    expect("validate finds a short seated sheet", "seats has 11 suits" in errs2 and "give seats or suits" in errs2)
    expect("seated cards dump round-trips", kitdata.dump_cards(json.loads(kitdata.dump_cards(edited))) == kitdata.dump_cards(edited))
    expect("patch_cards leaves names alone", edited["weaponCards"][0]["name"] == "@k")

    ids = {"start", "relief", "junction", "none"}
    game_rows = {"a": "start", "b": "relief", "c": "relief", "d": "start", "e": "none"}
    card = {"id": "a", "name": "A", "description": "", "members": [{"role": "R", "primary": "p1"}], "grant": "sale"}
    state = {"weapon": [dict(card, station="junction")],
             "armor": [{"id": "new", "name": "N", "station": "none", "members": []}],
             "place": {"b": "relief", "c": "junction", "d": "relief", "x": "start", "f": "wins-3", "g": "relief"}}
    base = {"stations": {"a": "start", "c": "start", "d": "relief"}, "fingerprints": {}}
    items = {"a", "b", "c", "d", "e", "f", "g"}
    kinds = {e["id"]: e["kind"] for e in ladder.diff(state, game_rows, {"a": card}, items, ids, base)}
    expect("diff: owner move", kinds.get("a") == "move")
    expect("diff: conflict", kinds.get("c") == "conflict")
    expect("diff: game moved", kinds.get("d") == "game-moved")
    expect("diff: orphan and new card", kinds.get("x") == "orphan" and kinds.get("new") == "new-card")
    expect("diff: unknown station", kinds.get("f") == "unknown-station")
    expect("diff: add and unseen", kinds.get("g") == "add" and kinds.get("e") == "unseen")
    expect("diff: agreeing rows are silent", "b" not in kinds)
    edited = dict(card, station="start", members=[{"role": "R", "primary": "p2"}])
    state2 = {"weapon": [edited], "armor": [], "place": {}}
    e1 = [e for e in ladder.diff(state2, game_rows, {"a": card}, items, ids, None, {"p1"}) if e["id"] == "a"]
    expect("diff: content edit flags unknown pieces", e1 and e1[0]["kind"] == "content" and "not a game item" in e1[0]["detail"][0])
    stale_base = {"stations": {}, "fingerprints": {"a": ladder.card_fingerprint(edited)}}
    e2 = [e for e in ladder.diff(state2, game_rows, {"a": card}, items, ids, stale_base) if e["id"] == "a"]
    expect("diff: content the game changed is stale", e2 and e2[0]["kind"] == "content-stale")
    expect("seat changes: armour compares seat by seat",
           ladder.seat_changes("armor", [{"armor": "s1"}, {"armor": "s2"}], [{"armor": "s2"}, {"armor": "s2"}]) == ["seat 1: armor s2 -> s1"])

    data = {"PRIMARIES": [], "SPECIALS": [], "KITS": [dict(card, station="start")], "ARMORKITS": [], "STATIONS": [],
            "ARMORS": [], "ITEMS": {"hull": [{"id": "vehicle:X", "name": "X", "station": "none", "access": "COMMON"}]},
            "MACHINES": [], "WEAPONREEL": {"cards": [], "leftOut": []}, "SYSTEMS": [], "IMAGES": {"i": "data:,"}}
    html = "<script>\nconst " + ",".join("%s=%s" % (js, json.dumps(data[key])) for js, key in ladder.PAGE_NAMES.items()) + ";\nconst MACH=[];"
    expect("page data round-trips", ladder.manifest(ladder.page_data(html)) == ladder.manifest(data))
    expect("manifest ignores art", ladder.manifest(dict(data, IMAGES={})) == ladder.manifest(data))
    expect("card tier is the scarcest piece", card_tier({"members": [{"primary": "p", "grade": "MILSPEC", "special": "s"}]}, "weapon",
                                                        {"p": {"access": {"MILSPEC": "ADVANCED"}}}, {"s": {"access": "PRESTIGE"}}, {}) == "PRESTIGE")
    for f in failures:
        print("FAIL " + f)
    print("self-test: %d failed" % len(failures) if failures else "self-test: ok")
    return 1 if failures else 0


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--page", help="the published page's HTML (the file an Artifact read saved); default tools/kits/review/published.json")
    ap.add_argument("--reel", help="a weapon reel index.json, to check the Weapons tab against a fresh reel")
    ap.add_argument("--md", help="also write the report as markdown here")
    ap.add_argument("--self-test", action="store_true")
    args = ap.parse_args()
    if args.self_test:
        return self_test()
    rep = check(args.page, args.reel)
    md = rep.markdown()
    print(md)
    if args.md:
        Path(args.md).parent.mkdir(parents=True, exist_ok=True)
        Path(args.md).write_text(md, encoding="utf-8", newline="\n")
    return 1 if rep.errors else 0


if __name__ == "__main__":
    sys.exit(main())
