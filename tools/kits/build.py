"""Build the kit ladder page: a self-contained HTML tool for authoring squad cards and when they unlock.

    python tools/kits/build.py [--artifact] [--dolls <dir>] [--reel <index.json> [--clips <assets.json>]] [out.html]
    (default build/kits/kit-ladder.html; --artifact drops the html/head/body wrapper for publishing through the
    Artifact tool)

The Weapons tab (--reel) has one card per clip of the weapon reel: its stats from the weapon catalog and
MechWeaponComponent, its sounds, its clip and a notes box saved to the artifact's db (collection
weapon-notes, one doc per clip id). Re-run after weapon changes:
  1. render the weapon reel (its index.json sits beside weapon-reel-<category>.mp4);
  2. python tools/kits/cut_weapon_clips.py <reel>/index.json <clips dir>   (clips and posters, outside the repo);
  3. upload <clips dir>/*.mp4 to the artifact's asset store (Artifact tool, asset: true) and write
     <clips dir>/assets.json as {"<clip id>": "<asset url exactly as returned>"};
  4. build with --reel <reel>/index.json --clips <clips dir>/assets.json and republish with the db, user,
     downloads and assets capabilities.

Reads the shipped marine weapon, special-equipment and armour catalogs, the equipment access tiers,
the strings, the armoury icons and the loadout cards (mod/data/marines/loadout-cards.json: weapon cards and
tactic sheets), and embeds all of it in one file.

Two ladders, never mixed: weapon cards (primaries and secondaries per marine) and Equipment cards
(armour per marine). Each card draws the way a Barracks loadout card does (kit bricks coloured by
what each piece throws or wears) and can be placed on the Alpha mission timeline to say after which
mission it goes on sale. Every card's and machine pattern's seeded station is the game's own Kit Ladder
(mod/data/marines/kit-ladder.json; Relief's free pick is the cards' "grant"), so a rebuild shows what the game
sells when. Exports a kits.json holding both ladders.
"""
import base64
import glob
import io
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import kitdata  # noqa: E402
import ladder  # noqa: E402

ROOT = ladder.ROOT
MOD = ROOT / "mod"
JAVA = ladder.JAVA
NOT_KIT_PRIMARY = {"weapon.drone-pulse", "weapon.drone-ion"}
PRIMARY_ICON = {"RIFLE": "weapon-rifle", "DMR": "weapon-dmr", "LASER_GUN": "weapon-laser-gun", "SMG": "weapon-smg"}
PRIMARY_LIE = 32
ART_PX = 160

# The Alpha mission timeline, in campaign order: the page's names and notes for the game's Kit Ladder stations
# (kit-ladder.json's stations), which this list must match id for id. A card placed on a station goes on sale once that
# mission settles; "start" is the company's opening issue, "none" is never sold in the store.
STATIONS = [
    ("none", "Not sold", "Unplaced", "Never on a store shelf. Patrons and recovery may still hand it out by tier."),
    ("start", "Company start", "Start", "What a fresh company holds."),
    ("relief", "Relief", "Wayline opening", "First Wayline job: the one free pick (Steady Line or Forward Screen, with its armour sheet). The other path and Checkpoint Issue go on sale in the store."),
    ("counterattack", "Counterattack", "Wayline opening", "Recovered Hound and first refit."),
    ("junction", "Open the Junction", "Broken Supply Route", "Defended junction."),
    ("thunder-run", "Thunder Run", "Broken Supply Route", "Convoy escort, settled won or lost. Convoy Escort Issue is listed at the employer's market."),
    ("dust-ledger", "Dust Ledger", "Corvus Account", "Saltwell Cross; side objectives."),
    ("blind-watch", "Blind Watch", "Corvus Account", "The Sounding on Warion; sable mortars and missiles. Counter-Battery Issue is listed at Hegemony markets."),
    ("cold-account", "A Cold Account", "Corvus Account", "Garnir."),
    ("salvage-rights", "Salvage Rights", "A Working Ship", "Shepherd recovery, first contract."),
    ("cold-start", "Cold Start", "A Working Ship", "Shepherd recovery, second contract."),
    ("working-passage", "Working Passage", "A Working Ship", "The Morrow flies. Builder's Guard is listed at Asharu, and the end-game equipment wall opens here, before the Bloom challenge."),
    ("colony-breach", "Colony Breach", "Wellhollow Bloom", "Rav Bloom challenge, chapter 1 (replayable)."),
    ("cordon-road", "Cordon Road", "Wellhollow Bloom", "Chapter 2."),
    ("the-burn", "The Burn", "Wellhollow Bloom", "Chapter 3."),
    ("broad-front", "Broad Front", "Wellhollow Bloom", "Chapter 4, the end-of-alpha challenge."),
    ("post-alpha-small-war", "A Small War (cut from alpha)", "Post-alpha", "Agreus cards parked here: the recovered Specter and Arbalest sections and the Boarding Jack."),
]

def kit_ladder():
    """{page id: station id} for every placement in kit-ladder.json (tools/kits/ladder.py reads them). A duplicate
    placement is reported by check.py, not here."""
    station_list = ladder.stations()
    if [s[0] for s in STATIONS] != ["none"] + [sid for enum, sid in station_list if enum != "NONE"]:
        raise SystemExit("kits/build.py: STATIONS must list kit-ladder.json's station ids in its order, 'none' first; the data has %s"
                         % [sid for _, sid in station_list])
    try:
        rows = ladder.rows(station_list=station_list)
    except ladder.LadderError as e:
        raise SystemExit("kits/build.py: %s" % e)
    return {r["id"]: r["station"] for r in rows}


def load(path):
    """Starsector's relaxed JSON: whole-line # comments and trailing commas are allowed."""
    raw = re.sub(r"(?m)^\s*#.*$", "", Path(path).read_text(encoding="utf-8"))
    return json.loads(re.sub(r",(\s*[}\]])", r"\1", raw))


def flat(tree, out=None):
    out = {} if out is None else out
    for key, value in tree.items():
        if isinstance(value, dict):
            flat(value, out)
        else:
            out[key] = value
    return out


def constants(java_file):
    return dict(re.findall(r'public static final String (\w+)\s*=\s*"([^"]+)"', java_file.read_text(encoding="utf-8")))


def image(path, art_on=True):
    if not art_on:
        return None
    from PIL import Image
    art = Image.open(MOD / path).convert("RGBA")
    art.thumbnail((ART_PX, ART_PX))
    buffer = io.BytesIO()
    art.save(buffer, "PNG", optimize=True)
    return "data:image/png;base64," + base64.b64encode(buffer.getvalue()).decode()


# What the machine-dolls render audit found, by machine; the page shows each beside its doll.
DOLL_NOTES = {
    "bulwark": ["Arm sockets are 2x2 and hold two Chainguns; the right auxiliary bay ships empty."],
    "hound": ["One shoulder socket, on the left only."],
    "warden": ["The 2x3 shoulders hold 2x2 LRM-5 (Frag) racks, leaving a spare row."],
    "heavy_apc": ["The one APC chassis: the convoy escort is this hull in heavy plating with a spall liner, not a doll of its own."],
    "fire_support_apc": ["No bay card: capacity 0, unlike the troop carrier on the same chassis."],
    "breakthrough_tank": ["Two kit racks share four slots; the stock fit fills only the first, with its capacitor bank."],
    "laser_tank": ["The stock flux parts (double bank, two vent arrays) fill three of the four rack slots across both racks."],
}


# Where the machine stands on its pad in the suite's 1920x1080 Mech Lab frame: the vehicle bay's pad and the mech bay's.
PAD_BOX = {"vehicle": (1380, 240, 1790, 1010), "mech": (1065, 330, 1545, 900)}


def pad_photo(folder, machine):
    """The machine as the Mech Lab shows it on its pad, cropped from the suite's full frame."""
    from PIL import Image
    name = machine["id"] + "-full.png"
    # The suite writes the frames flat beside the manifest; a curated review set keeps them under full/.
    frame = next((f for f in (folder / "full" / name, folder / name) if f.exists()), None)
    if frame is None:
        return None
    buffer = io.BytesIO()
    Image.open(frame).convert("RGB").crop(PAD_BOX[machine["kind"]]).save(buffer, "JPEG", quality=85)
    return "data:image/jpeg;base64," + base64.b64encode(buffer.getvalue()).decode()


def machine_dolls(folder):
    if folder.suffix == ".json" and folder.is_file():
        # The dolls a published page already carries (refresh.py from-page), when the suite has not been run here.
        out = json.loads(folder.read_text(encoding="utf-8"))
        for m in out:
            m["notes"] = DOLL_NOTES.get(m["id"], [])
        return out
    manifest = folder / "manifest.json"
    if not manifest.exists():
        return []
    out = []
    for m in json.loads(manifest.read_text(encoding="utf-8")):
        png = folder / (m["id"] + ".png")
        m["img"] = "data:image/png;base64," + base64.b64encode(png.read_bytes()).decode() if png.exists() else None
        m["photo"] = pad_photo(folder, m)
        m["notes"] = DOLL_NOTES.get(m["id"], [])
        out.append(m)
    return out


def java_switch(body):
    """{CONSTANT: value token} from 'case A, B -> 1.5f;' and 'case A -> { return 1.5f; }' lines."""
    out = {}
    for names, token in re.findall(r"case ([A-Z_0-9, ]+) -> (?:\{\s*return\s+)?([\w.]+?)f?;", body):
        for n in names.split(","):
            out.setdefault(n.strip(), token)
    return out


def method_body(source, signature):
    if signature not in source:
        raise SystemExit("kits/build.py: %r not found in MechWeaponComponent.java; the file was reshaped" % signature)
    body = source[source.index(signature):]
    return body[:body.index("\n    }\n")]


def mech_parts(text, registry_ids):
    """Every MechWeaponComponent with the figures the Weapons tab shows: footprint, mount, rack and the authored
    cycle and flux."""
    src = (JAVA / "battle/mech/MechWeaponComponent.java").read_text(encoding="utf-8")
    enum_body = src[src.index("public enum MechWeaponComponent {"):src.index("public boolean chainsSalvos()")]
    flux_consts = {name: float(value) for name, value in re.findall(
        r'static final float (\w+) = Float\.parseFloat\(\s*System\.getProperty\("[^"]+",\s*"([\d.]+)"\)\)', src)}

    def resolved(table):
        out = {}
        for name, token in table.items():
            try:
                out[name] = float(token)
            except ValueError:
                if token not in flux_consts:
                    raise SystemExit("kits/build.py: MechWeaponComponent value %r for %s is not a number or a known constant" % (token, name))
                out[name] = flux_consts[token]
        return out

    def authored(signature):
        return resolved(java_switch(method_body(src, signature)))

    cycle = authored("private float authoredCycleSeconds()")
    flux = authored("private float authoredFlux()")
    parts = []
    for m in re.finditer(r'(?ms)^    ([A-Z_0-9]+)\((.*?LayeredMechAppearance\.\w+)\)[,;]\s*$', enum_body):
        args = [a.strip() for a in re.sub(r"//[^\n]*", "", m.group(2)).split(",")]
        if len(args) != 10 or not args[0].startswith('"component.'):
            raise SystemExit("kits/build.py: MechWeaponComponent.%s has %d constructor arguments, expected 10" % (m.group(1), len(args)))
        token = args[6]
        if token.startswith('"'):
            weapon_id = token.strip('"')
        else:
            const = token.split(".")[-1]
            if const not in registry_ids:
                raise SystemExit("kits/build.py: MechWeaponComponent.%s weapon %r does not resolve" % (m.group(1), token))
            weapon_id = registry_ids[const]
        enum = m.group(1)
        if enum not in cycle or enum not in flux:
            raise SystemExit("kits/build.py: MechWeaponComponent.%s has no authored cycle or flux case" % enum)
        parts.append({"enum": enum, "name": text(args[1].strip('"')), "mount": args[2].split(".")[-1],
                      "hardpoint": args[3].split(".")[-1], "fp": [int(args[4]), int(args[5])], "weaponId": weapon_id,
                      "perTrigger": int(args[7]), "salvos": int(args[8]), "cycle": cycle[enum], "flux": flux[enum]})
    if len(parts) < 20:
        raise SystemExit("kits/build.py: parsed only %d MechWeaponComponent constants" % len(parts))
    return parts


def fmt(x, unit=""):
    if x is None:
        return None
    return ("%g" % round(x, 2)) + unit


def weapon_cards(reel_index, clips_map, weapons, text, registry_ids):
    """One card per clip in the weapon reel index: the clip's asset url and poster, its fire mode and sounds, and
    the stats of the weapon (and, for a mech or vehicle gun, of the MechWeaponComponent part) it fires."""
    index = json.loads(Path(reel_index).read_text(encoding="utf-8"))
    urls, poster_dir = {}, Path(reel_index).parent
    if clips_map:
        urls = json.loads(Path(clips_map).read_text(encoding="utf-8"))
        poster_dir = Path(clips_map).parent
    parts = mech_parts(text, registry_ids)
    cards = []
    for reel in index["reels"]:
        for clip in reel["clips"]:
            wid = clip["weaponId"]
            w = weapons.get(wid)
            if w is None:
                raise SystemExit("kits/build.py: reel clip %s fires %s, which no *.weapon.json defines" % (clip["shot"], wid))
            sim = w["sim"]
            label = clip["name"].split(" on the ")[0]
            matches = [p for p in parts if p["weaponId"] == wid]
            if len(matches) > 1:
                matches = [p for p in matches if p["name"] == label]
            part = matches[0] if len(matches) == 1 else None
            if w["mount"] == "mech-mount" and part is None:
                raise SystemExit("kits/build.py: reel clip %s (%s) matches %d MechWeaponComponent parts" % (clip["shot"], wid, len(matches)))
            rotary, beam, mag = sim.get("rotary"), sim.get("beam"), sim.get("magazine")
            per_shot = sim.get("projectilesPerShot", 1)
            burst = sim.get("burstCount", 1)
            rows, notes = [], []

            def row(label_, value, hint=None):
                rows.append([label_, value if value not in (None, "") else "n/a", hint])

            rng = fmt(sim.get("range"), " cells")
            if sim.get("minRange"):
                rng += " (min %g)" % sim["minRange"]
            row("Range", rng)
            dmg = fmt(sim.get("damage"))
            if per_shot > 1:
                dmg += " × %d" % per_shot
            row("Damage", dmg)
            row("Penetration", fmt(sim.get("penetration")))
            if sim.get("contact"):
                row("Contact warhead", "%g dmg · %g pen" % (sim["contact"]["damage"], sim["contact"]["penetration"]),
                    "vs hardened targets")
            row("Accuracy", fmt(sim.get("accuracy")))
            if rotary:
                spin = "spin-up %gs · " % rotary["spinUp"] if rotary.get("spinUp") else ""
                row("Fire rate", "%s%g rds/s" % (spin, rotary["fullRate"]), "held trigger")
                row("Belt", "%d rds · refill %g/s" % (rotary["belt"], rotary["refill"]))
                row("Flux", "%g per round" % rotary["fluxPerRound"])
                dps = sim["damage"] * rotary["fullRate"]
            elif beam:
                row("Fire rate", "beam · %g pulses/s" % beam["pulseRate"], "held trigger")
                row("Flux", "%g per second" % beam["fluxPerSecond"])
                dps = sim["damage"] * beam["pulseRate"]
            elif part:
                rounds = part["perTrigger"]
                spacing = sim.get("burstSpacing", 0)
                row("Per trigger", "%d rd%s%s" % (rounds, "s" if rounds > 1 else "", " · %gs apart" % spacing if rounds > 1 and spacing else ""))
                row("Cycle", fmt(part["cycle"], " s"), part["hardpoint"].lower() + " part")
                row("Flux", "%g per trigger" % part["flux"])
                dps = sim["damage"] * rounds / max(part["cycle"], (rounds - 1) * spacing)
            else:
                spacing = sim.get("burstSpacing", 0)
                if burst > 1:
                    row("Burst", "%d · %gs apart" % (burst, spacing))
                row("Cooldown", fmt(sim.get("cooldown"), " s"))
                cyc = sim["cooldown"] + max(0, burst - 1) * spacing
                dps = sim["damage"] * per_shot * burst / cyc if cyc > 0 else None
            row("≈ DPS", fmt(dps), "damage over its cycle; ignores accuracy, reload and flux")
            if part and part["salvos"] > 0:
                row("Rack", "%d rds (%d × %d)" % (part["salvos"] * part["perTrigger"], part["salvos"], part["perTrigger"]))
            if mag:
                row("Magazine", " / ".join(str(s) for s in mag["size"]) + " · reload %gs" % mag["reload"],
                    "surplus / service / milspec / masterwork · " + mag["trigger"])
            if sim.get("aoeRadius"):
                row("Blast radius", fmt(sim["aoeRadius"], " cells"))
            if sim.get("wallDamage"):
                row("Wall damage", "%g%s" % (sim["wallDamage"], " · r %g" % sim["wallDamageRadius"] if sim.get("wallDamageRadius") else ""))
            if sim.get("incendiaryHeat"):
                row("Incendiary heat", fmt(sim["incendiaryHeat"]))
            if part:
                row("Size", "%d×%d" % tuple(part["fp"]), "columns × rows")
                row("Mount", "%s · %s" % (part["mount"].lower(), part["hardpoint"].lower()))
            else:
                row("Mount", w["mount"].replace("-", " "))
                if clip["category"] == "vehicle":
                    notes.append("A vehicle turret gun with no mech part: no footprint, part cycle or flux.")
            tags = [t for t, on in (("indirect", sim.get("indirectFire")), ("tracks fliers", sim.get("tracksFliers")),
                                    ("interceptable", sim.get("interceptableProjectile")), ("boost ramp", sim.get("boostRamp"))) if on]
            poster = poster_dir / (clip["shot"] + ".jpg")
            cards.append({
                "id": clip["shot"], "cat": clip["category"], "name": clip["name"], "weaponId": wid,
                "catalog": text(w["catalog"]["displayName"]), "part": part and part["name"], "mode": clip["mode"],
                "shooter": clip["shooter"], "rounds": clip["rounds"], "role": clip.get("role") or "",
                "fire": clip.get("fireSound"), "impact": clip.get("impactSound"), "loop": clip.get("loopSound"),
                "rows": rows, "tags": tags, "notes": notes, "url": urls.get(clip["shot"]),
                "poster": "data:image/jpeg;base64," + base64.b64encode(poster.read_bytes()).decode() if poster.exists() else None,
            })
    return {"cards": cards, "leftOut": index.get("leftOut", [])}


def collect(dolls_dir=None, reel_index=None, clips_map=None, art=True, strict=True):
    """Everything the page embeds, keyed by the template's placeholder names (KITS, ITEMS, WEAPONREEL, ...), plus
    WEAPONS (every *.weapon.json weapon) and PARTS (every MechWeaponComponent), which are not embedded. ``art=False`` skips the raster art and machine dolls (fast, for
    check.py). ``strict=False`` gives a card or pattern with no placement the station None instead of stopping,
    so check.py can report it."""
    strings = flat(load(MOD / "data/strings/strings.json"))
    text = lambda key: strings.get(key.lstrip("@"), key) if key else ""
    templates = load(MOD / "data/marines/equipment-templates.template.json")
    access = {e["equipmentId"]: e["accessTier"].upper() for e in templates["armor"] + templates["specialEquipment"]}
    grade_access = {e["equipmentId"]: {g.upper(): v["accessTier"].upper() for g, v in e["grades"].items()}
                    for e in templates["primaries"]}
    weapons = {}
    for f in glob.glob(str(MOD / "data/marines/*.weapon.json")):
        for w in load(f)["weapons"]:
            weapons[w["id"]] = w

    def kind_of(weapon_id):
        w = weapons.get(weapon_id)
        if not w:
            return "UTILITY"
        render = w.get("render", {})
        sprite = (render.get("projectileSprite") or "").rsplit("/", 1)[-1]
        if render.get("energy") or render.get("beamStyle") or render.get("heldSpriteFamily") == "LASER_GUN":
            return "ENERGY"
        if sprite.startswith("missile") or render.get("contrail") or w.get("sim", {}).get("boostRamp"):
            return "MISSILE"
        return "BALLISTIC"

    images = {}
    primaries = []
    for w in weapons.values():
        if w["mount"] != "marine-primary" or w["id"] in NOT_KIT_PRIMARY:
            continue
        family = w["render"]["heldSpriteFamily"]
        sim = w["sim"]
        variants = {}
        for grade in ("SURPLUS", "SERVICE", "MILSPEC", "MASTERWORK"):
            name = PRIMARY_ICON[family]
            if family == "RIFLE" and grade == "SURPLUS":
                name = "weapon-rifle-surplus"
            if family == "DMR" and grade == "MASTERWORK":
                name = "weapon-dmr-masterwork"
            variants[grade] = "graphics/ui/armory/%s.png" % name
        for icon in variants.values():
            images[icon] = image(icon, art)
        primaries.append({
            "id": w["id"], "name": text(w["catalog"]["displayName"]), "kind": kind_of(w["id"]),
            "lie": PRIMARY_LIE, "icons": variants, "note": text(w["catalog"].get("role")),
            "access": grade_access.get(w["id"], {}),
            "stats": "range %g · dmg %g × %d burst" % (sim.get("range", 0), sim.get("damage", 0), sim.get("burstCount", 1)),
        })

    specials = []
    for e in load(MOD / "data/marines/marine-special-equipment.equipment.json")["equipment"]:
        icon = e["presentation"]["armoryIcon"]
        images[icon] = image(icon, art)
        wid = e["activation"].get("weaponId")
        specials.append({
            "id": e["id"], "name": text(e["catalog"]["displayName"]), "kind": kind_of(wid),
            "lie": e["presentation"].get("armoryIconLie"), "icon": icon, "note": text(e["catalog"].get("subtitle")),
            "access": access.get(e["id"], "COMMON"),
            "thrown": e["activation"]["type"] in ("arc-explosive", "utility-smoke", "utility-satchel"),
        })

    armor_doc = load(MOD / "data/marines/marine-armor-catalog.armor.json")
    effect_icon = {"breacher-assist": "graphics/ui/armory/system-breach-assist.png",
                   "field-aid": "graphics/ui/armory/system-field-aid.png"}
    for icon in effect_icon.values():
        images[icon] = image(icon, art)
    systems = {}

    def add_system(sd):
        if sd["id"] in systems:
            return
        if sd["effect"] == "breacher-assist":
            stats = "arc %g° · soak %g · %gs window / %gs recovery · shove x%g" % (
                sd["shieldedArcDegrees"], sd["screenSoak"], sd["durationSeconds"], sd["cooldownSeconds"], sd["moveSpeedMult"])
        else:
            stats = "makes the wearer a squad medic"
        systems[sd["id"]] = {"id": sd["id"], "name": text(sd["displayName"]), "grade": sd["grade"].upper(),
                             "effect": sd["effect"], "icon": effect_icon[sd["effect"]], "stats": stats,
                             "note": text(sd["description"])[:140].rsplit(" ", 1)[0] + "…"}

    for sd in armor_doc["integralSystems"]:
        add_system(sd)
    armors = []
    for a in armor_doc["armor"]:
        c, bt = a["catalog"], a["battle"]
        icon = c["iconPath"]
        images[icon] = image(icon, art)
        system = a.get("integralSystem") or bt.get("integralSystem")
        sys_id = None
        if system:
            if "ref" in system:
                sys_id = system["ref"]
            else:
                add_system(system)
                sys_id = system["id"]
        armors.append({
            "id": a["id"], "name": text(c["displayName"]), "icon": icon, "system": sys_id,
            "role": c["role"], "tier": c["tier"], "powered": c["armorClass"] == "power-suit",
            "access": access.get(a["id"], "COMMON"),
            "stats": "tier %d · %s · armour %g / rating %g" % (c["tier"], c["role"], bt.get("armorCapacity", 0), bt.get("armorRating", 0)),
        })
    armor_by_id = {a["id"]: a for a in armors}
    registry = constants(JAVA / "battle/weapon/WeaponRegistry.java")

    # Loadout cards: the weapon cards' twelve seats and the tactic sheets' suits, from loadout-cards.json.
    cards = kitdata.load_cards()
    kits = []
    for card in cards["weaponCards"]:
        members = [{"role": m["role"], "primary": m["primary"], "grade": m["grade"], "special": m.get("special"),
                    "belt": m.get("belt")} for m in card["members"]]
        if len(members) != 12:
            raise SystemExit("kit %s has %d members" % (card["id"], len(members)))
        kits.append({"id": card["id"], "name": text(card["name"]), "description": text(card["description"]),
                     "members": members, "grant": card.get("grant") or "sale"})

    # Armour cards: a seated sheet's twelve suits exactly as the game issues them; otherwise the sheet's suits and its
    # seat classes saying how many seats are power suits, which the game deals by role, so that seeding is an even
    # spread (and seating it on the page and applying it makes the sheet seated).
    armor_kits = []
    for sheet in cards["tacticSheets"]:
        patterns = kitdata.sheet_suits(sheet)
        if sheet.get("seats") is not None:
            classes = None
        elif sheet.get("armorClass") == "POWER_SUIT":
            classes = ["POWER_SUIT"] * 12
        else:
            classes = sheet.get("seatClasses") or ["STANDARD"] * 12
        standard = [s for s in patterns if s in armor_by_id and not armor_by_id[s]["powered"]] or patterns
        power = [s for s in patterns if s in armor_by_id and armor_by_id[s]["powered"]] or patterns
        members, si, pi = [], 0, 0
        for i, cls in enumerate(classes or ()):
            if cls == "POWER_SUIT":
                suit, pi = power[pi % len(power)], pi + 1
            else:
                suit, si = standard[si % len(standard)], si + 1
            members.append({"role": "Team Leader" if i % 4 == 0 else "Rifleman", "armor": suit})
        if classes is None:
            members = [{"role": "Team Leader" if i % 4 == 0 else "Rifleman", "armor": suit}
                       for i, suit in enumerate(sheet["seats"])]
        armor_kits.append({"id": sheet["id"], "name": text(sheet["name"]), "description": text(sheet["description"])
                           + " (" + sheet["tradition"].replace("_", " ").title() + " tradition, "
                           + sheet["mix"].replace("_", " ").lower() + " mix)", "members": members,
                           "grant": sheet.get("grant") or "sale"})
    if len(armor_kits) < 20:
        raise SystemExit("armour sheets read: %d" % len(armor_kits))

    placed = kit_ladder()
    for kit in kits + armor_kits:
        if kit["id"] not in placed and strict:
            raise SystemExit("kits/build.py: card %s has no placement in kit-ladder.json" % kit["id"])
        kit["station"] = placed.get(kit["id"])
        grant = kit.pop("grant")
        kit["grant"] = grant

    # Machine licences: one chip per pattern the store can teach, tiered by MechPattern's own tables.
    pattern_src = (JAVA / "marine/MechPattern.java").read_text(encoding="utf-8")
    tiers = {}
    for kind, name, tier in re.findall(r"(CHASSIS|WEAPON|VEHICLE|KIT)_TIERS\.put\(\w+\.(\w+),\s*EquipmentAccessTier\.(\w+)\)", pattern_src):
        tiers[(kind, name)] = tier
    hw_src = (JAVA / "ops/battleview/HardwareArt.java").read_text(encoding="utf-8")
    doll_src = (JAVA / "ops/battleview/DamageDollCanvas.java").read_text(encoding="utf-8")

    def stems(source, anchor):
        if anchor not in source:
            raise SystemExit("kits/build.py: anchor %r not found in the Java source; the file was reshaped" % anchor)
        body = source[source.index(anchor):]
        body = body[:body.index("};")]
        out = {}
        for names, stem in re.findall(r'case ([A-Z_0-9, ]+) -> "([a-z0-9-]+)";', body):
            for n in names.split(","):
                out[n.strip()] = stem
        if not out:
            raise SystemExit("kits/build.py: no 'case X -> \"<stem>\";' lines found after %r; the switch was reshaped" % anchor)
        return out

    weapon_stem = stems(hw_src, "private static String stem(MechWeaponComponent component)")
    internal_stem = stems(hw_src, "private static String stem(MechInternal internal)")
    vehicle_doll = stems(doll_src, "public static String dollPath(VehicleType type)")

    def svg_uri(path, light=False):
        data = (MOD / path).read_text(encoding="utf-8")
        if light:
            data = data.replace("currentColor", "#dbe5df")
        return "data:image/svg+xml;base64," + base64.b64encode(data.encode("utf-8")).decode()

    weight = {"COMMON": "light", "ADVANCED": "medium", "PRESTIGE": "heavy"}
    items = {"hull": [], "mweapon": [], "mpart": []}

    def item(group, kind, enum, name, sub, tier, icon=None, tone="DOLL", fp=None):
        item_id = "%s:%s" % (kind.lower(), enum)
        if item_id not in placed and strict:
            raise SystemExit("kits/build.py: %s has no placement in kit-ladder.json" % item_id)
        items[group].append({"id": item_id, "name": name, "sub": sub, "access": tier,
                             "station": placed.get(item_id), "icon": icon, "tone": tone, "fp": fp})

    variants = re.findall(r'^\s+(\w+)\("[a-z-]+", "(@[^"]+)"', (JAVA / "battle/mech/MechVariant.java").read_text(encoding="utf-8"), re.M)
    for enum, key in variants:
        tier = tiers.get(("CHASSIS", enum))
        if tier:
            item("hull", "CHASSIS", enum, text(key), "mech · " + weight[tier], tier,
                 svg_uri("graphics/ui/dolls/mech-%s.svg" % enum.lower(), True))
    for enum in re.findall(r"^\s+(\w+)\(", (JAVA / "battle/vehicle/VehicleType.java").read_text(encoding="utf-8"), re.M):
        tier = tiers.get(("VEHICLE", enum))
        if tier:
            item("hull", "VEHICLE", enum, text("@vehicleTypeName" + enum), "vehicle · " + weight[tier], tier,
                 icon=svg_uri("graphics/ui/dolls/%s.svg" % vehicle_doll[enum], True) if enum in vehicle_doll else None)
    comps = re.findall(r'^\s+(\w+)\("component\.[^"]+",\s*"(@[^"]+)"(?:,\s*MountFamily\.(\w+))?',
                       (JAVA / "battle/mech/MechWeaponComponent.java").read_text(encoding="utf-8"), re.M)
    weapon_src = (JAVA / "battle/mech/MechWeaponComponent.java").read_text(encoding="utf-8")
    # Hardpoint type and grid footprint, read from each component's constructor: ... HardpointType.X, <columns>, <rows>
    hardpoints = {m[0]: (m[1], (int(m[2]), int(m[3]))) for m in re.findall(
        r'^\s+(\w+)\("component\.[^"]+".*?HardpointType\.(\w+),\s*(\d+),\s*(\d+)', weapon_src, re.M | re.S)}
    if not hardpoints or len(hardpoints) != len(comps):
        raise SystemExit("kits/build.py: MechWeaponComponent constructor shape 'HardpointType.X, <cols>, <rows>' matched %d of %d components"
                         % (len(hardpoints), len(comps)))
    unknown = {t for t, _ in hardpoints.values()} - {"BALLISTIC", "ENERGY", "MISSILE"}
    if unknown:
        raise SystemExit("kits/build.py: unmapped HardpointType values %s" % sorted(unknown))
    wfp = {k: v[1] for k, v in hardpoints.items()}
    ifp = {m[0]: (int(m[1]), int(m[2])) for m in re.findall(r'^\s+(\w+)\("internal\.[^"]+".*?EquipmentAccessTier\.\w+,\s*(\d+),\s*(\d+)',
        (JAVA / "marine/MechInternal.java").read_text(encoding="utf-8"), re.M | re.S)}
    for enum, key, mount in comps:
        if enum not in weapon_stem:
            raise SystemExit("kits/build.py: HardwareArt.stem has no case for component %s" % enum)
        tier = tiers.get(("WEAPON", enum))
        if tier:
            item("mweapon", "WEAPON", enum, text(key), (mount.lower() + " mount") if mount else "mech weapon", tier,
                 svg_uri("graphics/ui/mech-hardware/%s.svg" % weapon_stem[enum]), hardpoints[enum][0], wfp[enum])
    internals = re.findall(r'^\s+(\w+)\("internal\.[^"]+", "(@[^"]+)", Role\.(\w+),\s*EquipmentAccessTier\.(\w+)',
                           (JAVA / "marine/MechInternal.java").read_text(encoding="utf-8"), re.M)
    for enum, key, role, tier in internals:
        internal_tone = {"ENGINE": "ENGINE", "PLATING": "ARMOR", "CAPACITOR_BANK": "FLUX"}.get(role, "UTILITY")
        item("mpart", "INTERNAL", enum, text(key), role.lower().replace("_", " "), tier,
             svg_uri("graphics/ui/mech-hardware/%s.svg" % internal_stem[enum]), internal_tone, ifp.get(enum))
    for enum, key in re.findall(r'^\s+(\w+)\("kit\.[^"]+", "(@[^"]+)"', (JAVA / "battle/vehicle/VehicleKit.java").read_text(encoding="utf-8"), re.M):
        tier = tiers.get(("KIT", enum))
        if tier:
            kit_id = re.search(r'^\s+%s\("(kit\.[^"]+)"' % enum, (JAVA / "battle/vehicle/VehicleKit.java").read_text(encoding="utf-8"), re.M).group(1)
            item("mpart", "KIT", enum, text(key), "vehicle kit", tier,
                 svg_uri("graphics/ui/mech-hardware/%s.svg" % kit_id.replace(".", "-")),
                 "FLUX" if enum == "CAPACITOR_PACK" else "UTILITY" if enum in ("READY_RACK", "ACCELERATED_LOADER") else "AUX", (2, 1))
    for lst in items.values():
        lst.sort(key=lambda x: (x["name"]))

    machines = machine_dolls(Path(dolls_dir) if dolls_dir else ROOT / "build/snapshots/machine-dolls") if art else []
    weapon_tab = weapon_cards(reel_index, clips_map, weapons, text, registry) if reel_index else {"cards": [], "leftOut": []}
    stations = [{"id": s[0], "name": s[1], "group": s[2], "note": s[3]} for s in STATIONS]
    return {"PRIMARIES": primaries, "SPECIALS": specials, "KITS": kits, "ARMORKITS": armor_kits, "STATIONS": stations,
            "ARMORS": armors, "ITEMS": items, "SYSTEMS": list(systems.values()), "IMAGES": images, "MACHINES": machines,
            "WEAPONREEL": weapon_tab, "WEAPONS": weapons, "PARTS": mech_parts(text, registry)}


def main():
    args = sys.argv[1:]
    dolls_dir = ROOT / "build/snapshots/machine-dolls"
    if "--dolls" in args:
        i = args.index("--dolls")
        dolls_dir = Path(args[i + 1])
        del args[i:i + 2]
    reel_index = clips_map = None
    if "--reel" in args:
        i = args.index("--reel")
        reel_index = args[i + 1]
        del args[i:i + 2]
    if "--clips" in args:
        i = args.index("--clips")
        clips_map = args[i + 1]
        del args[i:i + 2]
    argv = [a for a in args if a != "--artifact"]
    artifact = "--artifact" in args
    data = collect(dolls_dir, reel_index, clips_map)
    weapon_tab = data["WEAPONREEL"]
    missing_clips = [c["id"] for c in weapon_tab["cards"] if not c["url"]]
    if clips_map and missing_clips:
        print("warning: no uploaded clip for %s" % ", ".join(missing_clips))
    page = (Path(__file__).with_name("template.html")).read_text(encoding="utf-8")
    for key in ("PRIMARIES", "SPECIALS", "KITS", "ARMORKITS", "STATIONS", "ARMORS", "ITEMS", "SYSTEMS", "IMAGES", "MACHINES", "WEAPONREEL"):
        page = page.replace("__%s__" % key, json.dumps(data[key], ensure_ascii=False))
    if artifact:
        # The Artifact tool wraps the page in its own skeleton, so publish only the content.
        page = page[page.index("<title>"):].replace("</head><body>", "", 1).replace("</body></html>", "")
    out = Path(argv[0]) if argv else ROOT / "build/kits/kit-ladder.html"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(page, encoding="utf-8", newline="\n")
    # What this build embeds, for check.py's "changed since publish" and the three-way ladder diff: refresh.py
    # record-published copies it into tools/kits/review/published.json once this page is published.
    Path(str(out) + ".manifest.json").write_text(json.dumps(ladder.manifest(data), indent=1, sort_keys=True), encoding="utf-8", newline="\n")
    print("wrote %s: %d primaries, %d secondaries, %d suits, %d weapon cards, %d equipment cards, machine licences %s, %d weapon clips, %.0f KB" % (
        out, len(data["PRIMARIES"]), len(data["SPECIALS"]), len(data["ARMORS"]), len(data["KITS"]), len(data["ARMORKITS"]),
        {k: len(v) for k, v in data["ITEMS"].items()}, len(weapon_tab["cards"]), out.stat().st_size / 1024))


if __name__ == "__main__":
    main()
