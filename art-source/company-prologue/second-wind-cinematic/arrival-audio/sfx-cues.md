# Jangala arrival: background sound cue sheet

Status: DRAFT. Second pass after the owner's first review; rough mixes in review. Nothing is shipped.
Written: 2026-10-19
Updated: 2026-10-19 — owner review: bass engines and swishes for the passes, nature in C3, the travel-reels line moved to C3, C4 and C5 joined into one shot with a heavy port and a rocket landing.

The B-roll sound for the shots of `storyboard-v3.md` (C1, C2, the entry, C3, and C4 and C5 as one shot),
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
- **Source.** *Gen* is an ElevenLabs generation (`make_sfx.py` id). *Code* is synthesised by
  `synth_flyby.py` from the shot's own geometry. *Existing* names a file the repository already
  has, which can stand in or be layered.

## Sound direction

- **Weight over realism in space (owner, 2026-10-19).** There is no sound in vacuum, but
  the ships need weight: each big hull carries a deep bass engine hum, and everything that
  passes the camera close gets a swish. The bass stays below the voice band; the swishes
  land in the gaps where they can.
- **The ear is aboard until the descent.** The cabin hum runs under C1 and the bridge tone
  under C2; the world outside reaches C3 muffled through the glass. From the joined C4–C5
  shot the ear is outside with the camera.
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
| `c1_hull_creaks` | Old hull flexing: slow metal groans | 1.0, 33.0 | Spot | −12 | Gen |
| `c1_rcs_puffs` | A thruster burst heard through the hull as the ship corrects | 5.5 | Spot | −14 | Gen |
| `c1_sparks` | Faint fizz of the broken conduits | 6–10, while the damage fills the frame | Spot | −22 | Gen |
| `c1_sw_engine_loop` | Second Wind's drive as a deep bass hum: far at first, loudest as it passes and its engines face us, easing as it recedes | 0–37.7, peak at 11 | Bed | −14 (−26 to −12), below 220 Hz | Gen |
| `c1_sw_passby_swish` | A big heavy swish as the ship passes the camera | 9.25–14, peak at 11 | Spot | −10 | Gen |
| Squelch open / close | Approach's key-up and key-down | 3.0 / 11.8, 23.7 / 31.7 | Spot | as shipped | Existing `jangala_squelch_open`, `_close` |
| `hound_flyby_a` | The customs Hound crossing left to right, **synthesised from its flight path** (`synth_flyby.py`, owner 2026-10-19: the generated swish missed). An engine drone and turbine whine with the pitch drop computed from the path, an air rush that swells and brightens as it closes, distance falloff and a pan from its bearing. `hound_flyby_b` is a sharper reading | 21.5–34.5, closest at about 27.5 | Spot | −8 | Code |

## C2 — PATROL ABEAM, on the bridge (15.0 s)

Hail 07 0.46–9.39 (Approach), 08 9.76–14.21 (Castle). The fighters pass the port slits at
3.0 and 4.2, the Legion is abeam at 8.0, the de-orbit burn starts at 9.6 with a 16° pitch
over 3.5 s, and the Legion clears the slits at about 14.

| Cue | What | When | Kind | Level | Source |
|---|---|---|---|---|---|
| `c2_bridge_bed` | Bridge room tone: console fans, electronics hum, distant drive | 0–15, from a 1 s crossfade off the cabin bed | Bed | −16 | Gen |
| `c2_console_beeps` | A contact chirp as the Legion is tracked; a short double tone as the fighters come in | 0.2, 2.0 | Spot | −14 | Gen |
| `c2_fighter_swish` | Fast swishes as the fighters pass: one per fighter, three a vic, 0.12 s apart | 2.5–3.2 and 3.7–4.4, peaks at 3.0 and 4.2 | Spot | −8 to −15 | Gen |
| `c2_legion_engines_a`, `_b` | The Legion's engines: two layered deep bass hums, swelling to the beam and falling away | 0–15, peaks at 8.0 and 8.5 | Bed | −12 and −13 (−28 at the ends), below 220 Hz | Gen |
| `c2_legion_presence` | The warship's mass going by: a sub rumble under the hums | 5–17, peak at 8.0 | Transition | −14, below 220 Hz | Gen |
| `c2_switch_clicks` | Castle keys her reply; then she closes the channel to Approach | 9.5, 14.4 | Spot | −12 | Gen |
| `c2_deorbit_burn` | Main drive lights: a thump, the rumble rising, the hull creaking into the pitch | 9.6–15, rising into the entry | Transition | −12 rising to −6 | Gen |
| Squelch open / close | Approach's key-up and key-down | 0.3 / 8.94 | Spot | as shipped | Existing |

## Entry — the heat and the cloud (3.0 s)

Castle's "Cleared. Everybody breathe." from 0.16 s, off channel, no squelch. Glow covers the
glass by 1.1 s; cloud from 1.3 s, white by 2.5 s; cut to C3 on white.

**The picture is being reworked (owner, 2026-10-19):** a longer transition, with motion down
and to the right (flames and smoke streaming bottom right to top left) and the camera and
window shaking. The roar and buffeting below stretch with it; C3 then opens on the ship
levelling out.

| Cue | What | When | Kind | Level | Source |
|---|---|---|---|---|---|
| `c2_deorbit_burn` (carried) | The burn's tail under the glow | 0–1.0 | Transition | −8 | Gen |
| `entry_reentry_roar` | Re-entry through the hull: muffled roar building, plasma hiss | 0–3.0, running on 1.5 s into C3 | Transition | −10 under the line, swelling to −2 on the white after she finishes | Gen |
| `entry_buffet` | Airframe buffeting, fittings rattling, thumps | 1.3–3.0 and into C3 | Spot | −10 | Gen |

## C3 — FIRST OPEN SKY (12.0 s)

The world shake decays with a 0.9 s time constant; then the passengers look. The owner wants
the shot longer, opening on the ship levelling out; the cues hold their times from the start
and the beds simply run on.

| Cue | What | When | Kind | Level | Source |
|---|---|---|---|---|---|
| `entry_reentry_roar` / `entry_buffet` (tails) | The entry's last buffeting | 0–1.5 | Transition | −6 falling away | Gen |
| `c3_wind_easing` | Wind roar on the fuselage easing to a smooth airflow | 0–8 | Transition | −6 easing to −16 | Gen; existing `jangala_arrival_wind` |
| `c3_frame_settle` | A last rattle as the fittings settle, one creak of the window frame | 2.0 | Spot | −14 | Gen |
| `c3_cabin_flight_bed` | The cabin in steady flight: air over the hull, ventilation, a low drone | 0–12, under everything; into C4's first second | Bed | −16 | Gen; existing `jangala_arrival_cabin_bed` |
| `c3_nature_muffled` | The jungle below heard through the glass: birds and insects, low-passed at 1.8 kHz | 1.5–12, in once the buffeting has gone | Bed | −16 | Gen |
| `c3_river_muffled` | The river's rush, far off, low-passed at 1.2 kHz | 1.5–12 | Bed | −18 | Gen |
| Castle | "Like the travel reels they ran in the canteen... except this one doesn't loop." Moved here from C4 (owner): it is about the view | 4.0–9.1 | Voice | 0 | `c4_reels` take |

## C4–C5 — THE ROAD, THE PORT AND WE'RE DOWN (one shot, 49.5 s)

One continuous camera (owner, 2026-10-19): the sky pan from farmland over the freeway and
the industrial belt to the port (0–8), down over Kilo Nine, the landing, the yard, the
families, the rise and the title. Until it is rendered as one, the review plays the C4
sketch (0–16) and the C5 sketch (16–49.5) back to back. Times below are on the joined clock;
C5's own beats are at their C5 time + 16.

Second Wind brakes into a hover at 6.6 and touches down at 11.0 in a ring of dust. The yard
vehicles roll from 14.2; the customs car is in by 21. Castle's in-flight line is now about
where they are going: "Greywater's past the cranes. Let me do the talking." from 1.0 (owner:
the line said after landing came off wrong). Her all-hands is on the loudspeaker from 21.0,
the ramp drops at 24, the camera rises 34–42, her last line ends at 40, the title from 41,
black by 49.

| Cue | What | When | Kind | Level | Source |
|---|---|---|---|---|---|
| `c3_cabin_flight_bed` (carried) | The cabin, fading as the picture goes outside | 0–1.0 | Transition | −16 to silence | Gen |
| `c4_high_wind_bed` | Open air at altitude, soft and steady | 0–15, easing out as the camera descends | Bed | −14 to −28 | Gen |
| `c4_ground_distant` | Far below: farm machinery and a slow train | 0–6 | Spot | −18 | Gen |
| `c4_freeway` | Heavy trucks and haulers on the freeway | 1–48.5; loudest crossing the road (4), low over the yard, up again with the rise, out under the title | Bed | −12 (−22 over the yard, −14 at the top of the rise) | Gen |
| `c4_port_heavy` | The industrial port at work: cranes, containers banging, machinery, horns, forklifts | 3.5–48.5; full from 7, ducked 6 dB under the announcement, up at the top of the rise, out under the title | Bed | −12 (−22 to −10) | Gen |
| `c4_port_ambience` | A lighter port layer under it: distant machinery, reversing beepers | 5–34 | Bed | −18 | Gen |
| `c1_sw_engine_loop` (reused) | Second Wind's drive as bass under the descent, building to touchdown | 3–12.5 | Bed | −10 (−18 to −7), below 220 Hz | Gen |
| `c4_landing_rocket` | The landing burn: a deep rumbling roar under the hover, cut off at touchdown | 4–14, sustained 4.5–10.5 | Spot | −6 | Gen |
| `c4_ship_hover` | Thrust texture under the burn | 6–11.3 | Spot | −14 | Gen; existing `shuttle-engine/engine_hum` |
| `c4_touchdown` | The landing: a deep thump and the gear's metal groan | 11.0 | Spot | −2 | Gen |
| `c4_dust_hiss` | Downwash blasting dust and grit across the pad | 11.0–15 | Spot | −10 | Gen |
| `c4_spool_down` | The drive winding down | 11.2–16 | Transition | −8 | Gen; existing `c5_yard_spool_down` |
| `c5_vehicles_arrive` | The tug, bowser and crew carrier start, move in and stop: idle, air-brake hiss | 14.2–20.2 | Spot | −12 | Gen |
| `c5_yard_bed` | The yard at the jungle's edge: insects in the tree line, birds, breeze | 12–49.5; full to 34, thinning under the rise, out by 47 | Bed | −14 | Gen; existing `c5_yard_jungle_bed` |
| `c5_hull_ticks` | The hull ticking and pinging as it cools | 16.5–30 | Spot | −14 | Gen; existing `c5_yard_hull_ticks` |
| `c5_car_doors` | The customs car's doors; the officers step out | 19.5 | Spot | −12 | Gen |
| `c5_steam_vent` | Steam venting off the hull as the engines go dark | 20–24.6 | Spot | −14 | Gen; existing `c5_yard_steam_vent` |
| `c5_pa_click` | The loudspeaker switched on before "All hands", and off after the announcement. A clean click and a speaker thump; **no squelch** | 20.75, 29.8 | Spot | −10 | Gen |
| `c5_ramp_drop` | The stern ramp: hydraulic whine, then the clunk as it meets the slab | 21.6–25.6, the clunk at 24.6 | Spot | −8 | Gen |
| `c5_footsteps_crowd` | The families walking off: footsteps on grit and concrete, bags | 25–43, fading as the camera climbs | Bed | −14, to −24 by 42 | Gen |
| `c5_crowd_murmur` | Quiet unintelligible family voices, a child | 26–41, well under Castle | Bed | −20 | Gen |
| `c5_port_wide` | The whole port opening up as the camera rises: distant cranes, a lifter climbing, trucks, horns | 33–49.5, up from −17 to −5 at 42, then fading to silence by 48.5 under the title | Bed | −17 → −5 → out | Gen |
| Music | Not chosen. Arrives under Castle's last line and carries the title | 37–49.5 | — | — | — |

## What is still open

- **Castle on the bridge.** C2 now puts the camera on the bridge beside Castle, while her
  lines are still the cabin-intercom takes. If the bridge pass stands, her C2 lines want a
  dry room sound rather than the intercom band; the mixes keep the shipped takes.
- **The entry's frame.** Whether the entry and C3 keep the passenger-cabin window after the
  bridge (an open question in `c2-layout.md`). The cue sheet assumes the ear returns to the
  cabin at the entry.
- **The picture changes are pending.** The longer entry, the longer C3 and the joined C4–C5
  camera belong to the picture sessions; when they land, retime the cues here and in
  `sfx_mix.SHOTS`.
- **C4's opening line.** The Greywater take stands in for a line about where they will set
  down; a new line naming Kilo Nine may be wanted.
- **Runtime.** The arrival is played as cues by the game's player (`runtime-contract.md`).
  Chosen takes are encoded the way the hail is (`audio.json`, `:asset-pipeline:processAudio`)
  when the owner accepts them; this sheet's times become each shot's `cues`.
