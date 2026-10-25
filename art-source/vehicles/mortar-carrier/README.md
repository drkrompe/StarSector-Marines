# Mortar carrier

`VehicleType.FIRE_SUPPORT_APC`'s sheet, `mod/graphics/battle/vehicles/mortar-carrier.png`: four 768x576 frames (live
hull, live turret, wreck hull, wreck turret), all facing LEFT, strict top-down. The hulls are the heavy APC's
(`army-apc.png`) verbatim; the turret is ImageGen, drawn at the hull's scale (320 px a cell, `turretVisualCells` 2.4)
with its pivot on the frame centre, which the type puts on the APC's roof bearing.

Two turrets were made for the owner to choose between, each from a `guide.py` geometry guide at 640 px a cell with the
APC sheet as material reference:

- **open** (`open-guide.png`, `open.prompt.txt`, `open-raw.png`): an open turntable, the 120 mm tube raised steeply
  with its bore toward the eye, ready bombs racked round the rear. The muzzle is the bore, 0.36 cells ahead of the
  pivot (`mount.vehicle-mortar`). Shipped.
- **enclosed** (`enclosed-guide.png`, `enclosed.prompt.txt`, `enclosed-raw.png`): a low NEMO-style turret house with
  one long breech-loaded tube; its muzzle brake is 0.78 cells ahead of the pivot.

Each wreck turret (`<option>-wreck-raw.png`) is an ImageGen damage edit of its live raw (`wreck.prompt.txt`): split
tube, peeled plate, gutted hatches and burst racks, not a recolour. `pack.py` registers each raw by its measured scale
and pivot against its guide (`REGISTRATION`) and lays the wreck over the live raw with `wreck_fit.fit_to`.

```
python art-source/vehicles/mortar-carrier/guide.py
python art-source/vehicles/mortar-carrier/pack.py [open|enclosed]
```

Switching to `enclosed` also moves `mount.vehicle-mortar`'s `muzzleOffsetCells` to 0.78.
