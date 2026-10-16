# Technical pickup and pintle MG

Built-in ImageGen edited the previous runtime sheet (`wheel-well-guide.png`)
using `wheel-well-prompt.txt`; the untouched result is `wheel-well-master.png`.
Metal fenders cover all four tires in the overhead silhouette. The left-facing
hood, cab and open rear bed retain their identity. The older guide, prompt and
master remain as first-pass source lineage.

`wreck-guide.png` is the registered live sheet. Built-in ImageGen's exact
`wreck-prompt.txt` produced `wreck-master.png`: torn front engine bay,
cracked windshield, bent bed rail and damaged separate MG. Covered wheels stay
inside the fenders. No fire, smoke, ground shadow or debris carpet is baked in.

`registration.json` and `pack.ps1` uniformly scale and translate each source,
preserving alpha and proportions, into four full 768x576 frames: live hull,
live MG, wreck hull, wreck MG. Runtime output:
`mod/graphics/battle/vehicles/militia-technical.png`. Reproduce with:

```powershell
& art-source/vehicles/technical/pack.ps1
```

The live hull reference (531,375), scale 0.8, and bed bearing (847,375) place
the gun 0.9875 cells aft on the three-cell hull frame. The MG bearing
(1784,375) registers to its frame center, with no compensating pivot offset.
Its 1.508943-cell frame width places the barrel centerline tip 0.58 cells
forward. The shared MG weapon, alternating lateral ballistic releases,
ammunition and cadence are unchanged. Wreck hull reference (632.73,406),
scale 0.63, and MG bearing (1889,400), scale 0.6, preserve the mounting points.
Both final bearings survive destruction.

The technical gets a 0.58-cell visual blast, a seven-second front-engine burn
with 35 seconds of smoke, and a two-second pintle flash with twelve seconds
of smoke. No secondary damage, navigation or cover is created. Covered wheels
need no tread overlay; handling and durability remain unchanged.

Review with `gradlew createSnapshots -Psnapshot=freight-vehicles`: cardinal
poses, live/wreck swap and real damage effects on Thunder Run seed 9301.
Campaign views keep starting positions, empty magazines and controlled lethal
hits. Frames use 1920x1080, UI 100%, camera zoom only. This is art/effect
evidence, not mission balance or live-save acceptance.
