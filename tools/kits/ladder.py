"""The Kit Ladder's repo side, shared by the kit tools (kit-ladder.md): read KitLadder.java's rows, compare a saved page
state with them, patch them, and fingerprint the data a page build embeds.

Pure Python over the repository's files; no network, no Gradle. The game data is canonical: the Kit Ladder page
(tools/kits/build.py) is an editor seeded from it, and nothing here writes a game file except apply_ladder.py.
"""
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "src/main/java/com/dillon/starsectormarines"
KIT_LADDER = JAVA / "marine/KitLadder.java"
UNLOCK_STATION = JAVA / "marine/UnlockStation.java"
FIRST_REFIT = JAVA / "marine/FirstRefit.java"
REVIEW = Path(__file__).resolve().parent / "review"
PUBLISHED = REVIEW / "published.json"

# KitLadder's pattern(...) overloads: Java class -> page item kind.
PATTERN_KIND = {"MechVariant": "chassis", "MechWeaponComponent": "weapon", "MechInternal": "internal",
                "VehicleType": "vehicle", "VehicleKit": "kit"}
KIND_CLASS = {v: k for k, v in PATTERN_KIND.items()}
STATIC_IMPORT = "import static com.dillon.starsectormarines.marine.UnlockStation.%s;"

CARD_ROW = re.compile(r'^(\s+)(weapon|sheet)\((?:SquadEquipmentDoctrines\.(\w+)|FirstRefit\.Path\.(\w+)\.weapons),\s*(\w+)\);\s*$')
PATTERN_ROW = re.compile(r'^(\s+)pattern\((\w+)\.(\w+),\s*(\w+)\);\s*$')
ANY_ROW = re.compile(r'^\s+(?:weapon|sheet|pattern)\(')


class LadderError(Exception):
    """The Java was reshaped past what these tools parse; fix the file or the parser, never guess."""


def read(path):
    return Path(path).read_text(encoding="utf-8")


def constants(text):
    return dict(re.findall(r'public static final String (\w+)\s*=\s*"([^"]+)"', text))


def stations(text=None):
    """[(ENUM, page id)] in campaign order, NONE last as UnlockStation declares it."""
    found = re.findall(r'^    ([A-Z_0-9]+)\("([a-z0-9-]+)"\)', text if text is not None else read(UNLOCK_STATION), re.M)
    if not found or found[-1][0] != "NONE":
        raise LadderError("UnlockStation: expected its constants in 'NAME(\"id\")' form, NONE last; parsed %s" % found)
    return found


def first_refit_paths(text=None):
    """{Path enum: weapons card id} for FirstRefit's two Wayline paths."""
    return dict(re.findall(r'^\s+(\w+)\("[^"]+", "(wayline:[^"]+)"', text if text is not None else read(FIRST_REFIT), re.M))


def rows(text=None, doctrines=None, paths=None, station_list=None):
    """Every KitLadder row in file order: {line, kind, ref, id, station (page id), enum (station constant)}.

    ``doctrines`` maps SquadEquipmentDoctrines constant names to card ids; ``paths`` is first_refit_paths()."""
    text = text if text is not None else read(KIT_LADDER)
    if doctrines is None:
        doctrines = constants(read(JAVA / "marine/SquadEquipmentDoctrines.java"))
    paths = paths if paths is not None else first_refit_paths()
    by_enum = dict(station_list if station_list is not None else stations())
    out = []
    for n, line in enumerate(text.splitlines()):
        if not ANY_ROW.match(line):
            continue
        card, pattern = CARD_ROW.match(line), PATTERN_ROW.match(line)
        if card:
            _, kind, const, path, enum = card.groups()
            if const:
                if const not in doctrines:
                    raise LadderError("KitLadder.java:%d names SquadEquipmentDoctrines.%s, which is not a constant there" % (n + 1, const))
                ref, item = "SquadEquipmentDoctrines." + const, doctrines[const]
            else:
                if path not in paths:
                    raise LadderError("KitLadder.java:%d names FirstRefit.Path.%s, which FirstRefit does not declare" % (n + 1, path))
                ref, item = "FirstRefit.Path.%s.weapons" % path, paths[path]
        elif pattern:
            _, cls, const, enum = pattern.groups()
            if cls not in PATTERN_KIND:
                raise LadderError("KitLadder.java:%d: pattern(%s...) is not one of %s" % (n + 1, cls, sorted(PATTERN_KIND)))
            kind, ref, item = "pattern", "%s.%s" % (cls, const), "%s:%s" % (PATTERN_KIND[cls], const)
        else:
            raise LadderError("KitLadder.java:%d is a row in no parsed shape; keep weapon/sheet/pattern(<constant>, <STATION>);\n  %s"
                              % (n + 1, line.strip()))
        if enum not in by_enum:
            raise LadderError("KitLadder.java:%d places %s on %s, which is not an UnlockStation" % (n + 1, item, enum))
        out.append({"line": n, "kind": kind, "ref": ref, "id": item, "station": by_enum[enum], "enum": enum})
    return out


# ---------------------------------------------------------------------------------------------------------------
# The page's saved state (db doc kits/state, field json) and how it differs from the game.

def load_state(path):
    """The page state from an ArtifactData out_dir file (kits/state.json: {"json": "<state>", "at": ...}),
    or a bare state object, or a kits.json the page's Download button wrote."""
    doc = json.loads(read(path))
    if isinstance(doc.get("json"), str):
        state = json.loads(doc["json"])
        state["_savedAt"] = doc.get("at")
        return state
    if "weaponCards" in doc:  # the page's Download kits.json shape
        card = lambda k: {"id": k["id"], "name": k.get("name"), "description": k.get("description"),
                          "station": k.get("unlocksAfter") or "none", "grant": k.get("grant") or "sale", "members": k.get("members")}
        place = {it["id"]: it.get("unlocksAfter") or "none" for lst in (doc.get("machineLicences") or {}).values() for it in lst}
        return {"weapon": [card(k) for k in doc["weaponCards"]], "armor": [card(k) for k in doc["equipmentCards"]], "place": place}
    if "weapon" in doc and "armor" in doc:
        return doc
    raise LadderError("%s is not a Kit Ladder state (kits/state doc, page state or kits.json)" % path)


def page_stations(state):
    """{item id: station id} for everything the page state places: cards and machine licences alike."""
    out = {}
    for lst in ("weapon", "armor"):
        for k in state.get(lst) or []:
            out[k["id"]] = k.get("station") or "none"
    for key, station in (state.get("place") or {}).items():
        out[key] = station or "none"
    return out


def seat_changes(lst, page, game, pieces=None):
    """Readable differences between a card's seats on the page and in the game. A weapon card is compared seat by seat
    (SquadEquipmentDoctrines lists twelve seats); a tactic sheet by its set of suits only, since the game seats a
    sheet's suits by role and the page's seating is just an even spread. Pieces the game does not have are flagged."""
    unknown = lambda x: " (not a game item)" if pieces is not None and x and x not in pieces else ""
    out = []
    if lst == "armor":
        p, g = {m.get("armor") for m in page if m.get("armor")}, {m.get("armor") for m in game if m.get("armor")}
        out += ["+ %s%s" % (x, unknown(x)) for x in sorted(p - g)] + ["- %s" % x for x in sorted(g - p)]
        return out
    for i in range(max(len(page), len(game))):
        a, b = (page[i] if i < len(page) else {}), (game[i] if i < len(game) else {})
        fields = [f for f in ("role", "primary", "grade", "special", "belt") if a.get(f) != b.get(f)]
        if fields:
            out.append("seat %d: " % (i + 1) + "; ".join("%s %s -> %s%s" % (f, b.get(f), a.get(f), unknown(a.get(f)) if f != "role" and f != "grade" else "")
                                                       for f in fields))
    return out


def card_fingerprint(card):
    return _h([card.get("name"), card.get("description"), card.get("members")])


def diff(state, game_rows, cards, items, station_ids, base=None, pieces=None):
    """What a saved page state says against the game's rows. One entry per finding, sorted by kind then id:

    move            page and game disagree on a live item's station (with a base: only the page changed). apply writes it.
    add             the page places a real item KitLadder has no row for. apply writes it.
    game-moved      with a base: only the game changed since the page was published; the page is stale (republish).
    conflict        with a base: page and game both moved, differently. The owner decides; apply skips it.
    content         a card's name, description or seats differ from the game's, and the owner changed them on the page
                    (or there is no base to tell). Author it in Java by hand.
    content-stale   with a base: the card differs only because the game changed it after the page was published.
    unknown-station the page names a station UnlockStation lacks (the page itself reads it as none). apply skips it.
    orphan          a page key that is no game item (renamed or deleted). apply skips it.
    new-card        a card the page made that the game does not have. Author it in Java by hand.
    grant           the page's free-pick flag disagrees with FirstRefit. FirstRefit owns it.
    unseen          a game row the page state has never placed (the page seeds it from the game on load).

    ``game_rows`` {id: station}; ``cards`` {id: game card dict with name/description/members/grant}; ``items`` every
    real item id (cards and patterns); ``base`` the published page's manifest (stations and fingerprints), or None; ``pieces`` the
    weapon, special and suit ids the game has, to flag a card seat naming one it lacks."""
    page = page_stations(state)
    base_fp = (base or {}).get("fingerprints") or {}
    base = (base or {}).get("stations")
    out = []

    def add(kind, item, **kw):
        out.append(dict({"kind": kind, "id": item, "page": page.get(item), "game": game_rows.get(item),
                         "base": (base or {}).get(item)}, **kw))

    for item, station in page.items():
        if item not in items:
            add("new-card" if any(k["id"] == item for lst in ("weapon", "armor") for k in state.get(lst) or []) else "orphan", item)
            continue
        if station not in station_ids:
            add("unknown-station", item)
            continue
        game = game_rows.get(item)
        if game is None:
            add("add", item)
        elif station != game:
            b = (base or {}).get(item)
            if b is None or b not in station_ids:
                add("move", item)
            elif b == game:
                add("move", item)
            elif b == station:
                add("game-moved", item)
            else:
                add("conflict", item)
    for item in game_rows:
        if item not in page:
            add("unseen", item)
    for lst in ("weapon", "armor"):
        for k in state.get(lst) or []:
            game = cards.get(k["id"])
            if not game:
                continue
            changed = [f for f in ("name", "description") if (k.get(f) or "") != (game.get(f) or "")]
            detail = seat_changes(lst, k.get("members") or [], game.get("members") or [], pieces)
            if detail:
                changed.append("suits" if lst == "armor" else "seats")
            if changed:
                stale = k["id"] in base_fp and card_fingerprint(k) == base_fp[k["id"]]
                add("content-stale" if stale else "content", k["id"], fields=changed, detail=detail)
            if (k.get("grant") or "sale") != (game.get("grant") or "sale"):
                add("grant", k["id"], pageGrant=k.get("grant"), gameGrant=game.get("grant"))
    order = ["move", "add", "conflict", "game-moved", "unknown-station", "orphan", "new-card", "content", "content-stale",
             "grant", "unseen"]
    out.sort(key=lambda e: (order.index(e["kind"]), e["id"]))
    return out


DIFF_NOTE = {
    "move": "apply writes the page's station",
    "add": "apply adds a row",
    "conflict": "both moved since publish: owner decides",
    "game-moved": "game changed after publish: republish the page",
    "unknown-station": "page station is not an UnlockStation: skipped",
    "orphan": "not a game item: skipped",
    "new-card": "page-made card: author it in Java by hand",
    "content": "owner edited the card: author in Java by hand",
    "content-stale": "game changed the card after publish: republish",
    "grant": "free pick is FirstRefit's: change it by hand",
    "unseen": "page never placed it: seeded from the game",
}


def diff_table(entries, names=None):
    """A markdown table of diff entries."""
    names = names or {}
    if not entries:
        return "No differences: the page state matches KitLadder.java.\n"
    lines = ["| Kind | Item | Name | Page | Game | Published | What happens |", "|---|---|---|---|---|---|---|"]
    for e in entries:
        what = DIFF_NOTE[e["kind"]]
        if e.get("fields"):
            what += " (%s%s)" % (", ".join(e["fields"]), "; %d changes" % len(e["detail"]) if e.get("detail") else "")
        if e["kind"] == "grant":
            what += " (page %s, game %s)" % (e.get("pageGrant"), e.get("gameGrant"))
        lines.append("| %s | `%s` | %s | %s | %s | %s | %s |" % (
            e["kind"], e["id"], names.get(e["id"], ""), e["page"] or "", e["game"] or "", e["base"] or "", what))
    return "\n".join(lines) + "\n"


# ---------------------------------------------------------------------------------------------------------------
# Writing KitLadder.java.

def row_text(indent, kind, ref, enum):
    if kind == "pattern":
        return "%spattern(%s, %s);" % (indent, ref, enum)
    return "%s%s(%s, %s);" % (indent, kind, ref, enum)


def patch(text, changes, adds, station_list, doctrines, paths):
    """KitLadder.java with ``changes`` {item id: station id} applied to existing rows and ``adds`` {item id: (kind, ref,
    station id)} inserted, each block of rows kept in campaign order (stable), and the UnlockStation static imports
    regenerated from the stations in use. Deterministic: patching the result with the same input changes nothing."""
    enum_of = {sid: enum for enum, sid in station_list}
    rank = {enum: i for i, (enum, _) in enumerate(station_list)}
    lines = text.split("\n")
    current = rows(text, doctrines, paths, station_list)
    by_id = {r["id"]: r for r in current}
    for item, station in changes.items():
        r = by_id[item]
        indent = re.match(r"^(\s*)", lines[r["line"]]).group(1)
        lines[r["line"]] = row_text(indent, r["kind"], r["ref"], enum_of[station])
    # Adds go after the last row of the same kind and class, so they land in their own block.
    for item, (kind, ref, station) in sorted(adds.items()):
        group = (kind, ref.split(".")[0])
        same = [r for r in rows("\n".join(lines), doctrines, paths, station_list) if (r["kind"], r["ref"].split(".")[0]) == group]
        if not same:
            raise LadderError("no %s(%s...) block in KitLadder.java to add %s to; add the first row by hand" % (kind, group[1], item))
        at = same[-1]["line"]
        indent = re.match(r"^(\s*)", lines[at]).group(1)
        lines.insert(at + 1, row_text(indent, kind, ref, enum_of[station]))
    # Stable-sort each contiguous block of rows by station.
    i = 0
    while i < len(lines):
        if ANY_ROW.match(lines[i]):
            j = i
            while j < len(lines) and ANY_ROW.match(lines[j]):
                j += 1
            block = lines[i:j]
            enum = lambda line: re.search(r",\s*(\w+)\);\s*$", line).group(1)
            lines[i:j] = sorted(block, key=lambda line: rank[enum(line)])
            i = j
        else:
            i += 1
    body = "\n".join(lines)
    used = sorted({r["enum"] for r in rows(body, doctrines, paths, station_list)})
    imports = [n for n, line in enumerate(lines) if line.startswith(STATIC_IMPORT.split("%s")[0])]
    if not imports:
        raise LadderError("KitLadder.java has no UnlockStation static imports to regenerate")
    lines[imports[0]:imports[-1] + 1] = [STATIC_IMPORT % e for e in used]
    return "\n".join(lines)


# ---------------------------------------------------------------------------------------------------------------
# Fingerprints: what a page build embeds, so a check can say what changed since the page was published.

def _h(obj):
    return hashlib.sha1(json.dumps(obj, sort_keys=True, ensure_ascii=False).encode("utf-8")).hexdigest()[:12]


def manifest(data):
    """{fingerprints: {item id: hash}, stations: {item id: seeded station}, names: {item id: name}, clips: {clip id:
    {weaponId, url}}} for a page's embedded data (the template's placeholder names, as build.collect returns them).
    Icons, images, posters and clip urls are left out of the hashes: only names, stats and contents count."""
    fp, st, names, clips = {}, {}, {}, {}
    for p in data.get("PRIMARIES", []):
        fp[p["id"]] = _h([p.get(k) for k in ("name", "kind", "access", "stats", "note")])
        names[p["id"]] = p["name"]
    for s in data.get("SPECIALS", []):
        fp[s["id"]] = _h([s.get(k) for k in ("name", "kind", "access", "note", "thrown")])
        names[s["id"]] = s["name"]
    for a in data.get("ARMORS", []):
        fp[a["id"]] = _h([a.get(k) for k in ("name", "role", "tier", "powered", "access", "stats", "system")])
        names[a["id"]] = a["name"]
    for k in data.get("KITS", []) + data.get("ARMORKITS", []):
        fp[k["id"]] = card_fingerprint(k)
        st[k["id"]] = k.get("station")
        names[k["id"]] = k["name"]
    for lst in (data.get("ITEMS") or {}).values():
        for it in lst:
            fp[it["id"]] = _h([it.get(k) for k in ("name", "sub", "access", "tone", "fp")])
            st[it["id"]] = it.get("station")
            names[it["id"]] = it["name"]
    for c in (data.get("WEAPONREEL") or {}).get("cards", []):
        fp["clip:" + c["id"]] = _h([c.get(k) for k in ("name", "weaponId", "catalog", "part", "mode", "shooter", "rows", "tags",
                                                        "fire", "impact", "loop")])
        clips[c["id"]] = {"weaponId": c["weaponId"], "url": c.get("url")}
        names["clip:" + c["id"]] = c["name"]
    return {"fingerprints": fp, "stations": st, "names": names, "clips": clips,
            "leftOut": (data.get("WEAPONREEL") or {}).get("leftOut", [])}


# The page's script line: const PRIMARIES=<json>,SPECIALS=<json>,SEED_W=<json>,...; JS name -> template name.
PAGE_NAMES = {"PRIMARIES": "PRIMARIES", "SPECIALS": "SPECIALS", "SEED_W": "KITS", "SEED_A": "ARMORKITS",
              "STATIONS": "STATIONS", "ARMORS": "ARMORS", "ITEMS": "ITEMS", "MACHINES": "MACHINES",
              "REEL": "WEAPONREEL", "SYSTEMS": "SYSTEMS", "IMAGES": "IMAGES"}


def page_data(html):
    """The data a built page embeds, read back from its HTML (a build.py output, or the file an Artifact read saved)."""
    start = html.find("const PRIMARIES=")
    if start < 0:
        raise LadderError("no 'const PRIMARIES=' line: not a Kit Ladder page")
    pos, out, decoder = start + len("const "), {}, json.JSONDecoder()
    while True:
        m = re.compile(r"(\w+)=").match(html, pos)
        if not m or m.group(1) not in PAGE_NAMES:
            break
        value, pos = decoder.raw_decode(html, m.end())
        out[PAGE_NAMES[m.group(1)]] = value
        if html[pos] != ",":
            break
        pos += 1
    if "KITS" not in out or "ITEMS" not in out:
        raise LadderError("the page's data line parsed only %s" % sorted(out))
    return out


def game(reel_index=None):
    """The repo's side of a diff: {data (build.collect, no art, not strict), rows (KitLadder rows), stations [(ENUM, id)],
    rowStations {id: station}, cards {id: card}, items {every real item id}, names {id: name}}."""
    import build  # build imports this module; import lazily
    data = build.collect(reel_index=reel_index, art=False, strict=False)
    station_list = stations()
    doctrines = constants(read(JAVA / "marine/SquadEquipmentDoctrines.java"))
    the_rows = rows(doctrines=doctrines, station_list=station_list)
    cards = {k["id"]: k for k in data["KITS"] + data["ARMORKITS"]}
    patterns = {it["id"]: it for lst in data["ITEMS"].values() for it in lst}
    names = {i: v["name"] for i, v in list(cards.items()) + list(patterns.items())}
    return {"data": data, "rows": the_rows, "stations": station_list, "rowStations": {r["id"]: r["station"] for r in the_rows},
            "cards": cards, "patterns": patterns, "items": set(cards) | set(patterns), "names": names, "doctrines": doctrines,
            "pieces": {p["id"] for key in ("PRIMARIES", "SPECIALS", "ARMORS") for p in data[key]}}


def load_base(page=None):
    """The published page's manifest: from a page HTML when given, else the tracked review/published.json, else None."""
    if page:
        return manifest(page_data(read(page)))
    if PUBLISHED.exists():
        return json.loads(read(PUBLISHED))
    return None
