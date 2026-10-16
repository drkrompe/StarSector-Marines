# Wayline industrial freight hauler

`guide.ps1` owns the overhead silhouette: blunt chamfered cab at left,
raised driver roof, narrow machinery saddle and long raised freight module
with beveled orange shoulders at right. Wheels sit below covered side skirts.
Value planes imply height without tilted perspective or vertical container walls.

Built-in ImageGen reskinned `guide.png` with `prompt.txt`, using the previous
runtime image (retained as `material-reference.png`) for material/color only. `master.png` is the untouched result.
The earlier source remains in `../wayline-hauler.master.png` and
`../wayline-hauler.prompt.txt`. The parent `pack.ps1` forwards here so it
cannot restore the retired runtime image.

`wreck-guide.png` is the registered live sprite. Built-in ImageGen's exact
`wreck-prompt.txt` produced `wreck-master.png`: crushed cab, peeled freight
roof, opaque burned crates and crossbeams, and buckled orange rails. Cargo stays
attached and recognizable. No smoke, fire or ground damage is painted in.

`registration.json` and `pack.ps1` uniformly scale both 2048x768 masters by
0.5 into two full 1024x384 frames: live hull, wreck hull. Their common center is
(512,192). Runtime output: `mod/graphics/battle/vehicles/wayline-hauler.png`.

```powershell
& art-source/vehicles/hauler/guide.ps1
& art-source/vehicles/hauler/pack.ps1
```

The existing 4.8-cell frame, clearance width, handling, durability and freight
identity are retained. Destruction uses a modest 1.1-cell visual blast,
a nine-second cab burn with 45 seconds of smoke, and an eighteen-second
contained cargo burn with 90 seconds of smoke. There are no secondary
explosions, damage, navigation closures or concealment. The wreck retains its
final heading and has no turret.

Review with `gradlew createSnapshots -Psnapshot=freight-vehicles`: cardinal
poses, live/wreck registration, and actual technical/hauler destruction on the
production Thunder Run apron beside APCs and the tank. Frames are 1920x1080
at UI 100%, with camera zoom only. Empty magazines and controlled lethal hits
make this art/effect evidence, not mission balance or live-save acceptance.
