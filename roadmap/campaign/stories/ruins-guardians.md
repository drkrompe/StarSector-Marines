# Ruins guardians

Status: IN PROGRESS — owner direction ("mini bosses"); owner picks
`derelict-hive-fight` (a, the hatch rhythm) and `derelict-vault-guardian` (a,
the vault frame braces). The rules for derelict security's two guardians are
built (What is built). Builds on the picks `ruins-guardian-placement` (a) and
`ruins-guardian-kinds` (a). The walker's look is `derelict-walker-look-v3`'s
sentinel, which derelict security's heavy line ships.

Written: 2026-11-09

Read `ruins-expedition.md` (Occupants: Derelict security; Where phase B's
threats come from; Works) first, then `derelict-security-network.md`
(Interiors, What is built), `derelict-lore-guide.md` and `air-nouns.md`
(Flocks).

## Goal

A **guardian** is a ruins mini boss: one set-piece enemy bound to one place it
guards, with a fight the player can read and answer, a signpost before it is
met, and a reward for beating it. Placement (`ruins-guardian-placement` a):
every site has a **vault guardian**, and extensive and vast sites add a
**layer guardian** on the deepest interior layer before the vault's. Each
occupant has its own (`ruins-guardian-kinds` a); this story designs derelict
security's pair and leaves the other occupants' to slices of their own.

A guardian is an ordinary unit of its occupant under its own rules. No damage
immunity, no knowledge its sensors do not give it, and no AI cores
(`derelict-lore-guide.md`: not loot on legs).

## The company it is built for

The company flies one Hermes: twelve seats, one squad a trip. A guardian is
answerable by infantry: rockets, satchel charges, area fire, and a second squad
arriving on the next trip. Neither guardian needs a machine on the ground, and
each is built round the moment the second squad arrives.

## Derelict layer guardian: the hive cradle

Owner pick `derelict-hive-fight` (a, a hatch rhythm).

- **Where.** The court of the deepest interior layer before the vault's
  (`HiveCradle.site`). Inside the megastructure a court is a light well, open
  sky ringed by roofed halls, so the hive's flock is shut in its well: flocks
  never enter a roofed cell.
- **How it fights.** Shut, it is heavily plated and builds drones into its bay,
  a flock's worth in about one cycle. Once a cycle (about a minute), if its
  flock is short, its lamps light and its tone sounds for three seconds, then
  its hatch opens for eight seconds and puts up what the flock lacks. Open, its
  plate is an ordinary cradle's.
- **Counterplay.** Fight from the roofed halls round the well, which the flock
  cannot enter. Thin the flock with area fire, then hit the open hatch with
  rockets. A satchel charge always tells. It is a hardened cradle with a fixed
  cadence and keeps every cradle's rules: throwing its layer's junction away
  from it (the branch its zone is fed on, `DerelictPower`) or destroying its
  zone's node shuts it cold: it
  builds and launches nothing, but its plate stays shut, so only a charge or a
  long anti-armour fight kills it.
- **What it guards.** The court is the layer's lift point, the deepest one.
  While the hive or its flock stands in the court, the court cannot be held
  (the court's hold is uncontested-only).
- **What it drops.** Its store opens: a tech cache, if `ruins-find-tiers` (a)
  is picked, else a field cache. The court becomes holdable, so the boat can
  come down deep inside, which shortens every later trip and the egress.
- **Signposting.** The close reading taken at the stand before names a launch
  cycle in a light well. The lamps and the tone carry through the halls. The
  court's marker reads guarded once the hive is sighted.

## Derelict vault guardian: the vault frame braces

Owner pick `derelict-vault-guardian` (a). The vault frame is the
shipped walker in its vault loadout (`DerelictWalkerFrame`), on its interior
post on the vault's approach, which it never leaves, even in the contingency.
No new chassis: its boss read is the walker's look, the sentinel
(`derelict-walker-look-v3`), and its weapons and plate stay the vault frame's.

- **How it fights.** Stood still and engaged for two seconds, it braces, legs
  planted out on the hall's walls: full fire, and its front arc takes hits on
  doubled plate. Braced, it cannot turn. Walking or turning it is free:
  ordinary plate all round and half its rate of fire (`VaultFrameBrace`).
- **Counterplay.** Two squads through two doors. One holds the front from
  cover; the other hits the rear arc, which takes half again more damage, and
  every rear hit throws it out of its brace to turn. A target that leaves its
  front arc does the same. A satchel at a planted foot.
- **What it guards.** The vault, and the keypad on its antechamber floor
  (`ruins-expedition.md`, Works).
- **What it drops.** Its hall's store: a tech cache (or a field cache). The
  vault store stays the vault's own reward.
- **Signposting.** The vault's layer always reads strongest. Its footfalls
  carry. Its hall is marked once sighted.

## The walker's legs and what passes under it

Owner direction ("separate hit boxes for the legs / knees and the central
body"; "enemies likely should be able to path under the held up torso"),
built on the recommended option of the open pick `walker-legs` (a). Every
derelict walker carries it; the vault frame's brace reads it.

- **Knees.** Each of the four legs has a knee with its own structure, on the
  same unit as the torso (`WalkerKnees`). A hit strikes a knee by a share set
  by where it comes from: a braced front never (its front legs are planted
  behind its plate), a flank most, the rear less, from under the torso most of
  all. The knee on the shooter's side takes the hit instead of the torso. So
  leg shots are the flanking reward, and the second squad's job.
- **A leg lost.** It limps on three legs at 60% speed, grips no wall on that
  side and cannot brace without both front legs. Two lost and it is downed:
  the torso drops, it cannot walk, and it takes half again more damage from
  every side.
- **Underneath** (`WalkerFootprint`). A standing walker blocks the ground only
  at its feet. A person fits under the torso, a machine never; under it, the
  knees and the soft underside are in reach (hits from there land as from the
  rear). A downed walker blocks its whole body again.
- **Signposts.** A lost knee throws sparks, its foot drags a scored trail, and
  a LEGS pip row over the walker counts what stands (slice 8).

## What else stands guard

The ruins' batteries (`RuinsBatteries`, on AI-core and colony-item sites) are
a works hazard, outside the guardian tier: an occupied site keeps its
guardians beside them. On an empty site the batteries are its vault guardian
(`ruins-guardian-kinds` a, the vault's own battery) and nothing is added.
Guardian strength does not read the prize's stakes; that waits on the owner.

## Slices

1. **Rules (built).** The hive's site and hatch cycle, the vault frame's
   brace. Unit-tested.
2. **The hive in battle (built).** `DerelictSecurity.install` stands the
   hive in its court, its flock shut in the court's well, answering to the
   nearest zone. The hatch launches it in place of the slow rebuild and sets
   its housing's plate. The housing and its flock are defender combatants, so
   they contest the court's hold. Remains: a `sceneEvidence` scene (the hatch
   opens when the flock is short, and a thrown junction keeps it shut).
3. **The brace in battle (built, but for the clamp and the legs).** The
   director ticks each vault frame's brace with its movement and its target's
   bearing, the damage pipeline asks it how each hit lands (`HitScale`), and a
   free frame's guns cycle at `fireScale`. Remains: "never leaves its hall"
   clamps its answer from
   `WalkerPosture.VAULT_REACH_CELLS` (10) to the vault hall's own cells. It
   coordinates with the walker's leg rig (wall reach,
   `derelict-walker-wall-reach`), which draws the planted legs. Owned jointly
   with `derelict-security-network.md`'s heavy line; its art and gait are
   that story's.
4. **Stores and drops.** A guardian's store opens when it falls, as a sealed
   store does when it is powered (`ruins-expedition.md`, Works).
5. **Signposts.** The close reading's guardian line, the court's guarded
   marker, the hatch's lamps and tone, the walker's footfalls.
6. **Looks.** The hive's own housing: a heavier cradle over a well hatch,
   painted after the shipped cradle and node (`art-source/derelict-security`),
   through a pick. Until then it draws as the shipped cradle.
7. **The other occupants.** Scavengers (the crew boss and the cutting rig),
   the Rav (the old sleeper and the deep bore), an empty site (the vault's own
   battery), as `ruins-guardian-kinds` (a) names them, each a story slice of
   its own once picked.
8. **Legs shown (built).** The leg rig never lifts a lost leg, drags its
   foot and grips nothing with it, and the body sags and tips toward it; the
   lost knee bursts once, then throws sparks and is drawn burnt; the LEGS pip
   row stands over a walker missing a knee; a downed walker's torso sinks to
   the floor. Knees, feet and sparks read the hips' frame, as the legs hang
   off the hips.

## What is built

- `HiveCradle` (`battle.derelict`): `site(delve, grade)` picks the court of
  the deepest interior layer before the vault's, at extensive and vast only.
  `Hatch` runs the bay, the build clock, the cycle, the warning and the open
  window, launches only what the flock lacks, shuts cold and stops every clock
  while unpowered, and gives the plate rating for its state. Numbers UNTUNED.
- `VaultFrameBrace` (`battle.derelict`): the stance (free, bracing, braced),
  the damage scale by bearing (front, flank, rear), a rear hit unbracing it,
  and the fire scale. Numbers UNTUNED.
- In battle: `DerelictSecurity.standHive` and `FlockSystem.launchInWell` (a
  flock kept to its own light well, launched by a `FlockHatch`);
  `DerelictSecurityDirector.braceVaultFrames` (the stance each tick, hits
  through `UnitRosterService.hitScale`, fire cycles stretched). The hive draws
  as the shipped cradle until slice 6. Look stills: `createSnapshots
  -Psnapshot=ruins-guardians-look`.
- `WalkerKnees` and `WalkerFootprint` (`battle.derelict`), worn by every
  derelict walker through `DerelictSecurityDirector.armWalkers`: knee hits
  through `HitScale`, speed through a unit modifier, and the feet-only
  footprint through `SeparationSystem.Straddle`. Drawn by `DerelictLegRig`
  (`legsLost`) and `DerelictWalkerComposer` (burnt knee, `marker`); sparks
  from the director. The look suite's `guardian-walker-knee` clip shows a
  walker losing a knee, then a second.

Tests: `HiveCradleTest`, `VaultFrameBraceTest`, `FlockSystemTest`,
`WalkerKneesTest`, `WalkerFootprintTest`, `DerelictLegRigTest`.

## Constraints

- Strength is not tuned from autopilot runs; it waits on the owner playing a
  derelict battle.
- No shield or immunity: option (c) of `derelict-hive-fight` breaks this rule
  and is carded only to say so.
- The walker's art, gait and leg rig are `derelict-security-network.md`'s.
- Every door and seal on the way to a posted walker, the vault hall's own
  door among them, is at least two cells wide (the chassis's radius in a
  `CorridorStage.CORRIDOR_WIDTH` corridor).
- The hive keeps every cradle's rules: a dead node or a cut branch quiets it.

## Done

Both derelict guardians stand in a played battle and the owner has played one.
Fold the guardian into `ruins-expedition.md` (Occupants) and the units into
`derelict-lore-guide.md`, add a row to `shipped.md`, and delete this story.
