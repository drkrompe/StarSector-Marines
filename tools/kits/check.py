"""Check the Kit Ladder: the game's rows, the items they place, and the page built from them.

    python tools/kits/check.py [--page <page.html>] [--reel <weapon-reel/index.json>] [--md <out.md>]
    python tools/kits/check.py --self-test

Pure Python over the repository's files (and, when given, a page HTML an Artifact read saved); about a second.
Exits 1 on an error, 0 otherwise; warnings feed the owner's decisions (kit-ladder.md, Checks).

Errors: a row in no parsed shape, a station UnlockStation lacks, an item placed twice, a row naming no real item, a
real item with no row, a build.py station list out of step with UnlockStation, a Wayline free pick off Relief, a
reel clip firing a weapon no *.weapon.json defines.

Warnings (codes, so tools/kits/check-allow.json can accept one with a reason as "<code> <id>"):
  carrier      a vehicle kit sold before any vehicle hull, or a mech part before any chassis
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
import ladder  # noqa: E402

ALLOW = Path(__file__).resolve().parent / "check-allow.json"
NOTES = ladder.REVIEW / "weapon-notes.json"
RANK = {"COMMON": 0, "ADVANCED": 1, "PRESTIGE": 2, "RESTRICTED": 3}
NOT_KIT_PRIMARY = {"weapon.drone-pulse", "weapon.drone-ion"}
# Files that only declare or place an item; a reference anywhere else is a route worth naming.
DECLARING = {"KitLadder.java", "SquadEquipmentDoctrines.java", "ArmorSheetBundles.java", "MechPattern.java", "MechVariant.java",
             "MechWeaponComponent.java", "MechInternal.java", "VehicleType.java", "VehicleKit.java", "FirstRefit.java"}


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

    # Rows: one per item, each a real item; every real item placed.
    seen = {}
    for r in game["rows"]:
        if r["id"] in seen:
            rep.error("KitLadder.java:%d places %s again (first at line %d); the game refuses this at load" % (r["line"] + 1, r["id"], seen[r["id"]] + 1))
        seen.setdefault(r["id"], r["line"])
        if r["id"] not in game["items"]:
            rep.error("KitLadder.java:%d: %s (%s) is not a card or a pattern the store can sell (no armour bundle, or no tier in MechPattern)"
                      % (r["line"] + 1, r["ref"], r["id"]))
    for item in sorted(game["items"]):
        if item not in game["rowStations"]:
            rep.error("%s (%s) has no KitLadder row: the page cannot seed it and the store never sells it" % (item, names.get(item)))
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
        need = first_vehicle if it["id"].startswith("kit:") else first_chassis
        if rank[st] < need:
            what = "vehicle hull" if it["id"].startswith("kit:") else "mech chassis"
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
    for item in sorted(i for i, st in game["rowStations"].items() if st == "none"):
        if sources is None:
            sources = java_sources()
        row = next(r for r in game["rows"] if r["id"] == item)
        const = row["ref"].split(".")[-1] if not row["ref"].endswith(".weapons") else row["ref"].split(".")[2]
        value = item.split(":", 1)[1] if row["kind"] == "pattern" else item
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
        notes = json.loads(NOTES.read_text(encoding="utf-8"))
        for cid, n in sorted(notes.items()):
            if clips and cid not in clips:
                rep.warn("orphan-note", cid, "a note for a clip the page no longer has: %s" % (n.get("text") or "(empty)")[:80])
        open_notes = [cid for cid, n in notes.items() if n.get("text") and n.get("status") not in ("ok", "done")]
        rep.info.append("%d weapon notes, %d open (text and no ok status): %s" % (len(notes), len(open_notes), ", ".join(sorted(open_notes))))
    rep.info.append("Mod-added cards: a submod's cards have no KitLadder row and so read as none (never sold in the store).")
    return rep


# ---------------------------------------------------------------------------------------------------------------

SAMPLE = """package x;

import static com.dillon.starsectormarines.marine.UnlockStation.NONE;
import static com.dillon.starsectormarines.marine.UnlockStation.START;

public final class KitLadder {
    static {
        // Weapon loadout cards.
        weapon(SquadEquipmentDoctrines.A_WEAPONS, START);
        weapon(FirstRefit.Path.STEADY.weapons, START);
        weapon(SquadEquipmentDoctrines.B_WEAPONS, NONE);

        // Tactic sheets.
        sheet(SquadEquipmentDoctrines.D_ARMOR, START);

        // Vehicle kits.
        pattern(VehicleKit.LINER, START);
    }
}
"""
SAMPLE_STATIONS = [("START", "start"), ("RELIEF", "relief"), ("JUNCTION", "junction"), ("NONE", "none")]
SAMPLE_DOCTRINES = {"A_WEAPONS": "weapons:a", "B_WEAPONS": "weapons:b", "C_ARMOR": "armor:c", "D_ARMOR": "armor:d"}
SAMPLE_PATHS = {"STEADY": "wayline:steady"}


def self_test():
    """The pure logic on small inputs: row parsing, patching (and its idempotence), the diff and the page reader."""
    failures = []

    def expect(name, cond):
        if not cond:
            failures.append(name)

    rows = ladder.rows(SAMPLE, SAMPLE_DOCTRINES, SAMPLE_PATHS, SAMPLE_STATIONS)
    expect("rows parse every shape", [r["id"] for r in rows] == ["weapons:a", "wayline:steady", "weapons:b", "armor:d", "kit:LINER"])
    expect("rows read stations", [r["station"] for r in rows] == ["start", "start", "none", "start", "start"])
    try:
        ladder.rows(SAMPLE.replace("NONE);", "LATER);"), SAMPLE_DOCTRINES, SAMPLE_PATHS, SAMPLE_STATIONS)
        expect("unknown station refused", False)
    except ladder.LadderError:
        pass
    try:
        ladder.rows(SAMPLE.replace("pattern(VehicleKit.LINER, START);", "pattern(VehicleKit.LINER, START, 2);"),
                    SAMPLE_DOCTRINES, SAMPLE_PATHS, SAMPLE_STATIONS)
        expect("reshaped row refused", False)
    except ladder.LadderError:
        pass

    args = (SAMPLE_STATIONS, SAMPLE_DOCTRINES, SAMPLE_PATHS)
    once = ladder.patch(SAMPLE, {"weapons:a": "junction", "weapons:b": "relief"}, {"armor:c": ("sheet", "SquadEquipmentDoctrines.C_ARMOR", "relief")}, *args)
    twice = ladder.patch(once, {"weapons:a": "junction", "weapons:b": "relief"}, {}, *args)
    after = {r["id"]: r["station"] for r in ladder.rows(once, *args[1:], SAMPLE_STATIONS)}
    expect("patch moves rows", after["weapons:a"] == "junction" and after["weapons:b"] == "relief")
    expect("patch adds rows", after.get("armor:c") == "relief")
    expect("patch is idempotent", once == twice)
    expect("patch keeps blocks in campaign order",
           [r["id"] for r in ladder.rows(once, *args[1:], SAMPLE_STATIONS)] == ["wayline:steady", "weapons:b", "weapons:a", "armor:d", "armor:c", "kit:LINER"])
    expect("patch regenerates imports", "UnlockStation.JUNCTION;" in once and "UnlockStation.RELIEF;" in once
           and "UnlockStation.NONE;" not in once and once.index("JUNCTION;") < once.index("RELIEF;"))
    expect("patch with nothing changes nothing", ladder.patch(SAMPLE, {}, {}, *args) == SAMPLE)

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
    expect("seat changes: armour compares suit sets",
           ladder.seat_changes("armor", [{"armor": "s1"}, {"armor": "s2"}], [{"armor": "s2"}, {"armor": "s1"}]) == [])

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
