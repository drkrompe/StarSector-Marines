# Armor armory images

One painted armory image per armor appearance family, shipped as
`mod/graphics/ui/armory/armor-<family-slug>.png` (512 px, transparent). Every armor
pattern in `marine-armor-catalog.armor.json` points its `catalog.iconPath` at its
family's image, and `MarineArmorPattern` does the same for the legacy enum. Patterns that
share a family share its image (`arbalest` and `arbalest-recovered`, `specter-heavy` and
`specter-recovered`, the four MILITIA kits, and so on).

## Authority

- **Identity**: the family's battle layers, which the owner accepted. `build_refs.py`
  assembles each family's torso (with its shoulder layers, for the articulated shells) and
  helmet into `refs/<slug>-identity.png`. Palette, plate and pauldron shapes, markings and
  lamps come from there; the brief translates the overhead view into a front product shot.
- **Composition and finish**: the four former tier icons, kept in `composition/` because
  every raw was generated from one of them: field kit (unpowered fatigues), scout (light
  kits), combat (line suits), heavy (power suits). `briefs.py` picks one by weight class.
- **Design language**: `briefs.py` holds each family's identity paragraph, drawn from the
  pattern's catalog description and the briefs its battle layers were made from
  (`marine-modular-topdown/PROMPTS.md`, `sources/faction-armor/PROMPTS.md`).

## Steps

```bash
python art-source/armor-icons/build_refs.py            # identity references
python art-source/armor-icons/briefs.py                # prompts/<slug>.prompt.txt
python art-source/armor-icons/generate.py PALATINE     # one paid generation -> raw/<slug>.raw.png
python art-source/armor-icons/export.py                # raw -> mod/graphics/ui/armory/armor-<slug>.png
python art-source/armor-icons/point_catalog.py         # catalog iconPath -> family image
python art-source/armor-icons/contact_sheet.py --new   # review sheets under build/armor-icons/
```

`generate.py` goes through `tools/codex_imagegen.py` (the codex-imagegen skill) and records
provenance beside each raw. A regeneration is a new version (`--version v2`), named in
`export.py`'s `ACCEPTED`, never an overwrite of an accepted raw.

The export is mechanical: the raw's own alpha (every generation returned a real one), faint
haze cleared, cropped square about the opaque bounds with a 3% margin, resized to 512.
