# Runner impale and drag

Status: READY — Harvest's carrier is on main (`SwarmHarvest`); design settled with the owner

Written: 2026-10-21

Updated: 2026-10-21 — owner rule: direct fire never hurts a held body, area weapons do behind a switch.

Model: `colony-breach-nouns.md` (Harvest, Swarm strain, The feed seam, Bore).
Art and the motion study (blades swept back at rest, the forward strike, the
jaws, the drag pose) belong to the Rav redesign art session
(`art-source/rav-redesign/`); this story owns only the rule.

## Why

The redesigned Runner carries long arm blades swept back along its flanks and a
heavy jaw. The owner: it "might be able to impale and drag / carry off victims".
Harvest already says a Runner seizes what it brings down and drags it to a bore.
This story adds the version that makes it a fight instead of a funeral: **the
victim is still alive while the Runner drags them away**, so somebody can get
them back. The picture the owner wants is a melee where some Runners keep
attacking while others are already hauling people off.

## What the battle has today

Read before planning; these are what the rules lean on.

- **No downed state.** A body is alive or dead (`DamageResolver`, hp at or
  below zero). The only lasting non-fatal harm is the limb wound
  (`WoundService`), which a medic dresses. `squad-medic.md` put "a downed
  state" out of scope on purpose, and this story keeps it out: an impaled body
  is a living body that is being held, not a new kind of casualty.
- **The Runner's strike** (`SwarmPressureBehavior`): 5 damage every 0.7 s at
  1.5 cells, zero penetration, through the ordinary damage path.
- **Medics** (`SquadMedicSystem`) treat living squadmates in reach: structure,
  armor and limb wounds. They never carry anyone.
- **Morale** is per fire team (`SquadMoraleSystem`): a hit drains a little, a
  death in the team drains 0.30.
- **Casualties** leave the battle as surviving or fallen soldier ids;
  `CasualtyFate` rolls each fallen one into KIA, WIA or MIA afterwards.
- **Harvest** (`SwarmHarvest`): a Runner that kills a body seizes it and drags
  the corpse to the nearest living bore at 40% pace; a dead carrier drops it
  loose for the next Runner. Carriers do not strike.

## Vocabulary

No new strain and no new casualty kind. Two words, both fitted to Harvest:

- **Impale.** A Runner's strike that seizes a *living* body instead of wounding
  it. The Runner becomes a **carrier** in Harvest's sense, and the body its
  load.
- **Impaled.** The state of a body held by a carrier: alive, out of the fight,
  moving with the carrier. It ends one of three ways: **freed** (the carrier
  dies), **taken** (the carrier reaches a bore), or **killed** (the body dies
  while held, from something else).

## Rules

### 1. When an impale happens

It is **chance, and nothing else about the fight**. Any Runner strike that
lands can impale its target instead of doing its damage, on a seeded draw of
**5% per strike**, when:

- the target is a **living infantry body the swarm hunts**: a Marine, a
  militia fighter, or a colonist. Not a power suit (too heavy for a Runner),
  not a mech, vehicle or emplacement, and not a cohort evacuee while their
  escort lives (Harvest's law);
- the Runner is not already carrying;
- at least one **living bore** exists. No impales during seepage or after the
  last bore collapses: there is nowhere to take anyone.

So a pack in contact mostly fights and now and then one of them comes away
with someone on its blades. Health, isolation and armor do not matter, so a
fresh Marine in the middle of the line can be taken; the 5% is the number to
tune once the scene shows how often that reads.

The draw is keyed by the strike (tick, Runner, target) rather than taken from
the shared random stream, so turning impales off changes no other roll in the
battle.

### What the strike looks like

The owner's revised Runner has short, incised arm blades as well as its jaws
and clawed forearms. **Which of them a strike shows is presentation, not
rule.** A Runner has one strike, with one damage figure and one impale draw:

- an ordinary strike is drawn as a lunging chomp or a forearm swipe;
- an impale is drawn as one blade or both striking forward and going in, then
  reeling the victim back to the jaws before the drag-off begins.

The renderer picks the variant from a seeded draw keyed like the impale draw,
so replays look the same. Splitting bite and swipe damage, or giving a
two-blade impale its own odds, would mean two rules a player cannot tell apart
on screen, so this story doesn't do it.

### 2. The impaled body

- Alive, with the structure and armor it had before the blow (the impaling
  blow does no damage of its own), and **it takes no harm while held**: there
  is no bleed and no clock except the distance to the bore. It does not move,
  fire, take orders or count toward its squad's fighting strength.
- Other swarm bodies ignore it as prey, because it already belongs to the
  carrier.
- Drawn on the carrier, run through on its blades (the art session's drag
  pose), through Harvest's one-more-attached-sprite seam.

### 3. Rescue

- **Kill the carrier.** It is an ordinary 20 HP Runner, so one or two hits
  from any rifle. When it dies the body is **freed**: dropped where the carrier
  fell, alive, and back to what it was doing after a **3-second** recovery,
  with a limb wound in each leg (the blades went through). A Marine goes back
  under orders and a medic treats them as any wounded squadmate; a militia
  fighter goes back to its post; a colonist flees again.
- **A carrier holding one of a squad's own Marines is that squad's priority
  target** while it is in sight. That is a target-priority bump only. Squads do
  not chase carriers on their own: going after one is the player's call, made
  with an ordinary move order.
- **Direct fire never hurts the held body** (owner, 2026-10-21). Marines and
  militia can pour fire at a carrier without killing the person on its blades:
  a shot aimed at the carrier, a miss that strays into the held body, and a
  round that would pass through the carrier into it all leave it untouched.
  This holds for Marines, militia and colonists alike.
- **Area weapons still can** (grenades, mortars, rocket blasts, air-delivered
  ordnance). Calling a blast down on a carrier is a real choice with a real
  cost. The owner said "maybe" here, so it is a switch,
  `-Dbattle.colonyBreach.impale.areaHitsHeld` (default `true`), and an open
  question below.

**How the hit is kept off.** Direct fire is a ray traced against every body it
crosses (`BallisticResolver.trace`); that is what lets a miss hit a bystander
or a round go through one body into the next. The trace's candidate loop
already drops bodies that cannot be hit (the shooter itself, a carrier body
that is not present or airborne, civilians to a Marine-side shooter); a held
body joins that list, so it can be neither the locked target, nor an
incidental hit, nor the body behind a penetration. The ray simply passes it.
The held body stays in the spatial index and the roster, because occupancy and
everything else still need to see it. Blasts are resolved separately
(`Detonations.detonate` walks every body in the radius), so area damage reaches
a held body unless the switch is off, in which case that walk skips it the same
way.

### 4. Where the carrier goes, and what happens there

- The carrier drags its load down the **bore** flow field to the nearest
  living bore, exactly as Harvest drags a corpse.
- If that bore collapses on the way, it turns for the next nearest. If none is
  left, it drops its load where it stands and the body is freed.
- On reaching the bore's mouth the body is **taken**: killed and delivered
  through `SwarmBores.feed` as **one body** of biomass, the same as a
  harvested corpse. The bore grows and its next surge comes forward.
- A body still held when the battle ends counts as taken.

### 5. Morale, medics and the casualty count

- **Morale.** Impaling a Marine costs their fire team the drain of a death
  (0.30), at the moment of the impale, once. Being freed gives nothing back and
  being taken costs nothing more: the team has already reacted.
- **Medics** never approach a held Marine and never chase a carrier. A freed
  Marine is an ordinary patient.
- **Casualty count.**
  - Freed: alive, like anyone else alive at the end.
  - Killed while held: fallen, rolled by `CasualtyFate` as usual.
  - Taken: a Marine is fallen with a **fixed KIA** fate, no roll and no
    sick-bay save. The outcome needs a list beside surviving and fallen (taken
    soldier ids) so the resolver can skip the roll. Taken militia and colonists
    are simply dead, and the breach snapshot counts them with the colony's
    losses.

### 6. What it costs the Runner

- A carrier **cannot strike**. It fights back only in melee if attacked, as
  Harvest's carriers do (Harvest's rule, inherited).
- It moves at Harvest's carrier speed; this story assumes **40% of a Runner's
  speed**, slower than a walking Marine, so a squad that is sent after it can
  catch it.
- It carries one body at most, and it drops out of its surge's stream.

## Beats

Added to the beat table in `colony-breach-nouns.md` when the story ships:

| Beat | Audience | When |
| --- | --- | --- |
| `marine_impaled` | PLAYER | A Runner impales a Marine |
| `marine_freed` | PLAYER | A held Marine's carrier dies |
| `marine_taken` | PLAYER | A carrier delivers a Marine into a bore |

Militia and colonist impales publish no beat; the breach snapshot counts them.
Radio lines ("They've got Okafor!") follow `colony-breach-nouns.md` (Sound); the impale and drag sounds are wired, so a carrier posts `SwarmCues.Kind.IMPALE` and `DRAG`.

## Scope

1. The impale draw in the Runner's strike, the impaled state, and the carrier
   hookup from Harvest (`SwarmHarvest`).
2. Freed, taken and killed-while-held endings for Marines, militia and
   colonists; the taken list in the mission outcome and its fixed KIA fate.
3. Carrier priority in squad target selection.
4. Morale drain on impale; medics skip held Marines.
5. The three beats, and impale / freed / taken counts in the breach snapshot.
6. `-Dbattle.colonyBreach.impale=false` as the control: strikes resolve as
   today.
7. Evidence: a behaviour scene with two loops. In the first a Runner pack
   meets a squad near a bore and nobody goes after the carriers: verdicts that
   impales happened only on eligible targets with a bore open, that a carrier
   never struck while carrying, and that a delivered Marine is reported taken
   (KIA) and fed the bore. In the second the squad shoots a carrier: verdicts
   that the Marine was freed alive with two leg wounds and rejoined. Mechanics
   only; impale counts and who won go in the metrics.

## Acceptance

- Impales happen only on eligible targets while a bore is open, never to power
  suits, never to an escorted evacuee.
- Killing the carrier always frees a living body.
- No direct-fire round, aimed, stray or penetrating, ever damages a held
  body; a blast does unless `areaHitsHeld` is off.
- A taken Marine writes back KIA without a roll.
- Deterministic; the control switch changes no other roll; unit tests on the
  impale draw and the outcome mapping each run under a second.

## Open question for the owner

- **Should area weapons hurt a held body?** Default yes (a blast on a carrier
  costs you the victim too); `-Dbattle.colonyBreach.impale.areaHitsHeld=false`
  is the other answer, ready to flip.

## Later, not in this story

- **A Brute that carries a power suit.** Suits are immune to Runners; letting
  the heavier strain take one would be its own rule and its own story.
