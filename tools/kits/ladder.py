"""The Kit Ladder's repo side, shared by the kit tools (kit-ladder.md): read the ladder's placements from
mod/data/marines/kit-ladder.json (through kitdata.py), compare a saved page state with them, patch them, and
fingerprint the data a page build embeds.

Pure Python over the repository's files; no network, no Gradle. The game data is canonical: the Kit Ladder page
(tools/kits/build.py) is an editor seeded from it, and nothing here writes a game file except apply_ladder.py.
"""
import copy
import hashlib
import json
import re
from pathlib import Path

import kitdata

ROOT = kitdata.ROOT
JAVA = kitdata.JAVA
REVIEW = Path(__file__).resolve().parent / "review"
PUBLISHED = REVIEW / "published.json"


class LadderError(Exception):
    """The data was reshaped past what these tools read; fix the file or the reader, never guess."""


def read(path):
    return Path(path).read_text(encoding="utf-8")


def constants(text):
    return dict(re.findall(r'public static final String (\w+)\s*=\s*"([^"]+)"', text))


def stations(doc=None):
    """[(NAME, station id)] in campaign order with ("NONE", "none") last, from kit-ladder.json's stations."""
    doc = doc if doc is not None else kitdata.load_ladder()
    ids = [s.get("id") for s in doc.get("stations") or []]
    if not ids or ids[0] != "start":
        raise LadderError("kit-ladder.json: expected its stations in campaign order, 'start' first; read %s" % ids)
    return [(sid.upper().replace("-", "_"), sid) for sid in ids] + [("NONE", "none")]


def rows(doc=None, enums=None, station_list=None):
    """Every placement in file order: {line (index), kind (weapon, sheet or pattern), item (the game id), id (the page
    id), station}. A placement naming no item the page knows, or an undeclared station, is a LadderError."""
    doc = doc if doc is not None else kitdata.load_ladder()
    enums = enums if enums is not None else kitdata.pattern_ids()
    known = {sid for _, sid in (station_list if station_list is not None else stations(doc))}
    out = []
    for n, p in enumerate(doc.get("placements") or []):
        item, station = p.get("item"), p.get("station")
        page = kitdata.page_id(item or "", enums)
        if page is None:
            raise LadderError("kit-ladder.json: placements[%d] names %r, which is no card or machine pattern id" % (n, item))
        if station not in known:
            raise LadderError("kit-ladder.json: placements[%d] places %s on %r, which is not a declared station" % (n, item, station))
        kind = ("weapon" if item.startswith(kitdata.CARD_PREFIX + "weapon:") else
                "sheet" if item.startswith(kitdata.CARD_PREFIX + "sheet:") else "pattern")
        out.append({"line": n, "kind": kind, "item": item, "id": page, "station": station})
    return out


# ---------------------------------------------------------------------------------------------------------------
# The page's saved state (db doc kits/state, field json) and how it differs from the game.

def load_state(path):
    """The page state from an ArtifactData out_dir file (kits/state.json: {"json": "<state>", "at": ...}),
    or a bare state object, or a kits.json the page's Download button wrote. Special ids and machine pattern page ids the
    game has since renamed (kitdata.RETIRED_SPECIALS, kitdata.RETIRED_PAGE_IDS) are read as their current ids."""
    return translate_retired(_load_state(path))


def translate_retired(state):
    for k in state.get("weapon") or []:
        for m in k.get("members") or []:
            for f in ("special", "belt"):
                if m.get(f) in kitdata.RETIRED_SPECIALS:
                    m[f] = kitdata.RETIRED_SPECIALS[m[f]]
    if state.get("place"):
        state["place"] = {kitdata.current_key(key): station for key, station in state["place"].items()}
    return state


def translate_base(base):
    """A published page's manifest with renamed page ids and clip ids read as their current ids (kitdata.current_key)."""
    if not base:
        return base
    out = {}
    for field, table in base.items():
        if not isinstance(table, dict):
            out[field] = table
            continue
        out[field] = {kitdata.current_key(key): value for key, value in table.items()}
        if field == "clips":
            for clip in out[field].values():
                if isinstance(clip, dict) and clip.get("weaponId") in kitdata.RETIRED_WEAPONS:
                    clip["weaponId"] = kitdata.RETIRED_WEAPONS[clip["weaponId"]]
    return out


def _load_state(path):
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
    """Readable differences between a card's seats on the page and in the game, seat by seat. A weapon card's seats are
    loadout-cards.json's twelve; a tactic sheet's are its seated suits, or for a sheet the game still deals by role the
    even spread build.py seeds, so seating such a sheet differently on the page reads as a change. Pieces the game does
    not have are flagged."""
    unknown = lambda x: " (not a game item)" if pieces is not None and x and x not in pieces else ""
    out = []
    if lst == "armor":
        for i in range(max(len(page), len(game))):
            a, b = (page[i] if i < len(page) else {}).get("armor"), (game[i] if i < len(game) else {}).get("armor")
            if a != b:
                out.append("seat %d: armor %s -> %s%s" % (i + 1, b, a, unknown(a)))
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
    add             the page places a real item kit-ladder.json does not place. apply writes it.
    game-moved      with a base: only the game changed since the page was published; the page is stale (republish).
    conflict        with a base: page and game both moved, differently. The owner decides; apply skips it.
    content         a card's name, description or seats differ from the game's, and the owner changed them on the page
                    (or there is no base to tell). apply_ladder.py --cards writes it.
    content-stale   with a base: the card differs only because the game changed it after the page was published.
    unknown-station the page names a station kit-ladder.json lacks (the page reads it as none). apply skips it.
    orphan          a page key that is no game item (renamed or deleted). apply skips it.
    new-card        a card the page made that the game does not have. Author it in loadout-cards.json.
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
    "unknown-station": "page station is not a declared station: skipped",
    "orphan": "not a game item: skipped",
    "new-card": "page-made card: author it in loadout-cards.json by hand",
    "content": "owner edited the card: apply_ladder.py --cards writes it",
    "content-stale": "game changed the card after publish: republish",
    "grant": "free pick is FirstRefit's: change it by hand",
    "unseen": "page never placed it: seeded from the game",
}


def diff_table(entries, names=None):
    """A markdown table of diff entries."""
    names = names or {}
    if not entries:
        return "No differences: the page state matches the game data.\n"
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
# Writing the data.

def patch(doc, changes, adds):
    """kit-ladder.json's document with ``changes`` {game id: station id} applied to existing placements and ``adds``
    {game id: station id} appended. Placement order is kitdata.ordered's (each kind's block in campaign order, a stable
    sort), so serialising the result is deterministic and patching it again with the same input changes nothing."""
    out = copy.deepcopy(doc)
    by_item = {p["item"]: p for p in out["placements"]}
    for item, station in changes.items():
        if item not in by_item:
            raise LadderError("kit-ladder.json has no placement for %s to move" % item)
        by_item[item]["station"] = station
    for item, station in sorted(adds.items()):
        if item in by_item:
            raise LadderError("kit-ladder.json already places %s" % item)
        out["placements"].append({"item": item, "station": station})
    out["placements"] = kitdata.ordered(out["placements"], [s["id"] for s in out["stations"]])
    return out


def patch_cards(cards_doc, edits):
    """loadout-cards.json's document with ``edits`` {card id: page card} written in: a weapon card takes the page's twelve
    seats; a tactic sheet takes the page's twelve suits as its ``seats`` (the game then issues each seat its own suit,
    so its suit set, armour class and seat classes follow from them). Names, descriptions and grants are never written:
    they are string keys or FirstRefit's."""
    out = copy.deepcopy(cards_doc)
    by_id = {c["id"]: ("weapon", c) for c in out["weaponCards"]}
    by_id.update({c["id"]: ("armor", c) for c in out["tacticSheets"]})
    for cid, page in edits.items():
        if cid not in by_id:
            raise LadderError("loadout-cards.json has no card %s to edit" % cid)
        lst, card = by_id[cid]
        members = page.get("members") or []
        if lst == "weapon":
            card["members"] = [{k: m[k] for k in kitdata.WEAPON_KEYS if m.get(k) is not None} for m in members]
        else:
            seats = [m.get("armor") for m in members]
            if len(seats) != kitdata.SQUAD or not all(seats):
                raise LadderError("%s: the page seats %d suits; a sheet seats %d" % (cid, len([s for s in seats if s]), kitdata.SQUAD))
            for derived in ("suits", "armorClass", "seatClasses"):
                card.pop(derived, None)
            card["seats"] = seats
    return out


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
    """The repo's side of a diff: {data (build.collect, no art, not strict), rows (placements), stations [(NAME, id)],
    rowStations {page id: station}, cards {page id: card}, items {every real page id}, names {id: name}, enums,
    gameIds {page id: game id}, ladder (kit-ladder.json), pieces}."""
    import build  # build imports this module; import lazily
    data = build.collect(reel_index=reel_index, art=False, strict=False)
    doc = kitdata.load_ladder()
    station_list = stations(doc)
    enums = kitdata.pattern_ids()
    the_rows = rows(doc, enums, station_list)
    cards = {k["id"]: k for k in data["KITS"] + data["ARMORKITS"]}
    patterns = {it["id"]: it for lst in data["ITEMS"].values() for it in lst}
    names = {i: v["name"] for i, v in list(cards.items()) + list(patterns.items())}
    sheet_ids = {k["id"] for k in data["ARMORKITS"]}
    game_ids = {i: kitdata.game_id(i, enums, sheet_ids) for i in list(cards) + list(patterns)}
    return {"data": data, "rows": the_rows, "stations": station_list, "rowStations": {r["id"]: r["station"] for r in the_rows},
            "cards": cards, "patterns": patterns, "items": set(cards) | set(patterns), "names": names, "enums": enums,
            "gameIds": game_ids, "ladder": doc,
            "pieces": {p["id"] for key in ("PRIMARIES", "SPECIALS", "ARMORS") for p in data[key]}}


def load_base(page=None):
    """The published page's manifest: from a page HTML when given, else the tracked review/published.json, else None.
    Ids renamed since it was published are read as their successors (translate_base)."""
    if page:
        return translate_base(manifest(page_data(read(page))))
    if PUBLISHED.exists():
        return translate_base(json.loads(read(PUBLISHED)))
    return None
