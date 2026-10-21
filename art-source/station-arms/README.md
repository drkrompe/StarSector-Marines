# Station arms

Forearm-and-hand layers drawn while somebody works a fixture with their hands: a
console, a bench, a counter, a mess table (`Affordance.handsOnFixture`). A body
layer's arms hang at its sides; these two layers put the forearms out in front
and the hands on the work. The compositor draws them between the body and the
head, in place of the primary weapon, and moves the hands forward and back on the
job's own progress.

**They share the body's grid.** Each layer is 150x240: its lower 150x120 is the
body canvas itself, so the compositor places it with the body's own transform and
it only extends forward of it. The forearm leaves the sleeve at mid-torso and the
hand rests at the front of the head.

`station_arms.py guide` draws both arms at x5 on one 1536x1024 sheet
(`../templates/station-arms-silhouettes.png`). Each role is its own ImageGen
reskin (`prompts/_shared.txt` plus `prompts/<role>.txt`; exact text in
`prompts/<role>.full.txt`), with that role's own body sheet
(`../ship-crew/raw/<role>.png` or `../colonist-modular-topdown/raw/<role>.png`) as
the reference for sleeve, skin and style. Raw masters and provenance are under
`raw/`.

```powershell
python art-source/station-arms/station_arms.py guide
python art-source/station-arms/station_arms.py export
```

Export cuts each arm, scales it with nearest sampling and bounds alpha by the
guide's own arm shape, writing `station-arm-left.png` and `station-arm-right.png`
beside each role's body. A family draws them when `LayeredArmorFamily.hasStationArms`
says it ships them: the four ship-crew uniforms and the three colonists for now.
Another family (the marines) needs only its own reskin and that declaration.
