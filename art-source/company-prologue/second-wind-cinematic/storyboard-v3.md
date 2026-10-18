# Second Wind arrival at Jangala: storyboard v3

Status: DRAFT. Image-first replacement for `storyboard-v2.txt`, which it supersedes.

Written: 2026-10-16

Updated: 2026-10-16 — hail locked; C1 rebuilt as painted ImageGen layers, a 3D planet and Second Wind's entrance.

## What changed from v2

Two rules decide what v3 keeps:

1. **Image first.** Every shot is a high-resolution still split into a few depth
   layers. Motion comes only from moving those layers sideways at different rates
   (parallax) and from slow scaling (push-in). Nothing is a modelled fly-through,
   and no ship mesh moves through a scene. The side window is the natural rig for
   this: the frame stays still while the world slides past behind it.
2. **Every shot has to pay off.** A beat is shown only if it gives the player
   something they came for (Jangala in space, Hegemony fleets, civilian traffic,
   a futuristic world on the ground) or carries the story (people who left a dead
   mine arriving to start over). Shots that only explain mechanics are cut.

So v3 drops the standalone entry shot, the ship-mesh motion in space, the
separate fields shot, and the landing's descent, correction and load-transfer
mechanics. The ship arriving matters. How the cradle takes the weight does not.

## Three ways a beat is presented

| Mode | What the player sees | Use it when |
|---|---|---|
| **Dialog** | Portrait or panel text, voiced or written. No new art. | The beat is information, orders or negotiation, and a picture adds nothing. |
| **Image + dialog** | One still illustration with narration or a speaker. | The beat has an emotional moment worth a picture, but no motion. The eight prologue pages work this way. |
| **Cinematic** | Layered parallax plates, with or without voice. | The beat is about place, scale or arrival: things the player should feel move past. |

## The whole opening, beat by beat

Rows marked *existing* are unchanged from `opening-dialogue.md`. The arrival rows
are new work.

| # | Beat | Mode | Payoff | Source |
|---|---|---|---|---|
| 0 | New-game choice | Dialog | The pitch: no licence, no patron, five hundred credits. | *existing* `smCerberusChoice` / `smCerberusSelected` |
| P1 | SECOND WIND, on the bridge | Image + dialog | Exhausted crew, a ship that is holding. | *existing* `second-wind` |
| P2 | THE WRITING ON THE WALL | Image + dialog | The operator was packing, not failing. | *existing* `claim` |
| P3 | WHAT WE LEFT BEHIND | Image + dialog | Abandoned. Castle's chit. | *existing* `last-carrier` |
| P4 | RAISING HER | Image + dialog | Four days left on the chalk. | *existing* `raising-her` |
| P5 | THE DEPARTURE ROSTER | Image + dialog | Nobody voted on the command chair. | *existing* `departure-roster` |
| P6 | SOMEWHERE TO DOCK | Image + dialog | Castle writes to Nessa after years. Hegemony patrols on the display. | *existing* `somewhere-to-dock` (Nessa memory moves to C4, see below) |
| P7 | A WAY TO EARN | Image + dialog | Sabine's twelve asleep beside their rifles. No licence. | *existing* `cargo-billets` |
| P8 | THE FIRST STEP | Image + dialog | The estimate on top of the old shift roster. | *existing* `yard-estimate` |
| — | *Campaign travel to Jangala* | — | The player flies there. | — |
| C1 | CORVUS | Cinematic + voiced hail | Jangala, the Hegemony fleet, civilian traffic, and how small they are. | **new** |
| C2 | PATROL ABEAM | Cinematic + hail continues | A Hegemony warship slides past the crew window. | **new** |
| — | Entry (transition only) | Cinematic | Glow and cloud wipe; not a beat. | **new**, 2–3 s |
| C3 | FIRST OPEN SKY | Cinematic, silent | Miners from an asteroid see sky. | salvage forest plate |
| C4 | THE ROAD AND THE PORT | Cinematic + Castle | A world people live on; the freight road they will fight for. | **new** panorama |
| C5 | NESSA'S YARD | Cinematic (push-in) | They made it. The ship is a wreck, and it is down. | **new** |
| Y1 | Yard report | Image + dialog | Nessa held to the estimate; the Wayline bridge. | *existing* `yard-hull` |
| Y2 | Yard completion | Image + dialog | Old seals on Nessa's wall. | *existing* `berths-fitted` |
| Y3 | Castle's preparation lessons | Dialog | Instructions in her voice. | *existing* |
| Y4 | Sabine's induction | Dialog | "Twelve present, Commander. Counted." | *existing* |
| M1 | Mara's briefing (Relief) | Dialog, voiced | A stranger asking for help. | *existing* |
| M2 | First payment | Dialog | "Somebody paid us to do exactly what we said we'd do." | *existing* |

The prologue stays a set of still pages. The Claim's decline is backstory, and a
still with narration carries backstory better than motion would. **RAISING HER**
(lighting the drive) is the only page that might later earn a modest push-in. It
is not part of this pass.

## Arrival shot list

The arrival runs about 90 seconds, most of it the space section that carries the hail. It plays once, the first time the player docks
at Jangala, and it can be skipped. Starts that use a fallback port never see it.
Timings are targets until the voice takes are measured.

### C1 — CORVUS (about 38 s, set by the hail)

**Built as** `corvus-approach/` (`c1-layout.md` holds the shared camera, sun
and layer table). The owner's direction for this shot:

- **Painted with Codex ImageGen**, layer by layer: a star sky, a distant
  Hegemony flotilla, a hero Hegemony warship, Jangala Station, and hero
  freighter traffic in interesting perspectives. Vanilla sprites are identity
  references only, never the picture.
- **The planet is a 3D sphere**: ImageGen albedo and cloud maps on a Blender
  sphere with an atmosphere rim, rendered as a layer.
- **Second Wind makes an entrance.** It comes into frame from off-screen bottom
  left, close and large, seen from astern with its engine bells toward camera,
  then recedes toward Jangala under the hail. Its card is a render of the
  damaged master, so it keeps one viewing angle while it travels away along
  roughly the camera's line of sight.

**Picture.** Jangala fills the right two thirds of the frame, with seas, canopy
and a storm system. Jangala Station sits in orbit at mid-depth. A Hegemony
battlegroup holds station near it: a capital hull and escorts in Hegemony
livery. Traffic lanes carry freighters, tankers and shuttles as running lights
and small hulls queued toward the station and the descent corridor. Second Wind
is in the foreground: small, scarred, one patched hull among working ships.

**Layers, back to front.** Starfield; planet; station and battlegroup; traffic
lanes; Second Wind as a cut-out.

**Motion.** A slow push-in, about 1.00 to 1.06 scale over the shot. The traffic
layer drifts slightly faster than the planet. Second Wind drifts a little toward
the planet. No rotation.

**Audio.** The hail exchange opens over this shot.

**Payoff.** The Hegemony fleet and civilian activity the player wants to see,
and the story's first contrast: an unlicensed wreck among warships and working
freighters. It pays off P6's "Hegemony patrols crawling across the navigation
display".

### C2 — PATROL ABEAM (about 12 s)

**Picture.** The crew window, from inside, while a Hegemony patrol ship slides
past close enough that its hull fills most of the glass. Jangala lies beyond it.

**Layers.** Fixed window frame and cabin; patrol hull; planet and stars.

**Motion.** The patrol layer moves sideways across the window. The planet barely
moves. The frame does not move at all.

**Audio.** The control's clearance ("Patrol has your signature") lands while the
hull is in the window.

**Payoff.** Scale and scrutiny, felt rather than described.

### Entry — transition (2–3 s)

Orange heat glow, then cloud, wiped across the same window frame. It joins C2 to
C3 and is not a shot in its own right.

### C3 — FIRST OPEN SKY (about 12 s, silent)

**Picture.** The approved forest reveal: canopy to the horizon, a river, mist in
the valleys, an Atlas lifter in the sky.

**Layers.** Fixed window frame and cabin; near canopy; middle ridges; far hills
and sky; the lifter as a cut-out.

**Motion.** The world slides aft at rates set by depth. The lifter crosses
slowly against it.

**Audio.** Cabin vibration easing, ventilation, and no speech. A passenger's
gloved hand may loosen on the frame.

**Payoff.** The emotional centre of the arrival. These people lived years inside
an asteroid mine. Let them, and the player, look.

### C4 — THE ROAD AND THE PORT (about 20 s)

**Picture.** One wide panorama seen through the same window, panned in a single
pass. It runs from farmland, to a freight road carrying a line of heavy haulers,
to the edge of a Hegemony-built spaceport: launch cradles, lifters climbing,
cranes, landing lights, towers, and native growth pressing in at the edges.

**Layers.** Fixed window frame; near fields and road; port district; far hills
and sky.

**Motion.** One continuous sideways pan across the panorama, with no cuts inside
the shot.

**Audio.** Castle's Nessa memory
(`openingCompanyPrologueNessaVennWeRanOreTogetherBefore`), *moved* here from P6
rather than played twice. Her last sentence, "Let me talk first.", lands as the
port fills the window.

**Payoff.** The futuristic world on the ground, and a quiet setup: the haulers
are Wayline's kind of drivers, on the kind of road the company will defend in
Relief.

### C5 — NESSA'S YARD (about 8 s)

**Picture.** Outside. Second Wind is down in a yard cradle, still visibly
scarred, with heat shimmer and vented steam. Yard crew in biofilter masks walk
out from a sheltered gantry. One figure stands with her arms folded, too small to
read a face.

**Layers.** Hull; steam and shimmer; crew; yard and sky.

**Motion.** A slow push-in. The steam layer drifts. The crew layer walks a few
pixels.

**Audio.** Flight noise falls away to ordinary yard sound.

**Payoff.** They made it, and the ship is a wreck. Cut straight into Y1, where
Castle reports the meeting with Nessa.

## C1–C2: Hegemony traffic-control hail

**Locked 2026-10-16** after the owner's second audition. Change it only by owner request.

A new voiced exchange. **Jangala Approach** is a new voice: a Hegemony traffic
controller, brisk and procedural, neither hostile nor friendly. Castle is
Castle's existing voice. Casting and sound treatment are under *Voices and
radio treatment* below. The exchange starts about 3 s into C1 and ends in C2.
Following owner call 6, no line names money or a contract figure.

| # | Speaker | Line | Over |
|---|---|---|---|
| 1 | Jangala Approach | Cerberus on the inbound lane, this is Jangala Approach. Your transponder reads a decommissioned mining escort. State your business in Corvus. | C1 |
| 2 | Castle | Jangala Approach, this is Second Wind. Civilian. Crew and families aboard, nothing for sale. We're expected at Greywater Down, private cradle Kilo Nine. | C1 |
| 3 | Jangala Approach | Corvus is under Hegemony customs. Declare any AI cores, harvested organs, recreational drugs or heavy armaments. | C1 |
| 4 | Castle | Nothing to declare, Approach. Ore dust and tired people. | C1 |
| 7 | Jangala Approach | Second Wind, you're cleared to Greywater Down on civilian corridor four. Keep your mounts cold and stay in the corridor. Patrol has your signature. | C2 |
| 8 | Castle | Corridor four, mounts cold. Thank you, Approach. | C2 |
| 9 | Castle (off channel) | Well. Somebody knows we're here. | Into the entry transition |

Line IDs stay stable, so the numbering skips 5 and 6.

**Notes on the lines.**

- **Approach is routine.** A Hegemony controller sees armed hulls all day. The
  voice is relaxed and professional throughout, never alarmed; the scrutiny
  comes from the procedure, not the tone.
- **Line 1:** the transponder still reports the Claim's old escort registry,
  because the crew painted the name over it (P5) but never re-registered the
  ship.
- **Line 2, Greywater Down:** traffic control knows ports, not every private
  dock on the planet, so Castle gives an address it can route.
  **Greywater Down** is the authored name of the surface port district, and
  Nessa's yard is the private cradle **Kilo Nine** there. Both are mod
  inventions, not vanilla canon. Future Jangala-yard text should use them.
- **Lines 3–4, customs:** the Hegemony's banned goods in Corvus are vanilla's own
  list for the faction (`hegemony.faction` `illegalCommodities`: drugs, organs,
  heavy armaments, AI cores), read off like a checklist. It sells the universe:
  this is a policed Hegemony system. Castle's answer is literally true; the
  company has no armaments *for sale*. Sabine's twelve and their rifles sit
  unspoken under it.
- **Line 7:** the mounts are mentioned in passing as a condition of clearance.
  That is how a professional handles an armed civilian hull.
- **Line 9:** the emotional turn. The operator abandoned these people and nobody
  was counting them. Being tracked by a Hegemony patrol is the first time in
  years anyone official has noticed they exist.
- **Cut after the first audition:** the register joke ("Second Wind isn't on any
  register I have." / "No. It's newer than the paint.") gave way to the customs
  beat. The point-defence challenge ("Are they live?" / "Nothing aboard is live
  but the crew.") read as alarm a controller would not show. Both pairs can
  return if the space section needs more.
- Each line also needs a subtitle, so the exchange reads in full with voice off.

## Voices and radio treatment

**Where the player is listening.** The camera is in the passenger compartment,
not on the bridge. The passengers hear the exchange because the bridge has left
the cabin intercom open. That gives three distinct sounds:

| Speaker | Path to the passenger | Sound |
|---|---|---|
| Jangala Approach | Station transmitter, then the ship's receiver, then the cabin speaker | Heavily masked: narrow band, hard saturation, a noise floor, and a squelch burst at each key-up and key-down. |
| Castle, lines 2–8 | Bridge microphone, then the cabin speaker | Light intercom treatment: the ordinary `comms` band, with no squelch and no noise floor. |
| Castle, line 9 | The same open intercom, after she has closed the channel to Approach | The same intercom sound, but with no squelch before it. The missing key-up is how the ear knows she has stopped transmitting. |

Cabin rumble and ventilation run under all of it. The radio carries the scene's
immersion. Only Approach is far away and degraded, and Castle is close and
human.

**Casting.**

| Speaker | ElevenLabs voice | Model |
|---|---|---|
| Jangala Approach | `lh03wW2cCRf05ksqPizq` (owner's pick). Used nowhere else yet. Later Hegemony traffic in Corvus can reuse it. | `eleven_v4`, like the rest of the game's recordings |
| Castle | Her existing company-officer voice, `cuwCdnt9CKAB9XrTA1j0` | `eleven_v4` |

**Delivery directions** (the bracketed `synthesisText` tags, recorded clean; the
treatment is applied afterwards):

| # | Speaker | Direction |
|---|---|---|
| 1 | Approach | `[flat, procedural, routine]`: the hundredth call of the shift. |
| 2 | Castle | `[steady, careful]`: rehearsed, and she wants it to go well. |
| 3 | Approach | `[dry, unimpressed]` |
| 4 | Castle | `[dry, small pause before]` |
| 5 | Approach | `[sharper, alert]`: the only line where Approach is paying attention. |
| 6 | Castle | `[calm, plain]`: no joke in it; it is the truth. |
| 7 | Approach | `[procedural, final]`: back to routine, the decision made. |
| 8 | Castle | `[relieved, quiet]` |
| 9 | Castle | `[quiet, half to herself, a breath of a laugh]` |

**The Approach treatment, `orbital_comms`.** This is in the audio pipeline
(`ProcessAudioTask`) and awaits the owner's audition. The exact recipe and its
rationale live in `radio-effects.md`. In short:

- The band is 500–2200 Hz, narrower than Talon's cockpit `air_comms`: this is a
  station across orbit, not a gunship a few kilometres away.
- Hard compression, saturation, a bit crusher for digital grit and a constant
  noise floor, with makeup gain so Approach sits at Castle's loudness (about
  −19 LUFS each).
- There is no pitch shift: the voice stays the one that was cast.
- Squelch bursts are separate sounds at the start and end of each Approach
  transmission. The timeline places them, so the gaps between turns can be tuned
  without re-encoding.
- Accept it by ear: every word of line 1 must still be understood through the
  cabin rumble. If the masking costs intelligibility, ease the crusher first,
  then the saturation, and keep the narrow band.

**Recordings.** All seven lines are recorded clean
(`asset-pipeline/src/tool/resources/audio/jangala-arrival/`, with receipts). The
catalog is `mod/data/marines/jangala_arrival_hail.json`. They are encoded to
`mod/sounds/story/jangala-arrival/`, but they are not yet registered in
`sounds.json`: playback belongs to runtime integration.
`arrival-audio/build_hail_preview.py` builds the audition mixes (`cabin`,
`radio` and `clean`) under `build/audio-previews/jangala-hail/`.

Line 9's take repeated the whole line. It is clipped at 2.72 s and its receipt
records the edit and the generated original's hash. ElevenLabs sometimes
repeats a phrase like this, so check every take for repeats before accepting it.

**The hail is longer than the shots it was written for.** The seven takes total
about 47 seconds of speech, and about 53 seconds with squelch and gaps. C1 and
C2 were budgeted at 22 seconds. The space section is the Hegemony-and-traffic
payoff and can carry more time, provided it has moving elements to fill it
(below): roughly C1 at 35 seconds and C2 at 12, with line 9 landing on the entry
transition. If that is still too long, line 8's read-back is the next cut,
about 5 seconds.

## Salvage from the current work

| Existing asset | v3 use |
|---|---|
| Space plate (`space-approach/`) | C1's planet layer. Regenerate wider, at plate resolution; add the station, battlegroup and traffic layers. |
| Forest reveal (`forest-reveal-realism/`, approved realism still) | C3, nearly as is. Split into depth layers. |
| Cabin window frame: the orange-and-cream cabin of `forest-reveal-realism/` and `entry-window/` (owner's choice, 2026-10-16) | Fixed foreground for C2, C3, C4 and the entry transition. One frame throughout: one cabin model and one palette, rendered from each shot's own camera, so a change of tilt is a re-render rather than a repaint. The grey `realism-lookdev/` window is retired as a frame; its landscape still remains a style reference. |
| Patched ship (`second-wind-states/patched/`) | Cut-out renders for C1 (space) and C5 (in the cradle). |
| Fields and port blockouts (`managed-land-realism/`, `port-refinement/`) | Layout guides only, for C4's panorama under the geometry-first ImageGen workflow. The current renders read as greybox and are not used as final art. |
| Landing scenes (`landing-wreck-pass/`, `landing-refinement/`) | C5's framing and cradle reference. The descent motion is retired. |
| Entry window (`entry-window/`) | Glow and cloud layers for the transition only. |

## Plate and motion rules

- Paint plates at 3840x2160 or larger. Make C4's panorama about 9600x2160 so a
  single pan never runs out of image. Review frames are shown at 1920x1080 with
  100% UI scale.
- Use no more than five depth layers per shot. Each layer only translates and
  scales. No layer rotates, deforms or changes perspective. Moving elements ride
  on those layers (see *Moving elements*).
- Hold one approach heading and one sun direction for all window shots: the world
  moves aft, and that flow never reverses between cuts.
- Ship damage stays consistent across C1, C2's glimpses and C5. No new damage
  appears during the descent.
- Ground crew wear environmental protection: Jangala's biosphere calls for
  biofilters.

## Moving elements

Plates can and should carry things that move: traffic, lifters, vehicles,
people, steam. Each one has to be chosen against how its shot's camera moves.

- **A moving element belongs to one depth layer** and moves in screen space at
  that layer's parallax rate, plus its own travel. A truck far down a road is
  fine. The same truck under a camera that changes perspective as it moves is
  not, because its size, angle and occlusion would all have to change with the
  view. v3's cameras only pan and scale, which keeps every element in this
  section safe. If a later shot wants real perspective change, its moving
  elements need their own review.
- **Travel parallel to the picture plane.** A vehicle on a road that runs across
  the frame only translates. On a road that recedes into depth, it would also
  have to shrink along an authored scale curve and keep its contact shadow. Use
  that only for distant, small elements.
- **Motion within an element comes from sprite sheets**, not from moving
  pieces: wheels, a walk cycle, steam, an engine glow, a crane's swing. Short
  loops at a fixed angle, matched to the plate's lighting.
- **Distance forgives.** The further away an element is, the less its
  perspective changes under a pan, so the busiest traffic goes at mid and far
  depth. Near layers carry few moving elements, and they move slowly.
- **Occlusion is layering.** An element passing behind a building lives on a
  layer behind that building's cut-out, never in front with a mask.

| Shot | Moving elements |
|---|---|
| C1 Corvus | Running lights and small freighters drifting along the traffic lanes; shuttles crossing toward the descent corridor; the battlegroup holding station with slow drift and engine-glow sheets. No rotating station: rotation is ruled out, unless an animation sheet carries it. |
| C2 Patrol abeam | The patrol hull is the moving layer. Engine glow and running lights flicker from sheets. |
| C3 First open sky | The Atlas lifter crossing; a distant flock from a sheet; mist bands sliding at their own depth. |
| C4 The road and the port | Trucks on the freight road, which runs across the window so they only translate (the owner's example); a sprayer in the fields; lifters climbing vertically from the port at far depth; crane swings and landing lights from sheets. |
| C5 Nessa's yard | Yard crew walking out on a walk cycle; steam and heat shimmer from sheets; a crane or hoist with a short loop. |

## Decisions

- **Trigger (decided).** The arrival plays the first time the player docks at
  Jangala after the prologue, before the yard panel. The player has flown there
  themselves, and the arrival is the reward.
- **P6 edit (decided).** On Jangala starts, Castle's Nessa memory leaves P6 and
  plays over C4. P6 keeps its narration, and Castle's line there becomes a short
  promise that sets up C4:

  > An old friend. I owe her more than she'll let me say. I'll tell you on the
  > way down, Commander.

  This swap lands **together with the arrival's runtime integration**, not
  before it. Done earlier, the memory would vanish from the game until the
  arrival ships. Fallback-port starts keep their existing P6 line. A player who
  skips the arrival does not hear the memory. Y1 stands on its own without it.
- **Approach casting (decided).** `lh03wW2cCRf05ksqPizq`, heavily masked by
  `orbital_comms` (above).
- **Runtime.** The arrival should play as layered images in the game's own
  renderer, not as a video file. Skip and once-only behaviour follow the
  prologue's.

## Production order

1. Agree this beat sheet and the hail lines.
2. Audition the recorded hail in the cabin bed. Settle the hail's
   length against C1–C2. Record the moved Nessa line. Shot lengths come from the
   treated takes.
3. Plates: C3 (salvage) first, then C1, C2, C4 and C5, following
   `geometry-first-imagegen.md` where geometry matters.
4. Build a layered animatic at 1920x1080 with the recorded audio, for review.
5. Runtime integration as its own story.
