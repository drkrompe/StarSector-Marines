# Body-part cover

Status: ACTIVE — exposure, lane props, part consequences and small-arms wear shipped; area exposure and per-part armour remain.

Updated: 2026-10-15 — three slices shipped and folded; intent recorded that prepared positions are meant to be dangerous.

Written: 2026-10-15

Cover today is one number. A body behind cover of level 1, 2 or 3 has a
15/30/45% chance of having a round that reached it stopped in the cover, and a
round that gets through still has its damage cut by the same 15/30/45%. That is
legible but flat: a sandbag wall and a trench at the same level protect a man
identically, the protection is counted twice, and nothing about *where* a man
was hit can ever matter.

This direction replaces the one number with **what cover hides of the man**.

**A prepared position is meant to be dangerous to assault.** With parts, the
rounds that get past cover land mostly on the head and arms, the head is worth
more, and a man firing from a trench is a hard target that hits back at full
strength. Riflemen walking at a dug-in line are supposed to lose. The answer to
a prepared position is what the attacker brings — heavier loadouts and
equipment, heavy and area weapons, smoke, flanking to strip the cover's
bearing, closing to where a trench hides nothing — not a softer cover table.
Measured on the Conquest trial ground, the two shipped slices together moved
the riflemen-only control from a narrow win to a timeout and raised marine
losses by about a fifth; that shift is the design working, and balance is
pursued through the attacker's tools.

**Stacked cover is not independent.** A prop in the lane and cover at the body
both hide mostly the legs and torso, so behind both a man is less protected
than two independent rolls would make him: what the first misses is mostly
head and arms, which the second hides least of. That is physical — two low
walls do not hide a head — and is kept rather than corrected.

## The model

An infantry body is four **body parts**, each with an authored **share** of the
silhouette a round can land on:

| Part | Share (starting value) |
|---|---|
| Head | small (0.10) |
| Arms | medium (0.20) |
| Torso | large (0.40) |
| Legs | large (0.30) |

A cover feature has a **cover profile**: for each part, the fraction of it the
feature hides from a shooter at full distance. A trench at range hides the legs
and torso almost entirely, the arms partly and the head barely; a waist-high
sandbag wall hides the legs and some of the torso; a low fence hides the legs.

A direct round against a body resolves progressively:

1. **Did it hit?** The existing target-plane aim, unchanged. Accuracy,
   training, smoke, spread and incoming-accuracy modifiers all act here.
2. **Which part?** The part at the height the round meets him. Parts are
   stacked up the body in bands as tall as their shares, and the committed aim
   lands uniformly up the silhouette, so the head is already a small target
   before cover enters into it. No extra roll is spent: the aim already
   decided where the round lands.
3. **Was that part hidden?** A roll against the profile's coverage of that part,
   scaled by distance. A hidden part means the round stopped in the cover.

So a man in a trench is hard to kill because a shooter can only reach his head
and shoulders, not because of a hidden percentage. An incidental victim — a man
a stray round crosses — is struck on whatever part is at the round's height
there.

A prop crossed in the lane short of the man catches by the same part: a round
headed for his legs meets a crate's crossed profile for legs, so a low crate in
the lane catches leg and torso shots and lets head shots pass, while stopping as
many rounds in all as its flat chance did. The part is the one the aim landed
on, not one read from the round's drawn height at the prop: that height is
tight around the point of aim, so the drawn impact and the prop's own
catch-height band do not yet follow the part. Making them follow needs the aim
pattern to span the whole silhouette, and is left to the profiles-from-geometry
step below. A round headed for no part — a miss, point aim, a partless target —
meets a crossed prop at the flat chance by level.

**Cover is counted once.** A round that reaches an exposed part has beaten the
cover; it does not also have its damage cut. The flat damage reduction remains
only for payloads that have no part — area payloads and unattributed damage —
until area exposure replaces it (below).

**Distance still matters, the way it does today.** Edge cover keeps its muzzle
ramp: a shooter right behind cover is not caught by it. A trench keeps its
shooter-distance ramp: a shooter standing over the lip sees the whole man. Both
ramps scale the profile's coverage, so close in, every part is exposed.

**A body plan says what parts a body has.** A unit type declares a body plan
or none. The plan names its parts and stacks their height bands; each part
carries its share, its damage multiplier, its cover columns (edge, occupant and
crossed, by level) and its consequence. The four parts above are the infantry
plan, which every kind of person has. Band order and part order are separate,
so a later plan can stack sections without reshuffling its tables.

**People and mechs have parts.** A mech's plan is its sections' height bands
(`mechs-nouns.md`, Section), a second plan through the same seam, not a
second hit system. Vehicles, emplacements, aircraft and creatures have no plan,
remain one whole-body contact and keep the flat model: vehicles and aircraft
take no terrain cover, and the rest keep the flat catch and damage cut by level.

## Shipped and open

- **Part consequences** (shipped). A part decides what a hit does: a head hit
  is worth more damage (×1.6), a limb hit less (arms ×0.75, legs ×0.85), the
  torso a little more (×1.1), so the share-weighted mean over an uncovered body
  is one and only cover — which leaves the head and arms to hit — shifts the
  average. A limb hit that removes structure leaves a wound: each leg wound
  slows a man ×0.85 and each arm wound costs him ×0.85 accuracy, stacking to a
  floor of ×0.6, each as a named unit modifier (`ai-nouns.md`, Unit
  modifiers), never as a branch at the read site. A medic's completed
  treatment dresses them. Still open here: the medic choosing a patient
  *because* of a wound (today he chooses on health and armour, and a wound
  needs structure lost, so the wounded are usually also the hurt), wound
  presentation on the body, and AI reading wounds.
- **Mech plans** (shipped). A mech chassis has a body plan too: four height
  bands (legs, torso, shoulders, head) whose parts are its sections' bands
  (`mechs-nouns.md`), with a multiplier of one. Their cover rows read from the
  ground up (the plan's `coverFromGround`): cover hides a mech's legs first and
  never its shoulders or head, and the man-sized catch band is not consulted.
- **Area exposure.** A blast asks which parts it can reach rather than rolling
  a hit: a shell bursting outside a trench reaches only what shows above it, a
  grenade landing in the trench reaches everything. That retires the last use of
  the flat damage reduction and makes a trench the right answer to shelling and
  the wrong answer to a grenade.
- **Small arms wearing cover down** (shipped, `small-arms-wear.md`). A round
  that stops in a crate, a sandbag or a wall names that feature, and its
  payload wears it on arrival through the map-structure channel, by its
  penetration against the feature's material rating
  (`combat-durability-nouns.md`). Rifles chew crates and fences, wear sandbags
  slowly and leave masonry standing; anti-materiel rounds open walls. The
  resolver stays read-only; the write is serial at arrival.
- **Per-part armour.** Armour stays one layer over the whole body; the part
  multiplier acts on the payload before it. Per-part armour waits for armour
  patterns that want to say helmet versus plate.
- **Profiles from geometry.** Profiles start as a short table keyed by cover
  level and by kind (edge cover, a feature crossed in the lane, a trench's
  occupant). Deriving them from a prop's authored height is possible later, but
  a table is the right first surface: it is tunable without re-authoring art.
- **AI reading exposure.** Tactical scoring keeps reading cover levels, which
  stay ordered by how much they hide, so position choice remains sensible. It
  may later score expected exposure directly.

## Laws

- Accuracy decides whether a round reaches the man; cover decides only which of
  him it can reach. Cover never becomes an accuracy penalty again.
- A part is decided once per body contact, and the round's height agrees with it.
- One interception per body contact: the part's coverage roll is the only cover
  roll a body contact makes.
- A body part is never a second hidden hit roll on the intended target: a round
  that hits and reaches an exposed part lands.
- Part shares, part multipliers and cover profiles are data, held by the body
  plan's parts and read in one place; nothing else re-derives them.
- A wound is a unit modifier, and needs structure removed: a hit the armour
  absorbed whole never reached the man.

## Boundaries

`ballistics-nouns.md` owns the contact walk this extends.
`combat-durability-nouns.md` owns what a landed payload does to armour and
structure; part consequences arrive there as payload properties, not as
resolver branches. `ai-nouns.md` owns unit modifiers, through which wound
effects will act.
