# Company view nouns

Status: ACTIVE — organization, Fleet Armory authoring, and cross-surface company read models continue to evolve.

Written: 2026-08-23

Updated: 2026-09-02 — a loadout card's twelve-billet distribution reads as one
joined line built from one entry per named thing, and each entry opens that
item's spec sheet; an entry counting items that need not be the same item names
nothing and opens nothing.

## Purpose

Company view makes the player's force legible as an organization wherever a
decision depends on it: between contracts, while assembling a deployment, in
battle, and after action. Most of the domain is a read model over the campaign
roster and contract state, not a second company simulation and not a
player-order layer. The Fleet Armory, the Mech Lab and the Boat Deck are its
deliberate authoring
seams: the first assigns reusable weapon and armor equipment definitions to
whole squads while preserving exact materialized kits; the second refits
persistent support squads.

This domain owns the shared language and presentation shape of the company. It
does not own personnel persistence, equipment progression, contract resolution,
financial accounting, or battle command. Those systems remain authoritative;
company view composes their stable outputs.

## Vocabulary

- **Company** — the player's aggregate force. There is no separate persisted
  company entity; `MarineRoster` and the campaign authorities supply its state.
- **Officer command** — the line squads assigned to one active
  `MarineCaptain`. Officer capacity is denominated in squads.
- **Squad** — the persistent, player-facing formation: up to twelve marines in
  three fire teams. Its identity, name, roster order, officer assignment,
  stationing binding, and NCO billet live in the campaign roster.
- **Fire team** — four adjacent squad billets derived from roster order. It is
  the Fleet Armory's player-facing equipment tier, the lift-capacity unit, and
  battle AI maneuver element. It is not another deployment selection or
  player command target.
- **Weapon doctrine** — one reusable squad-wide definition of twelve ordered
  primary, grade, role, and optional special-equipment issues. Special equipment
  belongs to this definition even when the item is a utility rather than a weapon.
- **Armor doctrine** — one reusable squad-wide definition of twelve ordered armor
  issues. It is independent of the weapon doctrine, so either half may change
  without redefining the other.
- **Squad equipment issue** — the explicit atomic transaction that resolves one
  weapon doctrine and one armor doctrine into the selected squad's exact twelve
  materialized kits.
- **Squad founding** — one trailing `+` formation card in the Fleet Armory gallery
  that consumes twelve Marines plus baseline arms, supplies, and provisions before
  creating a full named line formation. Its live material bill stays inside that
  future card slot; a partial bill creates neither personnel nor squad identity.
- **Billet** — one stable position in the squad's twelve-place equipment
  establishment. Alpha, Bravo, and Charlie inspect consecutive groups of four.
- **Conformance** — whether a squad's current personnel and materialized issue
  match its assigned doctrine pair. Wounds, vacancies, later individual mutations, and
  future equipment loss can degrade conformance without erasing intent.
- **Marine** — the persistent individual. Their equipment and quality belong
  to the personnel and progression domains; company view presents them in
  formation context.
- **Reserve pool** — a roster holding area, not a deployable or commanded
  formation. It never inflates command, formation, or field-strength counts.
- **Task force** — the officer commands represented by the squads selected for
  one operation. It is derived for deployment, not persisted as another roster.
- **Battle squad** — a battle-lifetime tactical realization of a campaign
  squad at one landing zone. It is not a campaign authority.
- **Whereabouts** — a derived statement of a squad's current role: available,
  stationed, recovering or understrength, or part of a live mission snapshot.
  It is not independently persisted.
- **Mech Lab** — the shipboard room over the persistent support squad and finite
  mech component stock defined by `mechs-nouns.md`. It presents and refits typed
  hardpoints, and uses vacant gantries to fabricate persistent chassis, but mutates
  them only through the cargo-backed campaign workshop authority.
- **Barracks** — the read-only shipboard quarters browser for squads currently
  carried aboard the flagship. It presents roster truth through a physical room;
  it does not author equipment, recovery, stationing, or personnel state.
- **Boat Deck** — the shipboard room over the company's ship's boats, standing
  in the hull's own hangars. It presents the deck and refits one boat at a time,
  mutating nothing except through the cargo-backed boat workshop.
- **Campaign boat** — one persistent, company-owned ship's boat: a stable id, a
  tail name, the pattern it is built on, and one fitting in each of its slots.
  It is the campaign authority that freezes a battle airframe; the sortie flying
  that frame never reaches back into it. `air-nouns.md` owns the pattern and the
  frozen frame.
- **Fitting** — installed work in one of a boat's **slots**: plating, which is
  hull, and drive, which is speed and acceleration. Each slot has a standard
  fitting every boat is built with and upgrade tiers above it.
- **Boat workshop** — the atomic cargo-backed authority that installs a fitting
  and builds a boat into an empty berth. It shares the fleet's hold with the
  Mech Lab rather than owning a second one.
- **Lost boat** — a campaign boat whose frozen frame was shot down on a mission.
  It is struck from its berth and never comes back. `air-nouns.md` owns the
  battle-side ledger it is read from.
- **Vacant berth** — a berth of the hull's with nothing standing in it, which is
  what a lost boat leaves behind and the only thing a boat can be built into.
- **Passenger casualty** — a marine who was still aboard a boat when it was
  lost. They are fallen the same way a marine shot on the ground is fallen and
  roll the same recovery outcome.
- **Boat fabrication** — the workshop building the hull's own pattern into a
  vacant berth, at standard fit, for a bill authored per pattern. The player
  does not choose the pattern, because the hull already has.

## Surface boundary

The retained Company -> Squad -> Fire Team hierarchy is shared across two rooms
with different intent. Barracks is the ordinary read-only browse path: selecting a
squad visits its assigned quarters and muster. Fleet Armory is the deliberate
inspect/edit path for equipment definitions, exact billet comparison, and issue.
Its selectable company and squad cards retain readiness, recovery, and reinforcement
in formation context. A reinforcement control is a secondary card action; selecting
the rest of an Armory squad card still enters that squad.

Fleet Armory is the only Armory route. The former Armory Administration shell,
its individual-kit browser, fire-team template designer, squad-arrangement editor,
and embedded Mech Lab were retired rather than retained as duplicate UI. Personnel
reinforcement now lives on formation cards. Mech Lab has returned as its own retained
surface over `MechBay`, not as a tab in a catch-all screen.

The deployment surface remains squad-granular for commitment, but a selectable squad
card is not merely its name. It shows the current twelve-billet establishment as three
derived fire teams, including vacancies and personnel who are not ready to deploy.
Each occupied billet carries compact primary/special shorthand and separate visual
firepower and protection measures. Hover inspection names the marine, availability,
full primary, armor, special equipment, integral suit system, and issued profile.
The officer shown above those billets is the actual leader resolved by deployment
policy: the squad's fit home captain, or the operation commander when command is
inherited. The portrait is that `MarineCaptain`'s persisted Starsector sprite path,
not a UI-owned likeness or a name-based substitute.
Those two measures reuse `LoadoutEffectiveness` against the catalog ceiling; they do
not collapse reach, specials, systems, morale, terrain, or orders into a fictional
overall power score. The card remains a projection: its only command is still whole-
squad selection.

## Organization and leadership

The full organizational hierarchy is **company → officer command → squad → fire
team → marine**. Hosts stop at the depth their decision needs: the Fleet Armory
opens squad equipment and fire-team inspection, while deployment and battle command
remain squad-granular.

Officer command and enlisted leadership are separate ladders. Lieutenant,
Captain, Major, Lieutenant Colonel, and Colonel caps are respectively 3, 6, 10,
16, and 24 squads; any marine-count form is explanatory display only. Enlisted
rank follows squad and team billets rather than accumulating as an independent
promotion track.

Each non-empty line squad has one roster-derived NCO. Other manned fire teams
have their own enlisted lead. Leadership is recalculated after membership,
fitness, or relevant experience changes using rank, then experience, then the
senior billet. Equals are separated by where they stand on the roll, not by a
marine's identity: an identity here is a generated one, so a seeded roster that
resolved its ties that way produced a different NCO every time it was built —
which the frozen manifest now depends on, since it seats that NCO first. A wounded senior may resume the billet on return; a
lost or unfit leader is replaced deterministically. Fire-team membership is
likewise derived from current billet order, so transfers and casualties cannot
leave a second stored partition behind. The historical squad roll may retain KIA
and MIA records, but those records do not occupy current billet positions; active
and WIA marines preserve their stable order and later replacements fill the open
positions derived around them. A WIA marine continues to hold a billet until
recovery and therefore cannot be silently replaced into an overstrength squad.

Personnel reinforcement is a roster transaction rather than a recruitment shop.
One action fills true open billets from ready named reserves first, then directly
removes marine personnel from the player's fleet cargo for any remaining vacancies.
Creating the corresponding named billet holders is an internal materialization of
that consumed personnel, not a separate currency conversion or player-facing enlistment
step. Mission shortfalls use the same draw in place instead of routing through another
Armory screen. The transaction does not invent personnel, move WIA marines out of
recovery, or claim that replacement equipment conforms to the squad's assigned doctrines.
Armory formation cards and the mission-shortfall action pair that transaction with
the base game's Marines icon; the icon identifies the cargo commodity and does not
change the reserve-first draw order.
The company, squad, and fire-team projections report WIA counts and the earliest
remaining recovery clock; the named-marine view reports that individual's remaining
hours and days.

`homeCaptainId` is a squad's durable organizational default, not a history of
temporary mission borrowing. Stationed squads remain under one officer because
a garrison is a posting, not a task force.

## Squad equipment doctrines

Routine Armory authorship sits at squad scale. `MarineSquad` persists one weapon
doctrine id and one armor doctrine id. Each reusable definition describes twelve
ordered billets, so a single choice may still mix equipment by team, role, or
individual position. Weapon doctrine owns primary family, grade, role label, and
optional `specialEquipmentId`; armor doctrine owns only protection. A smoke grenade
or satchel therefore follows the Weapon slot without being reclassified as a
ballistic `WeaponDef`.

The two selected definitions compose into one exact twelve-billet preview. Alpha,
Bravo, and Charlie remain four-billet inspection slices: selecting a team changes
which four named marine cards are visible but does not reset or narrow the squad
transaction. Each card combines the persistent marine's rank, status, aptitude,
experience and career with the projected role and equipment. Weapon and armor
figures use the same catalog and individual-profile rules as battle, while exact
values remain legible beside comparative capability meters. Live portraits cycle
the authored idle clip with stable per-slot phase offsets; headless evidence fixes
the phase for deterministic comparison.

The three fire teams occupy compact Alpha, Bravo, and Charlie tabs directly on the
FIRE TEAM context line rather than consuming another status row or side column.
Readiness and recovery stay with the affected marine: ready, wounded, missing, killed,
and vacant dossiers use distinct border treatments while explicit status and RTD time
remain readable inside. A dossier's upper-right
portrait sits beside identity and service history; compact class, weapon-tier, and
armor-tier badges replace implementation terms with formation-readable language.
Weapon, armor, and specialty descriptions come from their owning data catalogs, not
from Fleet Armory markup. Those descriptions open as the shared spec sheet
(`ui-nouns.md`) when the corresponding equipment label is hovered; the normal card keeps
identity, comparison meters, and exact values continuously visible instead of clipping
lore into the comparison surface. Each issued weapon, armor pattern, specialty item, and
carried integral system pairs its equipment label and sheet heading with the
representative vanilla faction flag authored by its owning catalog or armor tradition. Health, armor, resistance, and actual movement speed share
the durability-and-mobility meter block; damage, range, accuracy, and sustained output
share the weapon block. Catalog-wide ceilings keep every comparison stable across
team selection and equipment changes.

The selected team's four dossiers form a two-by-two viewer beside a vertical loadout
browser. Weapon and Armor are mutually exclusive tabs over that browser, and rarity
filters bound a growing list without reducing the dossier detail. The browser contains
only definitions the company actually knows; unknown definitions have no placeholder,
silhouette, name, tier, rarity, provenance, or lore to spoil future discovery. Each
known entry leads with an authored power tier, campaign rarity, provenance, substantial
setting paragraph, and deterministic twelve-billet distribution. That distribution reads
as one joined line and is built from one entry per named thing, so each entry opens that
item's spec sheet (`ui-nouns.md`) on hover: a weapon at the grade the loadout issues it,
an armour pattern, a carried integral system. An entry that counts items which need not
be the same item — a specialty count over a mixed issue, a capability family filled from
two patterns — names no single catalog item and opens nothing, and a role count is a
count rather than a catalog item at all. Tier describes
expected capability while rarity describes acquisition scarcity and presentation.
Rarity is never a random-roll weight, and selection never rolls equipment from a pool.
Faction-authored entries pair that provenance with the owning faction's vanilla flag,
using the same logo art Starsector assigns in its faction definition. Mixed outlaw
traditions name one representative faction explicitly in authored presentation data;
company-authored definitions carry no borrowed faction badge.

The separate equipment designer authors **weapons only**, and uses the same visual
language before a definition is saved: each billet card keeps a compact live
render in its upper-right corner, wearing whatever the squad's assigned tactic
sheet issues that billet — read, never edited, because a marine still has to be
drawn wearing something and the honest something is what they would deploy in.
Each card also reserves a short line for role or equipment flavor, and shows
fixed-scale capability meters beside exact values. Those scales use catalog ceilings rather than the other
three visible billets, so cycling an item produces a meaningful at-a-glance change
and team selection cannot rewrite the comparison baseline.

Issue is explicit and atomic. The preview prices only changed incoming equipment;
unchanged kit is free and removed kit grants no cargo refund. A stationed squad,
any vacancy or WIA billet, a missing equipment template card, or insufficient
supplies, heavy armaments, heavy machinery, or food leaves cargo, both doctrine ids,
and every marine's existing kit untouched. Success spends the complete cargo cost,
writes both ids together, and materializes all twelve exact issues onto
`MarineSoldier`, which remains the battle-facing state consumed by deployment.
Later inventory changes do not silently optimize or reshuffle that result.
The issue surface renders each nonzero line as a live-spec commodity icon with
available / required quantities and marks shortages in place. Names, paths, and
inventory quantities remain projections of the commodity registry and cargo
authority, not a Fleet Armory-owned material catalog.

Built-in weapon definitions are authored deterministic distributions rather than
live best-fit allocators. This makes faction-flavored profiles such as **Luddic
Path Assault Equipment** explainable in preview and stable after issue.
Player-authored weapon definitions persist in `MarineArmory` beside that immutable
built-in catalog. Authoring consumes nothing and may mix every billet, but every
referenced primary-and-grade and special must have a collected equipment template
card.

Armour deliberately has no equivalent. A tactic sheet is a role mix plus a
tradition, resolved against owned stock every time it is read
(`role-and-access.md`), so it cannot be frozen and there is nothing for a player
to author into it. Cargo, readiness, and stationing constrain the
later squad issue.

Catalogued armour patterns form explicit capability bands rather than a single
faction ladder. Tier I is the universal unpowered baseline. Tiers II, III, and IV
each contain multiple faction-authored equivalents, and equal tier means comparable
battlefield ambition rather than identical values: Hegemony patterns emphasize
standardized plate, Tri-Tachyon composites trade raw protection for movement and a
harder firing solution, League patterns stay balanced, Church and Knight patterns
favor resistant legacy armor at a mobility cost, Sindrian patterns carry deep visible
plate reserves, and outlaw rigs pair crude material volume with poor resistance.
Those differences live on concrete armor-pattern stats and twelve-billet issue, never
on a hidden faction modifier or set bonus.
Edits remain a draft until **Save as New**, so a definition already assigned to a
squad never silently refits its materialized equipment.
The early **Frontier Security Equipment** and **Frontier Patchwork Protection**
pair deliberately spend scarce pulse rifles and militia plate on the first billet
of Alpha, Bravo, and Charlie while their line marines retain field rifles and
unplated fatigues. Starting and migrated armories collect every template referenced
by that exact issue; the live cargo preview remains honest about the cost to
materialize it.

**Corporate Blacksite Breach** is the built-in player assault profile:
its twelve exact billets contain one Shattercap frag carrier, so its authored
distribution stays legible. Player-authored definitions must likewise resolve to
twelve exact billets before the same issue transaction can commit.

Legacy four-billet templates, three-template arrangements, and their persisted ids
remain readable compatibility input for existing saves; they are no longer writable
player intent and have no Fleet Armory UI. Load migration composes each complete
legacy three-template intent into deterministic player-owned Weapon and Armor
definitions without rewriting any marine's current kit. The first later successful
squad issue clears stale per-team assignment ids so only one equipment authority remains.

Equipment template cards are literal permanent collectibles owned by Progression.
They are not a deck, hand, consumable, or squad definition. Definitions remain
reusable intent; base-game fleet cargo pays only when changed kit is materialized.
The browser may give rare known entries stronger borders, color, and provenance copy
to make acquisition feel rewarding, but those effects never change definition contents
or combat rules.

## Mech Lab

The Mech Lab is the focused squad-first surface over `MechBay`; it does not reuse
personnel doctrine. Campaign mechs retain individual identity inside a selected
support squad of up to four chassis. The retained room shows the whole squad and a
separate asset-selection screen; choosing an assigned chassis opens its existing
gantry without changing lance composition.
The fitting header also provides explicit previous/next controls over the lance's four
numbered gantry pads. Reaching a vacant station clears the chassis selection and opens
the chassis fabrication catalog; from the facility overview, the same action begins at
the fabrication action drawn and hit-tested over the vacant physical pad rather than at
a remote text button. Its surface follows the projected five-by-seven pad at any camera
or UI scale; it is neither a fixed pixel box nor a control snapped to one floor cell.
Vacant summary cells are informational, while occupied cells remain direct
refit targets. The asset browser remains the direct way to jump across lances.
When a generated ship carries more than one vehicle-bay compartment, a separate room
navigator cycles those actual compartments in fore-to-aft order and derives the ship
breadcrumb from the bay currently framed. A one-bay ship spends no chrome on the pager.
Entering the room or selecting a lance presents the wider facility first. This
overview has no selected chassis and therefore renders neither equipment selectors nor
socket details; a compact four-gantry rail identifies the assigned assets without
taking width from the room. The camera always includes all four physical pads rather
than adapting its crop to the occupied count, so vacancies and future assigned armor
retain stable places. Choosing an occupied gantry establishes the selected asset,
eases the shared camera into it without interrupting the technicians' task clock, and
reveals the fitting controls. Selecting the already-occupied `MECH LAB` room route
clears that selection and returns to the lance overview rather than acting as a dead
control. The focused workspace gives its three primary regions to the equipment
catalog, a wide top-down fabrication bay, and the socket rack. Selecting a location
scopes the equipment catalog. Refittable hardpoints list compatible registered
assemblies, their finite owned/free count, provenance, capacity, and material bill.
Fixed cores and ammunition remain inspection-only.

The fabrication bay is a diegetic, flat top-down ship facility rather than a neutral
diagram or pseudo-3D illustration. Its floor, walls, four hazard pads, registered
industrial fixtures, service gallery, and connected cross-corridor reuse the battle
renderer and the same room vocabulary as generated facilities. The selected mech uses
the battle compositor's actual layer order and hull-relative mount transforms, while
workers use real layered infantry dolls on shared ambient routes for welding, parts
movement, inspection, and coordination. Every occupied berth publishes two shoulder,
two waist, and one head places to stand. At runtime, their focus resolves against the
parked asset's oriented service envelope, whose beam and length belong to that asset
rather than to a mech-only UI assumption. Fixture work retains its authored point of
interest. The torch and sparks project either focus through the room camera, so work
lands on a walker, vehicle, or fixture rather than open deck. Machines stand at the
continuous centre of their exact berth footprint and face
cardinally along the berth's long axis toward its mouth and the room's shared inboard
service lane; that same authored heading remains their way out. Those actors are
presentation-only and do not create a second schedule, labor,
inventory, or refit authority. Wide-screen layout is the
reference composition; narrow and user-scaled layouts retain access through bounded
scrolling rather than compressing the room until every label is simultaneously visible.

The weapon-refit catalog is the current click-to-commit form of a future drag gesture.
Ballistic, energy, missile, and omni are compatibility rules; component slot cost and
the fabrication bill remain independent constraints. A free owned assembly installs
directly. If none is free, one atomic campaign command rechecks player cargo, consumes
the whole bill, adds one finite assembly, and installs it while returning the outgoing
assembly to stores. The missile mini-fab retains its existing finite-stock command;
cores and integral ammunition remain locked.

A vacant gantry presents the three established chassis patterns rather than fake
equipment controls. A successful commit consumes ordinary fleet cargo and creates a
persistent campaign mech with stable identity, default doctrine, standard replenisher,
and its complete standard roll-out fit. It cannot exceed the lance's four physical
gantries. Material badges resolve names and icon paths through the same shared live
commodity presentation seam used by Fleet Armory and battle resource readouts—supplies,
heavy machinery, metals, and rare metals use the artwork that ships with the base game
rather than copied mod assets.

The same selection-and-catalog grammar may serve tanks and future scarce heavy armor.
Each asset class still supplies its own projection and socket layout, so a shared
room does not collapse vehicles into mech chassis semantics.

Subsystem assignment is an atomic inventory transaction. Installed copies
count against owned quantity and the target's current component returns before
the candidate is checked. The active squad freezes into plain deployment values
only when a sourced Mech Support power is resolved, preserving the same rule as
personnel deployment: campaign objects do not enter battle.

## Boat Deck

The Boat Deck is the Mech Lab's grammar over a different noun: the room shows
the hull's hangar as it actually is with the boats standing on their berths, an
overview card per berth, and — once a berth is chosen — that boat's slots and a
catalog scoped to the selected one. Selecting the already-open `BOATS` route
puts the deck back rather than acting as a dead control, the way the lab's own
route does. A hull with more than one hangar gets the same fore-to-aft pager,
and one with none shows the route greyed, exactly as a hull with no vehicle bay
shows no lab.

**The hull comes with her boats.** A campaign starts with every berth holding
the hull's own pattern at standard fit, so a boat is never something the company
must buy before it can leave the ship. What it must do is replace one it has
lost: a vacant berth is built back up through the same workshop, at standard
fit, for a bill authored per pattern. The player names a berth rather than a
pattern, which is the one place this departs from the lab's chassis forge — a
lance is a choice of machines and a hangar is a fitting of the hull.

**A boat that was shot down is gone.** The deck does not quietly replace it on
the next read. Reconciling against the ship the company is already on keeps
every berth exactly as it is, empty ones included; only a move to another hull
compacts the survivors and fills what is left with her own boats. A read that
refilled a hole would make the loss unobservable, which is the same as its not
having happened.

**Who was aboard went down with her.** The marines still aboard on the sortie
that was lost are fallen, and roll the same outcome as a marine shot on the
ground. Passengers of later cycles never left the ship and are untouched —
neither survivors nor casualties, because their sortie never flew.

**The lift is what is left.** What the deck offers a briefing is the boats
standing in berths, so a company a boat down lifts one boat less and a company
with none is told the mission cannot launch. That is the existing rule doing its
job rather than a second check about losses.

**The picture follows the deck.** A vacant berth has no boat standing in it on
the hangar picture and is drawn as the hole it is; a fabricated one has a boat.
The ship is laid out again when the set of held berths changes rather than
patched live, because tearing a parked airframe out of a running deck scene —
and its servicing, and the hands walking to it — is a second mechanism for a
rare event, and one that would have to agree with the first.

**The boats are the company's; the berths are the ship's.** The boat deck never
holds a berth count of its own: how many berths there are is asked of the hull,
and what stands in them is what the company owns. When the company moves ship
its boats go into the new hull's berths in berth order up to her count; boats
beyond it, and boats of a pattern her bays do not hold, are left with the old
hull and the room says so in its summary. Vacant berths on the new hull are
filled with her own boats at standard fit.

That reconcile is idempotent and runs **whenever the deck is read against the
ship the company is on** — the room attaching and the lift itself — so a stale
deck is never presented and never flown. It is a read-time rule rather than an
event somebody has to remember to fire on a transfer, and one authority
(`ShipsBoatsAboard.reconcile`) knows what feeding it means; the room decides
only when.

**A fitting is installed work, not a spare.** Plating is welded on and a drive
is built in, so unlike a mech's components there is no finite stock, nothing
returns to stores, and the outgoing fitting is scrapped. That is the one
deliberate departure from the mech component model and it is a fact about what a
fitting is rather than a shortcut past inventory: the catalog rows carry a
material bill and an affordability reason, and no owned-versus-free count,
because there is nothing to count. Material badges resolve through the same
shared commodity presentation the Fleet Armory and the Mech Lab use.

A failed fit changes nothing but the room's feedback line — neither the boat nor
the hold — because the workshop rechecks affordability at the commit and spends
the whole bill before installing anything.

**Presentation conveys the deck; it does not own it.** The picture is the ship's
own hangar drawn by the deck scene, and the boats in it are the aircraft the bay
actually keeps and turns round. What the room adds over that render is the one
thing a render cannot say — which berth is selected — so a click on a berth and
a click on its card are the same act. The campaign deck is one berth list across
the whole ship while a bay knows only its own, so a bay's berths are read as its
share of that list; the two counts are measured equal rather than guaranteed
equal, and a disagreement in either direction is drawn honestly rather than
thrown. Fittings do not change the picture, and they do not change the
turnaround: a better boat is faster and tougher in the air and is not serviced
faster on the deck.

## Shipboard Barracks

Barracks represents the marine habitation deck while the flagship is carrying the
company between planets. A scalable squad rail selects one line formation at a time;
the main region shows that squad's quarters and a twelve-billet muster without exposing
Armory authoring controls. The room is casual company browsing, not a second roster or
equipment authority.

Barracks is also where a formation's **service record** is read. Each rail entry
carries a one-line career summary so squads compare at a glance, and the selected
squad's record opens as operations, confirmed kills, rounds on target, casualties,
and friendly-fire damage. That last measure is reported rather than netted into
damage dealt, because a squad being dangerous to stand near is part of who it is.
The record is read-only evidence: `progression-nouns.md` forbids a career from
becoming a combat input, so nothing shown here changes how the squad fights.
A formation with no operations says so rather than rendering a row of zeroes, and
zero states are spelled out in words because the heading face carries no
placeholder dash glyph.

The campaign roster holder establishes only the non-deployable reserve formation
during game load. A new company has no line squad until the player commits the full
founding bill in Fleet Armory. Company HQ and Barracks therefore present honest zero
states, while Fleet Armory renders a dedicated squad-and-plus founding emblem as the
gallery's only formation slot until the first squad exists and keeps it trailing every
later squad. Existing
saves retain their named formations.

The quarters use a bounded indoor `BattleSimulation` as a scene host, sharing battle
tiles, registered building doodads, camera scale, and the layered marine compositor
with live combat and the Mech Lab. The authored deck contains twelve two-cell berths,
lockers, lounge and planning furnishings, shipboard terminals, and a three-lane practice
range behind an internal blast wall. Ready marines aboard ship appear in their actual
issued armor, weapons, and special equipment. WIA marines retain their named muster
billet and recovery clock but are not fabricated as healthy room actors. Stationed
squads leave their shipboard quarters empty, and vacancies remain explicit. None of
those projections advances campaign time or mutates roster state.

Room actors are real entities in that bounded simulation, not separately painted
portraits placed over a tile screenshot. Live Starsector rendering and deterministic
headless evidence collect the same `GROUND + DOODADS` and `UNITS + SHOTS`
command passes; only the final graphics drain changes.
This keeps the snapshot useful as scene-composition proof without giving tooling a
second barracks layout or appearance implementation.

Leisure is authored as battle-owned **ambient task routes**, not Barracks canvas
animation. The same deterministic station/walk/activity sampler now drives Mech Lab
technicians and is available to mission setup for civilians, workers, engineers, or
guards. Live Barracks presentation advances its bounded simulation on the ordinary
fixed battle clock; deterministic evidence replays that same clock to an authored
time. Barracks has no mission winner, so its bounded clock remains live until its host
closes. A live route chooses destinations but never writes actor positions: marines
claim lounge, locker, and firing-lane task points exclusively, travel through ordinary
battle pathfinding and separation, and perform the activity only after arrival. A
threat-policy interrupt releases both route and claim, allowing the actor's existing
role to resume.

An ambient route may bind a simulation-owned practice target. Crossing one of its
authored primary-fire beats then calls the ordinary infantry firing service: the
marine's issued definition resolves the physical shot, `ShotEvent`, tracer or
projectile, impact timing, and authored fire sound exactly as it does in battle. Range
targets are invisible, durable simulation fixtures behind the lane backstops; they are
not campaign personnel. Carried special equipment still cycles its actual use pose as
a safe dry drill rather than launching shipboard explosives. The entire room remains a
disposable projection: range damage, shot clocks, and presentation audio never consume
campaign ammunition or mutate roster, recovery, or inventory state.

## Deployment identity

A campaign deployment is a frozen value snapshot, never a live roster
reference. Selected squads are frozen in stable roster order so their seats
remain contiguous. Each campaign seat carries its marine identity plus a
`CampaignSquadTag`: stable squad id, deploy-time label, NCO marker, fire-team
index, and the strength that selected squad is assembling toward. A later
roster rename or transfer cannot rewrite the battle.

At landing, tagged personnel group by **campaign squad and logical arrival
area**. Legacy point landings use their exact landing cell as that area.
Repeated lifts to the same area join one battle squad; a campaign squad split
across areas deliberately becomes separate labelled fragments. The frozen NCO
seeds the battle leader. Expected strength comes from the selected manifest,
while landed strength grows as each member deboards, so morale can distinguish a
squad still assembling from one already mauled.

Scenario-generated personnel normally retain the per-sortie fallback. A
mission-authored paired arrival is the exception: each two-craft group shares a
stable arrival-group-and-wave identity, mints one generated battle squad, and
assembles toward the combined embarked strength. Conquest balances its cycle
plan across the mission-configured reusable pairs and extends that plan until
every selected named squad has a seat; selection beyond the mission's minimum
demand is deployed rather than labelled as an orbital reserve.
A debug company intentionally freezes a real detached roster and therefore
carries campaign-shaped identity without touching campaign state.

For ordinary generated one-shot missions, the selected ready named personnel
are the deployment manifest even when the player fields less than the tier
recommendation. The briefing exposes selected headcount, the four-person hard
minimum, recommended squads, and issued experience-band distribution; it never
fills missing seats with anonymous generated marines. Authored missions,
stationing, and Conquest keep their own personnel requirements.

The standing law is **values cross the campaign-to-battle seam; campaign
objects do not**. Labels remain stable, battle code never resolves the roster,
and generated personnel never gain campaign identity by accident.

## Battle HUD

The default battle HUD is a scale-invariant task-force projection, not a squad
browser. One fixed plate reports effective squads out of all Marine squads that
have landed, living marines out of peak landed strength, the living squads'
engaged / suspicious / unaware mix, and aggregate cohesion. Wiped squads remain
in the committed denominator; an alive morale-broken squad is not combat
effective. The plate snapshots those values before render and never retains a
live simulation entity.

The battlefield is the squad browser. Picking a unit establishes the existing
view-only `Selection`; a Marine squad then replaces the force plate with its
member detail in the left dock, using the frozen `campaignLabel` with a numeric
fallback. The selected squad's GOAP debugger may open in the right dock for the
same selection. Clearing selection, clicking empty ground, or losing the squad
closes both. There is no default all-squad GOAP overview and no scrolling battle
roster, because either one grows with force size and turns the tactical canvas
into a data wall. Defender selection may still open the diagnostic without
pretending defenders belong to the player's company.

These surfaces remain read-only. Selection does not write an assignment,
objective, waypoint, or any other mission-command input.

Player-facing battle chrome uses the retained MLX path. One compact top-right
command rail owns pause / 1x / 2x / 4x time selection and, only on maps with
capturable compounds, the capture ledger. The ledger reports secured out of
total, keeps one stable abbreviated chip per compound, groups the secured /
contested / hostile counts, and spends a progress bar only on the most advanced
contested site. World-anchored compound markers remain the primary spatial
read. There is no full-height objective roster, and maps without compounds pay
only for the time-control row. Java projects live state and actions into the
retained model; MLX owns the hierarchy and presentation.

The same retained path owns a bounded bottom-center command-power tray. Its
resource block and compact cards replace the manually painted power menu, and
the tray grows only by a narrow instruction strip while a power is armed.
World targeting remains outside MLX so the reticle, invalid-target feedback,
cancel gesture, and activation click stay in battlefield coordinates and ahead
of squad picking.

## Battle maneuver doctrine

The player and the mission commander continue to assign objectives to squads.
Inside that order, infantry AI treats each roster-derived four-marine fire team
as an intact maneuver element. Scenario-generated squads receive the same
stable four-seat partition at spawn, so the doctrine is not limited to named
campaign personnel. Replanning may change a team's tactical role but does not
reshuffle its membership.

Formation steering is hierarchical. Marines first hold a compact team-local
shape; coherently moving sibling teams then occupy laterally separated anchors
in a shallow squad arc. A team moving on a different path is formed around its
own heading and is not pulled back toward the squad-wide arc. This replaces the
old whole-squad ring dispersion for infantry while leaving mech formations
independent. An ordinary no-contact advance releases those teams as a spatial
echelon: the lead team earns a short distance before the next team steps off,
and each follower keys off the team immediately ahead rather than a shared
timer. A shared-destination approach retains its last movement heading briefly
after arrival so the open-ground footprint finishes settling instead of
collapsing into a halt-point brick; distinct authored posts do not receive that
arrival correction. Navigation has authority in contained terrain: each team
reads its near path clearance, releases the echelon gate, contracts both its
local interval and squad-arc anchor before a doorway or narrow run, queues
through under ordinary pathing and collision separation, then reforms
automatically after clearing the constraint.

Bounding overwatch rotates one intact team forward while every sibling team
with a firing solution covers it. With three healthy teams this is one moving
and two overwatching; degraded two-team squads use one and one. Fix-and-flank
likewise leaves at least one team on the contact axis while a sibling maneuvers
to the flank waypoint. Flank positions must be reachable without an extreme
structural detour; if the room or portal layout cannot support the maneuver,
the flank step yields to ordinary engagement instead of orbiting the building.
Bounding retains the same all-or-nothing fallback when the contained space
cannot supply distinct reachable firing positions. A team with fewer than two
survivors is dissolved for tactical purposes and its survivors fold
deterministically into the nearest viable sibling; the underlying campaign
billet identity remains unchanged.

## Lift and arrival

Hull capacity is a physical maximum, not a promise that every mission fills the
hold. Small lifts have four seats, medium lifts eight, and the Valkyrie twelve.
The mission arrival policy owns the embarked seats per sortie. An ordinary
generated mission freezes only the selected named personnel, so its final craft
or final pass may carry fewer marines than the hull maximum and all force,
arrival, and outcome accounting uses that exact embarked count. Conquest uses two
six-seat Aeroshuttles in each paired arrival group to deliver a twelve-marine
squad together; the transport boundary may cut across fire-team membership,
while the ground squad and its three stable fire teams remain unchanged.

**The NCO is the first off the boat and the last onto it.** A squad's seats are
frozen with its leader at the head of the squad's own run of the manifest, so
the NCO rides the squad's first lift and is on the ground as the leader the rest
of it closes on; a manifest cut short by the seats actually available drops the
tail of a squad rather than its NCO. The same law read from the ramp is that a
leader boards last: a lift that cannot take the whole squad leaves the NCO
standing with the people it could not carry. Everyone else keeps roster order,
because seat assignment must be the same on every run for the same roster and
selection.

This is a law of lift ordering rather than a rule about the leadership billet,
and deliberately so. Cohesion is a pull toward the leader, so an NCO who landed
on a later lift would take the billet and drag the squad it is supposed to lead
back toward the landing zone while walking out to meet it. Deferring the billet
instead would leave a squad led by whoever happened to land first while its
actual NCO stood among them, which is a worse answer to a case that did not need
to exist. Ordering the lift removes the case.

A tagged or mission-grouped squad may assemble over several craft or passes. Until its frozen manifest is
present, the form-up gate suspends execution of its advancing assignment while
retaining the authoritative command directive and still allowing self-defense.
A timeout prevents a lost lift or split landing from deadlocking the mission.

A campaign marine who lands outside squad cohesion of a squad that is no longer
forming up is **rejoining**. That is the case the timeout leaves behind, and it
is equally a second lift arriving after the squad stepped off or a replacement
wave joining a squad already in contact. A rejoining marine closes on its squad,
returns fire at whatever is already within its own reach, and initiates nothing:
it does not acquire a contact of its own, take a firing position, or spend squad
equipment on the way. The squad meanwhile plans around it — a rejoining member is
left out of role and slot assignment and out of the rules that ask whether the
whole squad has arrived somewhere, so a squad is never pinned on somebody still
crossing. The state retires by itself the moment the marine is back inside
cohesion, and it becomes an ordinary member again on that tick. Cohesion is one
radius and one rule; rejoining is a priority over the squad's plan rather than a
second way of measuring distance. `SquadRejoin` is where it lives.

## Task-force command

Deployment command is derived from the selected squads. A squad follows its fit
home officer; an unassigned squad, or one whose home officer is unfit, falls to
the operation commander and consumes that commander's capacity. Capacity is
checked independently for every represented officer. This reduces to the old
single-officer rule for an unorganized roster while allowing deliberate
organization to scale to larger operations.

The task force follows roster order, not selection-set iteration, so its
grouping and presentation are stable. An empty selection is valid from the
command model's perspective; personnel selection owns whether deploying nobody
is allowed. Stationing remains a single-officer posting and does not use the
task-force model.

Officer consequences have not yet caught up with officer deployment:
`MissionOutcome` still resolves only the operation commander. Per-officer
outcomes and practical bulk assignment remain in `c13-the-task-force.md`.

## Campaign-map home

`CompanyViewAbility` opens the planet-free, read-only `CompanyHqScreen` from the
campaign map. The ability is an entry affordance rather than a timed or toggled
power. The null-planet host may transition directly only to the armory, Mech Lab,
and back; it must not navigate through mission, briefing, battle, result, or loot
screens that require a market or live operation. An obligation response is the
one handoff boundary: it queues the existing campaign event's deployment
request, then closes the HQ rather than routing through those screens itself.

Company surfaces are places aboard the player's flagship rather than abstract
application modules. Company HQ is the bridge command station and the nerve center
for the whole organization. A persistent top shell exposes `RETURN`, `HQ`, `BARRACKS`,
`ARMORY`, and `MECH LAB`, marks the occupied room, and carries the precise location context at the
right. `RETURN` closes the shipboard UI; room routes move directly between top-level
surfaces. Armory company, squad, and fire-team breadcrumbs remain page-specific below
that shell, while the bottom of every room is reserved for its own content. Future
Briefing Room, medical, logistics, or fabrication surfaces should join the
same spatial navigation vocabulary only when their real destination exists. A screen
says where the captain is and uses movement language for room transitions. It does not
advertise dead rooms as disabled feature promises.

The retained bridge dashboard has four concerns:

- **Force status** explains organization and readiness. Strength includes living
  active and wounded marines; availability is the line-ready subset. The entire
  gap—including wounded, stationed, and reserve personnel—is unavailable. The
  reserve pool is excluded from line and stationed squad counts, and recovery
  shows the earliest known return only when recovery exists.
- **Finance and standing** explain cash, reputation, employers, and runway from live
  campaign authorities. Only live stationing retainers count as income. Missing
  monthly data is unknown, never zero.
- **Mercenary rating and assessment** make the company's industry position and
  immediate posture readable without conflating them. `CompanyRating` is a
  presentation band over authoritative MRB credibility, never a second reputation
  track. The bridge adjutant's authored daily assessment uses the existing officer
  mood and live readiness facts; it may change as the company's situation changes
  without rewriting that rating.
- **Situation board** leads with obligations that can hurt the company: pending response
  deadlines and live stationing term endings. Overdue clocks are due now, never
  rendered as negative time. One contract may own both clocks. Below them it shows
  recent validated Chronicle facts newest-first. It reads only learned Chronicle rows,
  preserves rumor versus confirmed confidence, and cannot expose undiscovered world
  state.

The clock owns obligations; the future contract board owns offers. Ignoring an
obligation can cause failure or end a retainer, while ignoring an offer merely
loses an opportunity. Responding to an obligation follows the same deployment
route as its campaign event notice. Long lists state hidden counts, and missing
derived inputs are explained rather than silently collapsed.

The HQ introduces no persisted state and no second company hierarchy. Opening its
Armory transition first shows the owned-company collection as a responsive grid of
literal selectable portrait cards. Selecting one enters a company-specific squad
gallery; selecting a squad then exposes its three fire teams, named-marine viewer,
two squad doctrine slots, and one exact issue action. A breadcrumb keeps the
completed company and squad levels directly reachable while drilling down. A
company or squad card is only a presentation container over canonical roster
authority; neither creates parallel organization. Until a
multi-company campaign authority exists, the first grid contains exactly the one real
campaign roster rather than fixture companies. The HQ roster area remains the host
that the formation, whereabouts, and contract-board stories will expand.

## Presentation boundaries

Company reporting surfaces are read-only explanations. The Fleet Armory and Mech
Lab are the exceptions that author equipment through roster, armory, and mech-bay
services; their UIs do not mutate those facts independently. Mission
command owns battle assignments; `Selection` remains view state. Campaign
surfaces consume the live roster, while battle and results surfaces consume the
frozen deployment. A UI must not silently regroup marines, invent persistence,
or feed presentation state back into simulation.

The eventual formation snapshot should give campaign HQ, deployment, battle
rollup, and after-action views one shared organizational vocabulary without
forcing every surface into one widget. Detailed equipment, aptitude, experience,
and career presentation remains coordinated with `progression-nouns.md`.

## Debug company

A debug company is an in-memory `MarineRoster` built and equipped through the
same roster and armory laws as campaign personnel. It is never registered with
or written back to the campaign. Freezing it through the normal deployment seam
lets debug missions exercise squad identity, NCO leadership, multi-lift joining,
and form-up behavior instead of maintaining a parallel fixture model.

`DebugCompanyStage` describes points on the campaign arc by default size,
quality, and mech support; an explicit squad-count control may override size
without changing quality. Stages may exceed one officer's command cap so large
mission balance can be exercised, but debug command readiness does not redefine
campaign deployment law. The debug squad control has no scenario-authored upper
ceiling; requested scale is limited only by the host's practical runtime resources,
and missions still apply their real arrival policy. Player-side vehicle support remains deferred until a
production vehicle deployment seam exists.

## Invariants for future work

- `MarineRoster` remains authoritative for enduring personnel and organization.
- Squad and marine identity are frozen across the deployment seam; battle code
  never reaches back into campaign state.
- Fire-team membership is derived from billet order and never persisted twice.
- Fire teams are equipment/AI/lift units, not player command targets.
- Weapon and armor doctrines are reusable squad designs authored from collected
  templates; every changed issue remains bounded by ordinary fleet cargo.
- Mech weapon assemblies and subsystems are finite physical stock; installed copies
  remain counted, while their fabrication spends player cargo through one campaign
  authority. The Mech Lab cannot create a second inventory or commodity-art catalog.
- A failed squad equipment issue changes neither materialized kit nor either doctrine id.
- Per-soldier kit remains the battle-facing materialization until the deployment
  seam explicitly adopts another representation.
- A weapon doctrine may issue special equipment, but AI use follows faction-neutral
  battle policy rather than a hidden instruction encoded by its presentation card.
- Officer capacity is checked per command; task forces do not flatten back into
  one officer's cap.
- Reserve personnel do not count as formations or field-ready strength.
- Campaign views read live state; battle and result views read frozen mission
  state.
- Missing or unavailable information is explained, not rendered as zero.
- Presentation conveys organization but never changes simulation authority.

## Extension boundaries

Future extensions may add a shared formation read model, safe late-arrival
rejoin, participating-officer outcomes, practical large-company assignment,
and a sector contract board. Each extension must preserve the canonical
company organization and frozen deployment identities rather than persisting
a second presentation-owned roster.

Boat fittings may later be authored in data rather than in a code catalog and
gain a hardpoint slot — each of those extends the same campaign authority rather
than adding a second one, and none of them makes the room the owner of what the
company has. Recovering a downed boat's hull or crew is deliberately not on that
list: a lost boat is lost.

Equipment authoring remains bounded by collected templates and atomic cargo-backed assignment;
read surfaces remain projections of their owning campaign or frozen mission
state. A contract board may present offers and obligation clocks, but it does
not become their authority or introduce a second clock lifecycle.
