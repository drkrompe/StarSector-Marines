"""Build the weapon-kit ladder page: a self-contained HTML tool for authoring squad weapon kits.

    python tools/kits/build.py [out.html]        (default build/kits/kit-ladder.html)

Reads the shipped marine weapon and special-equipment catalogs, the strings, the armoury icons and
the built-in weapon doctrines in SquadEquipmentDoctrines.java, and embeds all of it in one file.
The page draws each kit the way a Barracks loadout card does (kit bricks coloured by what each piece
throws) and lets a catalog piece be dragged onto a squad member. Exports a kits.json: per kit, twelve
members of {role, primary, grade, special, belt}, in ladder order.
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
ART_PX = 192


def load(path):
    """Starsector's relaxed JSON: whole-line # comments and trailing commas are allowed."""
    raw = re.sub(r"(?m)^\s*#.*$", "", Path(path).read_text(encoding="utf-8"))
    return json.loads(re.sub(r",(\s*[}\]])", r"", raw))


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
    strings = flat(load(MOD / "data/strings/strings.json"))
    text = lambda key: strings.get(key.lstrip("@"), key) if key else ""
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
            "thrown": e["activation"]["type"] in ("arc-explosive", "utility-smoke", "utility-satchel"),
        })

    ids = {}
    for name in ("battle/weapon/WeaponRegistry.java", "marine/SpecialEquipmentRegistry.java", "marine/SquadEquipmentDoctrines.java"):
        ids.update(constants(JAVA / name))
    ids.update(constants(JAVA / "marine/BilletRoles.java"))
    src = (JAVA / "marine/SquadEquipmentDoctrines.java").read_text(encoding="utf-8")
    weapon_list = src[src.index("List<SquadWeaponDoctrine> WEAPONS"):src.index("ARMOR_PLANS =")]
    kits = []
    for block in re.split(r"(?=weaponDoctrine\()", weapon_list)[1:]:
        head = re.match(r'weaponDoctrine\((\w+),\s*"([^"]+)",\s*"([^"]+)"', block)
        members = []
        for call in re.finditer(r"\b(starterWeapon|weapon)\(([^()]*)\)", block):
            args = [a.strip() for a in call.group(2).split(",")]
            val = lambda a: a.strip('"') if a.startswith('"') else ids[a.split(".")[-1]]
            members.append({
                "role": val(args[0]), "primary": val(args[1]),
                "grade": "SURPLUS" if call.group(1) == "starterWeapon" else "SERVICE",
                "special": val(args[2]) if len(args) > 2 else None,
                "belt": val(args[3]) if len(args) > 3 else None,
            })
        if len(members) != 12:
            raise SystemExit("kit %s parsed %d members" % (head.group(1), len(members)))
        kits.append({"id": ids[head.group(1)], "name": text(head.group(2)), "description": text(head.group(3)),
                     "members": members})

    page = (Path(__file__).with_name("template.html")).read_text(encoding="utf-8")
    for key, value in (("__PRIMARIES__", primaries), ("__SPECIALS__", specials), ("__KITS__", kits), ("__IMAGES__", images)):
        page = page.replace(key, json.dumps(value, ensure_ascii=False))
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "build/kits/kit-ladder.html"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(page, encoding="utf-8")
    print("wrote %s: %d primaries, %d secondaries, %d kits, %.0f KB" % (out, len(primaries), len(specials), len(kits), out.stat().st_size / 1024))


if __name__ == "__main__":
    main()
