# Progression nouns

Status: ACTIVE — cross-tier quality, kit, career evidence, and legibility continue to evolve.

Written: 2026-08-23

Updated: 2026-10-23 — a billet may carry a second, thrown special on the belt.

Updated: 2026-10-24 — warrant obligations ask loyalty only; service and service calls retired with generic contracts.

Updated: 2026-10-27 — every equipment item is shown by a short catalog code.

Updated: 2026-10-27 — the shoulder pod is the SRM-POD carried special, fired without lowering the primary; no suit carries it as an integral system.

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
  missile or grenade. The weapon catalog owns this
  physical/presentation truth. Mechanism does not decide availability, grade,
  faction allegiance, or tactical role by itself.
- **Equipment grade** — the four-step manufacturing/condition quality axis:
  Surplus, Service, Milspec, Masterwork. Grade composes with family and profile;
  it does not create a separate unit type.
- **Equipment provenance** — the manufacturing tradition or factional lineage
  attached to a recipe and concrete item. Provenance governs availability,
  presentation, and at most a bounded side-grade; it is not an allegiance lock,
  a quality tier, or a set bonus.
- **Shown name** — the short catalog code an item is shown by, such as
  `AR-PULSE`, `GRN-FRAG`, `SHLD-HT` or `ARM-HEG-3-LN`. Armour codes read
  type, maker, suit tier and role. It is the item's `displayName` string and the
  only name the player sees; a primary that takes a grade marking shows it as a
  suffix (`AR-PULSE-2`, from `designationTiered`), and recruit issue (`GR-RFL`)
  does not. Ids never change with it. In-universe provenance stays in an item's
  description, never in a second name.
- **Special equipment** — one optional billet item carried alongside the
  marine's primary. Rocket launchers, anti-materiel rifles and fragmentation
  grenades activate weapon definitions; smoke, satchel
  charges, and deployables activate utility behavior. “Secondary” remains a
  transitional code/catalog name, not the enduring player-facing category.
- **Deployable** — a special item whose activation leaves a persistent object
  standing in the world rather than throwing, planting, or firing
  something. The carried item is spent; the placed object then has a life of its
  own, with its own position, durability, and expiry. A thrown grenade and an
  attached satchel are not deployables: neither is a thing that remains and acts.
- **Placed emplacement** — the object a deployable leaves behind. It is an
  ordinary static-emplacement actor — it draws, it is targeted, it takes damage
  through the ordinary durability pipeline, and it dies — and it takes its
  survivability, geometry, force value, magazine, and gun from the shipped
  emplacement catalogs rather than from the backpack that carried it. It is
  never a squad member and never a second soldier.
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
- **Armor role** — the suit's battlefield job: recon, line, assault, or support,
  with UNPOWERED identifying unpowered field kit. Assault means breaching, not
  a carrier class. Role stays independent of armor class and vertical quality.
- **Armor class** — the pattern's explicit STANDARD or POWER_SUIT carrier category,
  independent of role, tier, and provenance. STANDARD includes advanced powered
  line and recon equipment; POWER_SUIT is a wearable mechanized infantry shell
  whose armor provides a substantial endurance step. Neither class creates a new
  personnel identity or a mech chassis.
- **Armor pattern** — a concrete, player-owned infantry protection and
  appearance package. A pattern realizes one assault-armor role and may carry
  equipment provenance. Unlike weapon grade, it changes survivability and
  movement tradeoffs as its own kit choice.
- **Integral system** — a capability the suit itself carries, at most one per
  armour pattern. It is authored per pattern rather than per role, so which
  concrete suit was recovered is what surprises the player, and it never spends
  the billet's carried special item. An integral system must express itself as
  behavior — movement, protection with a clock on it, care — never as
  durability, and it declares the use policy it is spent
  on so its trigger is readable from the catalog entry rather than compiled into
  the sweep. Whether a pattern carries one is a question about what that suit is
  for, not a quota: see `integral-system-slate.md`.
- **System family** — the shared name and icon every system of one effect has
  in common, such as a breach assist. It leads, and the system's own name
  follows.
- **Shared system** — an integral system defined once at the armour catalog's
  top level and carried by several suits by reference. The personal shield is
  three of these, the Low, Mid and High Tech tiers (`integral-system-slate.md`);
  a suit referencing one may add its own carried hardware and nothing else, so
  the tier's numbers and name cannot fork per suit.
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
- **Equipment template card** — the catalog entry for one primary
  family-and-grade, armor pattern, or special item: its tier, cost and copy. It is
  content a loadout card names, never something the player acquires, holds or
  assembles on its own, and owning one opens nothing.
- **Loadout card** — the unit of access, play and reward: one Weapon Loadout or one
  Tactic Sheet, collected whole. **The player only ever interacts with complete
  cards**: they collect them and pick one of each for every squad; there is no
  designer and no way to assemble a card from parts. The company's collection is the
  Armory's known-card ledger, and a squad may be given only cards in it. Markets,
  licences, patrons, salvage, warrants and victory milestones all deal in loadout
  cards, never single equipment; learning a card records the equipment it names
  beneath it as content, never as a separate way in. A card's access tier is the scarcest tier it bundles;
  a faction offers it through a channel when the faction fields any bundled equipment
  there. Starter issue and story-reserved equipment are never cards.
- **Faction equipment source** — one campaign channel through which a faction
  may make a loadout card available: ordinary market stock, licensed access,
  patron reward, or operational recovery. A source declares eligibility and
  relative selection weight; it does not itself grant, sell, or recover a card.
- **Story equipment source** — a named authored acquisition owner reserved for
  a template outside ordinary faction channels. Its declaration identifies who
  must implement the reward; it does not certify a playable mission or a completed
  reward path, and never grants technology by itself.
- **Equipment access tier** — a card's authored Common, Advanced, Prestige or
  Restricted campaign band. It is independent of grade, armor role, provenance and loadout collectible rarity; acquisition channels interpret the
  first three through shared company-history and reputation gates. Restricted is
  not a fourth threshold: no reputation or victory count reaches it.
- **Military warrant** — a named, persistent authorisation from one tradition
  that makes the Restricted **loadout cards** it lists acquirable through that
  tradition's own licence and patron channels, and through nothing else. It names
  cards, never a weapon, a suit or any other single piece of equipment, and it
  teaches nothing by itself. Always written in full: the Agreus contracts'
  *civil-security procurement warrant* is a contract term that opens no access
  tier, and neither noun stands for the other.
- **Warrant obligations** — what a held military warrant costs to keep: loyalty
  in each obligation period. A warrant that is kept is *active*; one whose
  loyalty was broken is *suspended*; one whose issuer turned hostile is
  *revoked*. An *honoured* period is one that ended with loyalty kept.
- **Market listing** — a named card a story beat has put on sale at one named
  market, or at every open market of one faction, until the company knows or
  carries it. Authored stock the market channel displays, not a fifth channel.
- **Armory** — the persistent campaign authority for collected equipment
  templates and reusable squad definitions. Materialized marine kit persists on
  the marine; the Machine Shop fits learned technology for free.
- **Telemetry** — battle-local, lifecycle-stable evidence of a combatant's
  activity. It serves the debug balance artifact for all recorded combatants
  and career evidence only for campaign marines.
- **Trait** — a captain's stable named quality. Traits belong to leadership,
  not the individual marine experience ladder; some are currently functional
  while others remain planned mechanics.

## Officer career

Officers have a separate XP/rank ladder and permanent specialty training.
Appointment grants one choice; each higher rank grants another. Each specialty
has two ranks, so five lifetime points cannot master all four paths. Existing
saves receive rank-equivalent choice budgets without retroactive mission claims.
The dossier shows exact effects, next ranks and affected home squads; deployed
scope follows frozen command assignment, including borrowed squads.

Tactical Cover improves incoming-accuracy protection by 5/10%; Recovery Planning
shortens WIA time by 15/30%; Salvage Stewardship adds 10/20 recovery percentage
points using the strongest participating officer only. Field Medicine gives
each of the officer's squads one designated medic, without spending anybody's
loadout: the first middle-team marine other than the leader, in roster order,
replaced when they fall. That one billet rule is shared by the battle and the
company squad card, so the marine marked on the card is the one who treats. The medic walks to the worst-hurt squadmate within
the cohesion leash, holds fire while treating, and restores health and armor
at a steady rate, paid for point by point out of an energy pool that
regenerates slowly (authored per rank in `squad-medicine.json`); never their
own patient. A treatment starts only when the pool holds a worthwhile amount, or
enough to make the patient whole if that is less, and never for a scratch below
the wound floor; once started it spends until the patient is whole, the pool is
empty, or reach is broken, so a medic does not stop fighting to dribble out each
sliver as it regenerates. An interrupted treatment keeps what landed and simply
stops spending. The squad never stops for medicine: the patient carries on with
the squad and the medic walks at their side while treating. In battle the medic wears a small cross badge. A treatment is shown in
the world, not as an overlay: the medic kneels over an open kit in place of
whatever special they carry, and the patient gives off hands-on work rather than
a glow -- pale dressing mist for health, grey-blue sealant flecks settling onto
the suit for armor -- on the medic's side of the body. These are independent
of legacy recruitment traits and the one-shot moral outlook. Every officer
who actually leads engaged marines receives their own mission XP and service
entry; operation command is also credited. Per-attempt receipts prevent repeat
credit while allowing repeatable jobs to teach on each fresh attempt.
See `company-view-nouns.md` and `officer-careers.md` for settlement and UI scope.

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
definition, armor pattern and visible officer specialty fight the same, and no
hidden per-squad or per-marine modifier may separate them. Officer Tactical
Cover is an explicit command modifier (5/10% lower incoming accuracy), shown
in the officer dossier and frozen with deployment; it does not silently alter
the issued quality band. Innate aptitude varies within a
declared band and is readable on the marine; it is bounded expressly so it
cannot overturn the band the player read. This is what makes threat assessment
a skill rather than a guess, and it applies to defender formations as much as
to the player's own.

The shipped primary catalog now covers five player-facing decisions. GR-RFL
is the rugged chemical-slug baseline, AR-PULSE is the flexible pulse burst,
and RL-DMR is the deliberate gauss/rail marksman weapon. GR-SMG is a
close-range flechette shredder: one trigger releases several independently
resolved projectiles with low penetration, steep falloff, and wide spread. Its
legacy `SMG` enum name and `weapon.smg` id remain only for save compatibility.
MG-SUPP is the squad automatic slugthrower: one trigger begins a long
temporal burst that participates in the existing covering-fire and bounding
model without creating a suppression status or permanent gunner class.

Simultaneous projectile count and temporal burst count are separate authored
axes. Friendly-fire discipline treats one multi-projectile release as one
trigger decision, while every projectile retains its own physical trajectory,
impact, telemetry, and presentation. Both families use the same faction-neutral
engagement and maneuver rules for player and defender carriers. The automatic
requires acquired technology and appears in the built-in Fire Support template; target
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
authored independently of grade; the introductory Milspec MG-SUPP and AR-PULSE
circulate as Common technology. Sourcing experience there would fuse axes this
domain keeps separate — grade supplies quality, profile
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
an opening marine killing an endgame one — the exchange should remain strongly asymmetric with the weapon family held
constant, and different families can widen or narrow that advantage.
`TtkReportTest` publishes the current matchup; the power-suit capacity step
requires fresh measurement rather than retaining the former approximate
fifteen-to-one figure as a current outcome.

**The arc grows through readable capability and protection.** Regular infantry
improves through equipment, issued quality, and bounded integral systems. Power
suits add an explicit armor-endurance step so fewer infantry formations can do
more work. Their protection is authored armor capacity and rating, not hidden
health or a faction multiplier. Biological structure remains infantry structure;
when the shell breaks, the named wearer is exposed. High-penetration weapons,
concentrated fire, mobility costs, and the suit's conspicuous silhouette preserve
counterplay. `power-suit-infantry.md` owns the category's direction and field
acceptance; synthetic exchange rates do not establish squad-level equivalence.

Integral systems remain capabilities rather than permanent durability upgrades.
The armor catalog refuses a system that declares added capacity, rating, or hit
points, a cooldown that never expires, a resistance of 1, or an all-round arc.
A bounded directional screen remains combat durability's finite mitigation;
changing a pattern's base armor and running its temporary system are distinct
choices and distinct authorities.

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

**Lethality is settled from measured output, not from a described play
impression.** `TtkReportTest` and the `loadoutMatchup` ladder are the instruments.
Their standing readings: a service pulse rifle in a Regular's hands kills an
unarmored marine in the open in about three and a half seconds, so movement and
target priority stay actionable; cover is the strongest single lever, roughly
doubling time-to-kill behind light cover, nearly tripling it behind solid cover
and multiplying it about sixfold behind hard cover, while every baseline
engagement still resolves. The compound arc holds: an endgame Elite with a
Masterwork weapon kills an opening Green-with-Surplus marine about thirty times
faster than the reverse kills the endgame marine in power-suit armor. The preserved
fauna and swarm exchanges still serve their scenes: a swarm runner carries a
little more structure than one rifle round, which keeps the second shooter's
choice meaningful. The bottom edge is deliberately harsh. Surplus rifles cannot hurt powered armor
at all, and pirate rosters field it at medium and high risk. That wall is left
standing on purpose: a company still on starter issue that late in the campaign
has not kept up, and the answer is better kit from the Armory, not a stronger
starter card or a briefing warning.

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

Wayline's first story relief victory offers one free technology transfer through
**A Proper Issue** in the Armory. Steady Line learns Milspec MG-SUPP; Forward Screen
learns Milspec AR-PULSE and ARM-TACH-2-RCN scout suits. Comparison is read-only; acceptance
saves one choice and never changes a marine's kit. The Machine Shop subsequently
fits any learned gear freely. The unchosen path's specs remain in ordinary open
markets and faction mission-reward pools. Existing claimed bundles retain both
paths; no learned technology is revoked. `company-view-nouns.md` owns the lesson.


The armory has three deliberately separate layers. The **collection** is the set
of loadout cards the company holds. A **weapon card** is a developer-authored
twelve-billet issue; a **tactic sheet** is the armour half — it names roles and a
tradition, and the card's own patterns fill them, best for each job
(`role-and-access.md`). The exact kit on a marine is the materialized result.

**A card defines the whole squad.** The player's flexibility is the pairing of one
Weapon Loadout with one Armor / Equipment sheet, not building either half from parts.
The early mixed sections (Tri-Tachyon Specter Section, Hegemony Arbalest Section,
Salvage-assisted Section) are the transition into power armor — a few recovered,
lower-tier suits (two, two and four of twelve) among ordinary kit — so that a whole
squad does not move into powered armor the moment the first suit is acquired. Every
sheet works the same way: the card carries every pattern its twelve billets wear and
issues exactly those, whatever else the company owns.

**Opening a defended position is a ladder, not a power-suit privilege.** A dug-in
line with emplacements asks three jobs of an attacker: kill the guns from standoff,
put a salvo into the trench, and finish it close. Each rung pairs cards that cover
more of that. Trench-Clearing Issue (rocket tubes and grenades on service rifles)
gives a company in its starter armor an even chance against defenders like itself.
Adding the Freeport Assault Section's two rail packs makes that reliable. The
Arbalest Section and the XIV Shock carry the same three jobs in heavier kit, and
only the Shock, with real weapons, opens a line held by regulars. `loadoutMatchup`'s
`trench-vulcans` scenario is the instrument that places a card on this ladder.

**Knowing a tactic sheet is separate from being able to fill its billets.** A
new company knows only Watchman's Kit. What a company learns is a card: the
knowledge and licence to build and outfit one twelve-person section. There is no
learning a suit. Outfitting all twelve in powered armour is beyond an ordinary
contractor without a licence for it, so a suit reaches the company only as the
billets of a card it knows, and learning one card never teaches another sheet that
uses the same suit. Janus teaches Tri-Tachyon recon, for example,
without granting Hegemony or Sindrian formations. Wayline's accepted refit teaches
its own formation, and the alternative becomes usable when its offered technology
has been learned. These are permanent company permissions, not another consumable
item or equipment charge. Issue requires both a learned sheet and the
sheet's own patterns, which learning its card always provides. A role the sheet does
not cover falls back to its line kit; fallback never establishes knowledge of a sheet.

**Powered sections are a controlled technology.** The sector treats them the way a
non-proliferation treaty treats weapons: a contractor may field a few powered
billets inside an ordinary section, while a fully powered section needs a licence
from a power that controls the technology. In play this is the progression ladder.
Early cards put a handful of super soldiers in each squad of twelve rather than a
unit that can clear a map, and a later licence or warrant raises that ceiling on
purpose. A story that awards an all-powered card is a deliberate rung, never an
accident of what a suit can fill.

Older companies retain the cards already assigned to their squads (a saved
profile a squad was assigned becomes its cards), along with learned cards. Other
sheets are inferred only from their explicit acquisition requirements. Reloading
never grants the whole sheet catalog; it dresses every seat from its squad's cards.

**Armour is never authored a billet at a time, and there is no way to.** The
player could once build armour definitions by cycling twelve concrete patterns,
which froze them: such a definition names the militia vest twelve times and goes
on naming it long after the company can afford siege plate, because nothing in it
says what the billets are *for*. That path is gone. A weapon definition stays
per-billet because a fire-team lead's carbine is a real choice with no role model
above it, and because a weapon has no equivalent of "the best owned suit for this
job" to resolve against. Fitting learned kit is an explicit, free Machine Shop transaction. Swapping gear
never reads or spends supplies, armaments, machinery, or credits and grants no
refunds. Marines remain personnel cargo handled by personnel logistics.

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
cards, and fitting is free. **Victory milestones grant authored cards**, each paid
once and recorded so a load never pays it again: the Sindrian Security sheet at
three victories, Trench-Clearing Issue at four, and Line Infantry Issue at five
with one of them high-risk (`VictoryMilestones`). Nothing is paid at two: the
Wayline opening's cards are A Proper Issue's choice and Checkpoint Issue on sale. Story copy may call such a reward access to military
equipment; what the company receives is the card. The ladder is deliberately
modest: a reward, not twelve marines with a delete button. A deterministic **safety
net** keeps the collection at or above a floor of cards beyond the starter pair: one
after two victories, three after five, six after fifteen, eight after thirty and
nine after forty, topping up from an authored list of low-tier cards that leaves the
Wayline refit sheets a choice and never includes a Restricted card. A new company
holds only the starter pair (Dockside Issue and Watchman's Kit). Cards already
learned or still carried in cargo count toward the floor, so faction-shaped
acquisition advances the same curve instead of being duplicated by it; the net never
revokes a card, never learns a carried card implicitly, and stops well short of the
whole collection. A save from before the card ledger knows exactly the cards it
could already field, once.

Faction source pools author which cards may enter through market, license,
patron, and recovery channels. Every card also authors an access tier. Open
markets admit Common cards only, including the two introductory Milspec technologies,
so unrestricted credits cannot buy Advanced
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
New companies start with fatigues and Cordon shells; Ward is a protection upgrade
and Lashplate remains a collectible alternative. Cordon starts personnel in the
existing Green experience band and carries no integral armor system. These are generic circulation categories for early companies and
low-end defenders, not faction-equivalent powered suits.

A loadout card exists in fleet cargo as one parameterized Starsector special item
whose data is the stable card id. (Story rewards that still hand out a single recovered
armor template, and cards held from earlier saves, use the older equipment-template item,
which keeps learning exactly as described here.) Right-click learning follows
the familiar blueprint interaction and consumes one card only when it adds a new
capability to `MarineArmory`; duplicates, invalid data, and an unavailable Armory
remain unconsumed. This interaction deliberately does not implement a vanilla
blueprint provider or write player-faction hull, fighter, ship-weapon, or industry
knowledge, so infantry equipment cannot leak into ship production or the ship
editor. Market, reward, and salvage systems create the same validated cargo
payload; the S6 acquisition ladder still owns the eligibility of each entrance.

Collectible access tier is catalog data rather than a closed
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
directly, so a learned provider card can be selected, saved, freely fitted, and deployed without a Java enum constant. Special items
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

The merged catalogs enforce acquisition ownership at application load:
every collectible card must be starter-owned, appear in at least one faction
pool through a channel that can eventually admit its access tier, or reserve an
explicit named story source. A story-only armor template declares that owner
through `storySource`; it cannot also enter a faction pool or starter issue.
The declaration does not prove that the story reward is implemented.
**A card is not awardable until its presentation is declared.** A pattern may be
authored, inspected and calibrated before its art exists; it then states why in
`presentationPending`. No cargo payload can be made for a card carrying such a
pattern, and the ordinary card list omits it, so neither a story award nor any
other channel can hand out a suit that would deploy wearing nobody's art (or,
worse, another suit's). Explicit development learning still reaches it. An Advanced
or Prestige card authored only for the Common-only open market is therefore
still stranded. A Restricted template's owner is a warrant rule instead: it must
be offered by its issuing tradition's own pool, through licence or patron only.
Any other entry, whether another faction's pool, the open market or recovery, is an
unwarranted route and fails the audit just as a stranded card does.

**Restricted equipment is approved military issue.** The six approved tier-4 power
suits are Restricted: the Hegemony XIV battlesuit and military Arbalest, and the
Tri-Tachyon Specter, League Bulwark, Knights' Holdfast and Lion's Guard Mantle. A
template is Restricted because it is a tradition's current military issue, not
because it is powerful. The pirate Foundry-Breaker stays Prestige and recoverable,
as the crude road to powered armour that nobody warrants. The tier-3 recovered
Specter and Arbalest stay Advanced. The **issuer** of a Restricted template is the
tradition its armour catalog entry already declares; it is never authored a
second time.

**A card that carries Restricted equipment is a Restricted card**, and it is the
card that a warrant names and a channel refuses. Its issuer is the tradition that
builds that equipment. No channel offers Restricted equipment on its own, and no
channel offers a Restricted card unless the company holds a military warrant naming
that card; even then only the issuer's licence and patron channels offer it. A
warrant opens the cards it names, not their equipment: warranting the Hegemony
Shock card does not open another card that happens to carry the same XIV suits. A
Restricted card is offered only by a faction that fields its Restricted kit itself,
so an independent licence that fields a card's scout suits does not thereby sell
Hegemony military issue. The collection safety net and victory milestones never
grant a Restricted card. Polity release never lists Restricted kit: a warrant
authorises the company, not whoever the company arms.

A warrant may name many cards, and a later grant from the same tradition adds cards
to it rather than rewriting it. Tiers of a tradition's cards opened by deepening
reputation are a natural extension and not yet modelled; today a tradition's cards
arrive by an authored warrant's stages or all at once by its standing warrant. A
save from before warrants named cards held one row per warranted suit; on load each
issuer's rows become exactly the cards whose every Restricted suit was warranted,
keeping the original grant's reason and day.

**Every card has an issuer**, the in-universe office that printed it: a
Restricted card's issuer is its warrant's tradition, any other card's is the
tradition that fields it, and a card no tradition claims is a free-port
outfitter's. The issuer and a serial are printed on the card's stamp; the
Restricted seal and banner (`ui-nouns.md`) are how the player sees that a card
answers to a warrant.

A tradition grants its **standing warrant** once the company is at the Prestige
reputation band and its relationship with that faction is Cooperative, well above
the Favorable a licence needs. The standing warrant opens every Restricted
card the tradition issues that no earlier warrant already opened. This is the
route that keeps every approved suit reachable, so the open routes could close
without stranding anything. Authored warrants arrive earlier through story beats,
and a later standing grant completes them without rewriting them.

The **Hegemony auxiliary warrant** is the first authored one. After *A Small War*
settles, its procurement introduction reaches a Hegemony procurement office, which
sends terms of auxiliary enrolment citing HSS Resolute's Garnir after-action report
and the company's Agreus service. The offer asks only for a Favorable relationship,
the licence threshold, because an authored route does not wait for the standing
rule's bar. It is correspondence answered at Company HQ, never a shop or a grant on
arrival. The terms state what the warrant opens, the obligations it carries, and what
it does not grant: no other tradition's Restricted kit, no release to the company's
polity, no Hegemony command authority and no immunity from Hegemony law. Acceptance
opens the Hegemony Auxiliary Section card (stage one), the recovered Arbalest section
in approved suits, offered by the Hegemony licence and patron channels and by no one
else. The Hegemony Shock card, in XIV pattern (stage two), follows the warrant's
first honoured obligation period. Declining is a
real answer: the terms are never offered again and every other route, the standing
warrant included, stays open. Every step is once-only, so a replayed answer or a
repeated honoured period grants nothing further. Lion's Guard and Knights kit is therefore not "never". It
is "only for a company that has become one of theirs", which most companies will
not.

**A warrant is a relationship, so it keeps costing something.** Without that it
would be the last unlock rather than the story of a mercenary company becoming
somebody's auxiliary (`meta-progression.md` refuses Capability with no
counter-pressure). Every warrant, standing or authored, states its terms when first
held, and the same terms bind both:

- **Loyalty:** the company takes no contract against the issuer, and keeps the
  relationship at the warrant's floor. The floor is Favorable for an authored
  warrant, the licence threshold it was offered at, and the standing rule's own
  Cooperative for a standing one.

**Service** (a contract completed for the issuer each period, with a *service
call* the issuer sent when none was) is retired: the campaign no longer offers
generic contracts to serve with (`contracts-nouns.md`, Where missions come from).
What a warrant asks of a company in their place belongs to the progression
rework (`v0.2.md`).

A 90-day period that ends with loyalty kept is honoured and the next begins; the
Hegemony auxiliary warrant's first honoured period is what opens its stage two. A
contract taken against the issuer, or the relationship slipping below the floor,
**suspends** the warrant at once.

A suspended warrant's cards are not warranted: they cannot be acquired again, and
no squad can be **newly given** one. A squad already holding the card keeps it, and
its replacements are dressed from it as from any card, because a squad's kit is
always what its cards issue. Nothing learned is revoked and no squad's card is taken
away; the warrant caps how many squads field its cards, which is where a powered
company feels the pressure over a long campaign. A suspension begins a fresh period,
and the first one that ends with loyalty kept restores the warrant.

A relationship that falls to hostile **revokes** the warrant: the suspension rules
apply permanently, and no loyal period lifts them. An authored warrant is never
re-offered, but the standing rule still applies, and a fresh standing grant from
the same tradition replaces the revoked one.

The fitting check lives in the one place a sheet's billets are resolved for a
squad (`MarineRoster.previewSquadEquipment`), not in the screens, so every route to
fitting asks it, and a refused issue says which warrant withholds it. A
card opened by a held warrant counts only while that warrant is active.

The gate refuses new acquisition and revokes nothing. A save that learned a
Restricted card through the old open routes keeps it and fits it freely: no warrant
names it, so no obligation can withhold it. That is the compatibility promise, not
a loophole to close later. Held warrants are not currently listed on
any company page. The Company HQ's client-standing panel named each warrant's
issuer, the cards it opens, how it was earned and its standing under its
obligations (next service deadline, what restores it, revocation); it was removed
when the HQ was simplified, and that readout has no home yet. A Restricted card's own
tooltip and spec sheet name the tradition that issues it, in place of the
reputation and victory thresholds the other tiers show.

**Market listings are how a story stocks a shelf.** A card dropped into a market's
cargo would not survive, because every open-market restock clears the cards and
refills from the pools. So a story beat records a listing instead, and each restock
puts listed cards on a shelf of their own before it draws the rotation. Listings
are additional slots that never displace a rotating card, and a listed card is left
out of the rotating draw. A listing bypasses the reputation and victory thresholds,
because the story is the permission. It never bypasses the Restricted tier: listing
a Restricted card needs a military warrant naming it, and a shelf never shows a
Restricted card the company is not warranted for. A listing is withdrawn while the company knows
or carries the card and returns if it stops doing so; nothing else expires it.
Creating one restocks the markets at once, so the debrief's promise is on the shelf
now. The card's tooltip names the event that stocked it. Settlement code owns
once-only; listing the same card at the same place twice changes nothing. This is global reachability, not universal faction availability.

**Arc issue cards are how the early arcs pay in ways of fighting.** Four weapon
cards carry the kit no older card fielded, each stocked by the arc beat that
teaches its lesson. *Checkpoint Issue* (satchel-charge sappers and smoke) is listed at
the Wayline broker's market once Relief settles. It is bought beside *A Proper
Issue*'s free choice, never offered as one of its paths. *Convoy Escort Issue*
(rocket tubes and one Palisade pod) is listed at the route employer's market once
Thunder Run opens the road. *Counter-Battery Issue* (two Arrestor packs and an
anti-materiel rifle) is listed at every Hegemony open market once *A Cold Account*
settles: Advanced kit, no warrant. *Builder's Guard* (satchel-charge sappers and
Palisade pods) is listed at Asharu once the Morrow's Working Passage settles. Each
listing comes from the arc's own once-only settlement and is laid only on a won
beat; a settlement that cannot place it logs and moves on rather than unwinding a
payment. The cards also circulate like any other, because a card's pool weight is
its templates' weight: Counter-Battery Issue reaches a Hegemony licence through the
Arrestor pack, the others reach independent markets and licences. The listing is
the guaranteed route; the pools are the fallback.
**A card's identity is not about the company.** Its name and lore describe the kit
as the wider sector knows it, typically an old licensed pattern or a schedule that
is simply scarce, not a thank-you, a souvenir, or a record of the player's own
battles. An arc unlocking a listing is a market event (a depot clears its shelves,
a licence reaches the stalls) rather than a gift, and kit may appear that nobody on
either side of a fight has worn.
*Trench-Clearing Issue* (service rifles, rocket tubes and grenades) is the one arc
card handed over rather than stocked: a won *Open the Junction*, the first assault
on a dug-in position, puts one card in fleet cargo from Wayline's militia stores,
named in the terms before the battle and in the debrief after it. The Junction is
won once, so the card comes once, and a company that already knows or carries it
gets none. It is a card to learn; nobody's fitting changes.
It lets add-ons keep faction identity while ensuring that a newly collectible
family, grade, suit, or special item cannot be stranded by an omitted or
permanently ineligible source.

Armor patterns are authored player kit with distinct defensive and mobility
tradeoffs. Every currently authored pattern has a collectible card and an
acquisition owner, and the catalog audit preserves that coverage as content is
added. Recovered Specter is a guaranteed cargo-card reward for Limited Warrant
on Agreus after the Shepherd actually joins the fleet and North Weir is restored.
Learning it exposes its matching heavy fitting sheet; reward settlement never
changes issued equipment. Recovered Arbalest is the guaranteed cargo-card reward for The Yard's Owners.
Learning both recovered roles exposes Salvage-assisted Section, with eight regular,
two powered assault and two powered support billets. Explicit fitting preserves
the named squad and ordinary lift seats. Development test controls remain available. Neither A Cold Account completion nor the grounded
Shepherd's repair automatically learns either pattern.
The pirate Boarding Jack is the third recovered-rung assault suit, worn by two
assault billets in the Boarding Jack Section card. Its template is story-only
(`storySource` names *A Small War*), so no market, patron, licence, recovery or
milestone route can reach it, and no card carrying it is awarded until its
presentation is declared.
Existing military patterns retain their acquisition channels and prior ownership.
The pattern owns its explicit armor class and the deployed armor capacity, rating, movement modifier, and
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

Each billet has one special-equipment slot and may have a second, the **belt**,
which holds only a thrown item: smoke or a hand grenade. The belt exists so one
marine can carry a screen and a frag together (owner, 2026-10-23). It has no
action of its own: a belt item is swapped into the special slot to be used and
the slot's own item comes back once that action ends, so every channel, aim and
throw still runs through the one slot. A card that issues a belt item teaches
it like any other special. The item is a stable
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

The built-in identities are the rocket launchers, the SRM-POD shoulder pod, the
anti-materiel rifle, smoke grenades, mag-clamp satchel, fragmentation and
incendiary grenades, the flamer, the comms radio, the interceptor pod, the
interceptor pack, and the field mortar. Launchers and the rifle are direct-fire
activations, the shoulder pod is its own `shoulder-pod` activation, grenades and
the flamer are arcing weapon activations, smoke, satchels and the radio are
utility activations, and the interceptor pod, pack and mortar are deployable
activations, but only the item definition owns loadout identity, catalog copy, resource
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

An equipment presentation separates catalog art, attached carrier layers,
deployed-world art, and preview state. Carrier transforms are authored in
shoulder-width coordinates with carried/using states, pivot, occlusion,
optional recoil, and whether activation temporarily replaces the primary.
The actor is the default attachment; a definition may instead select the torso
or either shoulder. The live layered-unit renderer, Armory sample soldiers, and
development preview gallery resolve those transforms through one composition
helper.

An integral system may author optional carried hardware in its own presentation.
The fitted armor's actual system identity selects that hardware, independently
of the carried special item. It stays visible after ammunition is spent and
during cooldown, does not replace the primary, and does not infer a shot from
the duration of an active effect. The Specter frames' shield carries a small
screen emitter. Those art choices do not grant capabilities.

**Shoulder pod.** The SRM-POD (`special.missile-pod`) is a carried special in the
SRM family, beside the SRM-1 rockets, and it is no longer any suit's integral
system: the Packframe, both Arbalests and the Aegis composite are plain armour.
Its `shoulder-pod` activation never runs through the hand's channel. The pod picks
its own target, the best visible hostile inside the micro-missile's range and no
closer than the item's authored standoff (`sighted-standoff-contact`), and fires
the referenced weapon's whole salvo while the carrier keeps holding and firing the
primary. Each salvo spends one of the item's uses and starts the weapon's own
cooldown; its left-shoulder carrier layer is visible whenever it is carried and
never replaces the primary. The item may declare a physical shoulder mount and
muzzle geometry. Both the resolved round and its visual shot start at that
muzzle, a committed launch drives the mount's short recoil and flash, and the
muzzle cannot reach through a blocking wall or edge to authorize a shot the
carrier could not physically launch.

Pod use is coordinated within a squad. The first eligible carrier may launch
immediately; the squad then waits the item's authored salvo interval before
another carrier may spend a use. Opportunities rotate among carriers who are
currently ready and have a legal target and muzzle. A dead, empty, blocked, or
out-of-range carrier cannot reserve the next turn. Waiting neither spends
ammunition nor interrupts rifle fire, and another squad has its own cadence.
Individual ammunition and cooldown remain with each carrier; the referenced
weapon still owns the missiles within one salvo. The Hegemony Fire Support
Schedule issues it to two automatic riflemen, and Tri-Tachyon defenders carry it
in their roster special weights.

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

Direct control replaces the wearer's smoke reservation decision with one
explicit cursor-point request. Controlled carriers leave the autonomous carrier
selection, while the same authored throw channel, midpoint ammunition debit,
flight, and shared opacity execute the accepted request. Its committed point
survives handback so ordinary preparation can finish the throw. It creates no
new smoke cooldown or squad advance order.

The directly controlled wearer's mitigation-granting integral shield likewise
waits for explicit activation; its other suit systems retain their policies.
Activation still uses the integral-system owner and its authored resources,
duration, cooldown, and movement effect. Protection faces manual mouse aim and
reads the ordinary directional mitigation pool.

The Breachhand is reusable contact demolition for a marine who already
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

The built-in Fleet Assault
weapon doctrine issues exactly one
frag carrier across its twelve billets. All built-in defender roster profiles
author their own low/medium/high bulk and elite weights: the execution policy
is faction-neutral, while availability remains faction-shaped and risk-scaled.

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

What is drawn is the engagement, not the coverage. No ring marks the ground a
pod or a pack covers: a standing circle around every mount and every pack
wearer clutters the field and says nothing a player acts on, while the moment a
mount fires, connects or misses is the thing to see.

The first deployable is the **Palisade interceptor pod**: a folding cluster gun
a marine sets down where they are standing, which shoots down incoming warheads
crossing its bubble until its rounds or its cell run out. Placement is a short
committed channel and spends one carried pod. It is placed reactively — when
hostile ordnance is already in the air and headed for the ground the carrier is
on — so it costs nothing in a battle where nobody is firing missiles, and a
carrier standing inside a friendly pod's existing bubble declines rather than
stacking a second. Like every other executor, the placement authors no path,
clears none, and never retargets its carrier.

The **Arrestor interceptor pack** does the pod's job worn instead of set down, for
a squad that has to keep moving. It is Hegemony issue where the pod is not: a
chemical-motor missile kit in khaki plate and amber lights, the line army's
answer to shellfire, while energy point defence is the Tri-Tachyon direction.
A pod placed by an advancing section is left
behind by it, and the placement channel stops a marine in the middle of a bound,
so in measured fights an attacking squad almost never put one down and one it
did put down covered ground nobody stood on any more. The pack is switched on
rather than placed: the same reactive trigger, but no channel and no halt, so it
can come on mid-bound. It mints nothing — the carrier *is* the mount — covers the
ground around wherever they now stand, and goes with them when they fall.

The pack is a miniature launcher rather than a gun, and **its engagement takes
time**. The pod's burst is resolved where the round is; the pack's interceptor
is a missile in flight, launched like a Javelin: a charge kicks it out of its
tube, it climbs and coasts unlit for a moment, then its motor lights and it
sprints onto where the round will be when it arrives, and only then does the
round come apart or fly on. The coast is part of the flight, not decoration —
the pack has to commit that much earlier — which is why its powered stage is
fast. So a round that would land before an interceptor
could reach it is not engaged at all, and a shell already over the section is
past saving — the pack has to see rounds early, which is why it reaches further
than its gun-armed cousin while being the lesser mount in every other way: a
slower cycle, a poorer chance and six interceptors. It takes the odd shell off
a moving section rather than refusing a barrage. A pod is held ground; a pack
is an escort.

Placement never seals the cell it stands on. A carried pod sits on the floor its
carrier was standing on; closing that cell would both trap the carrier and shut
a navigation edge under existing paths. Only an emplacement that actually sealed
a cell re-opens one when it dies.

**A deployable leaves behind an actor.** The category's shared part is the
carried item's channel: an activation that commits the carrier, freezes them for
a short build, spends one piece of hardware, and enqueues a placement that the
serial pass resolves. A **placed emplacement** is an entity on a cell, with hit
points and armour, targetable, killable, drawn by the ordinary unit pass, and it
takes its survivability, geometry, and gun from the emplacement catalogs.

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
   roster command freely fits learned technology onto marines.
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
transactions. Progression supplies equipment-template ownership and the quality meaning those transactions materialize.

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
outside.

Mission briefing follows the same visible-quality law. For ordinary generated
work it reports the selected company's issued Green/Regular/Veteran/Elite
distribution beside a coarse risk-derived opposition expectation, while tier
and recommended squads describe quantity separately. The comparison is advice,
not an invisible strength calculation or a source of battle scaling; it must
not prevent a valid understrength fire team from attempting the work.

**The company roster ranks the company without arithmetic.** The Barracks'
Company Roster lists every marine on the books (the fallen are not) on one
comparable row: squad, status with the return day of the wounded, aptitude, the
band their issued armour sets, the primary and its grade, the armour, and the
service record that also decides seniority. The three quality axes are drawn as
**absolute** four-step bars, never scaled to the company, because a relative bar
makes a company of Limited marines look uniformly excellent; the step holding the
company's median aptitude is outlined so each marine also reads against the
company. Every column sorts, quality columns best first. Captains' traits read on
the officer dossier, and a trait with no mechanic says so rather than claiming
one; outlook traits read as character. Retained screens assign their compact text
faces by role (heading, body, compact) rather than one display face everywhere.

**The battle squad panel says who is doing the work.** Hovering a marine's card
in the selected-squad panel shows a running line for this mission: rounds landed
out of rounds fired, kills, damage dealt, and what the marine is doing now. That
status reads only state the simulation already keeps: *engaged* while the marine
holds a target, *wounded* below full health, otherwise *holding*. There is no
per-marine suppression state, so the panel does not invent one. The marine with
the most kills (then most damage) carries a warm mark on their card. Nobody is
marked until someone has landed a round, so the mark never names a marine for
doing nothing. The record is mission telemetry read after the tick and refreshed
while the card stays hovered; it never feeds back into the fight.

Avoid stacking redundant
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
  pool goes. The world treatment does not draw cooldown. Direct control's
  separate equipment card reads the existing cooldown because the player now
  chooses when to raise that wearer's shield.
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
- Defenders and the player draw a pattern's system from the same catalog entry.
  A defender-only tuning field, a second catalog, or a faction branch in the
  sweep is a defect: a recovered suit must behave identically to the one it was
  taken from, in both directions.
- Armor class is explicit pattern data, never inferred from role, tier, color,
  system, or faction. STANDARD and POWER_SUIT issue plans never silently substitute
  for one another. A missing class on legacy external pattern data means STANDARD.
- Refitting a marine into a power suit preserves identity, career, organization,
  twelve-person squad capacity, and ordinary named lift seats. No new roster or
  battle unit type is implied.
- A heavy battlesuit remains a one-person infantry billet using infantry
  weapons, cover, pathing, and casualty authority. Mech chassis, mounts,
  lances, and support delivery remain Mechs authority.
- A loadout card is the unit of access, a template is catalog content a card names,
  the Machine Shop fits a held card for free, and materialized kit belongs to the
  marine. None is a synonym for another.
- A cargo loadout card is consumed only by successful Armory learning. It never
  becomes player-faction ship-production knowledge.
- A billet carries at most one special item and one thrown belt item; nothing
  but smoke or a hand grenade rides the belt. Utilities do not become
  `WeaponDef` entries merely because they share that loadout slot with guns.
- No marine has a melee attack. Contact work is a planted satchel, never a
  strike.
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
