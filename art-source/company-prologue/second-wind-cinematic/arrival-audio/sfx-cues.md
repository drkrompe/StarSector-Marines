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

- **Engines are burning rocket fuel, sized by thruster (owner, 2026-10-19).** Bass and crackling
  combustion like a shuttle launch, never compressed air, hum or chimes. From the auditions
  (`audition_engines.py`): small thrusters close (the fighters, the customs Hound) use the bright
  crackling code-built voice; a medium drive at range or through the hull (Second Wind in C1, C2,
  the entry and C4) the muffled code-built voice; a heavy drive at range the darker code-built voice
  (the Legion's forward group); the generated `c4_launch_burn_t01` is a medium thruster close
  (Second Wind's lift jets in the landing) and `_t02` a single heavy thruster (the Legion's main
  drives, the departing Colossus), both flown along their paths with distance and Doppler.
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
| `second_wind_pass` | Second Wind's drive as a muffled heavy rocket, **synthesised from its lane** in the C1 scene: a deep roar with crackle, a heavy low end, the pitch drop and a swell as it brakes past the camera at 10 s, then its engines facing us as it opens up toward Jangala | 0–37.7, loudest 9–12 | Spot | −10 | Code |
| Squelch open / close | Approach's key-up and key-down | 3.0 / 11.8, 23.7 / 31.7 | Spot | as shipped | Existing `jangala_squelch_open`, `_close` |
| `hound_flyby_a` | The customs Hound crossing left to right, **synthesised from its flight path** (`synth_flyby.py`, owner 2026-10-19: the generated swish missed). A muffled rocket roar with a heavy low end and the pitch drop computed from the path, a dark air rush that swells as it closes, distance falloff and a pan from its bearing. `hound_flyby_b` is darker and heavier | 21.5–34.5, closest at about 27.5 | Spot | −8 | Code |

## C2 — PATROL ABEAM, on the bridge (15.0 s)

Hail 07 0.46–9.39 (Approach), 08 9.76–14.21 (Castle). The fighters pass the port slits at
3.0 and 4.2, the Legion is abeam at 8.0, the de-orbit burn starts at 9.6 with a 16° pitch
over 3.5 s, and the Legion clears the slits at about 14.

| Cue | What | When | Kind | Level | Source |
|---|---|---|---|---|---|
| `c2_bridge_bed` | Bridge room tone: console fans, electronics hum, distant drive | 0–15, from a 1 s crossfade off the cabin bed | Bed | −16 | Gen |
| `c2_console_beeps` | A contact chirp as the Legion is tracked; a short double tone as the fighters come in | 0.2, 2.0 | Spot | −14 | Gen |
| `fighter_00`…`fighter_12` | The six Broadswords, **synthesised from their flight paths** in `c2_bridge_blockout`: a hard thruster roar each, a sharp pitch drop at 160 m/s, swinging hard to port as they whip past; the leaders loudest | 2.0–5.5, peaks at 3.0 and 4.2 | Spot | −6 leaders, −9 to −11 wingmen | Code |
| `legion_drives`, `legion_forward` | The Legion, **synthesised from its track**: its main drives at the stern (arriving last, loudest just past the beam) and a forward cluster, deep muffled rockets with heavy bass, plus attitude-thruster bursts along the hull as it trims to the beam | 0–15, building to the beam at 8.0 | Spot | −7 and −9 | Code |
| `c2_console_lean`, `c2_steps`, `c2_brush_past` | The camera moving on the bridge (the 340 m C2 camera pass): a palm settling on the forward console at 1.5, the boot step down off the dais at 5.0 and four paces across the deck plate, a jacket brushing past Castle at 5.55, and a lean over the comms bank at 6.6. Close and quiet, under the hail | 1.5, 5.0–6.6, 6.6 | Spot | −14 to −18 | Gen |
| `c2_switch_clicks` | Castle keys her reply; then she closes the channel to Approach | 9.5, 14.4 | Spot | −12 | Gen |
| `second_wind_interior` | Second Wind's own drive heard from its bridge, the same rocket voice as C1: idling low, then the de-orbit burn lights at 9.6 with an ignition thump and opens up, on through the entry | 0–15, burn from 9.6 | Transition | −8 (idle about −22) | Code |
| Squelch open / close | Approach's key-up and key-down | 0.3 / 8.94 | Spot | as shipped | Existing |

## Entry — the heat and the cloud (7.0 s)

Longer since the owner's review (`c2_core` HEAT, SMOKE, CLOUD_IN, WHITE, BLOOM). Castle's
"Cleared. Everybody breathe." from 0.16 s, off channel, no squelch, over clear space to 1.2.
Heat from the bottom right fills the glass by 2.4, flames streaming to the top left; smoke
1.6–5.6; cloud 4.1–5.4; white 5.4–6.0; bloom to the cut at 7.0. The camera and window shake
(`c3_core.shake_envelope`) stay under a pixel to 0.6 s, peak at about 9 px from 4.0 to 5.2 s,
and decay (about 3 px at the cut).

| Cue | What | When | Kind | Level | Source |
|---|---|---|---|---|---|
| `second_wind_interior` (carried) | The de-orbit burn running on under the heat | 0–7.0, easing under the bloom | Transition | −8 | Code |
| `entry_reentry_roar` | Re-entry through the hull: the roar building with the heat, peaking with the shake, easing into the cloud | 1.2–7.0, peak 4.0–5.2 | Transition | −13 under the line, up to −3 at the peak | Gen |
| `entry_buffet` | Buffeting and rattling fittings, following the shake envelope in dB | 0.6–7.0, peak 4.0–5.2 | Bed (looped) | −28 to −4 | Gen |
| `c1_hull_creaks` | One groan of the hull under load | 3.6 | Spot | −10 | Gen |

## C3 — FIRST OPEN SKY (16.0 s)

Longer since the owner's review (`c3_core`): it opens still descending and shaking (about
3 px, 1 px by 2 s, gone by 5 s), the horizon swings level over 0.4–5.6 s with a settle around
5–7 s, then the passengers look. Castle's travel-reels line is at 7.0.

| Cue | What | When | Kind | Level | Source |
|---|---|---|---|---|---|
| `second_wind_interior` (tail) | The burn's last seconds | 0–2.0 | Transition | −10 falling away | Code |
| `entry_reentry_roar` (tail) | The roar's tail | 0–1.5 | Transition | −6 falling away | Gen |
| `entry_buffet` | The last buffeting, following the shake | 0–5.5 | Bed (looped) | −17 falling to −44 | Gen |
| `c3_wind_easing` | Wind on the fuselage easing to a smooth airflow as the ship levels | 0–16, easing over 0–5.5 | Bed (looped) | −6 easing to −18 | Gen; existing `jangala_arrival_wind` |
| `c3_frame_settle` | A last rattle and one creak of the window frame as it settles | 5.6 | Spot | −14 | Gen |
| `c3_cabin_flight_bed` | The cabin in steady flight: air over the hull, ventilation, a low drone | 0–16, under everything; into C4's first second | Bed | −16 | Gen; existing `jangala_arrival_cabin_bed` |
| `c3_nature_muffled` | The jungle below heard through the glass, low-passed at 1.8 kHz | 4–16, in once the shaking has gone | Bed | −16 | Gen |
| `c3_river_muffled` | The river's rush, far off, low-passed at 1.2 kHz | 4–16 | Bed | −18 | Gen |
| Castle | "Like the travel reels they ran in the canteen... except this one doesn't loop." | 7.0–12.1 | Voice | 0 | `c4_reels` take |

## C4–C5 — THE ROAD, THE PORT AND WE'RE DOWN (one shot, 61.4 s)

One continuous camera (owner, 2026-10-19; joined in `c5_scene`: C5 opens on C4's last frame and
pushes in from 445 m to the 220 m view of the ramp over its first 4 s): the sky pan from farmland over the freeway and
the industrial belt to the port (0–13.4), down over Kilo Nine, the landing, the yard, the
families, the rise and the title. C4 runs 0–21.4 and C5 follows; times below are on the
joined clock, and C5's own beats are at their C5 time + 21.4.

The approach is slower (owner: she named the landing zone after we were already landing).
C5 is 40 s (owner, 2026-10-19: linger on the title, more of the port at work): the title rises from C5 24, holds to 38.0 and fades to black over 38.0–39.5. The port beds carry the hold and go out with the fade; Ending A (`ending_strings_piano_t02`, 40 s) swells at the title, carries the hold and resolves into the black. Rows below give the 33.5 s timings where they differ.

Castle's in-flight lines are both over before the ship starts down: "Greywater's past the
cranes. Let me do the talking." 0.5–3.9, then "Look for the fenced yard on the east side.
Kilo Nine. Mind your manners down there; it's a favour." 4.6–10.9. Second Wind brakes into a
hover at 12.0, touches down at 16.4 in a ring of dust, and its drive winds down by 17.9. The
burn builds from under the end of her line into the touchdown. The yard vehicles roll from
19.6 and stop at 23.9, 24.9, 25.9 and 26.4. Her all-hands is on the loudspeaker from 26.4,
the ramp drops at 30, the families walk out from under the stern from 29.9, the camera
rises 39–47, her last line ends at 45.4, the title from 46, black by 54.

| Cue | What | When | Kind | Level | Source |
|---|---|---|---|---|---|
| `c3_cabin_flight_bed` (carried) | The cabin, fading as the picture goes outside | 0–1.0 | Transition | −16 to silence | Gen |
| `c4_high_wind_bed` | Open air at altitude, soft and steady | 0–20.4, easing out after the pan as the ship goes down | Bed | −14 to −28 | Gen |
| `c4_ground_distant` | Far below: farm machinery and a slow train | 0–11 | Spot | −18 | Gen |
| `c4_freeway` | Heavy trucks and haulers on the freeway | 1–53.9; loudest crossing the road (4), low over the yard, up again with the rise, out under the title | Bed | −12 (−22 over the yard, −14 at the top of the rise) | Gen |
| `c4_port_heavy` | The industrial port at work: cranes, containers banging, machinery, forklifts. **No horns** (owner: too much horn; the chosen take measures none) | 3.5–53.9; full from 7, ducked 6 dB under the announcement, up at the top of the rise, out under the title | Bed | −12 (−22 to −10) | Gen |
| `c4_port_ambience` | A lighter port layer under it: distant machinery, reversing beepers | 5–39.4 | Bed | −18 | Gen |
| `second_wind_c4_drive` | Second Wind's main drive heard from the sky camera: a muffled burning-gas rocket, broadband, **no hum** (owner, 2026-10-19: "burning gas thrusters, heavy lift"). Full until touchdown, out by 17.9 (thruster markers) | 0–17.9 | Bed | −12 (−16 throughout, under the lines and the landing roar) | Code (`synth_flyby.py`) |
| `landing_roar` | The belly lift jets: broadband crackling combustion with heavy low end, an ignition whump at 10.0, full 12.0–16.4, rougher as the hull nears the pad, out by 17.4. Held 6 dB down until Kilo Nine ends | 9.5–17.4 | Spot | −5 | Code (`synth_landing.py`, broadband rebuild) |
| `landing_grit` | Sand and grit blasted across the pad, building from about 13, peaking at touchdown; noise only, no pings | 13–17.4 | Spot | −11 | Code |
| `landing_thump` | Touchdown: the legs at 16.4 and 16.57, a noise thump with no tone | 16.4 | Spot | −2 | Code |
| `landing_spool` | Burner shutdown: sputtering coughs, pops and gas hiss after the cut-off | 16.6–18.5 | Transition | −8 | Code |
| `landing_settle` | Dust washing back down and the last grit | 16.9–21.4 | Spot | −14 | Code |
| `lighter_six` | Lighter 6 (`port_life`), a Hermes letting down from 200 m onto a pad about 270 m west of the yard: a small thruster, building as it slows to touchdown, cut at about C5 22.3 | 30.9–44.0, touchdown 43.4 | Spot | −14 | Code |
| `colossus_lift`, `colossus_mains` | The Colossus at the heavy berth lifting out (`port_fleet`), about 480 m from the camera: its ten lift jets spool up from C5 12 (40%) to full at 15 as it leaves the deck (the generated heavy burn, muffled by distance), then its six mains light at C5 19.5 with an ignition thump, full by 21, as it turns south-west and climbs away (the dark code-built heavy drive). Kept under the music and title | Lift from 33.4, mains from 40.9, to the end | Spot | −10 and −12, 3 dB down under the title | Gen + Code |
| `c5_vehicles_arrive` | The tug, crew carrier, bowser and customs car rolling in and stopping one by one, each with its air-brake hiss on its stop (`c5_scene`: stops at C5 2.5, 3.5, 4.5 and 5.0) | 19.9–28.4, hisses at 23.9, 24.9, 25.9, 26.4 | Spot | −14 to −18 | Gen |
| `c5_yard_bed` | The yard at the jungle's edge: insects in the tree line, birds, breeze | 17.4–54.9; full to 39.4, thinning under the rise, out by 52.4 | Bed | −14 | Gen; existing `c5_yard_jungle_bed` |
| `c5_hull_ticks` | The hull ticking and pinging as it cools | 21.9–35.4 | Spot | −14 | Gen; existing `c5_yard_hull_ticks` |
| `c5_car_doors` | A door 0.6 s after each vehicle stops; the customs car's two doors last | 24.5, 25.5, 26.5, 27.0 and 28.5 | Spot | −16, −14 for the customs car | Gen |
| `c5_steam_vent` | Steam venting off the hull as the engines go dark | 25.4–30.0 | Spot | −14 | Gen; existing `c5_yard_steam_vent` |
| `c5_pa_click` | The loudspeaker switched on before "All hands", and off after the announcement. A clean click and a speaker thump; **no squelch** | 26.15, 35.2 | Spot | −10 | Gen |
| `c5_ramp_drop` | The stern ramp: hydraulic whine, then the clunk as it meets the slab | 27.0–31.0, the clunk at 30.0 | Spot | −8 | Gen |
| `c5_crowd_murmur` | The families as a far murmur, low-passed at 900 Hz: from 220 m up there are no footsteps (owner, 2026-10-19) | 29.9–46.4, from when they come out from under the stern, well under Castle | Bed | −24 | Gen |
| `c5_port_wide` | The whole port opening up as the camera rises: distant cranes, a lifter climbing, trucks; no horns | 38.4–54.9, up from −17 to −5 at 47.4, then fading to silence by 53.9 under the title | Bed | −17 → −5 → out | Gen |
| Music | **Ending A chosen** (owner, 2026-10-19): `ending_strings_piano_t01` from `make_music.py ending`, strings with a soft piano on top, building from the yard to a peak at the title and fading to silence for the return to the game. Before it, the strings score (`score_strings_t01`) runs C1–C4 at +6 dB, with the piano-and-pad score layered into C3 as a suggestion. Laid out and ducked under the voices on the Arrival Score Desk (`score-desk.html`) | Ending 21.4–54.9 here | Music | +4 (ducked 8 dB under voices) | Gen (music) |

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
