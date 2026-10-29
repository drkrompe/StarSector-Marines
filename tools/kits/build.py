"""Build the kit ladder page: a self-contained HTML tool for authoring squad cards and when they unlock.

    python tools/kits/build.py [--artifact] [out.html]    (default build/kits/kit-ladder.html;
    --artifact drops the html/head/body wrapper for publishing through the Artifact tool)

Reads the shipped marine weapon, special-equipment and armour catalogs, the equipment access tiers,
the strings, the armoury icons and the built-in weapon and armour doctrines in
SquadEquipmentDoctrines.java / ArmorSheetBundles.java, and embeds all of it in one file.

Two ladders, never mixed: weapon cards (primaries and secondaries per marine) and Equipment cards
(armour per marine). Each card draws the way a Barracks loadout card does (kit bricks coloured by
what each piece throws or wears) and can be placed on the Alpha mission timeline to say after which
mission it goes on sale. Exports a kits.json holding both ladders.
"""
import base64
import glob
import io
import json
import re
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
MOD = ROOT / "mod"
JAVA = ROOT / "src/main/java/com/dillon/starsectormarines"
NOT_KIT_PRIMARY = {"weapon.drone-pulse", "weapon.drone-ion"}
PRIMARY_ICON = {"RIFLE": "weapon-rifle", "DMR": "weapon-dmr", "LASER_GUN": "weapon-laser-gun", "SMG": "weapon-smg"}
PRIMARY_LIE = 32
ART_PX = 160

# The Alpha mission timeline, in campaign order (campaign-overview.md). A card placed on a station
# goes on sale once that mission settles; "start" is the company's opening issue, "none" is not sold.
STATIONS = [
    ("none", "Not sold yet", "Unplaced", "Reachable only by ordinary shelves, licences, patrons and recovery for its tier."),
    ("start", "Company start", "Start", "What a fresh company holds."),
    ("relief", "Relief", "Wayline opening", "First Wayline job: the one free pick (Steady Line or Forward Screen, with its armour sheet). The other path and Checkpoint Issue go on sale in the store."),
    ("counterattack", "Counterattack", "Wayline opening", "Recovered Hound and first refit."),
    ("junction", "Open the Junction", "Broken Supply Route", "Defended junction. Trench-Clearing Issue is the reward."),
    ("thunder-run", "Thunder Run", "Broken Supply Route", "Convoy escort. Convoy Escort Issue goes on sale at the employer's market."),
    ("dust-ledger", "Dust Ledger", "Corvus Account", "Saltwell Cross; side objectives."),
    ("blind-watch", "Blind Watch", "Corvus Account", "The Sounding on Warion; sable mortars and missiles."),
    ("cold-account", "A Cold Account", "Corvus Account", "Garnir. Counter-Battery Issue goes on sale at Hegemony markets."),
    ("salvage-rights", "Salvage Rights", "A Working Ship", "Shepherd recovery, first contract."),
    ("cold-start", "Cold Start", "A Working Ship", "Shepherd recovery, second contract."),
    ("working-passage", "Working Passage", "A Working Ship", "The Morrow flies. Builder's Guard goes on sale at Asharu, and the end-game equipment wall opens here, before the Bloom challenge."),
    ("colony-breach", "Colony Breach", "Khettara Bloom", "Rav Bloom challenge, chapter 1 (replayable)."),
    ("cordon-line", "Cordon Line", "Khettara Bloom", "Chapter 2."),
    ("cordon-road", "Cordon Road", "Khettara Bloom", "Chapter 3."),
    ("the-burn", "The Burn", "Khettara Bloom", "Chapter 4."),
    ("broad-front", "Broad Front", "Khettara Bloom", "Chapter 5, the end-of-alpha challenge."),
    ("wins-3", "3 victories", "Count-based", "Any three won operations (VictoryMilestones)."),
    ("wins-4", "4 victories", "Count-based", "Any four won operations."),
    ("wins-5-risk", "5 victories + 1 high-risk", "Count-based", "The first chase."),
    ("wins-15", "15 victories", "Count-based", "Prestige recovery opens."),
    ("post-alpha-small-war", "A Small War (cut from alpha)", "Post-alpha", "Agreus cards parked here: the recovered Specter and Arbalest sections and the Boarding Jack."),
    ("post-alpha-warrants", "Military warrants (post-alpha)", "Post-alpha", "Approved kit that needs a tradition's warrant; warrants arrive with A Small War."),
]

# Where the shipped code already puts each card (ArcIssueListings, VictoryMilestones, the Agreus rewards).
PLACED = {
    "weapons:field-security": "start", "weapons:checkpoint": "relief", "wayline:steady-line": "relief",
    "wayline:forward-screen": "relief", "weapons:trench-clearing": "junction", "weapons:convoy-escort": "thunder-run",
    "weapons:counter-battery": "cold-account", "weapons:builders-guard": "working-passage",
    "weapons:line-infantry": "wins-5-risk",
    "armor:field-fatigues": "start", "armor:hegemony-auxiliary": "relief", "armor:league-mobile": "relief",
    "armor:sindrian-security": "wins-3", "armor:corporate-specter-section": "post-alpha-small-war",
    "armor:hegemony-arbalest-section": "post-alpha-small-war", "armor:boarding-jack-section": "post-alpha-small-war",
    "armor:hegemony-auxiliary-section": "post-alpha-warrants", "armor:hegemony-shock": "post-alpha-warrants",
}


# The only free unlock after the opening: Relief's pick of one Wayline path, with its armour sheet.
# The path not picked is sold in the store.
FREE_PICK = {"wayline:steady-line", "wayline:forward-screen", "armor:hegemony-auxiliary", "armor:league-mobile"}


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


def image(path):
    art = Image.open(MOD / path).convert("RGBA")
    art.thumbnail((ART_PX, ART_PX))
    buffer = io.BytesIO()
    art.save(buffer, "PNG", optimize=True)
    return "data:image/png;base64," + base64.b64encode(buffer.getvalue()).decode()


def main():
    argv = [a for a in sys.argv[1:] if a != "--artifact"]
    artifact = "--artifact" in sys.argv
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
            images[icon] = image(icon)
        primaries.append({
            "id": w["id"], "name": text(w["catalog"]["displayName"]), "kind": kind_of(w["id"]),
            "lie": PRIMARY_LIE, "icons": variants, "note": text(w["catalog"].get("role")),
            "access": grade_access.get(w["id"], {}),
            "stats": "range %g · dmg %g × %d burst" % (sim.get("range", 0), sim.get("damage", 0), sim.get("burstCount", 1)),
        })

    specials = []
    for e in load(MOD / "data/marines/marine-special-equipment.equipment.json")["equipment"]:
        icon = e["presentation"]["armoryIcon"]
        images[icon] = image(icon)
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
        images[icon] = image(icon)
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
        images[icon] = image(icon)
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

    ids = {}
    for name in ("battle/weapon/WeaponRegistry.java", "marine/SpecialEquipmentRegistry.java", "marine/SquadEquipmentDoctrines.java"):
        ids.update(constants(JAVA / name))
    ids.update(constants(JAVA / "marine/BilletRoles.java"))
    src = (JAVA / "marine/SquadEquipmentDoctrines.java").read_text(encoding="utf-8")

    def val(token):
        return token.strip('"') if token.startswith('"') else ids[token.split(".")[-1]]

    kits = []
    weapon_list = src[src.index("List<SquadWeaponDoctrine> WEAPONS"):src.index("ARMOR_PLANS =")]
    for block in re.split(r"(?=weaponDoctrine\()", weapon_list)[1:]:
        head = re.match(r'weaponDoctrine\((\w+),\s*"([^"]+)",\s*"([^"]+)"', block)
        members = []
        for call in re.finditer(r"\b(starterWeapon|weapon)\(([^()]*)\)", block):
            parts = [p.strip() for p in call.group(2).split(",")]
            members.append({
                "role": val(parts[0]), "primary": val(parts[1]),
                "grade": "SURPLUS" if call.group(1) == "starterWeapon" else "SERVICE",
                "special": val(parts[2]) if len(parts) > 2 else None,
                "belt": val(parts[3]) if len(parts) > 3 else None,
            })
        if len(members) != 12:
            raise SystemExit("kit %s parsed %d members" % (head.group(1), len(members)))
        kits.append({"id": ids[head.group(1)], "name": text(head.group(2)), "description": text(head.group(3)),
                     "members": members})

    def seats(primary, grade="SERVICE", automatic=None):
        out = []
        for i in range(12):
            leader = i % 4 == 0
            gun = automatic if automatic and i % 4 == 1 else primary
            out.append({"role": "Team Leader" if leader else ("Automatic Rifleman" if gun != primary else "Rifleman"),
                        "primary": gun, "grade": "MILSPEC" if gun != primary else grade, "special": None, "belt": None})
        return out

    # First Refit's two Wayline paths are built in code (FirstRefit.weapons), so they are seeded by hand.
    steady = seats("weapon.field-rifle", "SURPLUS", automatic="weapon.squad-automatic")
    forward = seats("weapon.field-rifle", "SURPLUS")
    for i in (0, 1, 4, 5, 8, 9):
        forward[i].update(primary="weapon.pulse-rifle", grade="MILSPEC")
    kits.insert(1, {"id": "wayline:steady-line", "name": text("@openingRefitPathSTEADY_LINE"),
                    "description": "Three Milspec automatics anchor nine riflemen (seeded by hand; built in code).", "members": steady})
    kits.insert(2, {"id": "wayline:forward-screen", "name": text("@openingRefitPathFORWARD_SCREEN"),
                    "description": "Six Milspec pulse rifles on the leaders and second seats (seeded by hand; built in code).", "members": forward})

    # Armour cards. ArmorSheetBundles lists each sheet's suits; the doctrines list says how many seats
    # are power suits. The game picks a seat's suit by role, so the seeded seating here is an even spread.
    bundles = {}
    bundle_src = (JAVA / "marine/ArmorSheetBundles.java").read_text(encoding="utf-8")
    for row in re.finditer(r"\{\s*SquadEquipmentDoctrines\.(\w+),((?:\s*\"armor\.[a-z-]+\",?)+)\s*\}", bundle_src):
        bundles[ids[row.group(1)]] = re.findall(r'"(armor\.[a-z-]+)"', row.group(2))
    plan_src = src[src.index("ARMOR_PLANS ="):src.index("private SquadEquipmentDoctrines()")]
    armor_kits = []
    for m in re.finditer(r'(?:new SquadArmorPlan|powerSuitPlan|plan)\((\w+),\s*"([^"]+)",\s*"([^"]+)",\s*ArmorTradition\.(\w+),\s*SquadRoleMix\.(\w+)(.*?)\)[,;]\s*(?=\n\s*(?:plan|powerSuitPlan|new SquadArmorPlan|private|$)|\Z)', plan_src, re.S):
        sheet, name, desc, tradition, mix, rest = m.groups()
        sheet_id = ids[sheet]
        patterns = bundles.get(sheet_id, [])
        if not patterns:
            continue
        classes = re.findall(r"ArmorClass\.(STANDARD|POWER_SUIT)", rest)
        if m.group(0).startswith("powerSuitPlan"):
            classes = ["POWER_SUIT"] * 12
        elif len(classes) >= 13:
            classes = classes[1:13]
        else:
            classes = ["STANDARD"] * 12
        standard = [p for p in patterns if not armor_by_id[p]["powered"]] or patterns
        power = [p for p in patterns if armor_by_id[p]["powered"]] or patterns
        members, si, pi = [], 0, 0
        for i, cls in enumerate(classes):
            if cls == "POWER_SUIT":
                suit, pi = power[pi % len(power)], pi + 1
            else:
                suit, si = standard[si % len(standard)], si + 1
            members.append({"role": "Team Leader" if i % 4 == 0 else "Rifleman", "armor": suit})
        armor_kits.append({"id": sheet_id, "name": text(name), "description": text(desc) + " (" + tradition.replace("_", " ").title()
                           + " tradition, " + mix.replace("_", " ").lower() + " mix)", "members": members})
    if len(armor_kits) < 20:
        raise SystemExit("armour plans parsed: %d" % len(armor_kits))

    for kit in kits + armor_kits:
        kit["station"] = PLACED.get(kit["id"], "none")
        kit["grant"] = "free-pick" if kit["id"] in FREE_PICK else "sale"

    # Machine licences: one chip per pattern the store can teach, tiered by MechPattern's own tables.
    pattern_src = (JAVA / "marine/MechPattern.java").read_text(encoding="utf-8")
    tiers = {}
    for kind, name, tier in re.findall(r"(CHASSIS|WEAPON|VEHICLE|KIT)_TIERS\.put\(\w+\.(\w+),\s*EquipmentAccessTier\.(\w+)\)", pattern_src):
        tiers[(kind, name)] = tier
    weight = {"COMMON": "light", "ADVANCED": "medium", "PRESTIGE": "heavy"}
    starters = {("CHASSIS", "HOUND"), ("WEAPON", "NOSE_CHAINGUN"), ("WEAPON", "SRM_5"), ("WEAPON", "SRM_5_FRAG")}
    items = {"hull": [], "mweapon": [], "mpart": []}

    def item(ladder, kind, enum, name, sub, tier, seed_start=False):
        items[ladder].append({"id": "%s:%s" % (kind.lower(), enum), "name": name, "sub": sub, "access": tier,
                              "station": "start" if seed_start else "none"})

    variants = re.findall(r'^\s+(\w+)\("[a-z-]+", "(@[^"]+)"', (JAVA / "battle/mech/MechVariant.java").read_text(encoding="utf-8"), re.M)
    for enum, key in variants:
        tier = tiers.get(("CHASSIS", enum))
        if tier:
            item("hull", "CHASSIS", enum, text(key), "mech · " + weight[tier], tier, ("CHASSIS", enum) in starters)
    for enum in re.findall(r"^\s+(\w+)\(", (JAVA / "battle/vehicle/VehicleType.java").read_text(encoding="utf-8"), re.M):
        tier = tiers.get(("VEHICLE", enum))
        if tier:
            item("hull", "VEHICLE", enum, text("@vehicleTypeName" + enum), "vehicle · " + weight[tier], tier)
    comps = re.findall(r'^\s+(\w+)\("component\.[^"]+",\s*"(@[^"]+)"(?:,\s*MountFamily\.(\w+))?',
                       (JAVA / "battle/mech/MechWeaponComponent.java").read_text(encoding="utf-8"), re.M)
    for enum, key, mount in comps:
        tier = tiers.get(("WEAPON", enum))
        if tier:
            item("mweapon", "WEAPON", enum, text(key), (mount.lower() + " mount") if mount else "mech weapon", tier, ("WEAPON", enum) in starters)
    internals = re.findall(r'^\s+(\w+)\("internal\.[^"]+", "(@[^"]+)", Role\.(\w+),\s*EquipmentAccessTier\.(\w+)',
                           (JAVA / "marine/MechInternal.java").read_text(encoding="utf-8"), re.M)
    starter_internals = {"INDUSTRIAL_ENGINE", "FACTORY_PLATING", "FACTORY_CAPACITOR_BANK", "STANDARD_LOADER"}
    for enum, key, role, tier in internals:
        item("mpart", "INTERNAL", enum, text(key), role.lower().replace("_", " "), tier, enum in starter_internals)
    for enum, key in re.findall(r'^\s+(\w+)\("kit\.[^"]+", "(@[^"]+)"', (JAVA / "battle/vehicle/VehicleKit.java").read_text(encoding="utf-8"), re.M):
        tier = tiers.get(("KIT", enum))
        if tier:
            item("mpart", "KIT", enum, text(key), "vehicle kit", tier)
    for lst in items.values():
        lst.sort(key=lambda x: (x["name"]))

    stations = [{"id": s[0], "name": s[1], "group": s[2], "note": s[3]} for s in STATIONS]
    page = (Path(__file__).with_name("template.html")).read_text(encoding="utf-8")
    for key, value in (("__PRIMARIES__", primaries), ("__SPECIALS__", specials), ("__KITS__", kits),
                       ("__ARMORKITS__", armor_kits), ("__STATIONS__", stations), ("__ARMORS__", armors), ("__ITEMS__", items), ("__SYSTEMS__", list(systems.values())),
                       ("__IMAGES__", images)):
        page = page.replace(key, json.dumps(value, ensure_ascii=False))
    if artifact:
        # The Artifact tool wraps the page in its own skeleton, so publish only the content.
        page = page[page.index("<title>"):].replace("</head><body>", "", 1).replace("</body></html>", "")
    out = Path(argv[0]) if argv else ROOT / "build/kits/kit-ladder.html"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(page, encoding="utf-8")
    print("wrote %s: %d primaries, %d secondaries, %d suits, %d weapon cards, %d equipment cards, machine licences %s, %.0f KB" % (
        out, len(primaries), len(specials), len(armors), len(kits), len(armor_kits), {k: len(v) for k, v in items.items()}, out.stat().st_size / 1024))


if __name__ == "__main__":
    main()
