# Asharu battle: progressive damage

Status: DRAFT — module and ledger in code (`asharu_damage.py`), bench-checked on the lead Lasher; not yet wired into `battle_blockout.py`.
Written: 2026-10-23

The owner asked that ships carry their damage as the battle goes on. A ship hit in S6 still shows
those scars, holes, fires and smoke in S6b, S7, S9b, S10 and S12, and gets worse as the story says.
`battle_blockout` rebuilds every ship's effects copy for each shot, so whatever one shot keys is
gone in the next. `asharu_damage` is the memory between shots: a **ledger** of every hit on every
ship, and an **apply** that rebuilds what a ship has taken so far on each shot's fresh copy.

## How it works

- **The ledger** (`asharu_damage.LEDGER`) lists each hit with its sequence time (seconds), a
  severity 0-4, a place on the hull (ship frame, metres: +X bow, +Y port, +Z dorsal) and the side
  it came from. Each hit is cast onto the plating along that side, so it lands on the real hull of
  whatever build is placed, and in the same place in every shot.
- **Where hits come from.** Most come from the shots' own `fx` entries: a tracer's target, a
  `blast_on`, `fire_on`, `smoke`, `debris` or mining beam. Each such hit names the entry kind that
  draws it (`by`) and its shot (`src`). The rest are story hits, where the shot list says a ship is
  hurt and no shot draws it (a Lasher burning, the Colossus's second pod failing).
- **`python asharu_damage.py`** checks the ledger against `battle_blockout.KEYFRAMES`. Every fx entry
  that lands on a ship must have a ledger hit in its shot (or be a fire still burning from earlier),
  every `by` hit must have its entry, and no ship may be placed after her kill. `--table` prints the
  tables below; `--at T` prints every ship's state at time T.
- **apply, once per shot build.** Hits before the shot are *persistent*: scars with their embers
  cooled by their age, fires still burning and vents still venting. Hits inside the shot are
  *keyed* at their frames: scars appear as rounds land. The moment itself (the kit's strikes through
  the shield where one is up, a blast, debris) is keyed only when the shot's fx do not already draw
  it. A fire or vent is added unless the shot draws a fire within 15 m.
- **Volumes are the cost** (k3 and k3b ran for hours on volumes). A ship carries at most two
  persistent fires and one persistent vent per shot, the most severe and newest first. A fire over
  the cap shows as glowing embers in its scar. `max_fires=0` turns them off for a shot that cannot
  afford them.

## What each damage level looks like

Nothing is round (owner, 2026-10-21). Flashes are the kit's jagged bursts, short-lived. What lasts
is scorch, holes, embers, smoke and vapour. A scar is a flat decal on the plating with a ragged,
lobed, streaked edge, stretched and turned per hit and seeded by the hit, so it looks the same in
every shot. Its embers are specks along the torn edge that flicker and cool, not a ring of light.

| Level | Name | Left on the hull |
|---|---|---|
| 0 | graze | One small soot mark, about 1 m. Hot for a moment, dark within seconds. |
| 1 | scored | A line of soot marks (a burst walking across the plate), or three marks round the hit. Embers fade over about 12 s, then a faint smoulder. |
| 2 | holed | A torn hole about 2.6 m across, black inside, ragged rim glowing. Four scorch marks round it. Pale vapour vents from it for about 14 s (a hit may set its own). Embers fade over about 40 s. |
| 3 | burning | A 3.4 m hole and five scorch marks. The kit's breach fire (ragged flame, its smoke, a flickering light) burns until the ledger says it is out (`burn_until`), else to the end. Embers over about 90 s. |
| 4 | broken | A 4.6 m hole, seven scorch marks, a larger fire, venting for about 30 s. The rim keeps smouldering. A kill is level 4. |

A mining laser's cut (`cut`) is a seam: narrow soot marks laid along the beam's line, walking along
it over 1.5 s as it cuts, with embers fading over about 25 s.

## The story in damage

- **WARDEN LEAD (lead Lasher)** is holed and set burning forward when she turns back in S6. She
  smokes through to S9b, where her aft deck catches, plating comes off her side, and the Falcon's
  broadside opens her stern.
- **Lasher A** burns in S6. **Lasher B** and the **merchant Wolf** come away scored.
- **The convoy's Hound** has her drive deck set burning and a second round in her drive cluster in
  S6d.
- **The Tarsus** has her drives shot out in S6c. The fire burns up out of the breach through S7 and
  is out by S10. Her forward deck is scored in S7.
- **The Colossus** has her port pod scored, set burning and shot out in S8-S9. Her starboard pod
  fails between S9b and S10, venting. Her back starts to go in S10, and she carries all of it into
  the atmosphere in S12.
- **Morrow** is only scuffed by the pod's debris before the fall.
- **The Falcon (P)** has her flank walked by the lead's guns after the overload in S9b, opened and
  burning, and holed further aft. She is still burning in S10.
- **The raiders the convoy hurts:**
  - the Manticore (P), scored;
  - Wolf (P) A, grazed, then cut by a Borer;
  - Wolf (P) B, a graze through her shield;
  - Kite (P) B, holed through her failing shield and driven off;
  - the Mk.III, her belly scored.
- **Hounds (P) C and A** are scored, then killed (S6b, S6d). Their kills are the shots' own blasts.
- **Left clean on purpose:**
  - the Phaeton, whose wreck state has its own cook-off;
  - the stand-ins (Mule, Buffalos, Dram, Buffalo Mk.II), which have no kit copy to mark;
  - Kite (P) A, Hound (P) B, the boats and the Borers.

## Wiring (for the blockout owner)

`apply(ship_key, ship_fx, t_shot0, t_shot1, frame_of, matrix=None, log=print, shot_fx=None, max_fires=2, max_vents=1)`,
or `apply_all(placed, t_shot0, t_shot1, frame_of, log, shot_fx)` for every placed ship. In
`blender_main`'s frame loop, call it in the `if not step:` kit build, after the kit copies are built,
reset and knobbed (after the cues and the jagged cook-off refresh), and before `scene_fx.SceneFx`
runs. Pass:

- `t_shot0, t_shot1 = spec['shot_t']` for a span, or `spec['key_t']` twice for a still;
- `frame_of = lambda t: anchor + round((t - spec['key_t']) * fps_s)`;
- `shot_fx=spec.get('fx', ())`.

Then `ship_fx.refresh(scene)` as the loop already does.

**Where the rounds land.** The fire-control plan (`asharu_gunnery`) reads the ledger. For every hit
drawn by a tracer, it opens an engagement from the shooter named in `src`, aimed at the hit's place
and walking toward its `to`. So in the shot itself the strikes land where the scar will stay. Its
own check flags a ledger hit that no gun can bear on.

## The ledger

Some story hits are gunfire the kit cannot deliver yet. Either the round would fly longer than 6 s, or no mount can bear. They stay story hits until the geometry changes, and `asharu_gunnery.check` names them.

### colossus

| t (s) | Hit | Severity | Source | What |
|---|---|---|---|---|
| 89.0 | rake | 1 scored | story (S8-S9; the Mk.III cannot depress onto her until her flight changes) | the Mk.III's guns walk onto the port pod from above |
| 93.5 | breach | 3 burning | S8-S9 k4 fire_on | the port pod burns, and burns into the atmosphere |
| 94.9 | he | 4 broken | S8-S9 k4 blast_on + debris | the port tug pod is shot out: torn open, its lines venting |
| 108.0 | he | 2 holed | story (between S9b and S10) | the starboard pod failing: an overstrained plate gives, venting |
| 113.3 | debris | 3 burning | S10 k5 debris | her back starts to go: plating shed off the dorsal spine as she noses down; it burns |

### colossus3

| t (s) | Hit | Severity | Source | What |
|---|---|---|---|---|
| 96.0 | rake | 1 scored | S8-S9 k4 tracer colossus | the Colossus's turrets score her belly |
| 99.5 | rake | 1 scored | story (S9) | a second burst as she swings her grapples away |

### falcon-p

| t (s) | Hit | Severity | Source | What |
|---|---|---|---|---|
| 104.8 | rake | 1 scored | S9b k4b tracer lasher-lead | the Lasher's guns walk down the open flank after the overload |
| 105.0 | breach | 3 burning | S9b k4b blast_on | hit hard: the flank opens and burns; still burning in S10 (continuity) |
| 106.0 | he | 2 holed | story (S9b) | the Lasher's last rounds hole her further aft, venting |

### hound-m

| t (s) | Hit | Severity | Source | What |
|---|---|---|---|---|
| 75.2 | rake | 1 scored | S6d k3e tracer hound-p-b | the second Hound (P)'s chaingun from below as she rolls |
| 75.5 | breach | 3 burning | S6d k3e fire_on | drives hit: her drive deck burns |
| 77.4 | he | 2 holed | story (S6d) | a second round in the drive cluster: she loses thrust, venting (the camera kick at 77.4) |

### hound-p-a

| t (s) | Hit | Severity | Source | What |
|---|---|---|---|---|
| 75.0 | rake | 1 scored | S6d k3e tracer hound-m |  |
| 76.9 | kill | 4 broken | S6d k3e blast_on + debris | killed at close range |

### hound-p-c

| t (s) | Hit | Severity | Source | What |
|---|---|---|---|---|
| 60.0 | rake | 1 scored | S6b k3c tracer lasher-b | bare armour soaks the first burst |
| 61.0 | rake | 1 scored | S6b k3c tracer wolf-m |  |
| 61.9 | kill | 4 broken | S6b k3c blast_on + debris | the Ammo Feeder burst breaks her up |

### kite-p-b

| t (s) | Hit | Severity | Source | What |
|---|---|---|---|---|
| 96.5 | he | 2 holed | S8-S9 k4 tracer colossus | leaks through her failing shield (flux .85): she breaks off, venting |
| 98.0 | cut | 1 scored | S8-S9 k4 mining borer-d | a Borer's laser on her side |

### lasher-a

| t (s) | Hit | Severity | Source | What |
|---|---|---|---|---|
| 52.8 | he | 2 holed | story (S6) | a Falcon (P) bolt opens her port quarter before S6's still |
| 54.5 | breach | 3 burning | S6 k3 fire_on | Lasher A burns |

### lasher-b

| t (s) | Hit | Severity | Source | What |
|---|---|---|---|---|
| 55.0 | rake | 1 scored | S6 k3 tracer falcon-p | the Falcon's fire scores her starboard bow |
| 61.5 | rake | 1 scored | story (S6b; the Buffalo Mk.II at 3.8 km is past the kit 6 s round limit) | the Buffalo Mk.II's fire scores her deck as she rolls over |

### lasher-lead

| t (s) | Hit | Severity | Source | What |
|---|---|---|---|---|
| 53.8 | rake | 1 scored | S6 k3 tracer manticore-p | the Manticore's light guns walk along her port bow as she comes about |
| 55.4 | he | 2 holed | story (S6; the Falcon at 3.8 km is past the kit 6 s round limit) | a Falcon (P) bolt holes her port quarter; she vents |
| 55.9 | breach | 3 burning | S6 k3 blast_on | the hit that turns her: her forward deck opens and burns; smoking from here to S9b |
| 103.5 | breach | 3 burning | S9b k4b fire_on | the Falcon's point guns as she rolls in: her aft deck catches |
| 104.6 | debris | 2 holed | S9b k4b debris | plating shed off her port side as she dives |
| 107.6 | breach | 4 broken | S9b k4b tracer falcon-p | the Falcon's broadside opens her stern: she tumbles away burning |
| 108.0 | rake | 1 scored | S9b k4b tracer falcon-p | the rest of the broadside walks her deck |

### manticore-p

| t (s) | Hit | Severity | Source | What |
|---|---|---|---|---|
| 55.5 | rake | 1 scored | story (S6; the lead at 3.4 km is past the kit 6 s round limit) | the lead's light MGs as she turns back |

### morrow

| t (s) | Hit | Severity | Source | What |
|---|---|---|---|---|
| 95.5 | rake | 0 graze | story (S8-S9) | the port pod's debris scuffs her back as she takes the weight |

### tarsus

| t (s) | Hit | Severity | Source | What |
|---|---|---|---|---|
| 68.0 | rake | 1 scored | S6c k3d tracer wolf-p-a | the Wolves (P) rake aft toward her drives as they pass |
| 69.6 | he | 2 holed | S6c k3d tracer wolf-p-b | a drive bell holed |
| 70.5 | breach | 3 burning | S6c k3d fire_on | drives shot out: fire burns up out of the breach (continuity), out before S10 |
| 84.0 | rake | 1 scored | S7 k3b tracer wolf-p-b | the covering Wolf (P)'s inverted gun run scores her forward deck |

### wolf-m

| t (s) | Hit | Severity | Source | What |
|---|---|---|---|---|
| 73.0 | he | 1 scored | story (S6c) | a blaster graze as she chases the Wolves (P) through the gap |

### wolf-p-a

| t (s) | Hit | Severity | Source | What |
|---|---|---|---|---|
| 56.8 | rake | 0 graze | S6 k3 tracer lasher-b | Lasher B's fire |
| 71.6 | he | 1 scored | S6c k3d tracer wolf-m | the merchant Wolf's bolt, past her front shield onto the flank |
| 95.8 | he | 1 scored | S8-S9 k4 tracer colossus |  |
| 97.5 | cut | 2 holed | S8-S9 k4 mining borer-c | a Borer's mining laser cuts a long glowing seam down her side |

### wolf-p-b

| t (s) | Hit | Severity | Source | What |
|---|---|---|---|---|
| 71.2 | rake | 0 graze | S6c k3d tracer mule | the Mule's guns, mostly on her shield |

