# Tech families: the low-tech, midline and high-tech design languages

Status: ACTIVE — the shared look and sound language every machine, suit and interior is drawn and voiced in.

Written: 2026-10-23

## Purpose

Starsector draws every hull in one of three families: **low-tech**, **midline** and
**high-tech**. A player reads a ship's family at a glance, by silhouette, paint, engine
flame and sound, before reading any of its stats. This doc carries the same split onto the
ground, so that a column of vehicles, a mech lance, a marine's suit and a ship's interior
can each be read the same way.

Each feature that draws or voices something cites this doc rather than restating the
families. `ship-interiors-nouns.md` (interior tech style) is the first to use it.
`MachineAudio` (ground engines) and `EngineVoice` (air and warship engines) are the first
in sound.

## Vocabulary

- A **tech family** is the design lineage a thing was built in: low-tech, midline or
  high-tech, after vanilla's hull families. It decides how the thing looks and sounds.
- **Exotic families** sit outside the three. In vanilla they are Omega, Dweller,
  Remnant/Threat and our own Colony Breach swarm. Each keeps its own language, defined by
  its own feature. They are not a fourth rung.
- A family's **visual language** is its materials, silhouette, livery, light and wear.
- A family's **sound language** is its power plant, weapon tone, machinery and vents.

Three things are easily confused with tech family, and none of them is it:

| Not this | Which is | Why it differs |
|---|---|---|
| **Pattern tier** (Common / Advanced / Prestige) | How rare a pattern is and how hard it is to get | The Breakthrough tank is Prestige and low-tech. The Hover scout is Advanced and high-tech. |
| **Armor tier** (1-4) | How much protection a suit gives | A tier-4 Knights reliquary suit is low-tech. A tier-2 Janus scout suit is high-tech. |
| **Faction** | Who fields the thing | A faction leans towards one family but fields all of them. The Hegemony runs League Bulwarks and Diktat Wardens. |

## Laws

1. **A family is never better.** It is a different way of being good. Low-tech is heavy,
   armoured and forgiving. Midline is balanced and serviceable. High-tech is fast, precise
   and fragile in its own ways. No family is a progression step, and nothing unlocks in
   family order.
2. **Family comes from lineage, never from the company's progress.** A thing's family is
   its pattern's origin. Restoring, repairing or upgrading a thing keeps its family:
   restored low-tech is better low-tech, never midline. (Ship interiors already follow
   this law.)
3. **A refit shows its own family where it touches the thing.** When one family's parts
   go on another family's hull, the hull keeps its silhouette. The refit's family shows in
   what it replaced: turret, livery, power plant, sensors. The laser tanks are the
   precedent: both are refits of the low-tech Ordnance Board hull. One carries Tri-Tachyon
   parts and the other League parts.
4. **Family is presentation.** No mechanic reads it. Lore may lean a pattern's authored
   numbers towards its family's character, the way vanilla does, but rules never branch on
   it.
5. **Every family is a full language.** Each needs its own look, sound and condition range,
   from wreck to pristine. A family that only exists as a recolour of another has not been
   designed yet.

## Faction leanings

These follow vanilla fleet doctrine. They are leanings, not ownership.

| Faction | Leans | Notes |
|---|---|---|
| Hegemony | Low-tech | Old Domain drawings, heavy plate; fields midline frames freely |
| Luddic Church, Knights of Ludd, Luddic Path | Low-tech | Conservative, hand-maintained (`equipment-lore-catalog.md`) |
| Pirates | Low-tech | Salvage and field rigs; take anything they can steal |
| Independents | Low-tech and midline | Frontier yards and commodity parts, plus whatever they can buy |
| Persean League | Midline | Coalition standards, shared parts, licensed assembly |
| Sindrian Diktat, Lion's Guard | Midline | Export-grade standard patterns, petrochemical wealth |
| Tri-Tachyon | High-tech | Composite, hover and drones; the company's white livery |

## Visual language

| | Low-tech | Midline | High-tech |
|---|---|---|---|
| Materials | Bolted and welded plate, castings, exposed cable and hydraulics | Pressed modular panels, sealed seams, standard fittings | Seamless composite, sealed shells, nothing exposed |
| Silhouette | Slab-sided, boxy, heavy, a lot of mass | Clean but practical; panelled, with access hatches | Smooth curves and low profiles; hover skirts instead of tracks |
| Livery | Drab, worn, faction paint over primer | Painted standard schemes with unit markings | White, graphite and accent cyan (Tri-Tachyon) |
| Light | Caged amber lamps strung on cable | Recessed strip light (open) | Luminous panels and emitter glow (open) |
| Engine flame | Vanilla low-tech orange | Vanilla midline amber | Vanilla high-tech blue (`EngineStylePalette`) |
| Wear | Rust, patches and replaced plates; reads as old | Scuffed paint, clean repairs; reads as serviced | Scorching and cracked panels; damage reads as failure, not age |

Low-tech is established. The low-tech interiors, the Breakthrough tank and the APCs
define it. High-tech has a start in the Tri-Tachyon hover line and the burst laser tank.
**Midline has no defined language yet.** The rows above are a direction to look-develop
from, not a finished design.

## Sound language

| | Low-tech | Midline | High-tech |
|---|---|---|---|
| Power plant | Rough combustion: diesel chug, knock and exhaust | Governed and steady: a smaller engine under an electric traction hum (open) | Turbine roar and whine; lift fans; electric |
| Ground engines today | `marines_engine_tracked` (heavy diesel), `marines_engine_wheeled` (truck diesel) | None | `marines_engine_turbine` (gas turbine), `marines_engine_hover` (lift fans) |
| Air and warship engines | Vanilla `lotek` clips | Vanilla `midtek` clips | Vanilla `hitek` clips |
| Weapons | Ballistic bark and mechanical cycling | Mixed ballistic and energy | Energy hum, charge and lance |
| Machinery | Clanking tracks, hydraulic hiss, heavy footfalls | Servo whir, cleaner footfalls (open) | Quiet actuators and electric pulses (open) |
| Vents | Vanilla flux vent and flush loop for every family; a per-family vent is open | | |

Air and warship engines already pick their voice by vanilla family (`EngineVoice.Tier`,
from each hull's engine-slot style). Ground engines don't yet: `MachineAudio.engineVoice`
picks by vehicle type.

## Where things stand

This placement is a reading of the shipped lore. None of it was authored as a tech family.
Correct it here when a pattern's lore says otherwise.

### Ground vehicles

| Vehicle | Family | Notes |
|---|---|---|
| Heavy APC, Up-armored APC, LRM carrier | Low-tech | Hegemony Ordnance Board hull |
| Fire-support APC | Low-tech hull | Diktat allotment; a midline refit candidate |
| Escort APC | Low-tech hull | League convoy grade; a midline refit candidate |
| Breakthrough tank, Screened tank, Dozer breacher | Low-tech | "A shape older than anyone's records" |
| Burst laser tank | High-tech refit of a low-tech hull | Tri-Tachyon emitter, livery and power plant |
| PD laser tank | Midline refit of a low-tech hull | League PD refit; the only midline-coded vehicle |
| Militia technical, SRM technical, Supply truck, Scout car | Low-tech | Field rigs on workshop pickups |
| Hover scout, Drone skimmer | High-tech | Tri-Tachyon Asset Protection |
| Wayline cargo hauler | Midline (reading) | Commercial industrial standard; unowned |

### Mechs

The three chassis are stand-ins for a grid of mech lines, tech family by weight class
(`mech-lines.md`).

| Chassis | Family | Notes |
|---|---|---|
| Hound | Low-tech | Frontier survey walker on commodity running gear |
| Warden | Midline | Sindrian export-grade standard pattern |
| Bulwark | Midline | League coalition standards and shared control systems |

### Infantry armor

- **Low-tech:** the unpowered and crude kit (fatigues, cordon shell, lashplate, Ward,
  Blackforge, packframe, boarding jack, foundry breaker) and the conservative faction lines
  (Church Palatine and orderly, Knights reliquary, Diktat furnace and vigil, Lion's mantle).
- **Midline:** the Hegemony and League standard lines (Legionary, redoubt, pathfinder,
  corpsman, XIV heavy, arbalest, Bastion, League bulwark heavy, outrider).
- **High-tech:** the Tri-Tachyon composite lines (aegis composite, Janus scout, specter
  heavy, riot shell).

### Ground emplacements

Each family builds a prepared and a militia emplacement style (`EmplacementStyle`,
`ground-turret-tiers.md`): low-tech brutalist concrete and earthwork; midline League modular panels
and barrier cells, or Diktat export kit and blockwork; high-tech field-emitter composite shells
in hex plating and deployed kit, under a drawn flux shield. The defenders' faction picks the
family (`EmplacementStylePolicy`). A fortification lot's walls, parapets and barricades are
drawn in its guns' style (`fort-works.md`).

### Interiors

Low-tech exists. Midline and high-tech are open (`interior-tech-styles.md`).

## Gaps

- **Midline is missing almost everywhere.** It has no visual language, no ground engine
  voice and no interior. On the ground, only the PD laser tank's turret, and two mechs
  whose art doesn't yet read as anything in particular, carry it.
- **The PD laser tank currently runs the high-tech turbine**, because no midline engine
  voice exists yet. By law 3 it should get the midline voice once that exists.
- **There is no high-tech mech.** Tri-Tachyon fields Wardens, a Sindrian hull that doesn't
  read as Tri-Tachyon. Six of the nine mech lines are open (`mech-lines.md`).
- **No mech or weapon sound varies by family.** Footsteps vary by chassis weight only, and
  there is one rifle voice for every faction.
- **Vehicles carry no family of their own.** `VehicleType` has no family property, so the
  engine voice is chosen per vehicle type. A family property would let sound, wrecks and
  vent colour follow from one fact.
- **Vehicle ownership by faction exists only as lore text** (pattern credits), not as data.
