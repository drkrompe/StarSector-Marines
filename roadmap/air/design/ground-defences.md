# Ground defences — fortresses that hold the sky

Status: ACTIVE

Written: 2026-10-14

Updated: 2026-10-14 — slices 1 to 4 built, with painted art for the battery and the generator; placement authority moved from map generation to the mission (a mission declares batteries and a shield generator, stages place exactly that); owner answers folded in: either side may hold them, the dome stops incoming fire only, balance and how often a battle has them are left to mission design and play.

Read `air-nouns.md` (**Overhead ships**, the balance law), `warship-behaviours.md` (gap
G5, the declared capital hazard and standoff), `ship-support.md`, and the two
command-power stories `shielded-fire-support-zones.md` and
`orbital-battery-fire-support-lock.md` first. This doc says what a world's ground
defences are in a battle. The fortress battery and the shield generator are built; the
defence nests are not.

## What the owner asked for

"Larger fortress-like standing structures with batteries and flux shielding, to be a
barrier to a ship simply flying through." Today nothing on the ground can hurt a ship (an
ordinary turret's round glances off the armour rule), so a capital over a battle goes
wherever it likes. The balance law already anticipated this: ground-to-ship is weak "at
least until planetary defences are built".

## The vanilla fiction is three different things

The market's own industries name them, and each does a different job:

| Industry | Vanilla text, in short | In battle |
| --- | --- | --- |
| Ground Defenses | a dispersed network of small, hidden weapon installations | **Defence nests**: many small hardened anti-air and anti-infantry emplacements. A threat to aircraft and people, not to a capital. Largely what defence posts already are. |
| Heavy Batteries | armoured subsurface batteries; no captain would get within their firing envelopes | **Fortress battery**: a few large structures carrying ship-grade guns whose envelope a ship must respect. The barrier. |
| Planetary Shield | a plasma envelope that disrupts long-range bombardment | **Shield generator**: a structure projecting a dome with its own flux pool that soaks ship-grade ordnance landing inside it until it overloads. |

So the target profile carries three signals rather than one `defenseLevel` number:
whether the market has ground defences, heavy batteries, and a planetary shield. They are
inputs a mission may read when it decides what its map carries, not the decision itself;
any mission may also declare its defences directly. The two command-power stories
already ask for the battery and shield signals; this is the same boundary change, made
once.

## Fortress battery

- **What it is.** A standing structure several cells across, built into the fortress
  ward (or an installation's own compound), with a battery of one to three large guns.
  The guns are vanilla weapons with vanilla numbers, taken from what the game's own
  station modules mount (Hellbore, Mjolnir, Gauss, Heavy Mauler and the like by faction
  and tech level), so a battery fights a ship at **ship-vs-ship balance**, not at the
  weak ground-to-ship rate. Its hull and armour come from the same source (station
  armour is in the 1600 to 2400 range), so a battery is as hard to crack as a ship.
- **The envelope.** Each battery has a firing envelope: its longest gun's reach at
  ground scale. A hostile ship inside it is engaged; its rounds reach the hull through
  the existing detonation route (shield first, then armour), so no new damage rule is
  needed, only the ground-side aim at an airborne hull.
- **A barrier, by behaviour.** A ship treats a live envelope as a capital hazard: the
  existing standoff keeps its station outside, the director does not pick stations
  inside, a pilot sees the ring, and a low pass through one is a gamble the pilot takes
  knowingly. A ship can still fight a battery from inside its envelope; it simply pays
  for it.
- **Fire control.** Each battery has a fire-control room inside the structure. Marines
  who take or destroy it silence the battery (the capture, contest, recapture and
  destroy rules of `orbital-battery-fire-support-lock.md`). A silenced battery also
  releases the Orbital Barrage lock, so the two stories share one structure.

## Shield generator

- **What it is.** A structure that projects a dome of a fixed radius, with a flux pool
  and dissipation like a ship's shield. Hostile ship-grade ordnance and orbital
  barrage landing inside the dome is absorbed as flux instead of detonating. When the
  pool fills, the dome drops and overloads for a few seconds, then comes back.
- **What it does not stop.** People, small arms, ordinary ground weapons, shuttles and
  fighters pass through, as vanilla says it disrupts bombardment rather than sealing the
  ground. Infantry fight under it normally.
- **How it is beaten.** Two ways, and both are the point: concentrate enough ship fire
  to overload it and land rounds in the gap, or send marines to take or wreck the
  generator (the relay of `shielded-fire-support-zones.md` is this structure).
- **Vanilla figures.** Vanilla gives the planetary shield no combat numbers, so the
  dome's capacity and dissipation are authored starting figures, tuned in play. This is
  the one place the law's "numbers from vanilla" has nothing to read.

## Defence nests

The dispersed Ground Defenses network is mostly today's defence posts and overwatch
towers, with one change: a nest's flak and anti-air weapons engage aircraft (they already
drain aircraft through the air-threat field). They do not threaten a capital, keeping
ordinary ground-to-ship weak. Nothing new is required beyond the profile signal deciding
how many there are.

## What this does to a battle

The fun is a combined-arms loop that the battle does not have yet:

1. A defended world's fortress denies the sky over its keep: batteries keep the company's
   ship at standoff, and the shield soaks what it lobs in from outside.
2. The marines' job becomes opening the sky: take a fire-control room, wreck a
   generator, hold the ground long enough for the ship to come in.
3. The ship's job is to cover that assault from outside the envelopes, then move in
   once they are down, and finish what its guns are for.

It works the same in either direction: whichever side holds the world has the batteries
and the generator, so a company fighting on its own side's world has an answer to an enemy
capital that is not another ship.

## Slices

1. **Profile signals** (built): ground defences, heavy batteries and planetary shield on
   the target profile, read once from the market (shared with both command-power
   stories), as inputs to mission design. As built: three booleans on `TargetProfile`,
   read in `TargetProfileResolver` (ground defences is true for Ground Defenses *or* its
   Heavy Batteries upgrade, which keeps the network), carried through fixture JSON, and
   restatable by a mission (`Mission.defences`, the DEBUG "Ground defences" dial on
   Conquest). Map generation does not read them; a mission definition may (see slice 3, *Placement authority*). `DetachmentResolver.planetHasHeavyArmaments`
   is left as it is: it also counts heavy industry, so it is not the same question.
2. **Multi-cell structures** (built): the turret catalog takes odd
   footprints larger than one cell, centred on the anchor cell. As built: `StructureDef`
   validates odd positive `footprintCells`; `StructureFootprint` owns placement validity
   and sealing; `BattleSimulation.spawn` seals a multi-cell structure's footprint and
   `TurretDemolitionSystem` opens it as rubble on death; the body radius is the authored
   `radius` circle; the ground pad is drawn at the full footprint. One-cell structures
   are unchanged, and defence-post layouts still require one-cell turrets.
   `structure.fortress-battery-placeholder` (3x3, heavy-mortar mount) is a stand-in for
   slice 3. A structure's painted body is its optional `art` block (`StructureArt`): a static base
   drawn over the whole footprint in place of the flat pad, plus centre-pivoted overlay layers that
   may spin or pulse. A rotating gun stays the mount's own `sprite` / `recoilSprite` pair.
3. **Fortress battery** (built): placement where a mission declares heavy
   batteries, vanilla station-module guns, ground-to-ship aim at a hull in the envelope, the
   envelope as a hazard warship AI keeps out of, fire control and its capture rules, and the
   Orbital Barrage lock. As built:
   - *Placement authority.* The mission declares, the map obeys. `GroundDefences` (battery count,
     shield generator yes/no) is the declaration; `MissionDefencePolicy.declare` is the one place a
     mission's declaration is decided and `MissionLaunch` stamps it on the profile
     (`TargetProfile.declaredDefences`). Precedence: the mission's explicit `Mission.defences`
     (the DEBUG dial) wins, then the mission type's policy, then none. A mission that says
     nothing carries no defences. The market's three signals stay on the profile as inputs a
     policy may read, and no generator stage or setup step reads them or derives a count from map
     area. **Conquest's policy** lives in `MissionDefencePolicy.conquest`: it reads the market's
     Heavy Batteries and Planetary Shield and declares four batteries and a shield generator
     accordingly, an untuned first-pass mission-design choice that a designer changes there.
   - *Placement.* `FortressBatteryStage` runs after the overwatch towers and stands exactly the
     declared number of batteries; with none declared the map is unchanged. Sites are open
     outdoor ground with a clear apron, deep band first; fewer are placed if the ground cannot
     hold them.
   - *Guns.* A structure that names a `battery` block in the turret catalog is registered as a
     battery when spawned, held by the side that spawned it. Its `BatteryLoadout` is a set of
     vanilla weapon ids taken from station modules (`citadel`: Gauss, Mjolnir, Heavy Mauler from
     `module_citadel1_lowtech`; `hellbore-pair` from `module_large1_lowtech`); every figure is
     the game's. The structure mounts `mount.fortress-battery` (the heavy-mortar weapon on its own gun-house art), so it also
     shells ground units as an ordinary emplacement. The gun house turns toward the hull the citadel guns are
     settling on or firing at (presentation only) and its barrel layer recoils on each ship round.
   - *Aim and damage.* A gun settles on the nearest hostile hull in reach for 1.5 seconds, then
     fires a ship's shell from the ground (`WarshipSystem.fireFromGround`): it flies, point
     defence can stop it, the shield then the armour rule take it, and it lands on hulls only
     (no ground detonation under the aim point). Aim leads the hull's drift. No traverse limit
     or ammunition yet.
   - *Hazard.* While operational a battery registers its envelope (longest gun reach) with the
     warship system as a live hazard hostile to everyone but its holder. A doctrine ship keeps
     its station beyond it plus the standard margin in any posture but withdraw, working from
     the station the mission gave so it returns when the battery falls silent; a ship with no
     doctrine, a piloted one, or one the mission places inside, gambles. The envelope is drawn.
   - *Fire control.* A point at the structure (a radius around its anchor, not a separate
     body): hostile presence contests it and a contested battery is silent; uncontested hostile
     presence for 12 seconds takes it, a holder left alone pushes the contest back in 8, and
     both present holds it. Written for sides, so either may hold or take. Destroying the
     structure silences it for good. A separate destructible fire-control body is not built.
   - *Orbital Barrage.* While an intact battery's holder is hostile to the company (held or
     contested) the power cannot be targeted and its card reads LOCKED; capture or destruction
     lifts it. The briefing statement of this and any contract mandate to keep the site intact
     remain in `orbital-battery-fire-support-lock.md`.
   - *Untuned.* A single battery kills an undefended capital in well under two minutes; envelope
     reach, lock time, capture times and counts are first-pass figures for play to settle.
   - *Art.* Static hardened base, one rotating gun house and a recoiling three-barrel layer, geometry-first
     (see `geometry-first-imagegen.md`). The id keeps its `-placeholder` suffix to spare every reference; there
     is no per-structure wreck sprite, a destroyed structure becomes ordinary rubble cells and a smoking wreck.
   - *Not built.* Faction tech choice of loadout, gun traverse and ammunition, a per-gun art mount, and a ship's hazard-aware
     station choice for the player's own calls (the player may still place a ship inside).
4. **Shield generator** (built): the dome, its pool and overload, absorption of ship-grade
   ordnance and barrage, the dome's look, and its capture rules. As built: the catalog's
   `structure.shield-generator` (3x3, role STRUCTURE, no gun) carries an optional `dome` block
   (`DomeSpec`: radius, flux capacity, dissipation, overload seconds, capture radius and time),
   and spawning it registers a dome with `ShieldDomeService`. Ship-grade ordnance is marked at its
   source (`PendingDetonation.asShipGrade` on warship rounds, warheads, lance beams and Orbital
   Barrage) and `Detonations` asks the domes before anything lands, so a hostile round inside an
   up dome becomes flux (the larger of its damage and wall damage) and does nothing else, ship
   landing FX and cues included. The round that fills the pool is absorbed; the dome then drops
   for its overload time and recovers empty. Small arms, mech weapons, rockets, aircraft and
   their ordnance are not touched (decided: the dome is ship-grade only). Capture: combatants
   within the capture radius and none of the holder's friends take it after the capture time, the
   holder's friends alone wind it back, and a taken dome reboots for 4 s; the entity's own faction
   never changes, so the holder is the service's record. Destroying the generator drops the dome.
   Look: `ShieldDomeRenderSystem` (HAZARDS) in the warship shield's cyan for the player's side and
   red for the enemy's, a pool gauge ring, amber recovery rim, hit marks, core glow and capture arc;
   an enemy dome draws only where the map has been seen. Sounds are the game's own
   `hit_shield_heavy_gun`, `shield_burnout` and `shield_raise` through the warship cue path.
   A mission that declares a shield generator gets one beside the keep, held by the
   garrison (`ShieldGeneratorPlacement.nearKeep`); which missions declare one is
   `MissionDefencePolicy`'s call. All figures are first-pass and **untuned** (1500 flux, 60 per second, 9 s
   overload, 16 cells). Evidence: `ShieldDomeServiceTest` and the `shield-dome` scene (control,
   dome, destroyed). Still to do: AI awareness (no squad is sent to take or wreck a generator and nothing orders a
   defender to retake one; a taken generator is still shot by its original side's enemies because
   its entity faction is fixed), a campaign mission that places one for a market-held
   shield, and the barrage-targeting preview showing a dome (barrage is absorbed, not denied, so
   `shielded-fire-support-zones.md`'s denial contract needs reconciling with the owner's decision).
5. **Defence nests**: the dispersed posts, sized by the mission's declaration (a count to add to `GroundDefences`); anti-air already exists.

## Decided

- **Either side may have them.** There is nothing special about attacker or defender: a
  battery or generator belongs to whoever holds the world, and a company fighting on a
  world its side holds has them exactly as an enemy garrison would. Every rule here is
  written for "its side" and "hostile", never for marines or defenders.
- **The dome stops incoming hostile fire only.** Its own side fires out freely, as every
  shield already behaves.
- **Balance is gameplay's to decide.** Whether a ship can grind a battery down alone, how
  big a dome's pool is, how far an envelope reaches: these are tuned in play, not settled
  here. The structure's figures start from vanilla's where vanilla has them.
- **How often a battle has them is a mission design question,** in the campaign and in
  ordinary battles alike, and its numbers come from play and balancing. A mission
  decides what defences its map carries (a Conquest map would likely have batteries); the
  market's industries are one input a campaign mission may read, not the rule. This doc
  defines what the structures are, not how many there are.
