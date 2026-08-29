# Progression nouns

Status: ACTIVE — cross-tier quality, kit, career evidence, and legibility continue to evolve.

Written: 2026-08-23

Updated: 2026-08-28 — perception joined movement and payload as a shipped
integral-system effect; and the running-system treatment became a halo worn on
the marine's own silhouette, asymmetric toward the arc it protects, dimming with
the screen's soak pool and shattering when that pool breaks.

## Purpose

Progression is the cross-tier model for a mercenary company's growing combat
quality. It joins four distinct but connected concerns: the kit a marine is
issued, the innate and earned qualities of that marine, evidence of what they
did in battle, and the campaign economy and presentation that make improvement
meaningful to the player.

This domain owns troop quality. It does not own the personnel lifecycle,
contract resolution, salvage settlement, ballistic resolution, or player
command powers. Those systems supply identities, outcomes, recovery, damage
and opportunities; progression interprets them as quality, reward, and
legibility.

## Vocabulary

- **Marine** — a named, persistent rank-and-file campaign soldier. `MarineRoster`
  is the authority for that identity, status, assigned kit, and career.
- **Profile** — a marine's battle-ready individual quality: immutable aptitude
  plus the experience band issued with their armour, represented at the battle
  seam by `SoldierProfile`.
- **Aptitude** — an innate, permanent marksmanship disposition. It is not an
  upgrade track and is not rerolled by experience.
- **Experience** — a marine's Green, Regular, Veteran, or Elite band. It is
  issued with the armour pattern the marine wears, not accumulated by that
  marine. It changes infantry combat performance and new-threat registration;
  it is separate from captain XP and rank.
- **Experience standard** — the band an armour tier fields. The four authored
  armour tiers map one-to-one onto the four bands, so a squad's standard is a
  direct reading of the suits it was issued, never a record of what anyone
  did.
- **Career** — persisted lifetime service evidence: deployments, wins, fired
  and landed rounds, dealt and taken damage, kills, and wounds. A marine holds
  one; a squad holds one. Both are cumulative totals, not per-mission journals.
- **Squad career** — a squad's own lifetime record, accumulated from every
  marine who has served in it. It belongs to the formation rather than its
  current membership: it survives replacement, and it survives a total loss and
  reconstitution under the same squad identity.
- **Equipment family** — the weapon's tactical identity: engagement band,
  firing pattern, baseline behavior, and family presentation. A family is not
  a quality tier or merely a technology label.
- **Delivery mechanism** — how a weapon produces and delivers its payload:
  chemical slug, gauss/rail kinetic, flechette cloud, pulse/laser energy,
  missile or grenade, or close-contact tool. The weapon catalog owns this
  physical/presentation truth. Mechanism does not decide availability, grade,
  faction allegiance, or tactical role by itself.
- **Equipment grade** — the four-step manufacturing/condition quality axis:
  Surplus, Service, Milspec, Masterwork. Grade composes with family and profile;
  it does not create a separate unit type.
- **Equipment provenance** — the manufacturing tradition or factional lineage
  attached to a recipe and concrete item. Provenance governs availability,
  presentation, and at most a bounded side-grade; it is not an allegiance lock,
  a quality tier, or a set bonus.
- **Special equipment** — one optional billet item carried alongside the
  marine's primary. Rocket launchers, anti-materiel rifles, fragmentation
  grenades, and close-contact tools activate weapon definitions; smoke, satchel
  charges, and deployables activate utility behavior. “Secondary” remains a
  transitional code/catalog name, not the enduring player-facing category.
- **Deployable** — a special item whose activation leaves a persistent object
  standing in the world rather than throwing, planting, reaching, or firing
  something. The carried item is spent; the placed object then has a life of its
  own, with its own position, durability, and expiry. A thrown grenade and an
  attached satchel are not deployables: neither is a thing that remains and acts.
- **Placed emplacement** — the object a deployable leaves behind. It is an
  ordinary static-emplacement actor — it draws, it is targeted, it takes damage
  through the ordinary durability pipeline, and it dies — and it takes its
  survivability, geometry, force value, magazine, and gun from the shipped
  emplacement catalogs rather than from the backpack that carried it. It is
  never a squad member and never a second soldier.
- **Placed cover screen** — the other thing a deployable may leave behind: a
  barricade occupying one boundary between two cells. It is not an actor and
  never enters the roster — cover is stored per cell per facing, so a screen
  is a property of the map rather than a body standing on it. It takes its
  cover, durability, and passability from a named shared-edge profile.
- **Engagement** — one burst a placed emplacement fires at one warhead. It
  spends a round of the mount's magazine and puts the mount on its interval
  whether or not it connects, so an engagement is an attempt, not an outcome.
- **Ordnance interception** — an engagement that connected: the warhead is
  removed from flight before it arrives. It is deliberately not damage —
  nothing is applied to anything, the round's payload never reaches the
  detonation authority, and no attacker is credited. Distinct from *physical
  interception*, which is a shot stopped by cover or by a body it ran into.
- **Point-defence target** — a round whose weapon data declares it may be
  engaged in flight. The property belongs to the weapon, never to a list held by
  whatever is shooting it down.
- **Contact reach** — the distance at which a close-contact item may act, plus
  the requirement that the carrier could physically cross the boundary between
  its cell and the contact's. Reach is not a very short weapon range: a target
  one cell away behind a shut barrier is out of reach even though it is close.
- **Contact reservation** — one carrier's claim on one contact while it is
  committed. It stops several carriers spending payloads on the same casualty,
  and it costs nothing to release.
- **Breach point** — a map cell authored as a legal place to cut an opening. A
  breaching tool considers only authored breach points, which is what separates
  a boarding tool from map-wide wall demolition.
- **Assault-armor role** — the suit's battlefield weight and purpose: light
  infiltration/recon, standard line combat, or heavy mechanized battlesuit.
  Unpowered field kit remains outside those three powered roles. Role expresses
  a protection/mobility silhouette, not a vertical quality tier.
- **Armor pattern** — a concrete, player-owned infantry protection and
  appearance package. A pattern realizes one assault-armor role and may carry
  equipment provenance. Unlike weapon grade, it changes survivability and
  movement tradeoffs as its own kit choice.
- **Integral system** — a capability the suit itself carries, at most one per
  armour pattern. It is authored per pattern rather than per role, so which
  concrete suit was recovered is what surprises the player, and it never spends
  the billet's carried special item. An integral system must express itself as
  behavior — movement, protection with a clock on it, a delivered payload,
  perception — never as durability, and it declares the use policy it is spent
  on so its trigger is readable from the catalog entry rather than compiled into
  the sweep. Whether a pattern carries one is a question about what that suit is
  for, not a quota: see `integral-system-slate.md`.
- **System family** — the shared name and icon every pattern's take on one
  effect has in common, such as a breach assist. Six suits carry that effect and
  each names its own version something else; the family is what lets a player see
  they are the same capability rather than six unrelated tricks, so it leads and
  the tradition's own name follows as flavour.
- **Use policy** — the authored moment at which spending a piece of equipment is
  a good idea: what has to be true, not what the thing does. It is a closed
  vocabulary shared by both carriers of equipment behavior, a carried special
  item and a suit's integral system, and it is validated against the effect it
  accompanies when the catalog loads. A policy named after its effect would be a
  second vocabulary in the first one's clothes, so a self-directed capability —
  which has no target to describe, only a situation it suits — names the
  situation: taking fire it cannot answer, a contact sighted at standoff.
- **Policy parameters** — the numbers one system's take on a policy is judged
  by, authored on the catalog entry beside it. How much incoming an industrial
  rig thinks is worth its cooldown is a judgement about that rig, so it belongs
  with the rig; a constant in the code that spends it would be one author's
  judgement about one suit imposed on every system that will ever exist. Each
  policy owns its own parameters, so two systems declaring different policies
  share no numbers and cannot perturb each other.

  The split within a policy is between fact and judgement. Whether a wearer is
  under fire, how much, from where, whether the ground already covers that
  bearing, whether they are under way, and whether the shooter is beyond their
  reach are all facts the sweep computes and no catalog may override — a suit
  does not get an opinion about whether it is being shot at. What a suit gets to
  author is the price: how much fire justifies spending a scarce card, and how
  much cover makes spending it pointless. That is why an occasion can be added
  to a policy without touching a catalog, and retuning a suit never needs code.
- **System grade** — how well a tradition builds its version, on the same
  Surplus/Service/Milspec/Masterwork ladder a weapon's manufacture already uses,
  so one word means one thing across the Armory. It is <b>description, not
  arithmetic</b>: unlike a weapon family, an integral system does not consume the
  grade's stat multipliers, because its numbers are authored outright and scaling
  them again would price the same quality twice. A Masterwork system is finely
  made, not automatically the strongest — a family is side-grades.
- **Running-system treatment** — what a wearer looks like while an integral
  system is running: authored appearance data written from the live effect and
  cleared when it ends. It describes the capability rather than the suit — a
  window with a clock on it, and, when the system raised one, the screen's
  facing, arc width, how much of its soak pool is left, and whether that pool
  has just broken — so a consumer draws from what is running and never from
  which pattern is running it. It is presentation in one direction only: the
  simulation neither reads it nor may ever come to.
- **Halo** — how the treatment is drawn: the wearer's own head and body layers
  emitted a second time behind them, slightly larger, tinted a shimmering blue,
  and swept along the screen's arc so the rim of light protrudes on the covered
  side and nowhere else. It reuses the suit's existing art rather than adding
  any, so a pattern that gets new armour art gets a halo shaped like it.
- **Equipment template card** — permanent collected capability for one primary
  family-and-grade, armor pattern, or special item. It gates authoring and issue,
  is never consumed, and is distinct from a reusable squad definition.
- **Faction equipment source** — one campaign channel through which a faction
  may make a template card available: ordinary market stock, licensed access,
  patron reward, or operational recovery. A source declares eligibility and
  relative selection weight; it does not itself grant, sell, or recover a card.
- **Equipment access tier** — a card's authored Common, Advanced, or Prestige
  campaign band. It is independent of grade, armor role, provenance, issue
  cost, and loadout collectible rarity; acquisition channels interpret it
  through shared company-history and reputation gates.
- **Armory** — the persistent campaign authority for collected equipment
  templates and reusable squad definitions. Materialized marine kit persists on
  the marine; changed incoming issue is paid from ordinary fleet cargo.
- **Telemetry** — battle-local, lifecycle-stable evidence of a combatant's
  activity. It serves the debug balance artifact for all recorded combatants
  and career evidence only for campaign marines.
- **Trait** — a captain's stable named quality. Traits belong to leadership,
  not the individual marine experience ladder; some are currently functional
  while others remain planned mechanics.

## Current quality model

### Composed combat quality

For an infantry primary, `InfantryCombatStats` composes weapon family,
equipment grade, and profile rather than treating any one as the complete
answer to "how strong is this marine?" Grade affects range, damage, accuracy,
cooldown, and spread. Aptitude affects accuracy and spread. Experience affects
accuracy, cooldown, spread, trigger discipline, and first action against a
new threat. Armor is a separate defensive/loadout decision.

The standing law is **family supplies role; mechanism supplies delivery; grade
supplies quality; provenance supplies source; profile supplies person**. New
content must preserve this separation. A high-grade weapon is still its family,
a gauss label does not earn a redundant family, and a skilled soldier does not
become a bespoke carrier type.

A second standing law governs legibility: **a squad's fighting quality is fully
determined by what the player can see**. Two squads carrying the same loadout
definition and the same armor pattern fight the same, and no hidden per-squad
or per-marine modifier may separate them. Innate aptitude varies within a
declared band and is readable on the marine; it is bounded expressly so it
cannot overturn the band the player read. This is what makes threat assessment
a skill rather than a guess, and it applies to defender formations as much as
to the player's own.

The shipped primary catalog now covers five player-facing decisions. The Rook
is the rugged chemical-slug baseline, the Lancer is the flexible pulse burst,
and the Longbow is the deliberate gauss/rail marksman weapon. The Rattler is a
close-range flechette shredder: one trigger releases several independently
resolved projectiles with low penetration, steep falloff, and wide spread. Its
legacy `SMG` enum name and `weapon.smg` id remain only for save compatibility.
The Stalwart is the squad automatic slugthrower: one trigger begins a long
temporal burst that participates in the existing covering-fire and bounding
model without creating a suppression status or permanent gunner class.

Simultaneous projectile count and temporal burst count are separate authored
axes. Friendly-fire discipline treats one multi-projectile release as one
trigger decision, while every projectile retains its own physical trajectory,
impact, telemetry, and presentation. Both families use the same faction-neutral
engagement and maneuver rules for player and defender carriers. The automatic
is starter-reachable and appears in the built-in Fire Support template; target
faction profiles decide whether and how heavily defenders issue either family.
`equipment-lore-catalog.md` owns the models' in-universe origin and credible
circulation. Those origins are mod direction constrained by faction lore, not new
claims about vanilla canon and not a reason to create equivalent faction clones.

### Aptitude and experience

Aptitude is innate, persisted per marine, and fixed for that marine's life. It
is the only quality axis an individual rank-and-file marine carries, and it is
what keeps two squads issued the same definition from resolving identically.

Experience is issued with the armour pattern rather than accumulated by the
individual. Armour tier is the source because it is the one quality axis
authored across all four steps, carrying no grade axis of its own, and because
it is what the player can see: the pattern drives the layered appearance family,
so a squad's band is legible from the deck. A marine in a heavy battlesuit is,
by construction, one of the people the company put its best kit on.

The primary deliberately does not feed the band. A primary's access tier is
authored as a strict function of equipment grade, so sourcing experience there
would fuse two axes this domain keeps separate — grade supplies quality, profile
supplies person. For the same reason there is no hidden "promoted because they
lead" bonus: the built-in squad definitions already issue the leader's billet
scarcer armour, so an NCO comes out steadier through the visible mechanism.
Because armour tier now sets a band, any issue path that picks a pattern for
looks alone is choosing combat quality and must say so.

**The arc ends in super soldiers.** A company opens on raw numbers in bad kit,
scraping a living out of weight of fire, and should end as something that reads
like a different species of soldier. That is an intent about the *compound*, not
about any single lever: band, equipment grade, and armour all move together
because the armour pattern is what sets the band to begin with. Measured as the
exchange between the two ends — an endgame marine killing an opening one against
an opening marine killing an endgame one — the asymmetry is roughly fifteen to
one with the weapon family held constant, so upgrading families across the arc
widens it further. `TtkReportTest` publishes that matchup and fails if the arc
flattens into a linear ladder.

**The arc is bought with capability, not durability.** Roughly 5.7x of that
asymmetry is shooter-side and only about 2.6x is target armour, and that ordering
is deliberate: the design wants firefights to stay lethal, so late equipment
should get more interesting rather than harder to kill. Armour patterns already
express themselves as a four-lever tradeoff — the tier-1 field kit is faster and
harder to hit than the tier-4 walking tank that pays a fifth of its speed for
plate — and that tradeoff shape is the thing to extend. A late-game suit earns
its place through what it can do, so growth belongs in authored capability
rather than in a wider capacity. That is what an **integral system** is for, and
the rule is enforced where an author will meet it: the armour catalog refuses a
system that declares added capacity, rating, or hit points by name, and refuses
a cooldown that never expires, a resistance of 1, or an all-round arc. A
capability that cannot be turned aside, waited out, or flanked is a durability
increase wearing a costume. `integral-armor-systems.md` carries the remaining
direction.

That compound is why no individual lever needs to be dramatic:

**A band is worth about an equipment-grade step.** That calibration is measured,
not asserted: against an unarmored marine the Green-to-Elite span is roughly
1.7x time-to-kill, while the Surplus-to-Masterwork grade span is roughly 3.5x,
and a single band step sits inside the range the grade steps occupy. The band
ladder was once described as a 1.23x effect, which is the accuracy multiplier
read alone; end to end it also carries cooldown, spread, and the one-off reflex
delay before the first shot at a newly acquired threat. `TtkReportTest` publishes
the isolated band table with aptitude pinned and fails if the span flattens into
noise or grows past the grade span — the latter matters because the armour
pattern already sets the band, so letting bands outweigh equipment would collapse
the two levers the player reads into one.

**Issued armour is the only experience authority.** There is exactly one place a
band comes from, and no second path exists to reach one: a marine carries no
persisted experience number, and the battle tier has no way to award a band
mid-fight. Changing a shooter's quality means re-equipping them. A future
proposal to let anything else raise a band — a mission reward, a training
facility, a battlefield promotion — is proposing a second authority, and has to
retire this one rather than sit beside it.

Rank-and-file marines are otherwise interchangeable, and the company's growth is
what the Armory has collected and can issue rather than a per-marine ladder.
Enlisted stripes follow the same source: a squad leader wears sergeant's stripes
when the suit they were issued fields them at the veteran band or better.

Seniority is separate from quality and means what it says: when two marines of
equal rank are candidates for a billet, the one with more deployments on their
career record takes it. That is the only thing the career record decides, and it
decides who stands where, never how well anyone shoots.

That makes collection and issue the player's progression axis: which definitions
the Armory owns, and which squads carry them. Casualties still cost bodies,
credits, and recovery time, but replacing a marine does not degrade the squad's
declared quality. Named identity continues to carry weight for captains and for
the enlisted leader billet, not for the rank and file.

Experience already has a behavioral meaning beyond output. For infantry
training archetypes, `FiringSystem` holds the first primary shot against a
newly selected threat until the tier's reflex delay expires. Continuing on the
same threat does not restart the delay; opportunity fire registers its own
observed threat without changing the pursuit target. A legal in-range acquired
threat remains the opportunity-fire choice until another is materially closer;
near-equal hostiles cannot alternate the registration identity each tick and
starve the first shot. Visual torso facing follows this acquisition at a
bounded rate but is presentation state, never another fire gate. Sustained
cadence remains a weapon-and-handling concern. This applies to trained humanoid
infantry, not to mechs, turrets, drones, or fauna. Experience changes first
action without becoming another permanent damage multiplier.

Because the band is issued rather than earned, the same experience vocabulary
describes defender formations, whose bands already resolve from unit type and
risk. Player and defender quality therefore read on the same scale, which is
what lets a player learn to judge a hostile formation on sight.

### Kit and armory

The armory has three deliberately separate layers. Equipment template cards are
permanent collected capability. A **weapon definition** is reusable twelve-billet
intent authored only from collected templates; a **tactic sheet** is the armour
half and is *not* authored the same way — it names roles and a tradition, and the
armoury fills them from the best owned pattern for each job
(`role-and-access.md`). The exact kit on a marine is the materialized result.

**Armour is never authored a billet at a time, and there is no way to.** The
player could once build armour definitions by cycling twelve concrete patterns,
which froze them: such a definition names the militia vest twelve times and goes
on naming it long after the company can afford siege plate, because nothing in it
says what the billets are *for*. That path is gone. A weapon definition stays
per-billet because a fire-team lead's carbine is a real choice with no role model
above it, and because a weapon has no equivalent of "the best owned suit for this
job" to resolve against. Issuing changed incoming kit consumes
base-game supplies, heavy armaments, and heavy machinery as one atomic squad
transaction; unchanged kit costs nothing, removed kit grants no refund, and food
remains available to later sustainment costs without being forced into routine
refits. Marines remain personnel cargo handled by personnel logistics.

Squad loadout collection presentation is authored data. Its tier communicates the
definition's expected power band, while rarity communicates how scarce or prestigious
that definition is in campaign acquisition and drives only its collectible visual
treatment. Provenance and a setting paragraph make the acquisition a lore-bearing
reward. None of those fields is a loot-table weight, and a definition's contents are
never rolled from a pool: every selection resolves the same ordered twelve-billet
Weapon or Armor issue, including any leader-specific and special-equipment placements.

Legacy recipe ids remain save-migration input, not live Fleet Armory authority.
The print-stock economy they fed — owned counts, per-soldier allocation against
those counts, and fabrication materials — has been deleted along with the
retired fire-team refit APIs that were its only readers. Equipment is owned as
permanent template cards, and an issue spends fleet cargo. Existing victory milestones grant template cards, including the
Shattercap after two victories. A deterministic safety net now keeps permanent
collection breadth at or above 21 cards after five victories, 25 after fifteen,
29 after thirty, and 30 after forty. Cards already learned or still carried in
cargo count toward that floor, so faction-shaped acquisition advances the same
curve instead of being duplicated by it. When a company falls below a floor,
the Armory grants the next bounded core fallback; the curve never revokes cards,
never learns a carried card implicitly, and leaves at least four of the current
34-card catalog to faction sources and active collection.

Faction source pools author which cards may enter through market, license,
patron, and recovery channels. Every card also authors an access tier. Open
markets admit Common cards only, so unrestricted credits cannot buy Advanced
or Prestige capability. Their faction-and-market-stable weighted selection
rotates monthly and scales with market size. Favorable-or-better standing adds
licensed offers; MRB reputation admits Advanced at 5 and Prestige at 20, using
the same bands as patron contract access. Patron rewards apply those MRB bands
as well. High-risk recovery instead uses operational history, admitting Advanced
after 5 company victories and Prestige after 15, so field discovery does not
require bureaucratic standing. Already-owned or carried cards are omitted.
Each completed patron contract also issues one
weighted card from that patron faction's pool through the immutable engagement
ledger. Delivery is exactly once, excludes learned or already-carried cards, and
does not treat system-generated extraction as patron work. A victorious high-risk
operation with salvage rights contributes at most one deterministic target-faction
card from the `recovery` pool to the ordinary weighted loot roll. If rolled, the
card enters the frozen manifest and competes inside the player's existing salvage
budget; settlement creates the same parameterized cargo item exactly once. Learned
and already-carried cards are excluded before the manifest freezes, low-risk work
does not roll this channel, unknown faction ids use the Independent fallback, and
explicitly excluded factions remain empty. The safety net guarantees breadth,
while source channels still decide most collection identity and pace.

The Fleet Armory landing view is the player-facing summary of this policy. It
shows only acquired card counts, including only access tiers represented in the
player's collection. Undiscovered cards leave no placeholder, catalog total,
identity, source, or per-card acquisition hint. The company's current generic
licensed/patron and recovery ceilings, the facts producing those ceilings, and
the next uncleared thresholds remain visible; those explain company capability,
not undiscovered contents. Open-market Common-only status is stated beside them.
A physical template-card tooltip repeats that acquired item's authored tier and
the shared MRB/recovery requirements, reading threshold values from the same
policy used by candidate filtering rather than maintaining presentation-only
numbers.

The lowest protection band is deliberately plural rather than one universal
"unarmored" result. Domain-pattern fatigues preserve mobility and sealing with no
armor reserve; expired Cordon security shells provide a little rated plate; and
Lashplate breaker harnesses supply crude mass with poor resistance and handling.
The opening Frontier Patchwork definition issues all three beneath one sound Ward
kit per fire team. These are generic circulation categories for early companies and
low-end defenders, not faction-equivalent powered suits.

A template card may exist in fleet cargo as one parameterized Starsector special
item whose data is the stable equipment-template id. Right-click learning follows
the familiar blueprint interaction and consumes one card only when it adds a new
capability to `MarineArmory`; duplicates, invalid data, and an unavailable Armory
remain unconsumed. This interaction deliberately does not implement a vanilla
blueprint provider or write player-faction hull, fighter, ship-weapon, or industry
knowledge, so infantry equipment cannot leak into ship production or the ship
editor. Market, reward, and salvage systems create the same validated cargo
payload; the S6 acquisition ladder still owns the eligibility of each entrance.

Collectible access tier and issue cost are catalog data rather than a closed
Java list. Each enabled catalog provider may add complete four-grade primary
families, armor cards, and special-equipment cards after the referenced
equipment has loaded. Card ids are derived from the stable equipment id,
duplicate claims fail with provider provenance, and learning keeps the same
Marine-Armory-only boundary. Missing or unknown access tiers fail catalog load
rather than silently entering the opening pool. A provider adding a marine primary, armor, or
special item must either contribute its complete player-template treatment or
declare a non-empty reason that the identity is not player equipment. A partial
primary grade matrix and a collectible-plus-exclusion contradiction both stop
application loading.
The doctrine editor derives primary family/grade, armor, and special-equipment
choices from the owned cards in that additive catalog. Persisted doctrines,
resolved billets, and materialized marines retain the contributed equipment ids
directly, so a learned provider card can be selected, saved, issued for its
authored cargo cost, and deployed without a Java enum constant. Special items
then execute through their closed typed activation and AI policy. If that
provider later disappears, save repair warns, returns unresolved primaries to
the starter field rifle and unresolved armor to field fatigues, and clears an
unresolved special slot.

Faction availability is a second additive catalog over those card ids. Exact
campaign faction ids merge offers from every enabled provider; a provider may
therefore add its own card to an existing faction without replacing that
faction's core pool. One faction/template/channel claim has one provider, and a
duplicate reports both sources rather than silently changing its weight.
Unknown factions resolve to the Independent pool. A faction with no
human-compatible player equipment declares an explicit reason instead of
quietly producing an empty or unreachable catalog. The four channel weights are
inputs for later acquisition consumers, never loot rolls performed during
application loading and never a hidden combat modifier.

The merged catalogs enforce the standing reachability law at application load:
every collectible card must be starter-owned or appear in at least one faction
pool through a channel that can eventually admit its access tier. An Advanced
or Prestige card authored only for the Common-only open market is therefore
still stranded. This is global reachability, not universal faction availability.
It lets add-ons keep faction identity while ensuring that a newly collectible
family, grade, suit, or special item cannot be stranded by an omitted or
permanently ineligible source.

Armor patterns are authored player kit with distinct defensive and mobility
tradeoffs. Every currently authored pattern has a collectible card and faction
acquisition source, and the catalog audit preserves that coverage as content is
added.
The pattern owns the deployed armor capacity, rating, movement modifier, and
incoming-accuracy tradeoff; `combat-durability-nouns.md` owns how battle damage
removes that armor and exposed structure. Structure remains the platform's base
capacity; armor no longer adds health or applies a permanent damage-reduction
multiplier after it breaks.

The planned assault-armor role makes those trades legible without turning the
current numeric `tier` into the suit's identity. Light armor favors mobility
and concealment-capable patterns, line armor is the all-environment baseline,
and heavy battlesuits trade speed for breach-level protection while remaining
one-person infantry. Powered sealing, recoil assistance, tactical relays, and
jump assistance are setting and presentation truths until a story names a
mechanical consumer. Optical camouflage likewise requires an honest shared
observation/perception contract; an armor description alone may not grant
invisibility or erase an opponent's remembered contact.

Equipment provenance composes beside role and pattern. It lets a Hegemony line
suit and a Tri-Tachyon line suit share the same role while differing in source,
visual language, maintainability, and a bounded authored skew. Player companies
may mix recovered traditions. Provenance never rewrites the marine's weapon
family, grade, aptitude, or experience, and faction identity never supplies a
hidden universal combat bonus.

Each billet has at most one special-equipment slot. The item is a stable
loadout identity with a typed activation: weapon-like specials reference the
weapon catalog that owns their payload and any traveling round, while utility
specials own their battle action without becoming zero-damage weapons. A
deployable is a utility activation whose action is a placement; the gun on the
thing it places belongs to the emplacement catalogs, not to the carried item,
so that gun is a real weapon definition rather than an exception to this law.
Progression owns template ownership, assignment, cargo issue value, and
reachability; battle AI owns when legal
issued equipment is used. The same use policy is faction-neutral even when
campaign availability differs by faction.

Planned special-equipment extensions retain that slot and activation law.
Combat stims are finite utility, not a weapon grade or permanent profile
upgrade. Martyr rigs and carried improvised charges are explicit faction
content with their own carrier cost and counterplay; faction flavor may not
silently graft self-detonation, aim bonuses, or shock immunity onto ordinary
infantry.

Target defenders consume one battle-frozen `GroundRosterProfile`. Its JSON
weights select among stable primary, grade, armor, special-equipment, and mech
ids already owned by their respective catalogs; they do not create factional
copies of those items or faction-only execution rules. Risk chooses which
weighted quality/protection tables apply, while mission setup retains force
scale and support gates. The current armor enum and art may still carry palette
names, but roster data uses semantic armor ids (`field-fatigues`, `scout`,
`combat`, `line`, `heavy`, `outlaw`, `militia`) so the next visual/content pass
can change colors without changing doctrine identity.

**Defenders carry what their patterns declare.** A pattern's integral system
rides on the roster's existing armour weighting through the same catalog entry
the player's Armory reads, so a hostile in a foundry-breaker fights with the
thing the rig is named after and a recovered suit behaves exactly as it did for
its previous owner. There is one data path and no defender-side tuning field: if
a defender's use of a system is too strong or too weak, the fix is the authored
numbers or the policy, never a second set of numbers or a branch in the sweep.
That also means a system is not a difficulty lever — it composes with the
weighting a roster already had, and no table should start reaching for
system-carrying patterns to make a fight harder.

**A defender's screen is not a narrower case of an attacker's.** An earlier
reading of the measurements held that defender adoption changed nothing because
the moment itself belonged to the attacker — a defender holding a line was
simply never crossing one, and so correctly declined. That reading was wrong,
and instructively so: the trigger it described had never fired for anybody. It
gated on applied velocity, read from a sweep that deliberately runs ahead of the
movement pass, so the value was always the zero the movement pass had just
written. A full battle raised zero screens, for attackers and defenders alike,
while the tests covering it passed by writing that velocity in by hand.

The correction is a better noun rather than a repaired predicate. What a soldier
raises a built-in screen for is **fire they cannot presently answer**, and that
is not a posture: a defender pinned by something out of their own reach is in it
exactly as much as a marine crossing a street. Holding a line is not a reason to
decline; having somewhere better to put the round is. So there is still one data
path and no defender branch, and it now carries a moment both sides genuinely
reach. Whether a hostile pattern's
system is readable before contact remains open: reading it at a distance makes a
fight plannable, and discovering it when a rig comes through a door is more in
keeping with how the rest of the equipment model treats recovery.

The built-in identities are the rocket launcher, anti-materiel
rifle, Wayfarer smoke grenades, Breachhand mag-clamp satchel, Shattercap
fragmentation grenades, the Emberjaw thermal breacher, the Quillon
vibro-blade, the Palisade interceptor pod, and the Rampart field revetment.
The first two are direct-fire activations, Shattercap
is an arcing weapon activation, smoke and satchels are utility activations,
the pod and the revetment are deployable activations, and
the breacher and blade share one close-contact activation,
but only the item definition owns loadout identity, catalog copy, resource
mode, initial ammunition, Armory art, activation type, AI policy, use-pose
profile, and local presentation recipe; the
referenced weapon definition owns range, damage, accuracy, impact, projectile,
and audio behavior. Persisted marines and billets use the stable `special.*`
id, while collected cards derive their stable identity from it. `MarineSecondary`
remains a built-in compatibility API and legacy-save input, not a battle-runtime
authority or second stat catalogue.

Those item definitions load from the built-in special-equipment JSON catalog
after the weapon registry. Parsing and reference validation fail loud:
unknown activation/resource/policy/pose vocabularies, invalid resource
combinations, duplicate ids, malformed presentation transforms, or a
weapon-like item pointing outside the marine-secondary mount class stop load.
JSON selects only closed, typed execution policies; it does not name Java
classes or inject simulation scripts.

The use-policy vocabulary is one closed enum with two carriers rather than one
per carrier. Joining costs an enum whose members no longer share a shape — some
describe a target, some only a situation — and buys a single parse-time
validation path and a single word for the same idea across the Armory, which is
the trade the standing "second carrier, not a second vocabulary" rule already
made for activation and resource mode. Membership grants nothing on its own:
each carrier validates the declared policy against its own declared effect, so a
suit cannot borrow a policy written for a grenade and a grenade cannot borrow
one written for a suit. Both refusals name the policies that would have applied,
in the style the armour catalog already uses to refuse durability keys.

**Perception is an effect, and it is player-facing by construction.** A sensor
sweep is the third thing an integral system may be, beside movement-with-a-screen
and a delivered payload. What it does is contribute a temporary observer to the
player's own reveal for its authored window — the same shadowcast, the same
reference count, and the same expiry a shuttle or a recon ping already gets
(`fog-of-war-nouns.md`), so it is a client of player reveal composition rather
than a second visibility path. It authors how far it reads and how far that read
carries into the walls in front of it, and the wall tolerance is bounded strictly
inside the range: a read that carried through walls as far as it carried through
air would leave nothing for a wall to do at the rim of its own disc.

A sweep is **information, not authority**. It reveals and does nothing else — it
does not target, mark, follow, or make anything easier to hit, and no decision
layer reads it, because fog of war is presentation authority and must never
become a simulation input. That is also why the moment a sweep is spent at is
judged from the wearer's own line of sight rather than from the reveal bitmap:
a policy that read the fog would let what the player has already been shown
decide what a marine does, which is the same law inverted. The consequence worth
stating plainly is that a sweep confers no advantage the same information
obtained by walking round the corner would not.

The corollary is the rule this effect exists to keep: **whatever a sensor system
finds, the player is shown.** A capability that fed better information to the AI
and showed the player nothing would be exactly the hidden modifier the
visible-issue law forbids, and a perception effect is unusually easy to build
that way by accident. Building the reveal as a player-picture contribution and
nothing else makes that true by construction rather than by discipline.

**A sweep does not conceal.** Concealment is a separate capability belonging to
the light/recon role behind its own contract (`powered-assault-armor-roles.md`),
and a reveal path that recomputed rather than contributed would be the cheapest
accidental route to it. A running sweep only ever adds to the picture; no cell
that was open closes because of one, and the wearer is exactly as visible while
it runs as they were before.

An equipment presentation separates catalog art, actor-local carrier layers,
deployed-world art, and preview state. Carrier transforms are authored in
shoulder-width coordinates with carried/using states, pivot, occlusion,
optional recoil, and whether activation temporarily replaces the primary.
The live layered-unit renderer and development preview gallery resolve those
transforms through one composition helper. A later Armory sample-soldier scene
should consume that same helper rather than recreate placement in UI code; the
existing Armory thumbnail remains the current production surface until that
scene is built.

The anti-materiel rifle is a four-round precision answer to visible hardened
targets. Its long brace locks one target and cancels when the target or direct
firing solution becomes illegal. The physical heavy round uses ordinary
ballistic collision, has no splash or wall damage, and cannot intentionally
select soft infantry. Squad reservation counts both carriers already bracing
and committed direct rounds so scarce shots do not overkill. The same policy
drives player and defender carriers; current authored availability is one
starter player rifle plus one carrier in high-risk non-militia defender
fireteams. Its report is an ordinary localized shot noise, not omniscient
identity disclosure.

Smoke grenades are two-canister tactical utility. A short authored throw
creates a simulation-owned, several-cell cloud for one maneuver bound. Its
cells are temporary opacity in the shared tactical line-of-sight layer: they
block observation and direct fire for both factions, but never movement,
ballistic travel, audio, damage, or remembered belief. Overlapping clouds
reference-count that opacity until the last field expires.

Smoke is planned at squad level, not fired as a target-of-opportunity weapon.
An exposed objective bound reserves one carrier and lane, pauses until the
cloud is actually opaque, then resumes; a breaking squad may supplement its
fallback with one useful throw while the other survivors keep moving. Active
or airborne coverage prevents redundant throws. The same rules drive player
and defender carriers. Player issue includes two starter canisters and a
Screen template; medium/high-risk non-militia defender fireteams may carry
smoke explicitly.

The Breachhand is reusable close-contact demolition for a marine who already
happens to be beside a hostile hardened target. Opportunity AI considers only
living turrets, drone hubs, and heavy mechs already inside contact range and
honest line of sight; it never assigns an approach or overrides survival
movement to manufacture a plant. An interrupted channel releases its target
reservation at no cost. A completed plant starts the carrier's personal
equipment cooldown rather than consuming battle ammunition.

The armed pack attaches to the target and follows even a moving mech until its
fixed fuse expires. The shared detonation authority resolves a compact,
friendly-fire-capable anti-materiel blast; light hardened targets die while a
heavy platform may survive damaged. The planting faction knows the temporary
hazard and routes friendlies out, while opponents gain no omniscient avoidance.
Player starter collection includes the Breach template. Cargo-backed issue
materializes a carried kit; battle placements do not consume campaign cargo. Ground
placement, infantry targeting, traps, disarming, and wall breaching are not
part of this equipment identity.

The Shattercap is a three-grenade short-arc anti-personnel special. Its
registry-owned weapon definition owns throw range, release scatter, flight,
arc, compact lethal area payload, negligible penetration, zero wall damage,
projectile art, and detonation audio. The special-equipment item owns the
finite three-use battle resource, loadout identity, activation policy,
carrier/throw presentation and template identity. A released grenade
remains a real in-flight projectile and detonates if its carrier dies; ordinary
cover, armor, friendly fire, telemetry, and anonymous detonation noise remain
shared combat authority.

Opportunity AI requires at least two recent direct-contact soft combatants in
one useful footprint. Audio-only contacts, isolated targets, hardened targets,
and stale tracks do not justify a throw. Safety expands the blast by authored
scatter and checks both friendly positions and committed movement paths.
Friendly overlapping landing reservations and in-flight frag footprints block
redundant throws, while genuinely separate clusters may be engaged. A carrier
does not interrupt mission-priority work, survival withdrawal, or its moving
half of a bound to begin a throw. Friendly squads know their own grenade;
opponents evade only when a squad member can honestly see the incoming body.
The battle HUD shows FRG stock and an amber friendly/red observed-hostile
landing ring, so unseen enemy throws provide no warning.

The player template card unlocks after two victories. The built-in Fleet Assault
weapon doctrine issues exactly one
frag carrier across its twelve billets. All built-in defender roster profiles
author their own low/medium/high bulk and elite weights: the execution policy
is faction-neutral, while availability remains faction-shaped and risk-scaled.

Close-contact boarding tools are the sector's two recognizable contact
implements, and they are one mechanism wearing two tactical identities. Both
occupy the ordinary special slot, both reference a marine-secondary weapon
definition for damage, penetration, wall damage, audio, and effects, and both
run the same typed executor, which replaces the traveling shot with an
adjacency test. **They are not a universal melee attack.** A marine carrying
neither gains no contact attack of any kind, and no other special item acquires
one by sharing the slot.

The **Emberjaw thermal breacher** is anti-hard contact work: a long, visible,
interruptible channel against an armored actor or emplacement the carrier is
already standing against. It applies bounded contact work with no area blast,
and at an authored breach point it applies the weapon's wall damage to that one
cell through the ordinary map-edit authority. Arbitrary obstacle-seeking and
map-wide wall chewing are not authorized, and a breach point is reachable only
from the four cells that share a face with it.

The **Quillon vibro-blade** is anti-personnel reaction equipment: one short
strike against a living infantry contact when a firefight has already collapsed
to arm's length. It cannot intentionally select a turret, drone hub, mech,
convoy vehicle, drone, or non-combatant, and it never opens a wall.

The two are distinguished by AI policy rather than by a second activation.
Thermal and arc are mechanism variants of the one breach-tool role, and vibro
and monofilament of the one edge role; provenance may vary within a family
without earning a fourth definition.

Both actions validate their contact before committing, again on every tick of
the commitment, and once more at the payload seam. Death, separation, a lost
line of contact, a barrier closing between the two cells, or a higher-priority
survival response cancels the commitment before the payload applies, and an
interrupted commitment costs the carrier nothing. While committed, a carrier
holds a contact reservation, so several carriers cannot spend several payloads
on the same contact.

Equipment influences per-action suitability without creating a permanent
breacher or swordsman role. A carrier uses its tool when ordinary squad
movement has already brought it into contact; the executor never authors a
path, never clears one, and never retargets its carrier, so engagement AI
cannot abandon a firing line or cross its maneuver leash to manufacture a
contact opportunity. Player and defender carriers obey the same legality,
interruption, reservation, and target policy. Faction profiles control
availability and provenance only: the blade is broadly circulated and
market-reachable, while the cutter is Advanced and reaches the player through
licensed, patron, and recovery channels.

Both use the shipped contact-work pose rather than introducing a new one.
Presentation carries the strike's audio, its impact effects, and the localized
noise a nearby squad may hear; it never decides whether the contact landed.

### Deployables and placed emplacements

A deployable is the third thing a special slot can be. The first two are
offence and utility; this one is denial, and denial is the only one of the three
that changes what the opponent is able to do rather than what the carrier can
do. Making the slot a real three-way choice is the point of the category, so a
deployable must not also be a good weapon.

**A placed emplacement is not a second soldier.** It refuses one class of thing
and does nothing else. It selects no actor, takes no objective, spots for
nobody, joins no squad, and runs no decision cadence — the tier law is
untouched, because nothing about it decides anything. What it does is a physical
test against objects already in the world, never a belief query, so it can never
engage ordnance it could not physically have reached.

**A placed emplacement is bounded on every axis, and can fail.** It engages
within a finite radius, at a finite rate, from a finite magazine, for a finite
time, and it stands in the open where both sides can see it and shoot it. The
rate bound is the load-bearing one: a salvo tighter than the engagement interval
saturates the mount and the surplus lands. This is the growth law applied to a
placed object — a defence that cannot be flanked, saturated, outlasted, or
destroyed is a ban on a weapon class wearing the costume of a capability.

**An engagement can also simply miss.** Whether a burst connects is a roll
against the mount weapon's authored accuracy, so losing one is an ordinary,
reachable state rather than an edge case, and the magazine is spent either way.
This exists for a reason beyond balance: a mount that never misses has only two
observable states, working and absent, and "it tried and lost" — the state
that makes the thing feel like equipment rather than a rule — would not exist
to be watched.

**The interception must be watchable, and a failure must look different from a
success.** This is not deferrable presentation. A stopped warhead is removed
from the air, which on screen is indistinguishable from a missile that was never
fired; and a saturating volley reads as some explosions arriving and some not,
with nothing to say a defence was involved. Both of those look exactly like a
bug. So an engagement is drawn as an engagement — the mount fires visibly, the
burst goes to where the round actually is, and the warhead comes apart there —
and an engagement that lost is drawn differently from one that connected, with
the round visibly flying on. The frame worth building the whole feature for is a
five-round salvo where three die and two arrive, and that frame only means
anything if a viewer can tell those two apart.

The first deployable is the **Palisade interceptor pod**: a folding cluster gun
a marine sets down where they are standing, which shoots down incoming warheads
crossing its bubble until its rounds or its cell run out. Placement is a short
committed channel and spends one carried pod. It is placed reactively — when
hostile ordnance is already in the air and headed for the ground the carrier is
on — so it costs nothing in a battle where nobody is firing missiles, and a
carrier standing inside a friendly pod's existing bubble declines rather than
stacking a second. Like every other executor, the placement authors no path,
clears none, and never retargets its carrier.

Placement never seals the cell it stands on. A carried pod sits on the floor its
carrier was standing on; closing that cell would both trap the carrier and shut
a navigation edge under existing paths. Only an emplacement that actually sealed
a cell re-opens one when it dies.

**A deployable leaves behind one of two kinds of thing, and only one of them is
an actor.** The category's shared part is the carried item's channel: an
activation that commits the carrier, freezes them for a short build, spends one
piece of hardware, and enqueues a placement that the serial pass resolves. What
that placement produces is not shared at all. A **placed emplacement** is an
actor — an entity on a cell, with hit points and armour, targetable, killable,
drawn by the ordinary unit pass. A **placed cover screen** is a *property of a
boundary* — there is no entity, because cover in this game is stored per cell
per facing and a barricade is the thing that puts some there. Each shape has its
own owner: an emplacement takes its survivability, geometry, and gun from the
emplacement catalogs, while a screen takes its cover level, catch height,
structure, and passability from the named shared-edge profile that
`mapgen-nouns.md` owns. A future deployable picks the shape that matches what it
actually leaves standing, rather than being bent into whichever one shipped
first.

The second deployable is the **Rampart field revetment**: folding baskets and a
stake kit a marine drops across one boundary of the ground they are already
holding. It is placed when the carrier is standing still, taking fire from a
hostile it can locate, and the boundary between them offers nothing — so it is
free in a battle fought from cover and it never pulls a marine off a firing
line to build.

**A cover screen protects one boundary, and the boundary belongs to both cells
it joins.** Whoever stands on either side of it is behind it against fire
crossing it, which is faction-blind because a barricade does not know who built
it, and it does nothing whatever against fire from the other three directions.
That directionality is the whole design: a screen that made its cell safer from
everywhere would be a durability increase in a costume, which is the same
mistake the all-round screen refused. It is enforced rather than asserted —
the damage path resolves cover against the bearing from the target to the hit's
source whenever the hit has a locatable one, and falls back to the
direction-blind scalar only for a hit with no bearing at all.

**A screen is cover, not a wall, and that is what makes it placeable at all.**
It leaves the navigation transition open: a soldier steps over it, shoots over
it, and sees over it. Nothing about the walkable graph changes when one appears,
so no path in progress is invalidated, no zone is severed, and no unit can be
stranded behind one — which is exactly the condition `mapgen-nouns.md` puts on
runtime construction. A barricade that also blocked movement would be a
different feature owing a different proof, and would be a portable wall rather
than a portable piece of cover.

Its bounds are the boundary it occupies, a finite time standing, and a small
structure that explosive wall damage depletes. Expiry and destruction share
one removal path, so a screen that ran its clock out and one that was blown
apart leave the boundary in the same state — the one it was found in.

**Which rounds may be engaged is the weapon's declaration, not the
emplacement's.** A weapon definition opts itself in, and the shipped warhead
families — the mech LRM and SRM, the shoulder micro-missile, and the marine
rocket — do. Nothing anywhere holds a list of engageable ids, so a warhead
authored later is engageable the moment its own data says so, and a bullet never
becomes engageable by accident.

**Interception is not damage.** An engaged round is removed before it arrives:
its payload never reaches the detonation authority, no damage figure is computed
for anyone, and nothing is credited as damage prevented. Interceptions are their
own telemetry quantity, credited to the marine who placed the pod so the record
survives the pod — and attempts are counted separately from successes, so an
after-action can say "engaged five, stopped three". Recording only the kills
would leave both bounds unreviewable: a mount that ran dry and one that was
outshot produce the same number.

### Telemetry and career

`CombatTelemetryService` records combat evidence at the shared firing and
damage seams. It records every designated combatant for a mission's debug
table, including non-campaign units. Damage attribution is based on applied,
post-mitigation damage clamped to the target's remaining health, rather than a
requested damage value; overkill is not credited output. Friendly fire is
separate and never grants a kill. One arriving round counts once even if its
explosion harms many targets, while each defeated victim is still a kill.
Unattributed damage records what happened to the target without inventing an
attacker. Ordnance a placed emplacement engaged in flight is counted as its own
quantity rather than converted into damage prevented, for the same reason
mitigated damage is: an engaged round never detonated, so there is no damage
figure to net out and nothing to attribute.

Telemetry survives a combatant's death transition. At mission end,
the gathered report is immutable and detached from battle entity handles.
`MissionOutcome` freezes only campaign-marine rows for campaign use, while the
full report remains an opt-in balance readout at `MissionResolver` DEBUG log
level; normal INFO logging does not emit its per-unit table.
`MarineRoster.applySoldierOutcome` uses the outcome manifest to count every
deployed marine, then folds any available telemetry into `SoldierCareer`. Thus
deployment is not inferred from having fired, and a fallen marine retains the
record of their final mission. Career retention is deliberately lifetime totals
rather than a per-mission journal.

The same fold credits the squad a marine deployed with, accumulating a squad
career from the frozen deployment tag rather than from current membership. A
squad's record therefore survives replacement, and survives a total loss and
reconstitution under the same identity: the formation is the thing with a
history, and the marines rotate through it. Because rank-and-file experience is
issued rather than earned, career evidence is a record and a presentation
input; it is never fed back as a quality dial.

The standing law is **outcome declares participation; telemetry supplies
evidence**. Telemetry must never become the authority for campaign identity,
casualty disposition, or deployment membership.

## Flow and authority boundaries

1. The personnel domain creates and persists a marine; progression retains
   only that marine's quality-bearing fields and assigned kit.
2. The armory collects templates and authors reusable definitions; one atomic
   roster command consumes ordinary fleet cargo for changed incoming kit and
   materializes it on marines.
3. Deployment carries the marine's profile and kit into battle. The battle
   resolves shots and damage independently of campaign persistence.
4. Battle telemetry records combatant evidence and follows the death path.
5. Mission resolution freezes a `MissionOutcome`; the campaign roster applies
   the authoritative disposition and folds eligible evidence into careers.
6. Campaign UI and battle presentation should expose quality without feeding
   presentation state back into simulation.

Campaign personnel owns who a marine is and whether they return. The battle
system owns whether and how an attack resolves. Campaign loot owns salvage
manifest and settlement. Progression owns neither, but consumes their stable
outputs for troop-quality advancement and explanation.

`company-view-nouns.md` owns squad-definition presentation and atomic assignment
transactions. Progression supplies equipment-template ownership, cargo issue
costs, and the quality meaning those transactions materialize.

## Presentation law

Progression information must be readable where the player assigns equipment,
chooses personnel, and observes combat. It must remain presentation-only:
simulation determinism and combat results cannot depend on UI or render state.
Equipment signal belongs to grade capability, while person-driven signal
belongs to the marine's profile, career, and current contribution. Because
quality is issued with a definition, that definition's declared standard is
itself a presentation obligation: where a squad's loadout is shown, its
experience standard is shown with it, and battle presentation should let the
same distinction be read from a silhouette. A suit's integral system carries
the same obligation, and carries one more: the Armory reports what the
simulation applies, never what the catalog declares. An authored effect that
does not run yet is not advertised, because a screen describing intent rather
than behavior is a brochure, and a player cannot tell the two apart from the
outside. Avoid stacking redundant
battlefield overlays; start with the closest decision surface and add in-world
signal only when it materially improves play.

**A running system is legible in the world, not in a status bar.** The wearer
looks different while it runs and stops looking different when it ends, and
that difference is drawn **on the wearer** rather than floating in front of
them: the treatment is the marine's own silhouette wearing a rim of light, not a
shape hanging off it. Four rules make the drawing honest, and each exists
because breaking it would teach the player something false:

- **The halo is the arc.** The whole counterplay of a directional screen is that
  it covers one facing and leaves the flanks open, so a 90-degree scrap screen
  and a 200-degree interlock screen must not look alike, and neither may be
  drawn as an all-round glow. The halo is therefore asymmetric: the enlarged
  copy is swept along the authored arc, so the rim is thick on the protected
  side and simply absent on the exposed one. The arc it is swept along is the
  one the damage path resolves against, copied rather than recomputed — a second
  calculation would eventually disagree with the first, and the value of the
  picture is entirely that it can be trusted.
- **The pool is shown draining rather than counted down.** A screen with a
  sliver of soak left reads as one: the rim fades as the pool is spent, and its
  angular extent never narrows, because the protected arc does not narrow as the
  pool goes. Cooldown is deliberately not drawn: these are spent on an authored
  policy rather than a player click, so a cooldown readout answers a question
  nobody has.
- **Breaking looks like breaking.** A window that ran out and a pool that was
  beaten to nothing are different events and must not share a picture. The
  shatter is its own brief moment — the wearer's whole silhouette thrown outward
  and gone — because a screen broken by concentrated fire is what the soak pool
  exists to make possible, and the player who earned it should see it.
- **The treatment keys on the capability.** A running system that raises no
  screen reports no arc and is drawn with an even, undirected rim that claims no
  facing; nothing in the presentation may branch on which armour pattern is in
  front of it. That is what lets a future system inherit the treatment by
  describing itself rather than by being added to a list.

Both sides read the same, because the capability is symmetric and an incoming
breacher is exactly the thing a player needs to recognise; a hostile's
treatment is gated on ordinary cell visibility like every other field effect,
so symmetry hands out no information the fog was withholding.

## Growth directions

The following are direction, not current behavior:

- Give defender formations the same readable loadout vocabulary the player's
  squads use, so hostile quality can be judged before contact.
- Expand primary families and special-equipment options while preserving the
  complete-template, explicit-exclusion, faction-source, and collection-floor
  laws. `stories.md` owns the concrete contact-tool, stim, grenade, and
  faction-demolition additions and their ordering.
- Extend the shipped world-reactive sources with MRB, relationship, and named
  operational gates while keeping advanced progression operation-shaped.
- Make grade, aptitude, experience, career, and captain traits legible in
  campaign and battle surfaces without changing simulation authority.
- Surface squad careers as a company-wide standing worth reading, including
  awards drawn from evidence the sim already separates, such as friendly-fire
  damage.
- Give only traits with an observable, domain-appropriate consequence a
  mechanic, and define a deliberate acquisition model before promising
  level-up rewards.

## Invariants for future work

- Aptitude is permanent and per marine; experience is issued with the squad
  loadout definition; captain rank is a separate leadership progression. None
  of the three is a synonym for another.
- Issued armour is the sole source of a marine's band. Neither the battle tier
  nor a persisted per-marine number may supply a second one.
- Seniority for a billet is deployments served; it orders people, never
  sharpens them.
- A squad's fighting quality is fully determined by visible issue. A hidden
  modifier that separates two identically equipped squads is a defect, not a
  feature.
- Family, delivery mechanism, grade, profile, armor role, armor pattern, and
  provenance stay composable rather than being fused into faction-specific
  unit variants.
- A new mechanism name earns a weapon entry only when its authored behavior or
  presentation supports a distinct tactical identity. Slug, gauss, pulse, and
  laser labels do not create parallel stat clones.
- Assault-armor role is not quality: a rare high-end recon suit may remain
  light, while a crude industrial battlesuit may remain heavy.
- An integral system is behavior with a bound on it, authored per pattern and
  carried by few. It never adds durability, never spends the carried special
  item, and never becomes an authority the player cannot read before issue.
- An integral system's trigger is authored, never compiled. The sweep that
  spends one dispatches on the declared policy and reads that system's own
  numbers; a threat radius, range band, or per-effect special case living in a
  system class is the defect this rule exists to prevent.
- A use policy names a moment, not an effect. A policy whose name only restates
  what is being spent is a second vocabulary and must be renamed rather than
  added.
- A policy's parameters belong to that policy alone. Two systems declaring
  different policies share no numbers, and no parameter may be reachable from a
  policy that did not author it.
- A perception effect reveals to the player. Feeding a decision layer something
  the player is not shown is a hidden modifier, not a capability, and reading
  the player's reveal back into a simulation decision is that law inverted.
- A perception effect never conceals, marks, targets, or makes anything easier
  to hit. Concealment belongs to the recon role and its own contract; a sweep
  that acquired any of those has stopped being perception.
- Defenders and the player draw a pattern's system from the same catalog entry.
  A defender-only tuning field, a second catalog, or a faction branch in the
  sweep is a defect: a recovered suit must behave identically to the one it was
  taken from, in both directions.
- A heavy battlesuit remains a one-person infantry billet using infantry
  weapons, cover, pathing, and casualty authority. Mech chassis, mounts,
  lances, and support delivery remain Mechs authority.
- A template card is permanent capability, a squad definition is reusable
  intent, fleet cargo pays changed incoming issue, and materialized kit belongs
  to the marine. None is a synonym for another.
- A cargo template card is consumed only by successful Armory learning. It never
  becomes player-faction ship-production knowledge.
- A billet carries at most one special item; utilities do not become
  `WeaponDef` entries merely because they share that loadout slot with guns.
- Contact damage exists only as a carried item. No marine has a fallback melee
  attack, and no future close-contact family may grant one.
- A close-contact family earns a definition through behavior, payload, or
  readable presentation. A mechanism or provenance name — thermal, arc, vibro,
  monofilament — is flavour on an existing role until it changes one of those.
- A breaching tool damages a wall only at an authored breach point, one cell at
  a time and with no radius. Obstacle-seeking demolition is a different feature
  and requires its own authority.
- Special-equipment use policy is simulation-owned and faction-neutral;
  template cards express issue, not hidden battle orders.
- Special-equipment data selects a closed activation and AI policy; executors
  remain typed code, and presentation state never becomes simulation input.
- Live carrier art and preview carrier art resolve the same actor-local recipe;
  a UI mannequin may choose a pose but must not own a second placement table.
- Career totals are lifetime evidence at both the marine and squad grain;
  adding a per-mission history requires a new retention and UI commitment.
- A squad career belongs to the formation and is never reset by replacement,
  by total loss, or by reconstitution under the same identity.
- All combat telemetry may inform balance; only identity-bound campaign rows
  may affect campaign careers or rewards.
- Attribution measures resolved outcomes, not requested damage or visual
  effects. Resolved armor and structure loss may be reported separately while
  remaining one aggregate career contribution.
- Career evidence is folded from a frozen campaign outcome; battle-local state
  must not become a second career authority, and career totals must never be
  read back as a combat-quality input.
- A running system's treatment is drawn from authored appearance data, written
  by a presentation system and read by render and audio. No simulation
  consumer may read it, and an activation, damage, or movement decision that
  came to depend on it is a defect rather than an optimisation.
- A drawn screen's arc is the arc the damage path resolves against, and its
  width is authored per pattern. Widening, rounding, or averaging it for the
  picture's sake is prohibited; so is any treatment whose readable extent
  implies a protected facing the simulation does not honour.
- A treatment ends with the effect that produced it and never survives its
  wearer. It is live-only state and leaves nothing on a corpse. The one part
  drawn after the effect is gone is the shatter, which exists precisely to show
  a screen ending and drains on its own within the same breath.
- A running system's treatment is composed from the wearer's own authored
  layers. It must not acquire art of its own: a treatment with a private texture
  is a treatment that stops matching the suit the first time the suit is
  re-authored.
- Presentation conveys existing quality but never changes sim state.
