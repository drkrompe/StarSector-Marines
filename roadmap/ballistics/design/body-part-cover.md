# Body-part cover

Status: DRAFT — direction agreed in conversation; first slice is `body-part-exposure.md`.

Written: 2026-10-15

Cover today is one number. A body behind cover of level 1, 2 or 3 has a
15/30/45% chance of having a round that reached it stopped in the cover, and a
round that gets through still has its damage cut by the same 15/30/45%. That is
legible but flat: a sandbag wall and a trench at the same level protect a man
identically, the protection is counted twice, and nothing about *where* a man
was hit can ever matter.

This direction replaces the one number with **what cover hides of the man**.

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
2. **Which part?** A roll weighted by part share. The head is already a small
   target before cover enters into it.
3. **Was that part hidden?** A roll against the profile's coverage of that part,
   scaled by distance. A hidden part means the round stopped in the cover.

So a man in a trench is hard to kill because a shooter can only reach his head
and shoulders, not because of a hidden percentage. The round's height follows
the part — a leg shot flies low and a head shot high — so a low crate in the lane
catches leg shots and not head shots, and the impact is drawn where the part is.

**Cover is counted once.** A round that reaches an exposed part has beaten the
cover; it does not also have its damage cut. The flat damage reduction remains
only for payloads that have no part — area payloads and unattributed damage —
until area exposure replaces it (below).

**Distance still matters, the way it does today.** Edge cover keeps its muzzle
ramp: a shooter right behind cover is not caught by it. A trench keeps its
shooter-distance ramp: a shooter standing over the lip sees the whole man. Both
ramps scale the profile's coverage, so close in, every part is exposed.

**Hulls have no parts.** Mechs, vehicles, emplacements and aircraft remain one
whole-body contact that takes no terrain cover, as they do today. A mech
hit-location model is a separate question and is not assumed here.

## Where this goes after the first slice

- **Part consequences.** A part decides what a hit does: a head hit is worth
  more damage, a limb hit less. A leg wound slows a man and an arm wound costs
  him accuracy, each as a named unit modifier (`ai-nouns.md`, Unit modifiers),
  never as a branch at the read site. This is what gives the battlefield medic
  something specific to treat.
- **Area exposure.** A blast asks which parts it can reach rather than rolling
  a hit: a shell bursting outside a trench reaches only what shows above it, a
  grenade landing in the trench reaches everything. That retires the last use of
  the flat damage reduction and makes a trench the right answer to shelling and
  the wrong answer to a grenade.
- **Per-part armour.** Armour stays one layer over the whole body until part
  consequences exist and armour patterns want to say helmet versus plate.
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
- Part shares and cover profiles are data, read in one place; nothing else
  re-derives them.

## Boundaries

`ballistics-nouns.md` owns the contact walk this extends.
`combat-durability-nouns.md` owns what a landed payload does to armour and
structure; part consequences arrive there as payload properties, not as
resolver branches. `ai-nouns.md` owns unit modifiers, through which wound
effects will act.
