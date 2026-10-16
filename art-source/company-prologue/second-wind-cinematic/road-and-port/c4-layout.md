# C4 THE ROAD AND THE PORT: shared layout for every layer

Shot C4 of `storyboard-v3.md`: about 20 seconds at 1920x1080 and 24 fps, with
Castle's Nessa memory playing over it. Every painted layer follows this sheet, so
the pieces composite into one camera. The numbers come from `c4_camera.py`, which
also writes `guides/c4-camera.json`. The world massing comes from `c4_world.py`.
If this sheet and the code disagree, the code is right.

## Purpose and payoff

This is the first time the crew see a world that people live on. Farms, a road
carrying freight, a village, and then a Hegemony spaceport crowding in at the
edge of the jungle. Before this they had the Claim (an asteroid mine), and C3,
which was jungle with nobody in it. This is the C3 jungle with people in it.

The shot also sets up the campaign without saying so. The heavy haulers on the
freight road are Wayline's kind of drivers, on the kind of road the company will
hold in Relief. The road runs the length of the shot and ends at the port's
freight gate, so the eye follows it into Greywater Down.

Castle tells the Nessa story over the pan. "Let me talk first." should land as
the port fills the window, which closes the shot on the next problem: Nessa.

## Camera

- **Rig.** The orange-and-cream cabin shared with C3 and the entry, re-viewed at
  C4's camera by `c4_cabin_render.py` (`guides/c4-cabin-projected.png`). The ship
  flies level along +X, which is its heading. That puts the bow at screen right,
  so the world slides left, toward the stern, which matches every other window
  shot. The window looks out of the port side along +Y.
- **Motion.** A pure truck: one continuous sideways move with no cuts, no
  rotation and no scale change. On screen it reads as the "one continuous pan"
  the storyboard asks for. It is a truck rather than a rotation because a truck
  gives true parallax with rigid layers (see below).
- **Lens.** 50° horizontal field of view through the glass at 1920 px, which is
  f = 2059 px, or 38.6 mm on a 36 mm sensor. The optical axis is level and the
  lens is shifted down, so the horizon sits at y = 180 and verticals stay
  vertical. The cabin's sill hides the ground at y ≥ 860, which is ground nearer
  than 666 m; the jambs stand at x = 211 and 1708 and the window head is above the
  frame. The cabin itself is rendered from its projector's eye point with its own
  26 mm lens, rotated level and shifted so its horizon is also y = 180: that
  re-views the painted cabin exactly. The world keeps the 50° lens above, so the
  land through the glass is 1.48 times the cabin camera's scale. Nothing in the
  frame gives that away, and it keeps the land at a readable size.
- **Height and speed.** 220 m above the plain, held level, at 62 m/s (about
  220 km/h on the approach corridor). It starts at X = 296 m and travels 1,139 m,
  so the port fills the window as "Let me talk first." lands.
- **Duration.** 18.375 s (441 frames), set by the take: 1.0 s of fields, the 15.84 s
  line, a 1.5 s hold on the port (`c4_camera.DURATION`, read from the catalog).

### Why rigid layers are honest here

The camera only trucks, so a layer painted as a pushbroom panorama at its
representative depth slides rigidly. That reproduces the true view of everything
at that depth. A layer is a band of ground between two distances from the track.
Band edges are lines of constant distance, so they project to horizontal screen
lines. Two layers sliding at different rates therefore never open a gap between
them. Every seam is a horizontal tree belt, verge or fence line.

What a rigid band cannot do is shear. Within the near band, the bottom really
moves 1.26 times faster than the top. So **the land is laid out in strips
parallel to the road**: crop rows, verges, canals, hedgerows and the fence. Lines
running with the motion hide the missing shear. Only one farm track crosses the
near band, at X = 290 m. Avoid adding cross lines to near layers.

## Sun and atmosphere

- **One sun**, shared with every window shot and the landing yard. It is
  `Shared sun | all shots` in `landing-wreck-pass.blend`, the unit vector
  (−0.61, −0.09, 0.78) toward the sun. That places it astern, a little behind the
  window side, at 51° elevation: late morning. On screen the key comes from the
  upper left and slightly behind the viewer, so the scene is front-lit from the
  left and shadows fall right and away.
- **C3 → C4 → C5 continuity (assumption).** C3 is being planned in parallel. This
  sheet assumes C3 uses the same sun, as the approved forest still does (lit from
  the upper left, late-morning humidity, mist in the valleys). C3, the entry
  transition and C4 last about 35 s together, so the time of day must not move.
  C5 uses the same sun on the ground.
- **Atmospheric perspective.** The air is humid. Each layer is blended toward the
  horizon sky (about RGB 204, 218, 232): L4 8%, L3 16%, L2 30%, L1 55%, with the
  ridges higher still. Contrast and saturation fall off with distance, and the
  blacks lift to blue-grey. Low mist sits in the far jungle hollows only. Sky:
  soft humid blue with scattered cumulus, which also fills the 180 px above the
  horizon where the lifters climb.

## Scale and distance

All values are from `c4_camera.py`, in output pixels at 1920x1080. Plates are
painted at twice that density.

| Subject | Layer | Distance | Real size | On screen |
|---|---|---:|---:|---:|
| Freight hauler, tractor + two trailers | L4 | 980 m | 32 m long | 67 px long |
| Hauler height | L4 | 980 m | 4.2 m | 9 px |
| Crop sprayer (hover tender), boom | L4 | 880 m | 24 m | 56 px |
| Weigh-station canopy | L4 | 1,010 m | 30 m | 61 px |
| Hedgerow tree, emergent | L4 | 1,080 m | 28 m tall | 53 px |
| Farm silo | L3 | 1,300 m | 22 m tall | 35 px |
| Sawtooth freight warehouse | L3 | 1,450 m | 180 m × 18 m | 256 × 26 px |
| Container gantry crane | L2 | 2,250 m | 58 m tall | 53 px |
| Fuel tank, 30 m diameter | L2 | 2,050 m | 18 m tall | 18 px |
| Port control tower | L2 | 2,700 m | 95 m | 72 px |
| Launch cradle tower | L1 | 6,600 m | 140 m | 44 px |
| Atlas-class lifter, nose up | L1 | 6,600 m | 220 m | 69 px |
| Volcanic cone (landmark from the farm and port studies) | L1 | 19 km | 1,150 m | about 120 px |

People are below one pixel, so nobody is painted. Every size above is an
authored scenario value, as in `port-refinement/`, not canon.

## Layers, back to front

The four ground layers are depth bands of world Y, the distance from the track.
Each plate is 2160 px tall. The width comes from window plus overscan plus the
layer's travel.

| Layer | Band (m) | Painted at | Slides | Over 20 s | Plate (px) | Screen band (y) | Contents |
|---|---|---:|---:|---:|---|---|---|
| L1 far | 3,800 → ∞, plus sky | 8,000 m | 16 px/s | 319 px | 4864 × 2160 | 0–299 | Sky and cumulus; blue ridges and the volcanic cone; hazy jungle plain; the launch field (four lattice cradles, terminal blocks, a beacon mast, one lifter standing in a cradle) |
| L2 port | 1,700–3,800 | 2,500 m | 51 px/s | 1,021 px | 6272 × 2160 | 299–446 | Left: distant farm strips and a village of about 34 houses with a co-op hall and water tower (people live here). Then a band of native jungle. Right: the port district: tank farm, container yard with three gantry cranes, overhaul halls, repair halls, control tower, shuttle pads with one parked shuttle, landing-light masts, a stack, and jungle pressing in behind |
| L3 mid | 1,100–1,700 | 1,350 m | 95 px/s | 1,891 px | 8016 × 2160 | 446–592 | Crop strips and an irrigation canal; a farmstead with three silos and a barn; a tree belt whose ragged top is the L2 seam. Then native growth, then the freight terminal: perimeter fence, gate spur, truck apron with parked trailers, three sawtooth warehouses, a dispatch block, cold stores, container rows and light masts |
| L4f fore | 640–840 | 730 m | 175 px/s | 3,213 px | 10656 × 2160 | 719–888 | Crop strips, the near side of the native block, then scrub, a drainage canal and a service track along the port. Added for the orange cabin's lower sill. Strips only, so its seam with L4 is a field track |
| L4 near | 840–1,100 | 950 m | 134 px/s | 2,687 px | **9600 × 2160** | 592–719 | Crop strips, verges, **the freight road** (two lanes, Y 972–988 m), the hedgerow beyond it (its top is the L3 seam), the **weigh station** (lay-by, canopy, office, barrier) at X 380–470 m, a block of native jungle at X 1,000–1,260 m, then the port edge: fence, freight gate frame, gate spur with three queued haulers (static), and freight sheds |
| L5 frame | fixed | n/a | 0 | 0 | 3840 × 2160 | all | The cabin window frame and sill from C2/C3, unchanged |

**Painting method** for each ground layer, following `geometry-first-imagegen.md`
and the `codex-imagegen` skill:

1. **Guide.** `build_c4_guides.py` renders the layer's clay massing alone, on a
   transparent film (L1 carries the sky), as an exact pushbroom panorama at its
   plate size. It writes `guides/c4-<layer>-guide.png`. The guide owns horizon,
   placement, silhouettes and scale.
2. **Reskin in sheets of strips.** Codex returns about 1.6 MP at the aspect it is
   given (1886 × 834 here), and a band is very wide and short. `c4_sheets.py build`
   cuts each layer into overlapping segments 1,715 output px wide, hazes the clay as
   the layer will be, sets it on a flat key sky, and stacks two or three segments
   of adjacent depths into one canvas with black separator bars. Five sheets cover
   the five ground layers at 1.1x output density (`guides/sheets/`). The first
   painted sheet is the material reference for the other four.
3. **Register before accepting.** `c4_sheets.py register` finds each strip by its
   separator bars (they came back within 5 px), stretches it onto the guide strip,
   then measures a smooth displacement field against the clay by normalised
   cross-correlation of gradients and warps the paint back. Overlapping segments
   are cut where the two paintings agree best, with a short feather, rather than
   averaged. `plates/registration.json` records every strip's correction.
4. **Alpha.** Codex keeps the flat key sky flat, so the matte is the guide's grown
   silhouette refined by distance from the key colour, choked one pixel, with the
   key taken back out of every partial pixel so no pale halo is left.
5. **Exclude moving elements from plates.** No haulers on the road, no sprayer,
   no climbing lifter, no lit landing lights. Queued and parked vehicles that
   never move are painted.

## Moving elements

Every moving element rides one layer at that layer's rate, plus its own travel.
It only translates, and it moves parallel to the picture plane, except for the
lifters, which climb vertically at a depth where perspective does not change.
`compose_c4.py` places each one from the same world numbers as the guides.

| Element | Layer | Path | Own speed | Screen speed | Method |
|---|---|---|---|---|---|
| Inbound hauler convoy, 5 loaded rigs, 62 m spacing | L4 | Inbound lane Y = 976 m, toward the port (+X) | 18 m/s | −94 px/s (falls back slower than the ground) | One fixed-angle side-view card per rig, 67 × 10 px (paint at 4x, 270 × 40), contact shadow on the card, wheel-turn sheet of 4–6 frames. The convoy reads as overtaking the farmland |
| Outbound empties, 3 + 2 rigs | L4 | Outbound lane Y = 984 m (−X) | 18 m/s | −175 px/s | Same cards, mirrored, never re-lit: the sun is high and behind, so both directions are side-lit from the upper left the same way |
| Crop sprayer (hover tender) | L4 | Y = 905 m, along the rows | 5 m/s | −123 px/s | Card plus a looping translucent spray-fan sheet (8–12 frames) and a soft shadow 2 px below |
| Lifter 1, already climbing | L1 | Cradle (2600, 6400) | climbs at 3.2 m/s² from 70 m | rises 30–80 px over the shot | Nose-up card at a fixed angle, which is honest because it climbs vertically at 6.4 km; engine-plume sheet; the plume brightens the haze under it |
| Lifter 2, launches at t = 7.5 s | L1 | Cradle (3300, 6900) | same | clears its cradle and rises about 20 px; its launch steam is the visible event | Same card plus a launch-steam sheet at the cradle (8–16 frames) |
| Gantry crane swings and trolley runs | L2 | Three cranes at X 1,360–1,620 m | — | sub-pixel to a few px | Short loop sheets at a fixed angle (the boom is about 50 px long), matched to the plate's lighting |
| Landing lights | L2 / L1 | Nine pad masts; beacon mast | blink | — | Additive dots in sequence on the masts (`blink` as in `c1_core.Element`). Daylight, so keep them small and red/white |

**What not to add.** No vehicles on the gate spur or other roads that recede in
depth: a vehicle there would have to shrink along a curve. No aircraft crossing
near the camera. No moving people.

## Audio and dialog sync

Over C4 Castle speaks her Nessa memory, `openingCompanyPrologueNessaVennWeRanOreTogetherBefore`
(`mod/data/strings/strings.json`), which moves here from P6:

> Nessa Venn. We ran ore together before she settled into the yards on Jangala.
> Once spent three days towing my dead freighter home and swore she'd cut me
> loose at every buoy. Never did. She'll have opinions about this ship. Let me
> talk first.

**Recorded** as `jangala_arrival_nessa` (take 3 of 3, 15.84 s, unedited; the receipt
records why takes 1 and 2 were not used). Word timings from speech-to-text are in
`c4-nessa-words.json`; the captions and this table come from them. The voice starts
at 1.0 s.

| t (s) | Line | Picture |
|---|---|---|
| 1.3–5.9 | "Nessa Venn. We ran ore together before she settled into the yards on Jangala." | Fields and the sprayer, the village and farmstead, the outbound empties, the first lifter already climbing far right |
| 6.6–11.9 | "Once spent three days towing my dead freighter home and swore she'd cut me loose at every buoy." | The loaded inbound convoy, centred about 8 s; the weigh station passing |
| 12.5–13.0 | "Never did." | Native jungle crosses the window |
| 13.7–15.2 | "She'll have opinions about this ship." | The freight terminal and port district slide in; the second lifter, launched at 7.5 s, climbs off its steam |
| 15.7–16.7 | "Let me talk first." | The port fills the window: cranes, tower, pads, lights, the second empties passing the gate. Held 1.5 s |

**Treatment (owner decision).** Over C1–C2, Castle is on the bridge and heard through
the cabin intercom. In P6 she promises the commander "I'll tell you on the way
down", so in C4 she is in the cabin, by the window. Record her clean and leave her
unprocessed, close, over cabin rumble and ventilation easing from C3. This is the
first time her voice reaches the passengers without the intercom, which suits
the most personal thing she says in the arrival.

## Salvage

| Source | Used for |
|---|---|
| `forest-reveal-realism/forest-reveal-realism.blend` + `assets/cabin-realism-v1.png` | The orange-and-cream cabin, re-viewed at C4's camera through its projector (`c4_cabin_render.py`) |
| `realism-lookdev/landscape-realism-v1.png` | Style reference: canopy texture, humidity, atmospheric depth |
| `managed-land-realism/` | Layout ideas only: field strips, the hover crop tender (four lift pods, transverse booms, reagent tank), farm compound with silos, the volcanic cone and hills. Its renders are greybox and are not used as art |
| `port-refinement/` | Layout ideas only: sawtooth freight warehouse, tank farms, gantry cranes, control station, light masts, freight tug and service traffic. Hound/Venture flight cards are a candidate for one far crossing on L1 if the shot needs more traffic |
| `port-approach-realism/` | Industrial material atlas and managed turf as material references for concrete and roofs |
| `parallax-window/traffic/atlas-painted-v1.png` | Identity reference for the lifter card (Atlas class), repainted nose-up |
| `vehicles/hauler/` (`geometry-first-imagegen.md` table) | Identity of the in-game freight hauler (cab, saddle, freight planes), so the cinematic's haulers match the ones the company later defends. The side-view card is new work |

## Build

From this directory. Blender opens the retained scenes read-only and never saves them.

```bash
python c4_camera.py                     # scale table, guides/c4-camera.json
"C:\Program Files\Blender Foundation\Blender 5.2\blender.exe" --background --factory-startup --python build_c4_guides.py -- --samples 16
"C:\Program Files\Blender Foundation\Blender 5.2\blender.exe" --background --factory-startup --python c4_cabin_render.py
python c4_sheets.py build               # guides/sheets/: the Codex edit targets
python c4_sheets.py register            # assets/c4-sheet-N-v*.png -> plates/
python c4_cards.py guides               # guides/cards/: hauler and air silhouettes
python c4_cards.py cut                  # assets/c4-haulers-v*.png, c4-air-v*.png -> cards/
python c4_plan.py                       # guides/c4-panorama-plan.png
python compose_c4.py                    # review stills and contact sheet
python compose_c4.py --frames           # build/cinematic/c4-road-and-port/: frames, timeline.json, mp4s
```

`compose_c4.py` also provides the runtime contract's data: `captions()` (three pages in
C1's paging, speaker `Castle`, drawn as "CASTLE / SECOND WIND"), `overlays()` (none)
and `audio_cues()` (C3's `jangala_arrival_cabin_bed` at 0.25 for the whole shot, and
`jangala_arrival_nessa` at 1.0 s, volume 1). `--frames` (or `--mux`) writes them
to `timeline.json`, a clean `c4-road-and-port.mp4` with the voice, and a
`-captioned.mp4` that draws C1's captions for review only.

### Generations and masters

Seven Codex generations: five plate sheets (`assets/c4-sheet-1..5-v1.png`), the hauler
sheet and the air sheet (lifter and crop tender), each with its brief in `prompts/`
and provenance beside it. Painted as cards: eight road trains in the game's hauler
identity (five loaded liveries, three empties, all with one shared orange cab stripe),
the Atlas lifter nose-up and the crop tender. Drawn procedurally because they are a
few pixels across: the plume and exhaust column, launch steam, spray fan and mist,
contact shadows, gantry trolleys with their loads, and the pad, tower and beacon
lights.

`guides/c4-panorama-plan.png` is the plan view: a top-down map with the camera track
and view wedges (distance on a square-root scale, which is why straight sight lines
draw as curves), then the layer strips with the window's footprint at the start,
middle and end.

## Look test (one generation)

`assets/c4-looktest-v1.png` (1886 × 834) with brief `prompts/c4-looktest-v1.txt`
and its `.provenance.json`. It reskins the whole clay composite at t = 15 s, from
`guides/c4-looktest-t15-guide.png`. Review copies:
`review/c4-looktest-in-window.png` (1920x1080 in the frame) and
`review/c4-looktest-registration.png` (a 50% overlay on the guide).

It settles three things:

- **The clay pushbroom guide reskins into the target look.** Believable
  tropical farmland, a native jungle mass, a lived-in port and a climbing lifter,
  all front-lit from the left with real atmospheric depth. The method holds.
- **Registration drifts in the lower bands.** The horizon, ridges, port and
  cranes land within a few pixels. The road and near fields came back about
  30 px high. Hence step 3 of the painting method: register per tile against
  the guide's lines.
- **Moving things must stay out of the guide.** The convoy in the test guide was
  baked into the plate, and doubled where it misregistered. The layer guides
  exclude moving elements. Also, the haulers came back as present-day
  semi-trailers. The hauler card needs its own identity brief, from the game's
  hauler.

It also shows that the port reads a little too much like Earth: a flat, empty
apron. Final plates should ask for denser yard clutter, Hegemony livery on the
cranes and tower, and more futurist structure (lattice cradles, mast arrays).

## Owner decisions (2026-10-16)

- **Castle is in the room, so her voice is unprocessed** in C4: no intercom
  treatment.
- **The window is the orange-and-cream cabin** shared with C3 and the entry, rendered
  from the cabin model at C4's camera. It replaces the grey `realism-lookdev` frame
  named in the rig and salvage tables below.
- **Consider showing her.** Castle at the window, looking out, gives the Nessa memory a
  face. That needs real thought about the cabin interior beyond the window frame, so
  it is a direction to design rather than an approved shot.

## Open questions

1. **Weigh station on the road.** Kept as a quiet nod to Relief's checkpoint, now
   passing during the towing sentence. Keep it, make it a militia post, or cut it?
2. **Hauler liveries.** Painted as mixed owner liveries (white, blue-grey, rust-red,
   sand) with one shared orange cab stripe, reading Wayline as a cooperative. Owner
   to confirm.
3. **Bed level at the C3 cut.** C4 reuses C3's cabin bed at 0.25 (C3 plays it at
   0.55), about 12 dB under Castle. A cue has one volume, so the bed steps down at the
   cut; a fade in the player would smooth it if the step reads as a seam.
4. **Showing Castle.** The owner's direction to design later (see above).
5. **Proposal for `storyboard-v3.md`.** C4's *Motion* line says "pan". It is
   built as a truck (true parallax with rigid layers). Wording such as "one
   continuous sideways travel" would avoid a future reader rotating the plates.
