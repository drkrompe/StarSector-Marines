# Rav growth stages

Status: IN PROGRESS — plan steps 1, 2 and 4 built (stage, stats, size, capsule, age mix); the Matriarch's stage table is in place, her maturing is not

Written: 2026-10-21

Updated: 2026-10-21 — step 1 built; per-stage numbers are plain constants in `SwarmStageStats`, as the Brute's are in `BruteChargeRules` (owner moved the Brute off switches), with the two controls kept as switches.

Model: `colony-breach-nouns.md` (Swarm strain: Growth stage, Pack leader; Bore tier; Spawning surge; Harvest).

## Intent

The owner asked whether size could be a strength and variety lever: "a pack
might have younger to elder hounds". It can. Every rav is born at one of three
**growth stages**: whelp, adult or elder. The stages are far enough apart in
size that they read at play zoom, and they differ in markings as well as size.
Stage scales strength, so a pack is a mix the player reads at a glance. The age
mix comes from the bore, so the colonists the hive eats show up as visibly older
ravs. Each pack follows an elder, and killing the elder breaks the pack.

A stage is not a strain. A whelp Runner is a Runner: it has the same body plan,
behaviour, `UnitType` and rig. No new `UnitType` and no new `LayeredArmorFamily`
is added. Both enums are append-only, and a stage is a property of one body,
not a kind of body.

## Scope

- Three stages for every strain: Runner, Brute, Spitter (stats only while its
  art is parked) and Matriarch.
- A stage is issued at spawn through `SwarmStrains`, as the Brute's plate is,
  so a director, a scene and a debug mission spawn the same elder.
- The age mix of a surge is set by the bore's tier and by what it has been fed.
- The pack-leader rule and pack break-up.
- How stage scales the Matriarch.
- Body size against flow fields, capsules and doorways.
- Stage in presentation: scale, palette and markings on the existing rigs, and
  pitch-shifted sound.

Out of scope: maturing in the field (one exception, the Matriarch, below), new
ImageGen art (unless the markings need it after review), new beats, and
balance acceptance (the readings below are starting values).

## Stage table

Linear scale is the stage's drawn size against the adult. Health, armour,
damage and reach rise with stage. Speed falls. The adult row is what ships
today, unchanged. Every other number is a starting value and is behind a
`-Dbattle.swarm.stage.*` switch, as the Brute's charge numbers are.

The scales are 0.80 / 1.00 / 1.22. That is about 25% and 22% apart in length,
and roughly 1.6x in area from stage to stage. Measured at 32 px per cell on
the review sheet, a standing Runner is 40, 50 and 62 px long.

### Runner (adult: 20 HP, no plate, 5 damage, 0.7 s, reach 1.5, 3.2 cells/s)

| Stage | Scale | Health | Armour | Damage | Reach | Speed | Sim radius |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Whelp | 0.80 | 12 | none | 3 | 1.3 | 3.6 | 0.24 |
| Adult | 1.00 | 20 | none | 5 | 1.5 | 3.2 | 0.30 |
| Elder | 1.22 | 34 | light plate, 6 at rating 4 | 8 | 1.8 | 2.9 | 0.37 |

The elder's light plate makes an SMG's spray slide off it and leaves rifles
alone. It is a reason to put aimed fire on the leader, not a second Brute.
The rating is checked against `loadoutMatchup`'s penetration columns before
it ships.

Harvest by stage: a whelp never impales, and it drags a load at 30% of its own
pace. An adult impales one blow in twenty and drags at 40%, as today. An elder
impales one in twelve and drags at 50%. So an elder is the rav most likely to
carry someone off.

### Brute (adult: 40 HP, plate 20 at rating 10, 14 damage, reach 1.7, walk 1.8, charge 4.0, mass 3)

| Stage | Scale | Health | Plate | Damage | Reach | Walk / charge | Mass | Sim radius |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Whelp | 0.80 | 26 | 12 at 8 | 9 | 1.5 | 2.0 / 4.3 | 1.5 | 0.36 |
| Adult | 1.00 | 40 | 20 at 10 | 14 | 1.7 | 1.8 / 4.0 | 3.0 | 0.45 |
| Elder | 1.22 | 64 | 28 at 12 | 20 | 2.0 | 1.6 / 3.7 | 5.4 | 0.49 (drawn 0.55) |

Mass follows volume (scale cubed), so the charge physics needs no rule of its
own. `HullImpact` already takes the Brute's mass as the point mass. At charge
speed the momentum is about 6.5 for a whelp, 12 for an adult and 20 for an
elder. An elder therefore moves a pickup a full cell and staggers a medium mech
that an adult only rocks. A whelp rebounds off an APC and is stunned. The wall
ram scales with momentum against the adult's 50. Infantry throw distance and
knockdown time scale with momentum, and so does the body cap: a whelp's charge
stops on the first body, an elder's goes through three. The light-wall limit
stays a law: no stage of Brute breaks a fortified wall.

The elder Brute's sim radius is capped at 0.49 (see *Clearance*). It is drawn
and shoved at its full 0.55.

### Spitter (adult: 12 HP, 7 acid, reach 14, 2.6 cells/s)

| Stage | Scale | Health | Acid | Reach | Speed |
| --- | --- | --- | --- | --- | --- |
| Whelp | 0.80 | 8 | 5 | 11 | 2.8 |
| Adult | 1.00 | 12 | 7 | 14 | 2.6 |
| Elder | 1.22 | 20 | 10 | 16 | 2.4 |

The acid's penetration against an airframe (a third of skin) does not change
with stage. The air threat ladder is read from durability, and an elder Spitter
must not become armoured air's answer. Its art is parked, so this table waits
for its rig.

### Matriarch

See *The Matriarch's stage* below.

## Age mix: how bores set it

A surge is drawn body by body. Today each body's strain comes from the tier's
table (`BoreStrainTable`). Each body now also draws a **stage**, from a second
table keyed by tier and shifted by the biomass the bore has eaten.

| Bore tier | Whelp | Adult | Elder |
| --- | --- | --- | --- |
| Fissure | 70 | 30 | 0 |
| Bore | 35 | 55 | 10 |
| Maw | 15 | 55 | 30 |

**Feeding ages the brood.** Every body delivered into a bore (the feed seam's
lifetime total, not its fresh biomass) moves four points of weight from whelp
to adult and two from adult to elder. The elder share is capped at 45 and a
share never goes below zero. An unfed fissure throws pale whelps. A bore that
has eaten a dozen colonists throws adults and elders, even at fissure tier.
That is the harvest made visible: the player watches carriers go down a hole,
and the next surge out of it is darker and bigger.

Rules:

- **A stage is fixed at spawn.** A rav does not age during a battle; the only
  exception is the Matriarch.
- **The draw is keyed, not streamed.** The stage roll is keyed by the bore, the
  pulse and the body's index in it, as the impale draw is keyed by the strike.
  Turning stages off then changes no other roll in the battle, so the control
  run differs only by stage.
- **The pulse releases its leader first.** If the pulse holds an elder, the
  first elder is released ahead of the stream, so the river runs behind its
  leader.
- **The population target counts weight, not bodies.** The director counts a
  whelp as 0.6, an adult as 1 and an elder as 1.8 against its target. A fed bore
  then sends fewer, older bodies rather than the same number stronger, so
  feeding raises danger without a runaway in body count. This is the main
  balance lever; the owner chose it over the same count of stronger bodies.
- The director's snapshot carries each bore's stage mix and the stage counts it
  has released. The breach snapshot carries the living count per stage.

## Pack leader

A **pack** is the bodies of one pulse. If the pulse released an elder, the
first one is the pack's **leader**. A pulse with no elder (most fissure
pulses) has no leader and plays as the swarm plays today: every body hunts on
its own.

**While its leader lives, the pack is cohesive.** It gets three things, all
limited to members within eight cells of the leader (the **leash**):

1. **Shared quarry.** Members take the leader's quarry when they have none of
   their own within a few cells, so a pack hits one point instead of spraying
   across the line. This is local pursuit, not new strategic movement; the
   flow field is untouched.
2. **Shared sensing.** A contact any member senses is sensed by the whole pack
   within the leash. This is still sensing (*The swarm senses, it does not
   know*): it is passed body to body, never read from hidden state.
3. **Tempo.** The attack cooldown is 15% shorter. A cohesive pack is visibly
   quicker on the blow.

The leader stays in the surge's stream until contact, so a surge keeps its
lane (*A field makes a stream*).

**When the leader dies, the pack breaks.** This is permanent for that pack:

- shared quarry, shared sensing and tempo end at once;
- members **scatter** for two seconds: each drops its quarry and takes the
  nearest one it senses itself;
- whelps **bolt**: for four seconds every whelp of the broken pack runs back
  down the bore field toward its bore, then turns and hunts again. A whelp
  carrying a load drops it;
- a broken pack does not elect a new leader. Its members may join another
  living leader's pack once they come within that leader's leash; that is how
  two surges merge.

The payoff for the player is readable: shoot the big dark one, and the small
pale ones run. The cost is real, because the elder is the hardest body in the
pack to kill.

Presentation over the rule: the leader's cry on new quarry is the pack's cry
(one `ALERT` for the pack, not one per body). A broken pack is heard as one
scatter shriek, a new cue kind (`SwarmCues.Kind.PACK_BROKEN`), pitched to the
leader's stage. There is no ring and no marker on the leader; its size and
colour are the marker.

`-Dbattle.swarm.packLeaders=false` is the control: packs are never formed and
every body hunts alone. Stages still apply.

## The Matriarch's stage

The Matriarch is the mother bore's heavy strain (`matriarch.md`). It uses the
same three stages under the same names, so no parallel noun is added; radio
may call a whelp Matriarch "young", which is flavour only. The draw is not from
the age table. **Her stage comes from how long the mother bore has been
feeding**: the mother's lifetime biomass when the Matriarch emerges.

| Mother's biomass at emergence | Stage | Scale | Health, plate, damage | Speed |
| --- | --- | --- | --- | --- |
| under 6 bodies | Whelp | 0.88 | 0.70x | 1.10x |
| 6 to 13 | Adult | 1.00 | 1.00x | 1.00x |
| 14 or more | Elder | 1.12 | 1.45x | 0.92x |

Her range of scale is narrower than the small strains'. She is already several
cells long, and 1.22x of that would not fit the colony's streets.

**She matures in the field.** She is the one exception to the fixed-stage
rule: she is fed from her mother. When the mother's lifetime biomass crosses
the next threshold while the Matriarch lives, she steps up one stage on the
spot. Her health keeps its fraction and her size, plate and voice step with
her. The step plays a moult: plates split and darken, and a roar sounds. So
harvest after she emerges makes her worse, and starving the mother keeps her
young. A collapsed mother stops her maturing; it does not kill her.

She leads every pack within her leash, elders included: an elder that comes
within her leash defers to her. Killing her breaks every pack she leads at
once.

## Clearance: body size against flow fields and capsules

There are three different questions here. Each keeps its own answer.

1. **Navigation: where a body can go.** The shared swarm flow fields are
   derived from walkable cells, which is a one-cell clearance: a body whose
   sim radius is under half a cell passes every door, barricade gap and
   one-cell alley. Every stage of Runner, Brute and Spitter keeps its sim
   radius at **0.49 or less**, so one field per goal still serves every body.
   The only stage that would cross that line is the elder Brute (natural
   0.55). It is capped at 0.49 for navigation, the spatial index, ballistics
   and blast catch, and it is drawn and shoved at full size. The alternative,
   a second field family at two-cell clearance, would double the field cost
   for one body type, and is rejected. An elder Brute squeezes through a
   doorway it visibly fills (owner, 2026-10-21); it does not widen the
   opening.
2. **Separation: how bodies crowd.** The capsule (`BodyCapsule`) is the
   crowding shape. Its half-length, radius and forward offset scale linearly
   with stage. The Runner's capsule, 0.52 / 0.23 / 0.10 today, becomes 0.42 /
   0.18 / 0.08 for a whelp and 0.63 / 0.28 / 0.12 for an elder. `BodyCapsule`
   is keyed by `UnitType` today, so this needs a per-body scale input read from
   the stage. The capsule soft-collision session is changing that class now:
   land this after it and coordinate through it. Surging bodies stay exempt
   from separation as the field law requires.
3. **The Matriarch.** She is multi-cell and never descends the shared field.
   She paths for herself on the clearance routes vehicles use
   (`ClearanceRoutePlanner`), at a clearance radius taken from her stage. She
   is one body, so a per-body route costs nothing that matters.

`UnitType.radius` stays the adult's value. The stage's scale is applied per
body, wherever the radius is read for that body.

## Presentation

### Size, palette and markings

- **Scale:** the rig draws at stage scale (`RavRunnerRig` / `RavBruteRig`
  take a scale; bone lengths, stance width and stride scale with it). Gait
  selection already runs off speed against leg length, so a whelp's legs
  cycle faster and an elder's lope is longer.
- **Whelp:** pale. Plates lifted toward a pale lilac and slightly
  desaturated, the sinew pinker. Spike arms are nubs, 0.55x long and a little
  thicker.
- **Adult:** the shipped art.
- **Elder:** dark. Plates go toward the Brute's old plum (the Brute's approved
  palette already darkens with age, so the family agrees), but the **spine
  ridge stays pale** (owner, 2026-10-21) so an elder stands off the creep.
  Healed scars run as pale raised seams across the carapace. Spike arms are 1.30x long, and the
  bone is slightly yellowed.
- For the Brute, the same three moves apply on its own palette: a whelp is a
  paler, lilac-plum calf with a stub ram-cap, an elder is near-black plum with
  scars and a heavier mace.
- **Production:** the export scripts (`export_runner_rig.py`,
  `export_brute_rig.py`) write one bone-sprite set per stage, with the recolour
  and the scar overlay baked in. That costs two more sprite sets per strain and
  no shader work. ImageGen is needed only if review asks for markings that a
  recolour cannot make, for example a different spike silhouette.

What the look check measured (sheet 01, 32 px per cell):

- Size and value carry the stage. Whelp L 175, adult L 136 and elder L 84
  (mean luminance of the opaque body) separate clearly from each other.
- Spike length does **not** read at play zoom while the arms are folded. It
  shows only in a strike. The stage's identity has to rest on size and value.
- The elder (L 84) sits close in value to the creep (L 94). It separates by its
  magenta sinew, its shadow and its size. The owner chose to keep its spine
  ridge pale as well; with the ridge (round 2) the elder measures L 105,
  above the creep.
- On paving (L 123), every stage reads.

### Sound

The audio side already jitters pitch on every emit. Stage multiplies into
that: **pitch is the inverse of linear scale**, 1.25x for a whelp (about +4
semitones), 1.00x for an adult and 0.82x for an elder (about −3.4 semitones).
Volume is 0.85x for a whelp, 1.00x for an adult and 1.15x for an elder. This
applies to every swarm sound a body makes: cry, strike, pain, death and
footfall. A whelp pack chitters high and light, and an elder's footfalls are
deeper and further apart because its stride is longer. The Brute's charge
sounds pitch the same way, so an elder charge is heard as a deeper thunder.
`SwarmAudio` reads the body's stage when it picks the clip. No new clips are
generated: one take pitched three ways stays recognisably the same creature,
which is the point. The Matriarch's moult roar is new: its voice was rejected
and waits for her design.

## Plan (when the owner says go)

1. A stage component on the entity, issued by `SwarmStrains.spawn(..., stage)`
   with stat scaling. Unit tests on the scaling functions only.
2. The age table and feeding shift, keyed draw, leader-first release, weighted
   population target, snapshot fields, `-Dbattle.swarm.growthStages=false`.
3. Pack formation, leash, cohesion bonuses and break, `PACK_BROKEN` cue,
   `-Dbattle.swarm.packLeaders=false`. Behaviour scene `pack-leader`: its
   verdicts assert the mechanic (members share the leader's quarry; within a
   tick of the leader's death the bonuses are gone; whelps head boreward),
   never who wins.
4. Capsule scale input (after the capsule session lands), the sim-radius cap.
5. Rig scale and per-stage sprite sets; snapshot suite rows for each stage.
6. `SwarmAudio` pitch and volume by stage.
7. The Matriarch's stage and maturing, when `matriarch.md` lands.
8. Balance reading: `commanderEvidence -Pmission=colony-breach` against the
   `growthStages=false` and `packLeaders=false` controls, in the same run.

## Owner decisions (2026-10-21)

- **Weighted population.** A fed bore sends fewer, older ravs: the director
  counts stage weight, not bodies.
- **Elder on creep.** Dark plum with a pale spine ridge.
- **Elder Brute at doors.** It squeezes through; no wall breaking for it.
- **Whelps bolt** for four seconds when their leader dies.
