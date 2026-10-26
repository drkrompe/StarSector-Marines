# Combat durability

Status: ACTIVE — armor-and-structure resolution is live; decision, evidence, and tuning follow-ons remain.

Written: 2026-08-23

Updated: 2026-10-25 — the plated Rav wear their chitin by the worn-plate law. Earlier: a damaged live hull smokes where it is hurt, and a mech's flux shows on its vents; worn plate draws located damage on live hulls and mechs; earlier: a ground hull holds damage by location (plates, drive, hull) and an infantry special's commit gate reads the plate facing it; earlier: a tank's turret is a section of its hull, on the mech section law; mech missile racks split into contact (anti-armor) and area (fragmentation) warheads; a rocket's contact payload rides its detonation. Earlier: the heavy cannon's contact hit is fixed.

## Purpose

Combat durability is the battle-owned model for how a damaging attack removes
an actor's protection and ultimately defeats it. It gives infantry armor,
mech plating, turrets, and future vehicles one shared vocabulary without
making those platforms share progression, chassis, or loadout authority.

The model separates **how much punishment an actor can survive** from **which
weapon is efficient against its protection**. That distinction is the main
balance seam: a Bulwark may carry both more armor and harder armor than a
Warden, while an anti-materiel rifle may defeat that armor efficiently without
needing an arbitrary bonus against every unit classified as hardened.

## Vocabulary

- **Structure** — the live capacity whose exhaustion defeats the actor. It is
  biological health for infantry, hull integrity for mechs and vehicles, and
  structural integrity for emplacements. Existing battle `HEALTH` storage may
  retain its implementation name while carrying this meaning.
- **Armor** — an optional live protection layer in front of structure. An actor
  with no armor capability takes structure damage directly.
- **Armor rating** — the resistance of the current armor to ordinary weapon
  damage. Rating is not another capacity and does not change accuracy.
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
- **Mitigation** — a bounded pool of post-cover damage an actor absorbs, for an
  explicit duration, across a bounded arc measured from its facing at the moment
  of the hit. It is not a capacity, it is not armor, and it never becomes
  either. Most actors never have one.
- **Soak pool** — the quantity a mitigation can still absorb. It is authored as
  an amount of damage, spent down by every hit it absorbs, and gone when empty.
  A pool is what makes a screen something fire can beat rather than something a
  shooter must wait out.
- **Screen** — the everyday word for a live mitigation. "Raising" one starts its
  clock and fills its pool. It ends two ways: the clock runs out, or the pool
  breaks.
- **Break** — a screen ending because its pool reached zero. It is the
  interesting ending and the one the presentation tier marks separately, because
  it is the outcome concentrated fire earns.
- **Mitigation arc** — the total angular width a screen covers, centred on the
  wearer's facing. A hit arriving outside it is refused nothing.
- **Mitigation facing** — the direction the screen points. It is simulation
  state maintained from the wearer's own movement and target, never read from a
  presentation facing.
- **Body part** — where on a person a direct round landed (`ballistics-nouns.md`).
  A payload may carry one; area, strafe and unattributed payloads, and every
  hit on a hull or creature, carry none.
- **Part multiplier** — what a payload that landed on a part is worth, as a
  multiple of its authored damage: the head more, the limbs less. The
  multipliers average to one over an uncovered silhouette.
- **Wound** — what a legs or arms hit that removed structure leaves on the man
  for the rest of the battle: a leg wound slows him, an arm wound costs him
  accuracy. It is a condition read through a unit modifier, not a capacity.
- **Material** — what a damageable piece of the environment is made of, as far
  as a round is concerned: wood, wire, drums, sandbags, machinery, timber, stone
  or masonry. One table keys every destructible prop family, shared-edge
  barrier kind and structural wall to one (`EnvironmentMaterial`).
- **Material rating** — a material's resistance to a round's penetration, read
  on the same scale as an armor rating. It is not a structure pool: how much a
  feature can take stays authored with the feature.
- **Material efficiency** — the share of a direct round's payload damage that
  wears a material: none while penetration is under half the rating, rising
  linearly to full at the rating. It reads the armor law's ratio and ceiling
  but not its chip floor. A **burning round** (a beam, or an authored energy
  weapon) chars what burns, wood and timber, with its whole payload whatever its
  penetration, so a laser tank cuts trees down that a rifle only scars; every
  other material reads it as an ordinary round.
- **Resolved damage** — actual armor and structure removed, each clamped to
  the capacity that existed, plus the mitigated damage a screen absorbed. Requested
  damage and resisted energy are not credited as resolved damage; mitigated
  damage is reported as its own quantity and never folded into either one.

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
double-counted.

**A body part scales the payload, not the armour.** A payload with a part is
multiplied by that part's multiplier at the same seam where a partless payload
takes its cover cut — before mitigation and armour — so penetration still meets
the same plate at the same efficiency, and armour remains one whole-body layer.
Per-part armour is a later question (`body-part-cover.md`). Shares and
multipliers live together on the part and are read nowhere else.

**A limb hit that reaches the man wounds him.** When a legs or arms payload
removes structure from a man who survives it, he carries one more wound on that
limb. A hit the plate absorbed whole never reached him and wounds nothing; a
head or torso hit is only damage. Wounds stack per limb toward a floor, so a
badly wounded man still walks and still shoots, and they last the battle unless
a medic's completed treatment dresses them. Their effect is a pair of named
unit modifiers (`ai-nouns.md`, Unit modifiers), so the slowed walk and the
shaken aim are read through, seen by the player, and gone with nothing to
restore when dressed. Hits by part and wounds inflicted are tallied per struck
side as battle evidence. Wall durability remains a separate map-structure system with
weapon-authored wall damage; wall damage is not renamed penetration.

**A direct round wears what it stops in.** A round whose payload arrives
directly — small arms, a precision special, a direct mount without an area
payload — spends its payload damage on the environment feature Ballistics says
stopped it, scaled by its material efficiency, through the same map-structure
channel the blasts below use: the same pools, the same damaged and destroyed
states, the same cover and navigation release. A round that arrives as a
detonation does its structural damage through that detonation's authored wall
damage instead, so nothing is counted twice. Ground a man stands in (a trench)
is never worn, and a feature no material is listed for is not worn by rounds.

Materials take no chip floor. Armor's floor exists so every weapon can
eventually strip a combatant's plate; given to masonry, it would let a rifle
squad cut through a building in seconds. So a rating says which rounds can bite
at all: wood and wire fall to anything, sandbags wear slowly under field rifles
and not at all under pulse fire, machinery, timber and stone only from the DMR
up, and masonry only to anti-materiel rounds and heavier. Ratings are set
against the catalog's penetration rather than per weapon, so a new weapon
finds its place in the table by its penetration alone.

Authored shared-edge barriers belong to that same map-structure boundary, not
to actor armor or structure. Their profile owns a small independent structure
pool, and explosion wall damage may deplete it. Breaking the feature removes
its cover and presentation identity and opens its navigation edge through the
map editor; none of those effects enter `DurabilityModel` or actor telemetry.

Cell-blocking industrial fences and sandbags also spend map structure through
weapon-authored wall damage. A surviving hit changes the prop to bent rails and
torn mesh, or torn bags leaking sand, while retaining obstruction and cover.
Exhaustion opens the cell, removes its cover, and retains low matching debris
over the original ground, including collapsed sacks and spilled sand. Neighboring props
keep their own protection, and navigation refreshes through the ordinary map
editor boundary. These breaches are neither doorways nor masonry collapses;
they do not peel adjacent roofs. Decorative walkable placements do not
acquire this obstruction contract.

**Fortified works hold the swarm off longer than a house.** The ground of a
fort or an objective (a Cordon redoubt and everything inside its wall, built or
compound; a colony compound's curtain; a Colony Breach redoubt's barricade
ring; a Cordon beat's set piece) is marked as works (`FortifiedWorks`). A swarm
strain's structural hit there, a Brute's blow, a charge's ram, a Runner's claw
at a barricade, counts for a fraction of what it does to a house wall, and a
charge rams a barricade on works rather than bowling it over. The wall keeps
its hit points, so it is still light enough for a Brute to choose to break,
and everything else's wall damage (a demolition charge, a shell, fire, a
dozer) is unchanged. So a field-grade wall still opens to a Brute, but in
something like the time a squad needs to answer the call, rather than the five
seconds a house wall takes. Ordinary buildings carry no mark.

Storage boxes, wooden crates and industrial crate stacks spend that same
structural damage in both walkable and blocking placements. Broken panels and
scattered supplies retain the original material and placement. Surviving damage
keeps protection; destruction removes only the crate contribution and opens a
blocking placement. Walkable crates carry independent removable physical cover,
so their destruction preserves other props and nature on the same cell.

Barrel groups also spend weapon-authored structure, with one pool across each
group's footprint and one damage spend per blast. Dented drums retain their
protection; destroyed groups leave low torn metal and scattered lids. Only
obstruction and cover owned by the barrels are removed: shipboard drum stock
does not own its underlying rack or pallet, so breaking the stock preserves
that fixture. Original ground and permanent terrain remain.

Trees use the same weapon-authored structural damage channel, with one pool
per tree across all its canopy cells. A blast spends damage once per tree,
including where crowns overlap. Surviving damage exposes broken limbs but
retains the full obstruction. Felling leaves a low broken stump and the short butts of its limbs,
flat enough to read as ground a vehicle drives over;
only cells free of surviving crowns and underlying hills reopen. Original
ground and permanent terrain remain, and neither actor durability nor
building collapse owns these changes.

Multi-cell rock and ice formations use the same whole-object law. Cracked
stone or ice retains the original silhouette's obstruction; low fragments open
the former footprint and reveal the ground beneath it. Rock and ice may have
different structure capacities, but neither has actor armor or building-collapse
semantics. One blast spends once per formation, regardless of how many cells
it reaches. Summit placements leave debris over the permanent cap; destroying
a rock or ice formation never opens a hole in its supporting mountain, regardless
of summit material. Continuous terrain ridges remain permanent and cannot be breached
through this prop damage channel.

The seven built-in nature rock groups, in each biome's stone set (`RockDamage.Biome`), also spend weapon-authored structure:
50 for small stones, 100 for medium cover rocks, and 200 for blocking boulders.
A surviving hit exposes cracks while retaining cover. Destruction leaves
separated low rubble, removes physical or directional cover, and opens a
boulder cell through the navigation boundary. Nearby props retain protection;
original ground and permanent cliffs remain. This uses `RockDamage` alongside
the tree and cover-prop state, not actor durability or masonry collapse.

Mitigation resolves **after cover and before armor**. Cover is a property of the
world the shot crossed; mitigation is a property of the target at that instant;
armor is what the target is made of. A mitigated hit still spends what remains
against armor at the ordinary efficiency rather than skipping a step, so a
marine behind a wall *and* behind a screen is simply both, in that order. The
arc is resolved against the bearing from the target to the hit's source, so a
hit with no locatable source — scripted damage, a strafing run — is never
mitigated: there is no bearing to measure, and inventing one would make a screen
work against fire it was never facing.

**A screen absorbs an amount, not a share.** It takes as much of the post-cover
hit as its pool still holds, spends exactly that, and lets the rest carry on to
armor untouched — a hit larger than the remainder neither wastes the overflow
nor swallows it. That is the difference between a screen and a soft
invulnerability window: a refused fraction is worth more the longer the fight
lasts and has no answer but patience, while a pool is a fixed quantity that
concentrated fire spends. Massed fire is meant to beat a screen, and breaking
one early is the reward for bringing it.

**Which end a screen came to is a durability fact, not a presentation guess.**
Only the damage path can observe the absorb that emptied a pool, so the break is
recorded on the actor and drains on its own shortly after — long enough for the
tail-of-tick presentation sweep to find it. Nothing in the simulation reads it;
it exists so a screen that shattered can look different from one that timed out.

Two obvious shortcuts are rejected, and stay rejected. **Extra armor capacity
while a system runs** has no facing and no visible expiry, and inflates the one
number this model has spent the most effort keeping honest;
`progression-nouns.md` forbids it by name and the armour catalog refuses a
system that declares capacity. **A flat incoming-damage multiplier** has no
facing either and is invisible: resolved damage would simply be smaller with
nothing to say why, and a player who cannot tell a working screen from a run of
lucky misses has been given a statistical rumour rather than a capability. That
is why mitigated damage is reported as its own resolved quantity — the same
number that tells the player their screen worked tells the balance harness
whether the arc asymmetry moved.

The first model deliberately has no through-armor structure bypass, damage
types, passive armor regeneration, localized facings, or ablative segments.
Those are possible extensions only after a weapon or platform demonstrates a
gameplay need that the armour-and-structure model cannot express. Machines have
demonstrated one: a mech's sections and a ground hull's plates are localized
armor, each by its own model (Standing laws).

**Treatment is the one way armor rises in battle.** A squad medic
(`progression-nouns.md`, Field Medicine) restores structure and armor capacity
to a squadmate over a treatment, and may bring a broken plate back from
zero. That is repair done to one marine by another, paid for out of the medic's
slowly regenerating energy pool — not regeneration of the patient, not
self-repair, and never a mitigation. Rating is unchanged: a re-plated suit is the
same suit with capacity back. A treatment that makes the patient whole also
dresses their limb wounds; one interrupted, or ended by an empty pool, leaves them. Armor break remains a
*transition* rather than a once-per-life event, so a re-plated marine whose
armor is shot away again breaks a second time.

`DurabilityModel` is the shared calculation boundary for prediction and live
application, and it consumes mitigation in the same call — prediction that
ignored a raised screen would make the AI systematically wrong at exactly the
moment the capability exists to create. It writes into caller-owned result scratch so the serialized
damage path stays allocation-free. `ARMOR` is an optional ECS capability;
armorless actors omit it. `DamageService` carries penetration through its SoA
mailbox, and `DamageResolver` applies the shared result after cover and generic
incoming-damage modifiers, then spends the screen's pool by what the model said
it absorbed. `MITIGATION` is a second optional capability, whose presence means
"this actor carries something that can raise a screen" rather than "a screen is
up"; `MitigationService` owns the pool, the arc test, and the break, and
`MitigationSystem` points the screen and drains its clock.

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
4. A capability the actor carries — today an armour pattern's integral system,
   tomorrow anything else — may raise a screen, handing over a soak pool, an
   arc, and a duration. Combat durability owns the pool, the clock, the arc, and
   both ways the screen can end from that point; the source does not resolve
   damage.
5. Ballistics and explosions decide contact and cover. The shared durability
   calculation predicts and applies the resulting mitigated, armor, and
   structure loss.
6. The application path emits at most one armor-break transition, records
   resolved damage — mitigated separately from absorbed — and defeats the actor
   only when structure reaches zero.

The same calculation must serve live application, AI prediction, and balance
tests. A target-type predicate such as “hardened” may remain as descriptive UI
vocabulary, but it is never damage authority.

## Platform identities

Infantry armor patterns remain composable equipment. Their explicit STANDARD or
POWER_SUIT class identifies the wearer's equipment category without creating a
new roster or battle unit type. A power suit supplies substantially more authored
armor endurance while the wearer retains biological infantry structure. Armor
break exposes that same named wearer; it never creates a second actor. Armorless
troops omit the armor capability. Recon, line, assault, and support roles remain
independent of class and may differ in capacity and rating as well as movement
and incoming-accuracy tradeoffs. `power-suit-infantry.md` owns the category direction.

Mech variants own distinct durability profiles. The Bulwark is the high-capacity,
high-rating anchor; the Hound gives up armor endurance for mobility; the
Warden has the least armor and structure and depends on range and screening.
These relationships are laws, while exact values remain balance output. The
mech readings (`mech-time-to-kill`, six seeds, stock frames, one team at 20
cells) keep that order in every row and put each d4 answer to mech armor on a
scale. Two rocket tubes kill a Hound in about 8 s and a Bulwark in 13 s. Six
DMRs take 19 and 37 s, and six field rifles 90 s, mostly failing to kill a
Bulwark at all. The AMR was the weakest answer (42 and 71 s, a four-round
magazine stripping a fifth of a Hound), so it now strikes for 70 (was 42) and
carries five rounds. Two AMRs take 24 and 38 s, and one magazine takes 44% of a
Hound. Penetration was left alone: at 18 it already defeats every mech rating
(10-18), so raising it would move only APCs and tanks. The mech heavy cannon
(the Warden's arms gun) took 83 and 136 s, as slow under the whole-body
control. It now lands a contact hit of 100 on the body it strikes, over its
unchanged 45 splash, as the Hephaestus turret cannon does, and takes 37 and
51 s. That contact hit is fixed: the cannon is equipment any 3x2 arms socket
takes, so it is balanced by what carrying it costs (mass, flux), never by its
damage (`heavy-equipment-progression.md`, the heavy cannon). Against hard targets (`hard-target-time-to-kill`, four seeds) both now
need about one magazine or a few rounds per emplacement or drone hub, and the
AMR still needs about eight magazines for a tank. Rockets remain the fastest
answer to every hard target. The specials stay off soft targets because the AI
fires them only at turrets, hubs, vehicles and heavy mechs; open-field matchups
of AMR-carrying cards against the starter issue read identically, fight for
fight, before and after. That gate is what keeps a 70-damage round off
infantry, so a damage-predicting replacement for it must look again.

Turrets and drone hubs use the same resolution model but own their values as
platforms. Biological aliens or other special actors may add armor only when
their authored identity calls for a real protective layer; unit category alone
does not imply it.

Mitigation is deliberately platform-neutral but proved on one carrier first: an
ASSAULT armour pattern's breacher assist, whose authored soak pool and arc are
the model's first real numbers. A mech, vehicle, or emplacement may
raise a screen on the same terms whenever one is authored; nothing about the
concept is infantry-specific, and nothing about it obliges every actor to have
one.

A directly controlled shield wearer supplies authoritative mouse aim for the
mitigation facing, including while moving sideways. Facing is updated before
damage can resolve and on a fresh grant; render interpolation never decides
the protected arc. This changes who supplies facing, while the same finite pool,
authored arc, expiry, and incoming bearing decide mitigation.

## Decisions, morale, evidence, and presentation

Durability-aware decisions evaluate expected armor loss, time to armor break,
and expected structure loss from current target state. Scarce anti-armor
ammunition should not be selected merely because a target's type once counted
as hardened, especially when its armor is already gone or another attack has
already committed enough damage to break it. A localized target is predicted by
location: an infantry special's commit gate reads a ground hull's plate facing
each contribution's source and the hull's own structure, never the summed
columns, which over-read both (`convoy-nouns.md`).

Morale, evidence, and presentation that claim to represent durability consume
the real armor-break fact or current armor and structure state. Structure
thresholds may still create escalating pressure after exposure. Kill credit
comes only from structure depletion, and presentation observes simulation; it
never creates armor state.

The resolver currently calculates and applies armor loss and structure loss
separately. Some readers remain compatibility boundaries: target selection
still contains hardened-type gates, mech morale still uses structure
percentage as an armor-loss proxy, telemetry records aggregate resolved capacity
loss beside a separate mitigated total, and armory/battle surfaces expose
limited durability evidence — a screen is legible in the Armory before issue and
on the field as the wearer's running-system treatment
(`progression-nouns.md`).
These transitional readers are not another durability authority and must not
be copied into new consumers. `stories.md` owns their bounded conversion to
current-state decisions, real armor-break morale, split evidence, and legible
armor, structure, rating, and penetration presentation.

**Worn plate** is located damage drawn on the living body: scuff marks on a
ground hull's plates and turret and on a mech's parts, so the side that has been
stripped looks stripped (`VehicleScuffs`, `MechScuffSink`). It is presentation,
and it observes, never records: a location's marks are a pure function of its
current armor (a mech section's also of its structure, a quarter of its wear),
so nothing is written per hit, a re-plated location comes back clean, and a
review frame or a replay draws exactly what the screen did. Each location owns a
fixed set of slots, each hashed from the body, the location and the slot into a
position, turn, stage and wear threshold. A slot shows once the location's wear
passes its threshold and never moves after, so marks build up in place, in three
stages: scrapes and bullet pocks, then scorch, then gouges and dents.

A mark belongs to the part of the silhouette its location covers. A hull frame's
opaque footprint, turned into the hull's own axes, is split along its diagonals
into front, rear, left and right plates; a turret frame is one region; a mech's
torso is split the same way, its rear quarter wearing with the rear plate; each
arm, shoulder pod and foot is its own part. A mark is laid in its part's frame
and turns with it, which matters for a mech, whose torso, hips and arms turn
independently, and a part drawn over another covers that one's marks as it
covers its paint. A mark never hangs off the silhouette: it may only sit on an
opaque point whose distance to the nearest transparent pixel covers the mark's
reach. So a narrow part, such as a Hound's torso, carries few and small marks.
That is the honest consequence of the law, not a defect. A wreck carries none,
because its registered wreck art already shows what happened to it.

The plated Rav wear by the same law, made organic (`RavWear`, `colony-breach-nouns.md`,
Matriarch): their own sheet of scored chitin, bruises and weeping wounds, a shell
channel reading the plate and a flesh channel reading health on one part's mask.

The marks are the mod's own scuff sheet, sized for vehicle sprites at play zoom
(`art-source/vehicles/scuffs/`). The base game's ship damage decals were tried
first and read only as smudges on a tank. Their size, 0.3 of a part's narrower
extent, was picked by the owner over 0.22 and 0.4.

**Damage smoke** is the other half of located damage on a living body: what is
spent smokes where it was spent. A mech's lost section bursts and smoulders and a
crippled mech smokes at its legs (`mechs-nouns.md`). A ground hull smokes by the
same law (`VehicleDamageFxSystem`): thinly off its engine deck once its own
structure is half gone or its plates are three-quarters worn on average, more
thickly once its structure is down to a quarter; a lost drive bursts at the
running gear and then smokes low along it; a lost turret bursts on its ring and
then smoulders there. A wreck takes none of this, because its own wreck smoke
already shows what happened to it. Like worn plate it observes and never
records: it reads current state, draws nothing from the battle's random stream
(cadence and jitter are hashed from the body and the tick), and keeps only which
losses it has already burst, so each loss bursts once. It emits into the effects
queues every presentation host drains, so a review frame smokes as the screen
does.

## Standing laws

- Structure, armor capacity, and armor rating are distinct axes.
- Every damage payload keeps damage and penetration as distinct axes.
- Armor presence and current armor state replace target-type damage bonuses.
- Consumers use the shared calculation, current capacities, and real armor-break
  fact. The tracked HP-threshold morale proxy is a compatibility exception;
  no new consumer copies a private formula or infers armor break from health.
- Resolved damage is clamped capacity loss; resisted or overkill damage is not
  credited output.
- Cover resolves before armor; walls retain their separate durability model.
- A contact payload and an area payload are separate tools, chosen per weapon:
  contact at high penetration answers plate and needs a hit, area at low
  penetration answers men and forgives a miss. The mech racks are the worked
  pair (`mechs-nouns.md`, Missile warheads). A rocket's contact payload travels
  with its detonation, so whatever intercepts the rocket stops both; the struck
  body takes the contact payload and no share of the splash.
- Rounds wear the environment by one law — penetration against a material
  rating — and only through the map-structure channel. No weapon class is
  named in that law; a rifle leaves a wall standing because its penetration is
  too low for masonry.
- A body part scales its payload before mitigation and armor; it never changes
  penetration or armor efficiency. A wound needs structure removed.
- Wounds act only as unit modifiers and are written only on the serialized
  damage path and the serial medic pass.
- Mitigation resolves between them, and is reported as its own quantity. It is
  always finite (a pool that can be spent), never all-round (the arc stays below
  360 degrees), always expiring, never a capacity, and never restores one — armor
  reaching zero stays an armor break until a treatment re-plates it. It does not touch accuracy: whether a shot
  lands is upstream and already has a pattern's incoming-accuracy multiplier.
- **A screen is spent by what it absorbed, and no more.** The pool falls by
  exactly the quantity the shared durability calculation reported as absorbed,
  which is what keeps prediction, live application, and balance evidence
  describing one fight.
- **Mitigations do not sum.** When more than one applies, the largest single
  pool does. Summation is how two individually reasonable authored numbers reach
  a pool no fight can spend, and the arc rule cannot rescue a design that has
  already got there. One live slot enforces this, at the deliberate cost that
  a smaller screen offered under a larger live one is dropped rather than queued
  behind it.
- A mitigation arc is a temporary capability with a facing. It is not an argument
  for giving every actor a permanent facing, localized armor, or ablative
  segments. Mechs are the deliberate exception, by their own model: a chassis
  divides its armor capacity and structure into section pools
  (`mechs-nouns.md`), each resolved by the same calculation with the chassis's
  one armor rating, and only the torso's structure decides death. Ground hulls
  are the same exception by theirs (`convoy-nouns.md`, a hull holds its damage
  where it lands): four armor plates read by the side a hit arrives from, a
  drive whose loss immobilises, an optional turret section that loses only the
  gun, and the hull's own structure behind them, the only pool whose zero is
  death. Each pool resolves by the same calculation and the hull's one rating.
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
