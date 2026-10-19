# Jangala arrival: background sound cue sheet

Status: DRAFT. First takes generated for every shot; rough mixes in review. Nothing is shipped.
Written: 2026-10-19

The B-roll sound for the five shots of `storyboard-v3.md` (C1, C2, the entry, C3, C4, C5),
read off each shot's current layout: `c1-v2-shot.md`, `c2-layout.md` (bridge pass),
`c3-layout.md`, `industrial-exterior/c4x_scene.py` and `c5-v2-closing.md`. Times are seconds
into the shot. When a layout moves a beat, move the cue with it; the times here follow the
layouts as of the date above.

`make_sfx.py` generates the cues with ElevenLabs' sound-effects model and keeps every take in
`sfx-takes/` with its receipt; `sfx-manifest.json` lists the prompt, settings, length, levels
and the chosen take for each cue id. `sfx_mix.py` lays the chosen takes against the picture
sketches and Castle's lines for review.

## How to read it

- **Kind.** A *bed* is continuous room tone or ambience, generated as a seamless loop. A
  *spot* is one event at a time. A *transition* carries the ear across a cut or a change of
  place.
- **Level** is how far the cue sits under the dialogue, in dB of loudness. The voices sit at
  about −19 LUFS (the hail takes and Castle's lines are matched there), so a bed at −14 sits
  near −33 LUFS. A level of 0 means as loud as a voice. Where no one speaks, the level is
  against where a voice would be.
- **Source.** *Gen* is an ElevenLabs generation (`make_sfx.py` id). *Existing* names a file
  the repository already has, which can stand in or be layered.

## Sound direction

- **The ear is aboard until touchdown.** From C1 to C4 the listener is with the passengers:
  the bridge has left the cabin intercom open (`storyboard-v3.md`, *Voices and radio
  treatment*). Even when the picture is outside, the hull is what we hear through. There is
  no sound in vacuum, so what comes from outside in C1 and C2 is felt through the structure:
  low, muffled, mostly below the voice band.
- **Nothing masks a word.** Every cue under a line is either below about 300 Hz or more
  than 14 dB under it. Spots land in the gaps between lines where they can.
- **The intercom keeps its rules.** Approach's squelch bursts stay as they are
  (`jangala_squelch_open` / `close`). Castle's lines carry **no squelch**; the missing key-up
  is how the ear knows she has closed the channel. In C5 her all-hands is the ship's
  loudspeaker heard across the yard (`SHIP_PA` in `record_castle_lines.py`), so it gets a
  clean switch click on and off, never a squelch or a static burst.
- **The world opens in steps.** Sealed hull (C1–C2), the hull under load (entry), the hull in
  air (C3), outdoors from high up (C4), the yard at ground level (C5), the whole port as the
  camera rises, then quiet under the title.
- **Music is not here.** The hopeful track is not chosen; it arrives under Castle's last line
  in C5 (`c5-v2-closing.md`). Leave the C5 tail clear for it.

## C1 — CORVUS (37.7 s)

Hail 01 3.1–12.3 (Approach), 02 12.6–23.6 (Castle), 03 23.8–32.2 (Approach), 04 32.5–37.7
(Castle). The ship comes at the camera, passes it at about 10–12 s, the camera turns to
follow it to the planet by about 15 s, and the customs Hound crosses close in 03.

| Cue | What | When | Kind | Level | Source |
|---|---|---|---|---|---|
| `c1_cabin_bed` | Passenger compartment: engine hum through the hull, air recycler, faint electrical buzz | 0–37.7, continuing into C2's first second and crossfading to the bridge | Bed | −10 alone (0–3), −16 under speech | Gen; existing `jangala_cabin_bed` (train rumble and fan) |
| `c1_hull_creaks` | Old hull flexing: slow metal groans | 1.5, 12.4 (in the turn gap), 33.0 | Spot | −12 | Gen |
| `c1_rcs_puffs` | Thruster bursts heard through the hull as the ship corrects | 5.5 and 9.0 | Spot | −14 | Gen |
| `c1_sparks` | Faint fizz of the broken conduits | 6–12, while the damage fills the frame | Spot | −22 | Gen |
| `c1_drive_passby` | The ship's own drive swelling as it passes the camera and its engines turn to us | 8–18, peak at 13 | Transition | −14, low end only | Gen |
| Squelch open / close | Approach's key-up and key-down | 3.0 / 11.8, 23.7 / 31.7 | Spot | as shipped | Existing `jangala_squelch_open`, `_close` |
| `c1_hound_flyby` | The customs Hound crossing close under thrust, muffled | 25.5–31.5, centred under "AI cores" | Spot | −14, ducked under 03 | Gen |

## C2 — PATROL ABEAM, on the bridge (15.0 s)

Hail 07 0.46–9.39 (Approach), 08 9.76–14.21 (Castle). The fighters pass the port slits at
3.0 and 4.2, the Legion is abeam at 8.0, the de-orbit burn starts at 9.6 with a 16° pitch
over 3.5 s, and the Legion clears the slits at about 14.

| Cue | What | When | Kind | Level | Source |
|---|---|---|---|---|---|
| `c2_bridge_bed` | Bridge room tone: console fans, electronics hum, distant drive | 0–15, from a 1 s crossfade off the cabin bed | Bed | −16 | Gen |
| `c2_console_beeps` | A contact chirp as the Legion is tracked; a short double tone as the fighters come in | 0.2, 2.4 | Spot | −14 | Gen |
| `c2_legion_presence` | The warship's mass going by: a sub rumble swelling to the beam and falling away | 2–13, peak at 8.0 | Transition | −12, below 200 Hz | Gen |
| `c1_rcs_puffs` (reused) | The fighters whipping past are felt, not heard: two soft low thumps | 3.0, 4.2 | Spot | −18 | Gen |
| `c2_switch_clicks` | Castle keys her reply; then she closes the channel to Approach | 9.5, 14.4 | Spot | −12 | Gen |
| `c2_deorbit_burn` | Main drive lights: a thump, the rumble rising, the hull creaking into the pitch | 9.6–15, rising into the entry | Transition | −12 rising to −6 | Gen |
| Squelch open / close | Approach's key-up and key-down | 0.3 / 8.94 | Spot | as shipped | Existing |

## Entry — the heat and the cloud (3.0 s)

Castle's "Cleared. Everybody breathe." from 0.16 s, off channel, no squelch. Glow covers the
glass by 1.1 s; cloud from 1.3 s, white by 2.5 s; cut to C3 on white.

| Cue | What | When | Kind | Level | Source |
|---|---|---|---|---|---|
| `c2_deorbit_burn` (carried) | The burn's tail under the glow | 0–1.0 | Transition | −8 | Gen |
| `entry_reentry_roar` | Re-entry through the hull: muffled roar building, plasma hiss | 0–3.0, running on 1.5 s into C3 | Transition | −10 under the line, swelling to −2 on the white after she finishes | Gen |
| `entry_buffet` | Airframe buffeting, fittings rattling, thumps | 1.3–3.0 and into C3 | Spot | −10 | Gen |

## C3 — FIRST OPEN SKY (12.0 s, no speech)

The world shake decays with a 0.9 s time constant; then the passengers look.

| Cue | What | When | Kind | Level | Source |
|---|---|---|---|---|---|
| `entry_reentry_roar` / `entry_buffet` (tails) | The entry's last buffeting | 0–1.5 | Transition | −6 falling away | Gen |
| `c3_wind_easing` | Wind roar on the fuselage easing to a smooth airflow | 0–8 | Transition | −6 easing to −16 | Gen; existing `jangala_arrival_wind` |
| `c3_frame_settle` | A last rattle as the fittings settle, one creak of the window frame | 2.0 | Spot | −14 | Gen |
| `c3_cabin_flight_bed` | The cabin in steady flight: air over the hull, ventilation, a low drone | 0–12, under everything; into C4's first second | Bed | −14 | Gen; existing `jangala_arrival_cabin_bed` |

## C4 — THE ROAD AND THE PORT (16.0 s)

The sky camera pans from farmland over the freight road and the industrial belt to the port
(0–8), comes down over Kilo Nine and lingers. Second Wind passes beneath, brakes into a hover
at 6.6, touches down at 11.0 in a ring of dust and spools down to 12.5. The customs car comes
through the gate from about 10, the yard vehicles roll from 14.2. Castle's "Like the travel
reels…" from 1.0 (about 5 s), and the optional "Greywater's past the cranes…" at about 7.0.

| Cue | What | When | Kind | Level | Source |
|---|---|---|---|---|---|
| `c3_cabin_flight_bed` (carried) | The cabin, fading as the picture goes outside | 0–1.0 | Transition | −14 to silence | Gen |
| `c4_high_wind_bed` | Open air at altitude, soft and steady | 0–16, easing down as the camera descends | Bed | −14, to −20 after 8 | Gen |
| `c4_ground_distant` | Far below: farm machinery, haulers on the freight road, a slow freight train | 0–8 | Spot | −18 | Gen |
| `c4_port_ambience` | The port arriving: cranes, machinery, reversing beepers, clanks | 5–16, into C5 | Bed | −18 rising to −12 | Gen |
| `c4_ship_hover` | Second Wind's thrust under the camera, braking into the hover and sinking | 4–11.2 | Spot | −12, building to −6 at touchdown | Gen; existing `shuttle-engine/engine_hum` |
| `c4_touchdown` | The landing: a deep thump and the gear's metal groan | 11.0 | Spot | −4 | Gen |
| `c4_dust_hiss` | Downwash blasting dust and grit across the pad | 11.0–14.5 | Spot | −10 | Gen |
| `c4_spool_down` | The drive winding down | 11.2–15 | Transition | −8 | Gen; existing `c5_yard_spool_down` |
| `c5_vehicles_arrive` (early) | The tug, bowser and crew carrier starting up and moving in | 14.2–16, into C5 | Spot | −16 | Gen |

## C5 — WE'RE DOWN (33.5 s)

Vehicles pull up and the customs car parks (0–5). Castle's all-hands on the ship's
loudspeaker from 5.0 (about 8.5 s): "Take it slow on the ramp" lands as the ramp drops at 8.
The families walk off from about 8.5 toward the workshop, 52 m away, still crossing as the
camera rises (18–26). Castle's private last line ends at 24; the title from 25; black by 33.

| Cue | What | When | Kind | Level | Source |
|---|---|---|---|---|---|
| `c5_yard_bed` | The yard at the jungle's edge: insects in the tree line, birds, breeze, far machinery | 0–33.5; full to 18, thinning under the rise, out by 31 | Bed | −14 | Gen; existing `c5_yard_jungle_bed` |
| `c4_port_ambience` (carried) | The near port behind the fence | 0–18 | Bed | −18 | Gen |
| `c4_spool_down` (tail) | The last of the drive | 0–1.5 | Transition | −12 | Gen |
| `c5_hull_ticks` | The hull ticking and pinging as it cools | 0.5–14 | Spot | −14 | Gen; existing `c5_yard_hull_ticks` |
| `c5_vehicles_arrive` | The tug, bowser and crew carrier pull up: idle, air-brake hiss | 0–4.5 | Spot | −12 | Gen |
| `c5_car_doors` | The customs car's doors; the officers step out | 3.5 | Spot | −12 | Gen |
| `c5_steam_vent` | Steam venting off the hull as the engines go dark | 4.0–8.0 | Spot | −14 | Gen; existing `c5_yard_steam_vent` |
| `c5_pa_click` | The loudspeaker switched on before "All hands", and off after the announcement. A clean click and a speaker thump; **no squelch** | 4.75, 13.8 | Spot | −10 | Gen |
| `c5_ramp_drop` | The stern ramp: hydraulic whine, then the clunk as it meets the slab | 7.4–9.2, the clunk at 8.6 | Spot | −8 | Gen |
| `c5_footsteps_crowd` | The families walking off: footsteps on grit and concrete, bags | 9–27, fading as the camera climbs | Bed | −14, to −24 by 26 | Gen |
| `c5_crowd_murmur` | Quiet unintelligible family voices, a child | 10–25, well under Castle | Bed | −20 | Gen |
| `c5_port_wide` | The whole port opening up as the camera rises: distant cranes, a lifter climbing, trucks, horns | 17–33, up from −17 to −5 at 26, then fading to silence by 32.5 under the title | Bed | −17 → −5 → out | Gen |
| Music | Not chosen. Arrives under Castle's last line and carries the title | 21–33 | — | — | — |

## What is still open

- **Castle on the bridge.** C2 now puts the camera on the bridge beside Castle, while her
  lines are still the cabin-intercom takes. If the bridge pass stands, her C2 lines want a
  dry room sound rather than the intercom band; the mixes keep the shipped takes.
- **The entry's frame.** Whether the entry and C3 keep the passenger-cabin window after the
  bridge (an open question in `c2-layout.md`). The cue sheet assumes the ear returns to the
  cabin at the entry.
- **The C1 drive swell is a liberty.** There is no sound in vacuum. The pass-by is written
  as the ship's own drive felt through its hull as it turns; if it reads as an exterior
  whoosh, drop it and keep only the bed.
- **Runtime.** The arrival is played as cues by the game's player (`runtime-contract.md`).
  Chosen takes are encoded the way the hail is (`audio.json`, `:asset-pipeline:processAudio`)
  when the owner accepts them; this sheet's times become each shot's `cues`.
