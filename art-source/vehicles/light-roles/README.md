# Light roles: supply truck and scout car

Both sheets are derived from the militia technical's shipped sheet
(`mod/graphics/battle/vehicles/militia-technical.png`, itself packed from
`art-source/vehicles/technical/`) by `pack.py`; no new generation was needed.

- `supply-truck.png` — the technical's live and wreck hulls verbatim, the pintle
  mount covered by the doodad crate stack
  (`art-source/doodads/sources/industrial-crate-stack.png`) with a soft contact
  shadow. The wreck's crates are charred and knocked askew. Four 768x576 slots:
  hull, empty, wreck hull, empty (no gun).
- `scout-car.png` — the same hulls with 128 px of open bed cut out (the cab slides
  aft, a shorter wheelbase), shifted from militia olive to a dust grey, with a
  drawn sensor mast (dish and whip) on the old pintle ring; the wreck keeps the
  mast stump. Four 640x576 slots, so `VehicleType.SCOUT_CAR`'s 2.5-cell length
  keeps the technical's pixel scale.

Re-run after the technical's sheet changes:

```powershell
python art-source/vehicles/light-roles/pack.py
```
