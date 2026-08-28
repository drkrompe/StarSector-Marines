# Combat durability

Status: ACTIVE — armor-and-structure resolution is live; decision, evidence, and tuning follow-ons remain.

Written: 2026-08-23

Updated: 2026-08-24 — distinguished live resolver truth from transitional decision, morale, telemetry, and presentation readers.

## Purpose

Combat durability is the battle-owned model for how a damaging attack removes
an actor's protection and ultimately defeats it. It gives infantry armor,
mech plating, turrets, and future vehicles one shared vocabulary without
making those platforms share progression, chassis, or loadout authority.

The model separates **how much punishment an actor can survive** from **which
weapon is efficient against its protection**. That distinction is the main
balance seam: a Bulwark may carry both more armor and harder armor than a
Sirocco, while an anti-materiel rifle may defeat that armor efficiently without
needing an arbitrary bonus against every unit classified as hardened.

## Vocabulary

- **Structure** — the live pool whose exhaustion defeats the actor. It is
  biological health for infantry, hull integrity for mechs and vehicles, and
  structural integrity for emplacements. Existing battle `HEALTH` storage may
  retain its implementation name while carrying this meaning.
- **Armor** — an optional live protection pool in front of structure. An actor
  with no armor capability takes structure damage directly.
- **Armor rating** — the resistance of the current armor to ordinary weapon
  damage. Rating is not another pool and does not change accuracy.
- **Penetration** — a property of a damaging weapon or attack describing how
  efficiently it removes armor. It replaces target-type damage multipliers;
  it does not increase damage against exposed structure.
- **Armor efficiency** — the fraction of a hit's post-cover damage that can
  remove armor for a particular penetration/rating pairing.
- **Armor break** — the one-time transition from positive armor to no armor.
  It is a simulation event that may drive morale, AI, telemetry, audio, and
  presentation; those consumers do not independently infer the transition.
- **Durability profile** — the spawn-time structure, armor capacity, and armor
  rating supplied by the owning platform or issued armor pattern.
- **Resolved damage** — actual armor and structure removed, each clamped to
  the pool that existed. Requested damage and resisted energy are not credited
  as resolved damage.

## Resolution law

Hit testing, physical interception, and cover remain upstream. Combat
durability receives the damage that survived those decisions.

For a target with armor, armor efficiency is:

```text
0.10 + 0.90 * clamp(penetration / armorRating, 0, 1)
```

The ten-percent floor ensures every damaging weapon can eventually chip armor.
Meeting or exceeding the rating removes armor at full weapon damage. While
armor remains, the hit spends damage according to that efficiency. If the hit
breaks armor, only the unspent portion continues into structure, where it
applies at full damage. Armor never provides reduction after it is gone.

For an unarmored or already exposed target, all incoming damage applies to
structure. Structure reaching zero owns defeat and kill credit. Armor reaching
zero does not kill an actor.

Cover applies before armor so physical protection is neither bypassed nor
double-counted. Wall durability remains a separate map-structure system with
weapon-authored wall damage; wall damage is not renamed penetration.

The first model deliberately has no through-armor structure bypass, damage
types, armor regeneration, localized facings, or ablative segments. Those are
possible extensions only after a weapon or platform demonstrates a gameplay
need that the two-pool model cannot express.

`DurabilityModel` is the shared calculation boundary for prediction and live
application. It writes into caller-owned result scratch so the serialized
damage path stays allocation-free. `ARMOR` is an optional ECS capability;
armorless actors omit it. `DamageService` carries penetration through its SoA
mailbox, and `DamageResolver` applies the shared result after cover and generic
incoming-damage modifiers.

## Ownership and flow

1. The platform authority supplies base structure. A mech variant, turret
   platform, drone hub, or future vehicle also supplies its chassis durability
   profile.
2. Progression supplies a deployed marine's chosen armor pattern. The pattern
   owns armor capacity, rating, movement tradeoff, and silhouette/evasion tradeoff;
   it does not resolve damage.
3. A weapon definition supplies one or more damage-and-penetration payloads.
   Ballistics decides whether a contact or area payload applies; mounts and
   carriers decide whether and when the weapon fires, but do not reinterpret
   penetration or stack mutually exclusive payloads.
4. Ballistics and explosions decide contact and cover. The shared durability
   calculation predicts and applies the resulting armor and structure loss.
5. The application path emits at most one armor-break transition, records
   resolved damage, and defeats the actor only when structure reaches zero.

The same calculation must serve live application, AI prediction, and balance
tests. A target-type predicate such as “hardened” may remain as descriptive UI
vocabulary, but it is never damage authority.

## Platform identities

Infantry armor patterns remain composable equipment rather than new unit
types. Armorless troops omit the armor capability. Scout, combat, and elite
patterns may differ in pool and rating as well as their movement and incoming-
accuracy tradeoffs.

Mech variants own distinct durability profiles. The Bulwark is the high-pool,
high-rating anchor; the Hound gives up armor endurance for mobility; the
Sirocco has the least armor and structure and depends on range and screening.
These relationships are laws, while exact values remain balance output.

Turrets and drone hubs use the same resolution model but own their values as
platforms. Biological aliens or other special actors may add armor only when
their authored identity calls for a real protective layer; unit category alone
does not imply it.

## Decisions, morale, evidence, and presentation

Durability-aware decisions evaluate expected armor loss, time to armor break,
and expected structure loss from current target state. Scarce anti-armor
ammunition should not be selected merely because a target's type once counted
as hardened, especially when its armor is already gone or another attack has
already committed enough damage to break it.

Morale, evidence, and presentation that claim to represent durability consume
the real armor-break fact or current armor and structure state. Structure
thresholds may still create escalating pressure after exposure. Kill credit
comes only from structure depletion, and presentation observes simulation; it
never creates armor state.

The resolver currently calculates and applies armor loss and structure loss
separately. Some readers remain compatibility boundaries: target selection
still contains hardened-type gates, mech morale still uses structure
percentage as an armor-loss proxy, telemetry records only aggregate resolved
pool loss, and armory/battle surfaces expose limited durability evidence.
These transitional readers are not another durability authority and must not
be copied into new consumers. `stories.md` owns their bounded conversion to
current-state decisions, real armor-break morale, split evidence, and legible
armor, structure, rating, and penetration presentation.

## Standing laws

- Structure, armor capacity, and armor rating are distinct axes.
- Every damage payload keeps damage and penetration as distinct axes.
- Armor presence and current armor state replace target-type damage bonuses.
- Consumers use the shared calculation, current pools, and real armor-break
  fact. The tracked HP-threshold morale proxy is a compatibility exception;
  no new consumer copies a private formula or infers armor break from health.
- Resolved damage is clamped pool loss; resisted or overkill damage is not
  credited output.
- Cover resolves before armor; walls retain their separate durability model.
- Armor does not alter hit chance. Movement and incoming-accuracy modifiers
  remain independently authored profile traits.
- The queued damage path remains allocation-free and deterministic.
- Exact values follow measured time-to-break and time-to-kill targets, not a
  requirement to preserve old mech hit-point totals.

## Boundaries

`progression-nouns.md` owns infantry armor availability, inventory, assignment,
and economic value. `mechs-nouns.md` owns chassis identities and their relative
durability roles. `moddable-weapons-nouns.md` owns weapon-authored damage and
penetration. Ballistics owns whether a projectile contacts a target and how
cover intervenes. Combat durability owns what that resolved contact does to
the target's live protection and structure.
