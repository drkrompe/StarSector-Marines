# Runner impale and drag

Status: PROPOSED — design only, open questions below for the owner; builds on `swarm-harvest.md` (the carrier) and cannot start before it

Written: 2026-10-21

Model: `colony-breach-nouns.md` (Harvest, Swarm strain, The feed seam, Bore).
Art and the motion study (blades swept back at rest, the forward strike, the
jaws, the drag pose) belong to the Rav redesign art session
(`art-source/rav-redesign/`); this story owns only the rule.

## Why

The redesigned Runner carries long arm blades swept back along its flanks and a
heavy jaw. The owner: it "might be able to impale and drag / carry off victims".
Harvest already says a Runner seizes what it brings down and drags it to a bore,
"and in the end Marines". This story is that last step, with one difference
that makes it a fight instead of a funeral: **the Marine is still alive while
the Runner drags them away**, so their squad can get them back.

## What the battle has today

Read before planning; these are what the rules lean on.

- **No downed state.** A Marine is alive or dead (`DamageResolver`, hp at or
  below zero). The only lasting non-fatal harm is the limb wound
  (`WoundService`), which a medic dresses. `squad-medic.md` put "a downed
  state" out of scope on purpose, and this story keeps it out: an impaled Marine
  is a living Marine who is being held, not a new kind of casualty.
- **The Runner's strike** (`SwarmPressureBehavior`): 5 damage every 0.7 s at
  1.5 cells, zero penetration, through the ordinary damage path. Plated Marines
  soak most of it into armor, so structure falls slowly.
- **Medics** (`SquadMedicSystem`) treat living squadmates in reach: structure,
  armor and limb wounds. They never carry anyone.
- **Morale** is per fire team (`SquadMoraleSystem`): a hit drains a little, a
  death in the team drains 0.30.
- **Casualties** leave the battle as surviving or fallen soldier ids;
  `CasualtyFate` rolls each fallen one into KIA, WIA or MIA afterwards.
- **Harvest** is not built past `SwarmBores.feed`. There is no carrier yet.

## Vocabulary

No new strain and no new casualty kind. Two words, both fitted to Harvest:

- **Impale.** A Runner's strike that seizes a *living* Marine instead of
  wounding them. The Runner becomes a **carrier** in Harvest's sense, and the
  Marine becomes its load.
- **Impaled.** The state of a Marine held by a carrier: alive, out of the
  fight, moving with the carrier. It ends one of three ways: **freed** (the
  carrier dies), **taken** (the carrier reaches a bore), or **killed** (the
  Marine dies while held).

## Rules

### 1. When an impale happens

A Runner's strike that lands on a Marine impales them instead of doing its
damage when **all** of these hold:

- the Marine is **infantry**: not a power suit, not mech or vehicle crew;
- after the blow, the Marine's structure would be **at or below a third** of
  its maximum, and the Marine would still be alive;
- the Marine is **alone**: no other living Marine within 3 cells. A Marine in
  their fire team's shadow can be cut but not taken;
- the Runner is not already carrying, and at least one **living bore** exists
  (no impales during seepage or after the last bore collapses: there is nowhere
  to go);
- a seeded draw passes: **20%** per qualifying strike.

So whether it can happen is something you can see and act on (a hurt marine on
their own near a living bore), while the draw keeps it from being certain.
Several Runners on one isolated, hurt Marine will usually take them within a
few seconds. A squad that keeps together almost never loses anyone this way.

The draw is keyed by the strike (tick, Runner, Marine) rather than taken from
the shared random stream, so turning impales off changes no other roll in the
battle.

### 2. The impaled Marine

- Alive, with the structure and armor they had before the blow (the impaling
  blow does no damage of its own). They do not move, fire, take orders or count
  toward their squad's fighting strength while held.
- Held Marines take no further damage from the swarm; other swarm bodies
  ignore them as prey, because they already belong to the carrier.
- Drawn on the carrier, run through on its blades (the art session's drag
  pose), through Harvest's one-more-attached-sprite seam.

### 3. Rescue

- **Kill the carrier.** It is an ordinary 20 HP Runner, so one or two hits
  from any rifle. When it dies the Marine is **freed**: dropped where the
  carrier fell, alive, back under orders after a **3-second** recovery, with a
  limb wound in each leg (the blades went through). A medic then treats them as
  any wounded squadmate.
- **The victim's squad shoots the carrier first.** A carrier holding one of a
  squad's own Marines is that squad's priority target while it is in sight.
  That is a target-priority bump, not a new order: the squad does not break
  formation to chase. Running after a carrier is the player's call, made with
  an ordinary move order.
- Shots at the carrier resolve against the carrier alone; the held Marine is
  not hit by them.

### 4. Where the carrier goes, and what happens there

- The carrier drags its Marine down the **bore** flow field to the nearest
  living bore, exactly as Harvest drags a colonist.
- If that bore collapses on the way, it turns for the next nearest. If none is
  left, it drops the Marine where it stands and the Marine is freed.
- On reaching the bore's mouth the Marine is **taken**: removed from the battle
  and delivered through `SwarmBores.feed` as **one body** of biomass, the same
  as a colonist. The bore grows and its next surge comes forward; nothing else
  is special about a Marine being fed.
- A Marine still held when the battle ends counts as taken.

### 5. Morale, medics and the casualty count

- **Morale.** The impale itself costs the victim's fire team the drain of a
  death (0.30), at the moment of the impale, once. Being freed gives nothing
  back and being taken costs nothing more: the team has already reacted.
- **Medics** never approach a held Marine and never chase a carrier. A freed
  Marine is an ordinary patient.
- **Casualty count.**
  - Freed: a survivor, like anyone else alive at the end.
  - Killed while held: fallen, rolled by `CasualtyFate` as usual.
  - Taken: fallen with a **fixed MIA** fate, no roll and no sick-bay save. The
    body was never recovered, which is what MIA already means in
    `personnel-nouns.md`. The outcome needs a third list beside surviving and
    fallen (taken soldier ids) so the resolver can skip the roll.

### 6. What it costs the Runner

- A carrier **cannot strike**. It fights back only in melee if attacked, as
  Harvest's carriers do (that is already Harvest's rule; this inherits it).
- It moves at Harvest's carrier speed; this story assumes **40% of a Runner's
  speed**, slower than a walking Marine, so a squad that moves can catch it.
- It carries one Marine at most, and it drops out of its surge's stream.

## Beats

Added to the beat table in `colony-breach-nouns.md` when the story ships:

| Beat | Audience | When |
| --- | --- | --- |
| `marine_impaled` | PLAYER | A Runner impales a Marine |
| `marine_freed` | PLAYER | A held Marine's carrier dies |
| `marine_taken` | PLAYER | A carrier delivers a Marine into a bore |

Radio lines for them ("They've got Okafor!") are `breach-audio.md`'s.

## Scope

1. The impale check in the Runner's strike, the impaled state on the Marine,
   and the carrier hookup from `swarm-harvest.md`.
2. Freed, taken and killed-while-held endings; the taken list in the mission
   outcome and its fixed MIA fate.
3. Carrier priority in squad target selection.
4. Morale drain on impale; medics skip held Marines.
5. The three beats.
6. `-Dbattle.colonyBreach.impale=false` as the control: strikes resolve as
   today.
7. Evidence: a behaviour scene with two loops. In the first, a hurt Marine
   alone near a bore is impaled and dragged and the squad does not reach them:
   verdicts that the impale happened only under the rule's conditions, that the
   carrier did not strike while carrying, and that a delivered Marine is
   reported taken and fed the bore. In the second the squad shoots the carrier:
   verdicts that the Marine was freed alive with two leg wounds and rejoined.
   Mechanics only; who wins goes in the metrics.

## Acceptance

- Impales happen only to hurt, isolated infantry, near a living bore.
- Killing the carrier always frees a living Marine.
- A taken Marine writes back MIA without a roll.
- Deterministic; the control switch changes no other roll; unit tests on the
  impale check and the outcome mapping each run under a second.

## Open questions for the owner

1. **Taken fate: MIA or KIA?** This draft says MIA (never recovered). KIA is
   bleaker and more honest about what a bore does.
2. **Who can be impaled?** This draft says Marines only. Militia and colonists
   could be too, but Harvest already takes them once they are dead, and
   impaling them alive only matters if someone would come for them.
3. **Is "alone within 3 cells" the right rule?** It makes the lesson "keep
   together" and keeps impales rare. Without it, a Runner pack could take
   Marines out of a fighting line.
4. **Should power suits be immune?** This draft says yes (too heavy to carry).
   A Brute could be the strain that carries a suit, as a later rule.
5. **Should the victim's squad chase on its own?** This draft only makes the
   carrier a priority target; chasing is the player's order. A short automatic
   pursuit would save more Marines, but it pulls squads off their tasks.
6. **Does a held Marine get hurt?** This draft says no, so the only clock is
   the distance to the bore. Bleeding while held would need a damage-over-time
   rule the ground model does not have.
