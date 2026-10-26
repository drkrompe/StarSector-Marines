# Company view nouns

Status: ACTIVE — organization, Fleet Armory authoring, and cross-surface company read models continue to evolve.

Written: 2026-08-23

Updated: 2026-10-22 — a squad's kit is its two cards; Barracks cards lead with them, and filling vacancies is a wrench on the card or beside the select toggle.

## Purpose

Company view makes the player's force legible as an organization wherever a
decision depends on it: between contracts, while assembling a deployment, in
battle, and after action. Most of the domain is a read model over the campaign
roster and contract state, not a second company simulation and not a
player-order layer. Barracks, Mech Lab and Boat Deck are deliberate authoring
seams. Barracks organizes officer commands, issues explicit replenishment
orders and raises new formations. Its Equipment workspace
retains Fleet Armory authoring. Mech Lab and Boat Deck refit their persistent machines.
Each surface acts through the corresponding campaign authority.

This domain owns the shared language and presentation shape of the company. It
does not own personnel persistence, equipment progression, contract resolution,
financial accounting, or battle command. Those systems remain authoritative;
company view composes their stable outputs.

Mission location artwork is a shared presentation record. `StoryScene` resolves
the mission identity and source for the campaign picker, DEBUG catalog and
after-action archive, including branch previews, retries and recurring work.
The archive depicts the location, never proof of victory, casualties or damage.
Unknown and generated operations retain their existing planet/scope fallback.
Shipped location JPEGs are 1200×400; full-resolution PNG masters and generation
prompts stay in `art-source/story-scenes`. Its `pack.ps1` downsamples the complete
3:1 composition without cropping or replacing masters. Add artwork to this
shared mapping instead of assigning paths independently in each screen.

## Vocabulary

- **Company** — the player's aggregate force. There is no separate persisted
  company entity; `MarineRoster` and the campaign authorities supply its state.
- **Officer command** — the line squads assigned to one active
  `MarineCaptain`. Officer capacity is denominated in coordinated squads.
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
- **Squad cards** — the weapon card and the tactic sheet a squad holds, chosen
  for each squad on its own. Both describe all twelve seats, and a seat's weapon,
  special, armor and equipment may differ from the next seat's. A sheet issues
  exactly the patterns its card carries, regardless of what else the company owns.
  Every line squad holds both cards from the moment it is raised.
- **Card assignment** — the atomic transaction that changes a squad's cards. It
  refuses while the squad is stationed or when the company may not issue a card,
  and a refusal leaves both cards untouched.
- **Squad founding** — the Barracks **Raise Squad** tile, trailing the formation
  board, that takes twelve Marines out of cargo and one credit price for their stores
  (the 36 supplies and 24 food it was once billed in, at their base prices: Price)
  before creating a full named line formation under an officer with a free command
  slot. Unlike the workshops' purchases it needs no port (owner): a squad can be raised
  anywhere. Its live bill (credits, then marines) stays on that tile; a partial bill
  creates neither personnel nor squad identity. Barracks is the only place a squad is founded.
- **Billet** — one stable position in the squad's twelve-place equipment
  establishment. Alpha, Bravo, and Charlie inspect consecutive groups of four.
- **Seat kit** — what one marine carries: exactly what the squad's cards issue
  the seat that marine holds. It is derived, never authored per marine, so a squad
  cannot drift from its cards.
- **Marine** — the persistent individual. Their equipment and quality belong
  to the personnel and progression domains; company view presents them in
  formation context.
- **Reserve pool** — a roster holding area, not a deployable or commanded
  formation. It never inflates command, formation, or field-strength counts.
- **Task force** — the named-officer groups and independent acting-NCO squads
  selected for one operation. It is derived for deployment, not persisted as
  another roster.
- **Battle squad** — a battle-lifetime tactical realization of a campaign
  squad at one landing zone. It is not a campaign authority.
- **Whereabouts** — a derived statement of a squad's current role: available,
  stationed, recovering or understrength, or part of a live mission snapshot.
  It is not independently persisted.
- **Mech Lab** — the shipboard room over the persistent support squad and finite
  mech component stock defined by `mechs-nouns.md`. It presents and refits typed
  hardpoints, and uses vacant gantries to fabricate persistent chassis, but mutates
  them only through the cargo-backed campaign workshop authority.
- **Barracks** — the officer-command and squad-profile administration surface.
  It has no per-squad drill-down; a squad card's Loadout opens that squad's
  Armory equipment workspace. Its bulk orders use roster and cargo authorities; it never simulates recovery
  or owns a second inventory.
- **Boat Deck** — the shipboard room over the company's ship's boats, standing
  in the hull's own hangars. It presents the deck and refits one boat at a time,
  mutating nothing except through the credit-backed boat workshop.
- **Campaign boat** — one persistent, company-owned ship's boat: a stable id, a
  tail name, the pattern it is built on, and one fitting in each of its slots.
  It is the campaign authority that freezes a battle airframe; the sortie flying
  that frame never reaches back into it. `air-nouns.md` owns the pattern and the
  frozen frame.
- **Fitting** — installed work in one of a boat's six **slots**: plating, which
  is hull; drive, which is speed, acceleration and deboard time; lift, the hold,
  whose tiers climb to Heavy and never cost a seat (`air-nouns.md`, Lift); cabin,
  which is seats; emitter, the shield's arc and flux per hit; and power, the flux
  pool's capacity and venting. Each slot has a standard fitting every boat is
  built with and up to three upgrade tiers; a lift tier goes only on a boat that
  does not already lift as much. The battle reads every slot (`air-nouns.md`,
  Fitted boat). The emitter's three tiers are the **low-tech, midline and
  high-tech shields**: wider arcs and less flux per blocked hit, each with its
  own price and its own look (orange, teal, blue). Planned: art of their own for
  the energy guns and missile pods, and a tuning pass on the part numbers
  against a matchup run.
- **Frame** — what a boat can carry in upgrades: every non-standard fitting and
  door gun has a **weight**, and their sum may not pass the frame's rating (10).
  The workshop refuses a fit that would take her over it, unless the fit makes her
  lighter, so heavy lift and a fast drive compete with plating and guns.
- **Boat spare** — a fitting or boat gun in the boat deck's **stores**, counted per
  part. A spare is bought for its price, or comes off a boat; fitting one takes it
  out of stores. Stores are the company's, not a boat's, so a part moves between
  boats through them and survives a change of ship. The stock parts a boat is built
  with are spares like any other: they come off into stores, cost a small price to
  buy, and go back on from there. A part fitted to a lost or left-behind boat
  goes with her.
- **Required part** — a slot a boat cannot fly without: drive, cabin and power,
  as a mech needs its engine and capacitor bank. Any socket can stand empty;
  a boat missing a required part warns on the doll (a red socket with the warning
  mark) and stays off the lift until one is fitted (`CampaignBoat.missingParts`).
  Plating is armour over her hull, never more hull: empty plating flies her as a
  bare hull with no armour over it (owner, 2026-10-26), an empty
  lift is the bare hold, an empty emitter flies her unshielded, and a bare
  hardpoint carries no gun. A briefing whose drop needs lift refuses to deploy with
  no flight-ready boat, and says which part is missing; a mission that starts on
  secured ground needs none. The Boat Deck's specifications column lists her
  **readiness**: each missing required part (red), then what she flies without (a
  bare hull, no shield, empty mounts; amber), then her frame, each line opening the
  socket it is about.
- **Boat workshop** — the atomic authority over the boats and their stores: it
  buys a spare into stores, fits a spare and stores what came off, strips a part
  back to stores, moves a gun between hardpoints, recycles a spare, builds a boat
  into an empty berth, expedites a repair, and scraps a boat out of her berth. Only
  buying, building and expediting spend, from the company's credits, which it shares
  with the Mech Lab rather than owning a second purse; they also need a civilized port.
- **Price** — what a part, gun, boat, chassis, vehicle, kit or repair costs: one
  credit figure (`FabricationCost`). The workshops once billed supplies, heavy
  machinery, metals and transplutonics; those bills were converted once at the
  vanilla base price of each (100, 150, 30 and 200 credits a unit), so the balance
  stayed where it was. Tooling takes 15% off, rounded up. The Second Wind's
  Engineering refits are priced the same way. The one thing no price stands in
  for is an **AI core**: a refit tier that needs an alpha, beta or gamma core still
  takes it from fleet cargo beside its credits (`RefitOrder.cores`).
- **Civilized port** — where the company can buy: the fleet docked at a visible,
  populated colony with a spaceport (disrupted or not) that the base game would let
  the player trade with (`CivilizedPort`). The company never re-derives that: vanilla's
  docking rules (`MarketPostOpen` in `rules.csv`, weighing transponder, standing and
  patrols) leave the dialog-local `$tradeMode`, and the port buys when it is `OPEN`,
  or `SNEAK` at a market with a black market, exactly where vanilla's own submarkets
  open (`BaseSubmarketPlugin.isEnabled`). So a hostile port sells through its black
  market to a fleet that came in dark, and refuses one it turned away. Hidden pirate
  and Pather bases, derelicts and bare worlds are not civilized. Buying a part or a machine, building a boat, refitting
  with a newly bought part and expediting a repair need one; away from it the
  rooms say why and BUY refuses (`NO_MARKET`). Fitting, stripping and moving what is
  already in stores, recycling and scrapping work anywhere, so a company heading
  into deep space buys its spares first.
- **Recycling** — breaking one spare in stores (a boat spare, or a Mech Lab weapon,
  internal or kit) down for credits. It is the machine shop's **reclamation**
  applied to a spare: what comes back is a share of what buying that spare costs
  now, rounded down — none while the shop's machinery is down, half once it runs,
  three quarters once its tooling is in, and all of it once a reclamation line is
  installed, so a part can be tried for nothing. The machine shop aboard does the
  work, so it needs no port.
- **Scrapped machine** — a boat, mech or vehicle the company broke up to free its
  berth or gantry. Everything fitted goes to stores, and its **hull** — its price less
  the stock parts it was built with, which came back on their own — returns at the
  shop's recycling rate.
- **Reserve hangar** — the company's store of whole machines: boats, mechs and
  vehicles taken off their berths and gantries with their identity, callsign, fit
  and repair clock intact. A stored machine flies and fights nothing, so no battle
  can lose it. The hangar is the company's, like its stores, and goes with it
  when it changes ship; how many machines it holds is the home ship's Reserve hangar
  refit (`ShipCondition.reserveCapacity`), counted across every kind.
- **Lost boat** — a campaign boat whose frozen frame was shot down on a mission.
  It is struck from its berth and never comes back. `air-nouns.md` owns the
  battle-side ledger it is read from.
- **Vacant berth** — a berth of the hull's with nothing standing in it, which is
  what a lost or scrapped boat leaves behind and the only thing a boat can be built into.
- **Passenger casualty** — a marine who was still aboard a boat when it was
  lost. They are fallen the same way a marine shot on the ground is fallen and
  roll the same recovery outcome.
- **Boat fabrication** — the workshop building the hull's own pattern into a
  vacant berth, at standard fit, for a price authored per pattern and raised to at
  least what her stock parts cost together (tooling's quote included). Those parts
  recycle like any spare, so a boat cheaper than her parts would turn credits out of
  nothing. The player does not choose the pattern,
  because the hull already has.

## Surface boundary

New optional company opportunities publish an unread marker on their destination
room tab. The contract board also exposes a direct preparation detour so a player
moving straight from one mission to the next can discover them. Second-bay
restoration, the first Armory refit and Wayward's gantry induction
have independent saved read receipts. Opening the actual offer or lesson clears
its receipt; entering an unrelated room does not. Reviewing spends nothing and
does not require finishing the purchase or tutorial. Completed opportunities stop
advertising themselves, while a later unlock raises a fresh marker. Eligibility
comes from the owning feature; money and docking requirements never turn unread
news into invisible news. Existing saves discover currently available unread
opportunities once. The retained shell presents these facts without owning rewards.

Barracks is the single troop-management entry point and the only place a squad is
chosen. Barracks opens on officer commands (All formations plus one
card per named officer), with squad selection for bulk orders. The rail offers
no acting-NCO card: a squad without an officer still fights under its own NCO
and its card says so, but assignment only ever moves squads to officers. A squad card's Loadout
opens that squad's Fleet Armory loadout. Equipment opens the same workspace on the
company's first line squad, or on First Refit while that offer stands. Fleet Armory
keeps no squad list of its own: readiness, recovery, reinforcement and home-command
assignment are all read and ordered on the Barracks formation board.

Squad cards show the twelve current billets grouped into Alpha, Bravo, and Charlie.
A filled marine cutout means ready, an amber medical mark means wounded, and a
hollow cutout means vacant; counts and a text legend accompany these shapes.
When the squad's home officer trains Field Medicine, the billet that will carry
the squad medic in battle wears a green plate with the cross cut out — never the
bare amber cross of a wound — chosen by the same billet rule the battle uses
from ready marines other than the NCO. A squad under operation-command fallback
shows no medic plate, since the card cannot know who will lead it.
Wounded marines retain their billet. Historical killed and missing personnel do
not occupy the current establishment. Readiness is distinct from stationing and
from squad selection.

The Armory has no separate top-level tab. Barracks owns its Equipment entry,
and a squad card's Loadout opens that squad directly. Returning preserves the
Barracks selection. Equipment news marks both the Barracks tab
and its Equipment entry; entering Barracks alone does not acknowledge the lesson.
The existing editor and inventory transactions remain authoritative.

Fleet Armory is the only detailed equipment editor. The former Armory Administration shell,
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
policy: the squad's fit home captain within command capacity, the operation
commander within remaining capacity, or its own acting NCO. A named officer's
portrait is that `MarineCaptain`'s persisted Starsector sprite path,
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
16, and 24 squads; any marine-count form is explanatory display only. These
limits bound named-officer coordination, not whether the force may deploy.
A squad outside available officer capacity acts independently under its own
NCO and retains ordinary mission execution. Named officers coordinate their
stable group's tasks and advance in Conquest. Player-chosen officer specialties
also provide explicit, launch-frozen passive effects; the Barracks dossier
shows their numbers and scope. Other mission adapters retain their existing
command allocation.
Enlisted rank follows squad and team billets rather than accumulating as an independent
promotion track.

Deployment freezes each selected squad's resolved command-group identity with
its personnel manifest. Later lifts retain it rather than regrouping by landing
area. An officer's group is not a western, central or eastern route assignment;
physical lanes and current tasks belong to mission command. Briefing and battle
views show actual leadership and published tasks, without inventing fixed lane
ownership. `mission-command-nouns.md` owns the group's battle authority and
`conquest-nouns.md` owns its first coordination policy.

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

A home-command change may move several line squads at once. The roster
rechecks the whole selection and destination at commitment and either applies
every assignment or changes none. Capacity counts whole assigned squads,
including stationed and understrength formations; selecting a squad already
under the destination does not consume another slot. Reserve formations and
stationed reassignments cannot enter the transaction. Clearing home command
uses the same deliberate transaction and restores the ordinary deployment
fallback without choosing an operation commander in advance.

## Squad equipment doctrines

**A Proper Issue** is Castle's optional first squad-refit lesson after the story
victory in Hold Until Relieved. The debrief opens the equipment lesson directly and HQ points to Barracks; its
opening conversation and gallery resume action keep the offer discoverable.
A new company knows only the surplus Rook, field fatigues, and Cordon security shell.
Other weapon families, specialist suits, and special equipment must be learned.
Existing saves keep every previously acquired spec.

Wayline offers one free technology transfer: Steady Line permanently teaches
Milspec Stalwart; Forward Screen teaches Milspec Lancer and Janus scout suits.
Acceptance grants reusable plan intent for both paths, but only the chosen path's
technology. The other remains obtainable through open-market specs or mission
rewards. Both featured Milspec specs circulate in the Common access band; military
grade and campaign acquisition tier are distinct. Existing learned collections,
including previously claimed two-path bundles, are never revoked.

The lesson presents exactly two complete equipment paths: **Steady Line** pairs
three Milspec automatics with a line-holding armor plan; **Forward Screen** pairs
six Milspec pulse rifles with a reconnaissance armor plan. Remaining riflemen
carry surplus Rooks. Both omit carried specials, disclosed in the review.
Armor still resolves from the company's owned patterns through the ordinary role
and tradition rules. These are a bounded introduction to the existing catalog,
not a separate equipment economy or a restriction on later Armory authorship.
All learned infantry equipment is freely fitted and swapped by the Machine Shop.
No supplies, armaments, machinery, or credits are charged for fitting; technology
acquisition remains the progression gate. Workshop restoration does not gate fitting.

After Relief, Castle's portrait reward prompt appears over the debrief before its
ordinary controls accept input. It leads with the free technology, shows the actual
Stalwart/Lancer/Janus art, and offers one compact **Choose Free Technology**
action. Neither the prompt nor the equipment lesson offers a Later button. Escape defers only the prompt; it never dismisses or settles the debrief.
The prompt does not learn technology. Its primary action opens the full comparison;
a company with one line squad has that squad selected automatically.

Accepting or deferring the handoff acknowledges this presented outcome, so returning
from the Armory does not immediately repeat it. An unfinished lesson can be prompted
on the next non-debug debrief; completion ends the reminders. An accepted transfer
instead offers **Fit Free Equipment**. The persistent debrief/HQ notice remains a
resume route. Narration waits until the reward prompt is dismissed or the player
returns. Pending salvage remains untouched across this handoff.

Reward, fit, and navigation actions use compact buttons in action rows, never
full-width acceptance bars. The two equipment choices remain comparison cards.

The player selects an actual line squad, compares either path without learning it,
then explicitly accepts one technology transfer. The review shows twelve clickable equipment squares in three fire-team columns of
four billets, alongside current and proposed equipment. The grid uses the Barracks'
current-equipment icons, with wounded and vacant slots identified. Selecting a
square displays current and proposed weapon, armor and
special equipment side by side, using existing equipment sprites and the shared
catalog spec sheets. Core stats stay visible; hover expands field notes and suit
systems. The review explicitly marks unchanged kit, replacements, removed specials,
and unoccupied plan-only slots. Only explicit Issue commits the ordinary free fitting transaction.
Wounds and vacancies never block assignment or issue. Current occupants, including
WIA personnel, receive the reviewed equipment; empty slots retain the plan and
incur no charge. Stationing and unknown technology remain blockers. Successful issue completes the saved
lesson once and offers inspection of the squad's three fire teams. Leaving and
returning preserves reward and completion; older saves may claim the uncollected
reward without losing existing gear. The lesson never gates the next mission.


Routine Armory authorship sits at squad scale. `MarineSquad` persists one weapon
doctrine id and one armor doctrine id. Each reusable definition describes twelve
ordered billets, so a single choice may still mix equipment by team, role, or
individual position. Weapon doctrine owns primary family, grade, role label, and
optional `specialEquipmentId`; armor doctrine owns only protection. A smoke grenade
or satchel therefore follows the Weapon slot without being reclassified as a
ballistic `WeaponDef`.

**One armoury presentation.** Every screen that shows a squad's cards and kit
(Barracks, Loadout, and the First Refit reward) draws it with the same four parts,
so a player reads the same thing the same way wherever it appears:

- the **licence card**: a card in full, in the spec sheet's own style (heading and
  crest, issuer stamp with the Restricted banner, metered rating, the fire-team seat
  picture). It is the card in the Loadout stores and First Refit's choices (both a
  left-hand column beside the squad view), and the sheet a card opens on hover
  anywhere;
- the **issued-card chip**: a card at a glance (face, name, Firepower or Protection
  Rating, the Restricted seal). Barracks stacks two compact chips on each squad
  card, and the Loadout squad view opens on the same two at full size;
- the **ratings**: a weapon card's Firepower Rating and an armour card's Protection
  Rating, each a percentage of the strongest twelve billets the catalogue could
  field, so 100% is the best kit that exists rather than the best the company owns;
- the **squad view**: the twelve marines as three fire teams of two by two, each
  with a portrait
  of the kit they would wear, four kit lines (primary weapon, armour, special
  equipment, integral system) giving what they carry now with an arrow to what the
  selected card would issue, and five numbers (sustained output, range,
  penetration, armour, movement) now and proposed, the arrow green where the number
  improves and red where it falls; weapon figures use that marine's own profile;
- the **licence keys**: tabs, filters and actions on and around cards are flat navy
  keys with the cards' edge colours (blue for a choice, gold for an action), not the
  console keys of the rest of the ship.

The Loadout screen follows the Mech Lab gantry's arrangement: the stores on the left
(the card browser) and the thing being fitted beside them, which is the whole squad.
The squad view opens on the squad's two issued cards beside the selected marine and
the Test Loadout action, with one line counting the marines the selected card would
change. The two selected definitions compose into one exact twelve-billet preview.
Hovering a kit line opens the shared spec sheet of the item it would issue; the
bindings read the live projection, so changing billets or cards needs no rebinding.
Selection picks the marine Test Loadout fields without changing the squad-wide
transaction. A vacancy shows the planned issue without inventing an equipped
soldier. Personnel careers and recovery management remain in Barracks.

Weapon cards and tactic sheets share the left-hand browser, under its Weapons and
Equipment tabs, filtered by tier (I to IV). Each card is drawn as a licence in
the spec sheet's own style (heading and crest, issuer stamp, metered rating), so a
card and the sheet it opens elsewhere are one design. Each card
paints its twelve seats as three fire teams of two by two, each seat's weapon or
suit with its special or integral system on the opposite corner: the picture the
Barracks hover paints, so a card reads the same on both screens. A new company's
only weapon choice is **Dockside Issue**, twelve surplus Rooks; its only tactic
sheet is **Watchman's Kit**, twelve Cordon security shells over field fatigues.
Starting specifications remain Rook, field
fatigues, and Cordon; no specialist weapon or suit is granted. A tactic sheet must
first be learned through its associated equipment acquisition or story transfer,
then be resolvable from owned patterns. Merely being able to substitute Cordon or
Janus into its billets does not unlock another faction's sheet. Among learned
sheets, plans that resolve to the same ordered twelve issued patterns appear as
one choice; the assigned sheet remains the visible representative. Grouping is
presentation only and never grants knowledge. Saved assignments,
collected specifications, and issued equipment remain intact. Unknown equipment
has no revealing placeholder.

Each weapon card leads with counted battlefield categories and shows each named
weapon quantity with its equipment sprite. Tactic cards show the resolved armor
quantities, sprites, and carried suit capabilities. The technology lesson and routine
browser use the same weapon card component. Tier, rarity, provenance, and setting
copy are supporting details. Each named catalog item opens its own spec sheet;
counts over mixed items and role totals do not invent a catalog subject. No fleet
inventory strip or repeated zero-cost message belongs on routine fitting; the action
lives on the card. Each routine-browser loadout card carries its own **Issue**
button, inline in the card's provenance row: pressing it selects that loadout and performs the whole squad issue
(paired with the other picker's current choice) through the same transaction,
eligibility and cargo rules. Selecting a card only previews; there is no separate
bottom-corner fit control. A card the transaction would refuse shows its reason
as a line on the card and disables Issue; the card matching the squad's current issue reads
"Issued" and is disabled; the shared status line speaks only when something is refused; the shared status line and the per-billet
"Keep current kit / will change" preview follow the selected card. The first-refit
technology lesson uses the same card without an Issue action. Tier describes
expected capability while rarity describes acquisition scarcity and presentation.
Rarity is never a random-roll weight, and selection never rolls equipment from a pool.
Faction-authored entries pair that provenance with the owning faction's vanilla flag,
using the same logo art Starsector assigns in its faction definition. Mixed outlaw
traditions name one representative faction explicitly in authored presentation data;
company-registered definitions carry no borrowed faction badge.

**The player never authors a card.** There is no equipment designer: the player
collects complete cards and picks one of each for a squad. A card is the unit of
access, play and reward; the weapons, suits and specials it names are its contents,
never something the player assembles. Cards outside the built-in catalog exist only
as developer-authored registrations under fixed ids (a story path's, a debug
operation's), plus any card a save from before the designer's removal already holds,
which keeps issuing for the squads that carry it.

Card assignment is atomic. The Machine Shop freely fits any learned equipment;
it neither reads nor spends fleet cargo, and removing kit grants no cargo refund.
Wounded personnel keep their seats and wear their seat's kit without changing
status or recovery time. Vacant seats retain the twelve-billet plan; even an empty
line squad holds its cards. Every occupied seat is dressed from the cards whenever
the cards or the squad's members change: a replacement or transfer takes the kit
of the seat it fills, and a casualty that moves everyone behind it up a seat moves
the kit with the seat, not with the marine. There is no issue order to forget. A
recruit in the reserve pool wears entry-level kit until a seat takes them. The seat
kit is materialized on `MarineSoldier`, the battle-facing state deployment reads. Collection changes never silently
optimize that result. Founding and personnel logistics retain their own costs.

Built-in weapon definitions are authored deterministic distributions rather than
live best-fit allocators. This makes faction-flavored profiles such as **Luddic
Path Assault Equipment** explainable in preview and stable after issue.
Registered weapon cards persist in `MarineArmory` beside that immutable built-in
catalog.

Armour deliberately has no equivalent. A tactic sheet is a role mix plus a
tradition, resolved against owned stock every time it is read
(`role-and-access.md`), so it cannot be frozen and there is nothing for a player
to author into it. Sheet knowledge, technology ownership, and stationing constrain
later squad issue and profile creation through the same company authority.

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
Starting armories collect only surplus Rook, field fatigues, and Cordon security shell.
Save repair retains previously learned specifications and assigned formations but
does not seed specialist gear or the unlearned faction-sheet library. The Machine
Shop fits learned equipment freely.

**Blacksite Breach Package** is the built-in player assault profile:
its twelve exact billets contain one Shattercap frag carrier, so its authored
distribution stays legible. Every card resolves to twelve exact billets before the
same issue transaction can commit.

Legacy four-billet templates, three-template arrangements, and their persisted ids
remain readable compatibility input for existing saves; they are no longer writable
player intent and have no Fleet Armory UI. Load migration composes each complete
legacy three-template intent into a deterministic player-owned weapon definition
and the starter tactic sheet without rewriting any marine's current kit. The first later successful
squad issue clears stale per-team assignment ids so only one equipment authority remains.

Loadout cards are what the player buys, is paid and recovers: a Weapon Loadout or
Tactic Sheet learned wholesale with its equipment, so the market item is the same
thing the Armory browser shows as a card. The equipment templates beneath them are
literal permanent collectibles owned by Progression.
They are not a deck, hand, consumable, or squad definition. Definitions remain
reusable intent; the Machine Shop fits learned equipment freely.
The browser may give rare known entries stronger borders, color, and provenance copy
to make acquisition feel rewarding, but those effects never change definition contents
or combat rules.

### Trying a marine's loadout

The selected occupied billet offers **Test Loadout** when its marine is active
aboard the company ship and that ship has a working fitted firing range. The
trial uses the marine's aptitude and that billet's proposed Weapon and Armor
equipment, including special equipment, the armor's integral system, and the
experience band that armor would issue. Testing does not require fitting the
squad first.

The player directly controls a disposable marine in a detached copy of the
ship's actual range. No campaign soldier identity, equipment issue, resource
bill, wound, experience award, or ship damage comes back from the trial.
Escape, Tab, losing the controlled body, or closing the operations dialog
discards the trial. Ordinary return restores the same selected billet and
equipment proposal. Vacant, wounded, or stationed billets cannot enter; a
missing or damaged range is explained rather than replaced by an invented room.

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
assemblies, their finite owned/free count, provenance, capacity, and price.
Fixed cores and ammunition remain inspection-only.

The fabrication bay is a diegetic, flat top-down ship facility rather than a neutral
diagram or pseudo-3D illustration. Its floor, walls, four hazard pads, registered
industrial fixtures, service gallery, and connected cross-corridor reuse the battle
renderer and the same room vocabulary as generated facilities. The selected mech uses
the battle compositor's actual layer order and hull-relative mount transforms, while
workers use real layered infantry dolls on shared ambient routes for welding, parts
movement, inspection, and coordination. Every occupied berth publishes two shoulder,
two waist, and one head access points. At runtime, each resolves to a close, free
standing cell outside the body and a contact on the parked asset's oriented service
envelope, whose beam and length belong to that asset
rather than to a mech-only UI assumption. Fixture work retains its authored point of
interest, but only active machine servicing receives the welding torch and sparks.
General repairs, bench fabrication, parts runs and readouts keep their ordinary work
poses without welding effects. The torch and sparks project the serviced machine's
body focus through the room camera, independent of the selected gantry. Machines stand at the
continuous centre of their exact berth footprint and face
cardinally along the berth's long axis toward its mouth and the room's shared inboard
service lane; that same authored heading remains their way out. Those actors are
presentation-only and do not create a second schedule, labor,
inventory, or refit authority. Wide-screen layout is the
reference composition; narrow and user-scaled layouts retain access through bounded
scrolling rather than compressing the room until every label is simultaneously visible.

The weapon-refit catalog supports both click-to-commit and direct manipulation.
Dragging catalog weapon art onto a compatible doll grid runs the same atomic fit or
fabrication command as its button. Dragging installed weapon art between doll grids
moves it only to a compatible vacant hardpoint; dragging it back across the equipment
catalog returns it to stores and leaves an explicit empty mount. Pointer capture keeps
the transaction coherent outside the source element, while a movement threshold keeps
a click on installed art as socket selection. Green and red footprint previews explain
the proposed fit without owning mutation.
Ballistic, energy, missile, and omni are compatibility rules; component slot cost and
the price remain independent constraints. A free owned assembly installs
directly, anywhere. If none is free, one atomic campaign command rechecks the port and
the company's credits, pays the price, adds one finite assembly, and installs it while returning the outgoing
assembly to stores. The missile mini-fab retains its existing finite-stock command;
cores and integral ammunition remain locked.

A vacant gantry presents the three established chassis patterns rather than fake
equipment controls. A successful commit, at a civilized port, pays the chassis price
and creates a persistent campaign mech with stable identity, default doctrine, standard
replenisher, and its complete standard roll-out fit. It cannot exceed the lance's four
physical gantries. Prices show as one credits row with the credit glyph.

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
catalog scoped to the selected one. Choosing a boat moves the camera, the way
choosing a pad does in the lab: it eases in on her berth and stands her right of
a drafting board, the **boat doll**, while the stores take the left of the room
and her specifications, condition and selected mount the right. The doll is the
lab's socket schematic, not a picture of the boat: each slot is a socket of
bricks with the fitted piece drawn over them in its socket's colour, wired to an
airframe core — door guns across the top, port to starboard, plating, drive and cabin
beneath them, and flux, hold and shield along the bottom — and the stores show each part on its own
bricks. Where a gun sits is shown on the boat herself: each hardpoint is ringed on
the berthed boat where her hull's weapon slot puts it, with its traverse as a
wedge, and the gun fitted there stands on it as its turret, the same sprite at the
same size it lifts with. A socket, the selected-mount card and the spot it names on
the boat are one selection.

**Fitting is drag-only, and parts are kept as the Mech Lab keeps them.** The stores
list every part the boat can take, narrowed by a filter strip of her socket kinds
(selecting a socket sets it); a card carries its tier, spare count, effect and
footprint. A card with no spare is greyed with its price and a BUY that buys one
into stores; away from a civilized port BUY refuses and the card says why. A spare is dragged onto a doll socket, which lights green where it fits
and red where it does not. Any fitted part, stock ones and the yard's gun included,
is lifted off the doll the same way: dropped on the stores it goes back as a spare
and the socket stands empty, and a gun dropped on another hardpoint moves there. What a fit displaces goes
into stores. A card with a spare, while the machine shop runs, carries a RECYCLE
beside its recovery in green; the first press arms it and says what comes back, and
a second press on the same card commits (Company resource bills, Recycling). The stores cards, filter pills, meters and selected-mount card are the
shared **fitting kit** (`FittingCard`, `FittingFilterPill`, `FittingMeter`,
`FittingSlotCard`, `PointerDrag`, `FittingDragGhost`) that the Mech Lab is built on
too, so the two rooms draw one vocabulary rather than two copies of it. Selecting the already-open `BOATS` route
puts the deck back rather than acting as a dead control, the way the lab's own
route does. A hull with more than one hangar gets the same fore-to-aft pager,
and one with none shows the route greyed, exactly as a hull with no vehicle bay
shows no lab.

**Second Wind starts with two wrecked Hermes, not two usable boats.** The accessible
wreck is repaired for the first ferry job, Counterattack, for the Hermes price,
drawn from the yard credit the local depot holds. Relief needs no boat: the company rides up to
it on Wayline's truck. The repair is Castle's first-lift lesson between the two
chapters. Restoring the sealed bay exposes the second wreck; its repair
is a separate paid order. Each original wreck is consumed once by a successful
repair. Failed payment leaves it intact, and a later combat loss never recreates it.
Legacy occupied or previously opened berths are treated as already recovered; the
still-sealed second berth can retain its original wreck. Other hulls keep ordinary
commissioning. The room draws dismantled wrecks through the existing aircraft wreck
renderer; they supply neither lift nor ambient servicing or sorties.

**New companies commission their boats.** A hull supplies the berth count and
compatible pattern, while a new company's berths start empty. Commissioning
pays the authored price and creates a standard-fit boat. One
reusable boat supplies the first ferry job's lift through repeated trips; further
boats increase simultaneous delivery. Existing owned boats remain intact, and a
legacy deck without an established ship receives its historical complement once.

**A boat that was shot down is gone.** Reads preserve vacant berths. Moving to
another hull compacts the survivors but never fills vacancies for free. Every replacement
requires the ordinary workshop price, creates a new identity at standard fit, and
never recovers the destroyed aircraft's installed fittings.

**A damaged survivor is grounded while the crew repairs it.** Recovery begins at
mission settlement and runs concurrently for every surviving boat aboard, including
while travelling. Full recovery takes two campaign days times the worst missing-hull
fraction observed during the battle, capped at two days. Tactical servicing can patch
a boat for another trip but cannot erase this overhaul work; trips do not add
separate repair jobs. A saved deadline carries progress across closing screens,
save/load and compatible ship transfers. Old boats without a deadline are healthy.
The original wrecked Hermes and sealed facilities remain separate paid restorations.

Boat Deck anchors a hull-condition bar and recovery clock to the actual berth;
clicking that marker selects the aircraft. The side panel holds retained fittings
and the paid repair price. Grounded
boats stay visible on their berths but neither ambient flights nor mission lift can
use them. Briefing explains the next recovery when all company lift is grounded;
launch rechecks that committed aircraft still exist and are airworthy. Paused UI time
is not campaign time. Returning to campaign and waiting requires no credits,
docking or restored workshop.

**Expedite repairs buys back the remaining wait.** It costs half her replacement
price times the remaining damage fraction, rounded up, with existing workshop tooling
applied to the quote, and needs a civilized port. The order completes that boat now
and retains identity and fittings. Time can lower a reviewed price; a changed job or
increased price requires review, and failed payment changes nothing. The shared
Available strip stays separate from Cost and Short by. Destruction takes precedence
over recovery and replacement never inherits the lost aircraft's upgrades.

Castle introduces the first damaged return, including after defeat, through her
portrait and the actual condition/order controls. Founding induction takes precedence.
An unfinished receipt survives alternate company/briefing entries and save/load;
returning after completion explains the ready boat, and a missing boat explains
replacement. Acknowledging the choice completes the lesson without requiring spending.
Paying for repairs alone does not acknowledge it. `boat-condition-and-repairs.md`
retains live campaign acceptance.

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
its boats go into the new hull's berths in berth order. **The boats travel with
the company to any ship and are never left behind**: a hull of another pattern
does not change the deck's pattern or trade its boats, and a hull with fewer
berths than boats still keeps every boat. A boat with no berth is stowed: it
flies like any other and is not drawn in the bay. Vacant berths on the new hull
remain empty until the company commissions boats.

That reconcile is idempotent and runs **whenever the deck is read against the
ship the company is on** — the room attaching and the lift itself — so a stale
deck is never presented and never flown. It is a read-time rule rather than an
event somebody has to remember to fire on a transfer, and one authority
(`ShipsBoatsAboard.reconcile`) knows what feeding it means; the room decides
only when.

**Every part is a counted spare** (Boat spare): a card carries its price and an
affordability reason beside its spare count. The room header keeps a compact live
stock strip of the company's credits and whether the fleet is at a civilized port,
separate from the selected part's or boat's price row; the Mech Lab's strip is the
same.

**A hardpoint carries a gun, fitted per hardpoint.** A boat has as many
hardpoints as its pattern and each holds one gun from the yard's boat-gun catalog,
or none; hardpoints are fitted by index, not as a slot. Guns come in three
**families**: ballistic door guns, energy guns that fire on flux and carry no
magazine, and missile pods with a few salvos a sortie. **Any family goes on any
hardpoint**: the hull's slots are small ballistic turrets, but a boat's yard is not
bound by a slot's type; the frame's weight is the limit. A hardpoint nobody has
refitted carries the pattern's air-to-ground kit for that mount, and putting that
yard gun back is free; on the Hermes that is a heavy MG on her first turret and the
other two **bare**. Stripping a gun puts the yard's own back, which on a bare
turret leaves it bare. Any other gun is a spare with a price, like a
fitting. The gun stands on the hull's turret slots before its fixed hardpoints, so
the place the doll rings is the place the battle mounts and fires it from, and a
company boat's gun fires only through its slot's arc: the Hermes's stern gun
covers the stern. The fitted guns replace the kit only for an air-to-ground sortie;
other roles still arm by role.

**The Hermes is the company's boat, and every refit is built around her.** She
takes every slot's upgrades, and a gun on each of her three turrets, from stores,
within her frame. The
Boat Deck shows the frame as a meter (weight / rating) and every part's weight on
its card and sheet.

A failed fit or purchase changes nothing but the room's feedback line — neither the
boat nor the purse — because the workshop rechecks the port and the credits at the
commit and pays the whole price before installing anything.

**Presentation conveys the deck; it does not own it.** The picture is the ship's
own hangar drawn by the deck scene, and the boats in it are the aircraft the bay
actually keeps and turns round. The shared company deck loads aircraft hull art
before building its renderer, alongside crew and machinery. Commissioning rebuilds
berth occupancy and the open Boat Deck adopts that scene without a manual refresh.
What the room adds over that render is the one
thing a render cannot say — which berth is selected — so a click on a berth and
a click on its card are the same act. The campaign deck is one berth list across
the whole ship while a bay knows only its own, so a bay's berths are read as its
share of that list; the two counts are measured equal rather than guaranteed
equal, and a disagreement in either direction is drawn honestly rather than
thrown. Fittings do not change the picture, and they do not change the
turnaround: a better boat is faster and tougher in the air and is not serviced
faster on the deck.

## Company resource bills

Company rooms show a shared stock strip immediately below navigation, away from
individual orders. Each resource is its icon and its exact, grouped quantity, with
no written name: the credit glyph and the base game's cargo icons are the
vocabulary the player already reads everywhere else. Columns are bounded,
including for holdings in the millions. The strip includes the resources
that room can actually draw — the Boat Deck and Mech Lab draw only credits, and
add a port line (docked: buying open, or no port: fit only); named ready reserves
remain a separate personnel count. Fleet cargo and the opening depot keep their existing access and earmarking
rules. Engineering reclamation uses fleet cargo, never the preparation depot.

An order shows **Cost** for each charged resource and **Short by** only for a
positive deficit, with the commodity name and icon. Commodity order is consistent
across bills and stock. Credits use the same vocabulary. Payment amounts belong
in the bill rather than in buttons. Uncharged fitting, assigning and installing
held spares use ordinary action names without a zero-cost bill or a Free badge.
Already-installed fittings show their installed state rather than a purchase bill.
Fabrication remains visibly distinct from installing an owned spare.

The presentation reads each workshop's quote; existing transaction authorities
revalidate and spend at commit. It does not merge infantry equipment, mech spares
and boat fittings into one economy. Bulk personnel orders quote the actual eligible
cargo draw after named reserves, retain skipped-squad reasons, and explain any
remaining full-muster shortage separately. Wounded retain their billets. Tutorials
use the same cost vocabulary and point to the stock display in the relevant room.

### Recycling

The workshop authority owns recycling as one transaction: it rechecks the spare and
the shop's rate, pays the credits into the purse, and only then takes the spare out of
stores, so a refusal changes nothing. Only a counted spare is recycled: a fitted part
is lifted off to stores first. A spare whose share rounds to nothing offers no RECYCLE.

The share is of today's quote, tooling and the spares rack included, so a recycle can
never return more than a rebuild would spend. The rate follows the home ship's machine
shop (`machine-shop-restoration.md`): buried or cleared, no recycling and no button;
repaired machinery, half; installed tooling, three quarters; the reclamation line, the
whole quote. A hull outside the restoration chain runs its shop as built, at half.
Recycled credits go to the company's purse, never a depot.

Scrapping a whole machine follows the same rate. A boat's SCRAP sits in the Boat
Deck's stage heading beside the berth, as the lab's does over a pad, and takes the
same two presses. `BoatWorkshop.scrap` checks the purse first, strips every fitting and
gun to stores through the ordinary strip, credits the hull, and leaves the berth
vacant; a shop that cannot recycle still breaks her up, for her parts alone. A mech's
hull is its chassis quote less the frame's plating and any factory-locked gun, which
stay as spares; a vehicle is bought whole without kits, so its whole quote is hull.
At the full rate, building a machine and breaking it up again is exactly free and
never a profit.

### Reserve hangar

STORE sits beside SCRAP in the Boat Deck's stage heading and the Mech Lab's garage
heading, shown only while the home ship's refit has room, and takes the same two
presses. It takes the machine off whole and leaves its berth or gantry vacant, as a
scrap does, but nothing is stripped or broken up. A Reserve strip ("RESERVE HANGAR
n/cap") lists what is stored: under the boats aboard on the deck overview, and in the
lance rail of the Mech Lab. RECALL puts a machine back into the first vacant place
of the right kind: a boat into a vacant berth of the pattern this hull's bays hold
(never over a founding wreck), a mech or vehicle onto a vacant working gantry of the
lance shown. RECALL is greyed while there is none.

`ReserveHangar` owns every move, and each is atomic: it checks room, the machine and
its destination before anything moves. A stored mech's or vehicle's fitted parts
leave the bay's owned counts with it and are owned again on recall, so storing never
turns a fitted part into a loose spare. A ship without the refit still lets
everything be recalled; storing and recalling are free and cost no upkeep.

The refit is a Structure-branch facility bought in credits alone: Restored (12,000,
after Structure and Power are restored) holds two machines, Improved I (20,000,
after Structure and Fabrication are restored) holds four.

## Shipboard Barracks

Barracks has no order panel. Filling vacancies is the **wrench**: on a squad card
it fills that squad, and beside the select-all toggle it fills the selection. Each
is an explicit order whose hover states the draw (named reserves first, then cargo
Marines) and what stays unfilled, never a button that silently spends all available
resources. A card shows its wrench only while it has true vacancies. There is no
equipment order, because replacements take their seat's kit as they join. Current billet counts state ready, wounded and vacant
separately. A squad card's **Loadout** opens that squad's exact Armory equipment
workspace; returning retains the Barracks selection. **Raise Squad** is a tile at the end of
the formation board, drawn as an open slot the size of a squad card, not a separate page: it pays the full founding bill and raises only into a free command slot: the open officer's, or
with All formations open, the first officer on the roll with room. When no
command has a slot the tile is disabled and says why, so the officers' combined
command caps are the company's force limit for raising. Its bill reads as one line
of commodity icons and counts, each marked when the stores fall short.

Barracks has no order that moves marines between squads. A squad gets its
strength back by waiting for its wounded, who return after one day of ship time,
or by filling true vacancies from reserves and cargo. The room header counts down
the whole hours until every line squad's wounded are back on duty.

After an operation, Prepare Company opens Barracks once salvage has been settled.
When a claim remains, that route first opens the normal salvage picker; it never
forfeits a claim just to change rooms. A ship still being prepared routes to HQ.
The ordinary mission-list exit remains available, and a planet-backed Barracks
visit exposes Missions to resume local work. Campaign-map visits never invent a
market-backed mission route.

The company officer offers a short, dismissible first-use management guide in an
anchored callout with her portrait and name, plus a Company Guide resume action.
She is pragmatic and matriarchal: warm, observant and firm as the moment needs,
with occasional dry humor rather than a fixed dry tone. The guide highlights the
next real preparation control and moves beside it. A dim backdrop leaves openings
around the lesson's declared controls and readouts: formations with filters and
paging, or the current resource order with its availability.
Only those regions and the portrait callout accept pointer and keyboard input;
scrolling stays available for the relevant pane. Openings track the live layout,
and dismissal immediately restores the full screen. Successful selection and preparation orders advance
the lesson; Next skips a lesson without spending resources. Advice includes current
selection and shortfalls, asks for no unnecessary spending or fake training casualty,
and keeps WIA in their billets. Dismiss or Escape closes it. Dismissal is saved
with the company; older saves default to an available introduction. Text is complete
without a recording; stable beat IDs allow separately auditioned ElevenLabs clips
using the existing company-officer cast.

The first non-debug briefing that recommends at least two squads introduces
Castle's **From One Squad to Two** reminder after initial induction. It states
both the recommended strength and the route's permitted depleted minimum,
counts actual ready personnel, and shows the authoritative one-squad founding
bill (its price and its marines) with what the company holds and the amount short. Founding includes the initial
kit; later refits have separate bills. Direct Armory and Barracks routes teach
raising a squad, assigning its home commander, and selecting it for deployment.
The reminder never spends cargo or changes launch rules. Explicit acknowledgement
is saved; leaving to buy stores or raise a squad keeps the unfinished reminder,
and the briefing can replay it. Existing two-squad companies are told not to
raise an unnecessary formation. Casualties do not reset acknowledgement.
The founding bill is indivisible, so a company that cannot yet pay it is never held at the
reminder: **Not Now** sets it aside, saved with the company, until the company has more
formations than it did then. The briefing still states the route's own two-squad requirement.

New companies receive one **First Mission Preparation** sequence with Castle:
company ship, commissioned lift, founding field commander, and her first squad.
It extends the saved officer induction rather than keeping a second tutorial
ledger. HQ introduces the sequence and ship selection triggers its workshop flow. Room
navigation follows current designation and deck readiness while the screen remains
open: no ship, preparing, and available/missing rooms are live states. Actions
recheck availability when clicked, including after a later hull transfer.

Ship confirmation checks quarters, at least one compatible boat berth, the
remaining credits for one boat and the remaining stores for one full twelve-person
squad. Both derive from the existing fabrication and founding catalogs. Compatible
owned lift and an already complete ready squad reduce what remains. Cost, Available
and Short by are shown before confirmation, which rechecks the purse and the hold. Selection spends nothing; each subsequent
workshop order consumes its own bill. Following the lesson needs no market trip.
Deliberately pausing to spend or sell those stores elsewhere can still create a
shortfall; the lesson never manufactures resources or overrides a workshop refusal.

Lieutenant Sabine Hale is the recurring founding field commander. She commanded
the escort section on Harker's Claim's ore runs, and the twelve who stayed with
her when the operator stopped paying for escorts became the company's first
squad. She values clear orders and a counted muster. Her ordinary appointment,
career and service record carry that role, with no extra specialty point or
invented recruitment charge. She is the one officer who cannot be lost in
battle: she carries the long story and later the company's ground-commander
voice, so a lost battle that would kill another officer wounds her instead, for
the same time as any wiped officer. The dossier retains her authored background
and dedicated female portrait.

After commissioning, Castle introduces Sabine's portrait and guides an actual
specialty choice. Barracks then guides the ordinary twelve-Marine, 36-supply,
24-food founding transaction on its Raise Squad tile, which includes baseline field
kit; with Sabine's command open the squad musters straight into it. A squad that
reached Barracks unassigned is homed through the capacity-checked Assign Selected
button on the commander card.
Completion requires a current company ship with owned lift, an actually trained
officer, and a full ready available squad assigned to that officer, followed by
acknowledgement. It returns to the retained briefing, or to HQ's existing Mara
Venn course/contact lead for Hold Until Relieved, canonically on Jangala.
The accessible-office policy remains the mission domain's authority.

The portrait popover moves beside the real controls. Its dimmed background has
live click-through openings, and four SVG milestones identify ship, officer,
squad founding and assignment. Ship selection, specialty training, squad
founding and assignment remain real orders. The opening lesson has no Later
control and Escape does not dismiss it. Return to campaign remains available for
travel or resupply; the required lesson returns on re-entry. Advancing dialogue
cannot substitute for real orders. The lesson always belongs to Sabine, since
she cannot be lost; no other officer takes it over.

Legacy companies without induction stay exempt; existing officer-only lessons
retain their scope and progress. All ordinary deployment routes enforce the same
saved completion guard before committing resources, while debug missions remain
exempt. Once complete, later losses never re-lock the tutorial. Readiness and lift
checks still apply to each mission. The lesson takes precedence over the optional
replenishment guide. `first-mission-preparation.md` and
`castle-officer-induction.md` retain live acceptance; narration remains a separate
production pass. Castle's first damaged-return guidance follows the Boat Deck
recovery model and preserves this founding lesson's precedence.

Barracks opens on company formations grouped by their home officer, with an
independent acting-NCO group for squads without an officer. Commander portraits,
readiness and occupied command slots make the organization visible. Search,
profile and attention filters bound the roster; pagination limits rendered squads.
One toggle selects every matching available squad across pages, or deselects them
once all are selected, and the board shows the selected total. Changing a filter clears selection; inspecting a squad and returning
preserves it. Stationed formations can be inspected but cannot receive bulk orders.

Formations are compact selectable cards. Each leads with the squad name and ready
count, then its two cards as two icon slots side by side (weapon card, then tactic
sheet). A slot carries no words: hovering it names and describes the card (rarity,
tier, rating, composition and lore). Then come its personnel state and
twelve current billets grouped into three fire teams.
Filled silhouettes, medical marks and hollow silhouettes distinguish ready,
wounded and vacant places without relying on color. Selection has its own frame
and checkbox; inspection is a separate action. Each card face is iconed by what
most of its seats carry, and its hover lays out all twelve seats as fire teams:
each seat's weapon with its special on the corner, or each seat's suit with
its integral system on the corner. Short personnel states stay visible, while hover and
keyboard-focus details explain recovery, command and cards. The card does not
repeat its commander: the officer rail already says whose squads are in view.

There are no saved squad profiles: the player picks each squad's two cards
individually through its Loadout. A save written while profiles existed gives each
squad the cards of the profile it was assigned, then drops the profiles.

Select squad cards, then use **Assign Selected** on the desired commander card.
The button shows the selected count and a live capacity preview; unavailable or
full officers cannot receive an invalid selection. The Independent card clears
home command through the same transaction. Viewing an officer dossier is a
separate action from assignment.

Home-command assignment applies the roster's atomic capacity checks to the whole selection.
Personnel replacement uses ready reserves first, then the existing one-Marine-per-
billet cargo transaction, in roster order. It stops when personnel run out; wounded
marines keep their billets and are never replaced to make a readiness counter green.
The preview shows available replacements and remaining vacancies. The base-game
Marines commodity icon marks unenlisted recruits in fleet cargo; its stock remains
tradable until enlistment. Named reserves already belong to the company and are
counted separately. Compact stock and enlistment amounts expose the distinction,
with hover details explaining the transfer. Replacements and
equipment issue are separate explicit orders, so assigning a role never silently spends.

Equipment fitting applies the reviewed plans freely to eligible selected squads,
including wounded and understrength formations. Stationed squads and plans missing
technology remain blocked. Current template ownership and exact issued equipment
remain authoritative. This introduces no finite rifle or suit stock or automatic
purchasing; personnel and founding costs remain separate.

Barracks has no individual squad drill-down. Ready, wounded and vacant counts
stay on each formation card, and per-billet kit is read in the squad's Armory
workspace. Squad and marine careers are still recorded but the squad career has
no Barracks surface; giving it one is open progression work
(`progression-nouns.md`).

The campaign roster holder establishes only the non-deployable reserve formation
during game load. A new company has no line squad until the player commits the full
founding bill on the Barracks Raise Squad tile. Company HQ, Barracks and Fleet Armory
therefore present honest zero states; with no line squad, Equipment has no loadout
to open and stays in Barracks. Existing saves retain their named formations.

Leisure is authored as battle-owned **ambient task routes**, not screen-owned
animation. The same deterministic station/walk/activity sampler now drives Mech Lab
technicians and is available to mission setup for civilians, workers, engineers, or
guards. The live company deck advances its bounded simulation on the ordinary
fixed battle clock; deterministic evidence replays that same clock to an authored
time. The deck has no mission winner, so its bounded clock remains live until its host
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
area**. Transports share one area by default, so different physical berths and
repeated lifts join one battle squad without per-mission grouping setup.
Only explicitly different tactical areas split a campaign squad into labelled
fragments; touchdown coordinates never define organizational identity. The frozen NCO
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
The gate holds a squad against its commander only; a player's order sends a
partial squad forward at once.

A campaign marine who lands outside squad cohesion of a squad that is no longer
held at its landing zone — the timeout expired, or the player sent it on — is
**rejoining**. That is the case the timeout leaves behind, and it
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

Each committed marine carries a value-only officer profile. Mission outcomes
credit officers whose marines actually reached the field or were lost aboard
company lift, plus operation command. Held-back home officers receive no credit.
Their rank, XP and specialties come from launch, never from later assignments.
Each result carries a unique attempt receipt so reopening a result cannot repeat
an officer's XP, promotion, fate or commendation. Repeatable jobs may share a
mission id; their attempts do not share receipts. Defeat awards 50 officer XP
when troops engaged; victory retains payout/100, or payout/150 with more than
two casualties in that officer's element. Natural Leader multiplies that
individual officer's award by 1.5. Defeat fate uses each officer's own engaged
strength and losses; operation command without an element uses the whole force.

The Barracks portraits open officer dossiers: assigned squads, status, command
capacity, rank progress, recorded operations, existing traits, specialty choices
and the latest twenty commendations. VIEW FORMATIONS returns to that command's
filtered squad list. One permanent training choice is available on appointment
and one per promotion, including cadre-earned promotions. Existing officers
receive their current rank's equivalent budget without invented service history.
Specialties are **one vertical tree**. Its root is the officer's training budget;
a trunk drops to a single crossbar, and each specialty is a branch hanging from
that bar, so a new specialty is a new neighbouring branch rather than a longer
page or a separate tree. A branch is its SVG emblem and title over its ranks,
stacked top to bottom; every rank is an SVG node that reads locked, ready to
train or trained.
The page carries no effect copy: hovering a rank opens its sheet with what it
does, whether it can be trained and what it costs, and clicking a rank that is
ready trains it. Only the next rank of a path, with a point to spend, is ready. Castle's officer-only induction shows five SVG milestones; full preparation
shows its four company milestones; neither progression display uses a stretched add button.
Each of four specialties has two ranks:

- Tactical Cover: incoming accuracy multiplied by 0.95/0.90 for marines in this
  officer's actual deployed command. This composes with issued armor; it does
  not change equipment ownership or the displayed equipment definition.
- Recovery Planning: WIA duration multiplied by 0.85/0.70 for those same named
  marines. Wounded remain wounded and retain their billets until recovery.
- Salvage Stewardship: +10/+20 recovery percentage points on victory. Salvage
  is one company pool, so only the strongest participating specialist applies;
  it adds to existing salvage-expert/fleet-hardware modifiers.
- Field Medicine: one designated medic in each of this officer's squads, who
  treats squadmates in battle from a few slowly recovering supplies
  (`progression-nouns.md` owns the treatment).

Unavailable officers and independent NCO groups provide no new field specialty
effects. Moral outlook and recruitment traits remain separate. The dossier
explicitly identifies inert legacy traits rather than promising effects.
`officer-careers.md` and `c13-the-task-force.md` retain live acceptance work.

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
`MECH LAB`, and `BOATS`, marks the occupied room, and carries the precise location context at the
right. `RETURN` closes top-level rooms; within Equipment it returns to the caller.
Room routes move directly between top-level surfaces. Equipment remains selected
under Barracks. Company, squad, and fire-team breadcrumbs remain page-specific below
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
  monthly data is unknown, never zero. Client crests follow the employer house's
  actual faction identity; an unknown identity never borrows a guessed flag.
- **Mercenary rating and assessment** make the company's industry position and
  immediate posture readable without conflating them. `CompanyRating` is a
  presentation band over authoritative MRB credibility, never a second reputation
  track. The bridge adjutant's authored daily assessment uses the existing officer
  mood and live readiness facts; it may change as the company's situation changes
  without rewriting that rating.
- **Situation board** leads with obligations that can hurt the company: pending response
  deadlines, live stationing term endings, and the recovery deadline of a stranded
  detachment. Overdue clocks are due now, never
  rendered as negative time. One contract may own both clocks. An intel terminal
  consolidates company dispatches, confirmed reports and unverified traffic into
  source windows, a compact report rundown and a focused briefing. Each source
  window previews actual received reports; choosing a source filters the rundown,
  and choosing a report brings its confidence, timing and detail into focus.
  Selection survives refresh by report identity and remains view-local. These are editorial
  categories over known information, not invented agencies or simulated new
  messages. Company operation reports belong to their own stream; political
  reports belong to the sector stream even when they concern the company.
  A Garrison defence settled in the player's absence is a company report: its
  after-action account states only frozen settlement facts, with authored
  colour that names nobody and claims no consequence.
  The newest bulletin opens the archive; recent validated Chronicle facts remain
  newest-first. Counts describe the bounded report window. It reads only learned Chronicle rows,
  preserves rumor versus confirmed confidence, and cannot expose undiscovered world
  state. The network display gives each source a receiver and a route through
  collation to the archive. Scans and traveling carrier traces are ambient
  instrument activity; only newly observed report identities produce a received
  report burst. The initial archive and unchanged hourly refreshes do not replay
  arrivals. Presentation remembers seen identities while the screen lives, even
  when a report falls outside the bounded window and later returns.
  Selecting a dossier triggers a brief read sweep; its text, source, confidence
  and subject remain ordinary readable content. A known subject's faction crest
  follows persistent affiliation, never a guess from its name. Receiver motion,
  selection and effect timing remain view-local and survive document refresh.

HQ also presents roster-derived directions to Mara Venn's opening work, canonically
on Jangala: her portrait and Independent crest, current planet/system, mission,
payment, preparation advice and local client route. Plot Course sets the ordinary
campaign navigation destination and dismisses HQ. The notice advances after Relief
and gives way to Broken Supply Route guidance after Counterattack; it never starts work, invents a received report,
or consumes company resources. Location availability and fallback are campaign facts,
not a location invented by the view.
`early-operation-nouns.md` owns availability and progression. The follow-on
notice keeps the accepted planet fixed, distinguishes pending commitment from
success, and shows the selected recurring arrangement after closure. Full
historical rescue counts and story/recurring awards remain in the broker dossier;
HQ uses a compact status and navigation lead, never a second progression record.

After the supply-route account closes, the Corvus Account lead follows Mara's
current chapter to Asharu, Warion and Garnir. Its course target and local offer
share the chapter's exact destination. Unavailable worlds disable navigation
with an explanation; completion retains the finite arc's paid record and
reminds the player that prior local work stays at the original site.

The clock owns obligations; the future contract board owns offers. Ignoring an
obligation can cause failure or end a retainer, while ignoring an offer merely
loses an opportunity. Responding to an obligation follows the same deployment
route as its campaign event notice. Long lists state hidden counts, and missing
derived inputs are explained rather than silently collapsed.

The HQ introduces no persisted state and no second company hierarchy. Troop
management routes into Barracks. Its Equipment entry and each squad's Loadout open
one squad's three fire teams, named-marine viewer, two squad doctrine slots, and
exact issue action.
Breadcrumbs retain editor navigation, while the Barracks tab returns to the
personnel workspace. These containers project the canonical roster; none creates
parallel organization or another inventory economy.

## Cerberus founding start and planetary stores

The optional Cerberus company start is selected through vanilla character creation.
It gives the player one unconverted Cerberus, 473 spendable credits and travel reserves;
it neither designates the vessel nor awards a shuttle, squad, mech or specialty.
The hull starts as the wreck the story describes: a Cerberus (D) carrying a full
wreck's five d-mods and only two Vulcans in its small mounts, the guns nobody stripped.
Vanilla and Nexerelin starts hand out the same variant (`CerberusCompanyStart.START_VARIANT_ID`);
restoring and arming her are ordinary yard work, not part of the opening.
Castle directs the player to an accessible planetary spaceport, preferring Jangala.
A new selection first opens **Second Wind**, a skippable seven-page illustrated prologue
with Castle's portrait and readable dialogue; each page turns on the Commander's own
answer to her, and the last one, "Take us to" the start port, plots the course. The crew escaped **Harker's Claim**, an
independent mining asteroid, after its operator withdrew. Company scrip became
worthless as the colony's supplies ran out. They restored a grounded Cerberus; the player
earned command by organizing the escape, while Castle supplied port and convoy
logistics experience. Earlier player biography remains open. They set out for
Jangala from the start, on Castle's one contact there, Nessa Venn at Greywater Down.
Everyone aboard holds worthless company scrip; the real credits Castle quietly saved
are what pay for the yard, and her finite prepaid consignment explains the stores. Castle holds the
4,862-credit conversion payment (her chits) separately from the fleet account, usable only for
the original ship's yard conversion. It falls short of the 4,986 estimate by exactly
the pressure test, which the yard waives whenever the reserve pays, so spending the
fleet account down to zero cannot prevent that order. The reserve is consumed once, never refunded as cash or renewed
on load; existing campaigns keep their original funds and payment rules. Civilian crew keep
the ship running; trained Marines must still be mustered through preparation.

Jangala is a practical first berth, with regular traffic and **Nessa Venn**, a yard
forewoman who ran ore with Castle before settling there. She once towed Castle's
disabled freighter home despite repeated threats to leave her behind; their familiar
barbs and her attention to the repairs carry that relationship into the yard visit.
This personal help is not a promise of Hegemony protection. Harker's Claim and Nessa
are authored backstory, not new sector entities or additional mission prerequisites.
There is one story for every start port (owner, 2026-10-20): when the start
lands elsewhere because Jangala is unavailable, Castle, Nessa and Mara still speak
of Jangala, and only the course, objective, Intel and mission text that names a
place uses the actual accessible destination. Its final action plots
that course and returns to the campaign. The Intel log retains the origin and
next real preparation instruction. Page progress and explicit completion or skip
survive saves; ordinary starts and pre-prologue saves do not opt in on load.
The opening grants no resources, repairs, conversion, recruits or mission progress.
Its illustrations are presentation, not a new hull-condition authority. Each beat
has its own image: bridge, Claim, last carrier, emergency repairs, boarding,
port approach and makeshift sleeping space. Exterior workers wear
sealed industrial EVA suits; boarding takes place inside a pressurized loading dock.
Exterior views use an upscaled vanilla Cerberus sprite as their identity reference.
Runtime art is full-frame 1200x800 JPEG; larger masters and reference art stay outside `mod/`.

A reviewed 4,986-credit yard order installs the permanent Mercenary Company
Conversion on the owned, active hull. Paying for it on the founding Second Wind
designates her as the company ship without a transfer screen: she is the hull the
conversion was bought for and her deck is pinned (`second-wind-refit-tree.md`), so
there is no choice to offer, and her deck is laid out ahead of the first visit. The
automatic designation still passes the ordinary preparation preflight; a refusal
leaves the ordinary transfer choice. Designation resumes Sabine, squad founding and
assignment.

The Cerberus opening names **Mercenary Company Conversion** explicitly and directs
the player to Marine operations in the planet menu, not the vanilla Refit picker.
At an accessible planetary port that entry opens the founding conversion first as
a four-page storybook on the prologue's retained document and custom visual host:
the bill, paid by page 1's Next; the runway the account leaves; the
road up; and the ore-dust papers that send the company to work (`opening-dialogue.md`
§2). Nothing is spent before that Next. After it the pages are a straight
click-through with no exit control, and Back never returns to the paid bill. The order buys +18
personnel places and trades away 25 cargo; the remaining damaged rooms, second
bay and Mech Lab stay damaged. The planet interaction remains alive as the docking
and depot authority. The opening is one walk over that dialog, never a trip back
through its menu: the Jangala arrival hands straight on to the yard storybook, and a
storybook closed after paying hands straight on to Marine operations at Sabine's
induction. The ordinary port menu returns only when the player leaves Marine
operations, or leaves the yard before paying. Finishing the preparation opens the
mission board, since the company's next step is its first contract. Until the
founding conversion is installed, no alternative hull can bypass company designation.

**The menu theme.** The opening storybook (the prologue's pages and the yard's) plays
the same looping Marines theme as Marine operations, never vanilla's campaign music,
and it is one track rather than two players (`MenuTheme`): every surface that wants it
holds it while shown, the first holder starts it and the last one out fades it and
restores default music. A hand-over between holders therefore neither restarts the
track nor lets vanilla in. The yard pages hand it to the port they return to, which
keeps it through that port's menu and into Marine operations until the player
undocks. The Jangala arrival plays its own score: it fades the theme out rather than
under it, and the next storybook page brings it back. The battle likewise replaces
the track and the next ordinary screen restarts it. A game load forgets every holder
and lifts the suspension, so a dialog torn down without its dismissal cannot leave a
campaign silent.

A `PreparationDepot` is a finite prepaid consignment at the planet that converted
this start's Cerberus: yard credit for one Hermes, at her price, and the first squad's
twelve Marines with credit for its stores, at their price. It is issued once, saved independently of cargo, and never
loaded wholesale into the small ship's hold. `WorkshopStores` combines cargo with
that depot only while interacting with its accessible working planetary port,
checks the whole order, then consumes local credit or stock before the company's own. The existing
fabrication and squad-founding adapters share that authority. New Second Wind starts
earmark the first Hermes and first squad bills separately: boat jobs cannot consume
the squad's marines or credit, and other workshop jobs cannot consume either allocation.
Preparation draws the squad's bill. The Hermes credit stays at the depot through
Relief and is drawn by the first-lift lesson, while docked there, before
Counterattack.
Preparation preflight can inspect both allocations but cannot spend them. Legacy
depots keep their existing remaining stock and unrestricted spending policy; the boat
materials they still held become yard credit, and the squad's supplies and food become
its squad credit, at the conversion prices (Price).
The depot does not replenish
on reload or on another conversion; unused stock remains at its planet. Ordinary
starts and saves receive none. Equipment fitting is free; enlistment retains its personnel cargo authority. Castle explains the local stores; workshop labels use available
stores rather than claiming every counted item is in the hold.

Leaving a room or its planet dialog spends no stores and completes no preparation
step. Re-entering through the company shortcut, mission board, or a direct room
link resumes the required step derived from the saved company: ship, Sabine,
formation, then assignment. After Relief the first-lift lesson resumes the same
way from the saved boat deck until a seated boat is airworthy or Counterattack is
won, and sends the company back to the depot when the Hermes credit is ashore. HQ may present the same guided handoff. Required
preparation does not interrupt an active battle, settlement, or developer mission.
If a remaining order needs reserved stores that are currently ashore, Castle names
their depot planet and offers Return to Depot, plotting its course and closing the
company screen. The player docks there and opens Marine operations from the planet
menu to restore access. This guidance never grants replacement stock or spends the
bill; the ordinary confirmed workshop order remains the only consumer.

Second Wind's original starting hull reserves a second shuttle bay from the start.
It is sealed and damaged, supplies no usable berth, and stays visibly distinct
from an empty working berth. Completing **Hold Until Relieved** unlocks an optional
8,000-credit restoration at an accessible planetary spaceport. The order lives in
Engineering, on the sealed hangar, with the repair's Cost / Available / Short by and a
review followed by explicit payment; its company notice opens Engineering on that
room. Boat Deck only says the bay is sealed and where it is restored. The yard price includes labor and structural materials.
Repair exposes the second Hermes wreck; making it airworthy still requires the ordinary
Hermes price. This spending is optional and does not gate subsequent missions.

**While the Second Wind is away the company has no home to open.** She is never
lost for good (`ship-interiors-nouns.md`), but while the company lives aboard her
and she is out of the fleet the Marines button answers with Castle's instructions
instead of Company HQ. Stored, the planet menu keeps Marine operations listed but
disabled, with the same instructions as its tooltip. Lost, the planet option
becomes **Second Wind** at every port with a yard open to the company: a one-page
storybook in Castle's voice - towed in after a fight, or found on the open market
after a sale or scuttling - with the price, the account and the d-mod she comes
back with. Buying is never refused; what the account cannot cover becomes debt.

**Campaign missions need her.** The briefing refuses to deploy unless she is in
the fleet and the company is quartered aboard her, and says which of the two is
missing. Debug missions are not gated.

Squads are fitted from the Armory, not from Engineering. Free fitting remains
available regardless of workshop restoration; repairing production machinery
affects boat and mech jobs.

The same hull's machine shop has a three-step restoration chain after Relief.
Clearance costs 750 credits, includes a pressure patch before the hatch reopens,
and recovers 10 metals once; machinery repair costs
3,000 and enables recycling of stored spares and scrapped machines at half their price;
installed tooling costs 4,500, raises that recycling to three quarters, and takes 15%
off Boat Deck and Mech Lab prices, rounded up. Equipment capabilities, known patterns,
squad issue and yard credit bills do not receive this benefit. A reclamation line, the
fourth stage, costs 7,500 and recycles at the whole price. Existing preparation
services remain available before restoring the workshop.

Paid stages require the original hull to be the active company home and an
accessible planetary spaceport. Recycling works underway once machinery is
restored. Installed tools belong to the hull; portable tools are a separate
future inventory concern. Each order rechecks its reviewed stage, current home,
funds and prerequisites. Inspection and changing screens spend nothing.

The company ship view lets the player inspect the actual workshop while Castle
explains its condition and next benefit beside her portrait. Compact SVG progress
marks the stages; each new stage needs its own purchase review. The room and its
working stations change immediately after a committed order. Fabrication previews
and commits use the same current installed-tool quote. A company that changes
ships receives the new home's benefits, never a discount left over from an old
screen. `machine-shop-restoration.md` owns this chain's live acceptance.

**Engineering** is the persistent ship-condition navigation tab, reached from
every company room and Armory subview. It contains the former Walk the Ship
room-condition survey. Small SVG glyphs straddle compartment walls with sealed,
damaged or faulty-power status. Healthy rooms have no glyph, keeping attention on
outstanding work. Glyphs have no opaque tiles and shrink for small rooms so
furnishings remain visible. Hovering any room or glyph opens a bounded
inspection card beside it; selecting pins that card for interaction without changing
the camera. The card retains Castle's portrait, condition, working benefit, bill and
reviewed repair action. Moving into its controls keeps it open; Close, Escape or
empty-deck selection dismisses it. Cards stay inside the viewport and scroll when
needed; badges follow the same camera as the rooms. A separate compact distribution
control represents the ship-wide circuits order. Sealed structural
rooms are distinct from accessible damaged rooms and usable rooms on faulty
power. Workshop stages retain their own reviewed orders; the second boat bay can
also be repaired here through its existing yard authority.

The original hull reserves a four-berth Mech Lab as a sealed wreck until **Take
Back the Depot**, the second story mission. Completion opens the room and restores
berth one with Wayward's recovery, so the first-mech fitting lesson opens into a
working bay without another bill. The completed chapter is the durable first-berth
repair fact, including on older saves.

Engineering offers three separate 5,000-credit yard repairs in berth order. Each
includes labor and materials, restores one gantry and increases usable capacity
from one through four; it awards no chassis or components. Every berth requires its
own review and authorization at an accessible planetary spaceport, with the active,
converted original hull as the owned company home. Payment rechecks recovery,
preceding repairs, funds and ownership; stale or repeated orders spend nothing.
The hull retains these paid receipts through saves and company transfers. Older
saves with the depot completed begin with berth one restored and the other three
damaged; their owned mechs are never removed. Ordinary hulls retain their facilities.

The room survey shows working berths out of four and the next repair's benefit and
bill. Mech Lab reads working physical gantries, so a damaged pad cannot be selected
for fabrication. The workshop checks this capacity again before spending materials.
Each completed repair refreshes the shared deck, room view and capacity together.

The original hull's mess hall starts sealed. Field rations in working quarters
keep the first mission available, at a cost: company infantry recover morale 15%
slower. A 2,500-credit yard order after Relief restores its pressure skin, galley
and tables and brings recovery to neutral; a proper galley (+15%) and a galley
with cold store (+25%) follow as refit tiers. Recovery applies once the normal
incoming-fire delay has elapsed. Morale still starts full, retains casualty
ceilings and break/clear thresholds, and has no additional food upkeep. Castle
explains the current arrangement and the exact figure beside her portrait in
Engineering.

The benefit belongs to the active converted original company home and freezes
with the selected personnel at launch. Later shuttle trips and split landings
retain it; allies, enemies, mechs and detached debug companies receive none.
Changing homes affects future deployments, not battles already underway. Other
hulls remain on their existing baseline. Hull welfare is distinct from officer
specialties, wound recovery and a future persistent company morale economy.

Two spare company bunkrooms start sealed, leaving 36 of the converted hull's
54 billets usable. A 4,000-credit yard order after Relief restores both rooms and
18 billets. A separate 3,000-credit distribution repair restores steady lighting
in working rooms throughout the hull; it offers crew comfort and a visible
restoration, not a combat buff. Structural rooms remain on emergency power until
their own repairs. Both orders require the active converted original company home
at an accessible planetary spaceport, include labor and materials, and recheck
funds and completion at payment. Inspection and review spend nothing. Every view
rebuilds from the same current condition after an order. `ship-condition-survey.md`
owns live acceptance of this survey and its new restoration paths.

**The refit tree** (`second-wind-refit-tree.md`) generalises these orders. Every
track of the founding hull stands at a band: Deficit (the battered start), Restored
(neutral, the figures every mission and evidence harness is tuned at), Improved I
and II. Five cores (Power, Life support, Structure, Compute, and Fabrication, which
is the workshop chain) gate the other tracks' tiers. A locked tier names what it
waits on in Castle's terms ("no power for it yet"). The distribution order is Power's
Restored tier and the mess order is the mess's; their prices and receipts stand.
Degraded scrubbers add a day to every wound's recovery until life support is
restored. The combat tracks (hull, flux, sensors, drive) are the hull's own
figures, applied through the conversion hull mod, so the refit screen shows them
and every fight she is in uses them. They start below a stock Cerberus and stay a
liability until the cores behind them are paid for; flux waits on the reactor
overhaul. The deck carries one icon chip per core in its top-left corner, ringed
in the colour of that core's band; a chip opens the **systems tree** over the
deck. The tree opens as a modal over the live ship, which stays visible
around and faintly behind it. The tree flows downward from the hull: the five cores hang from her, and
every other track hangs from the core it leans on most (drive, flux and the
Mech Lab's coolant plant from Power; mess, gym, sick bay and lounge from Life
support; hull, brig and range from Structure; sensors and mech calibration from
Compute; the mech armor shop, mech field repair and the vehicle bay from
Fabrication). Hydroponics hangs from Life support, the
holds from Structure, and the wardroom and briefing room from Compute: facilities
fitted into rooms she already has (the provisions store, the mess, the holds), so
buying one opens no compartment. Hydroponics and the holds act through the hull
mod (her supplies per month, her cargo); the wardroom scales officers' mission XP
when a mission resolves and is named on the briefing once it pays. The briefing
room's holo-tank shows the briefing the garrison the operation faces. It reads the
same target profile launch builds the battle from, so its headcount is the battle's;
its mechs are given as *up to*, because the landing caps them. The plot table below it is the tank's mount and has
no battle passive yet. Each track is a column of tier nodes,
owned, available (nothing but the price in the way) or locked. Most tiers need
two or three cores, so beside each unbought node sit small icons of the cores it
waits on, gold once met and red until then; requirements are marked this way
rather than drawn as lines across branches, so the tree stays a tree. The card docks down the tree's right edge and carries
Castle's account of how the system works aboard her, the current and next tier,
what the next tier needs, its bill, its lock reason and the reviewed order. The
workshop's stages appear under Fabrication but are still ordered from the
workshop card. Authorizing a tier plays it out on the deck rather than
in the card: the tree steps aside, the camera flies to the room the work is in
(the whole ship for the combat tracks), a curtain falls while the yard works,
and it lifts on the changed room with the tier's name and effect before the
camera flies back and the tree returns. The order is paid under the curtain,
not at the click, so a deck that is laid out again for a newly opened
compartment is never seen blank, and a refusal at that moment lifts on the
unchanged room. A core brought up from Deficit changes only the deck's
dressing, so it needs no curtain: it is paid as the camera arrives and plays
as a dressing wave across the running deck, which the reveal waits out. Each tier is its
own reviewed yard order under the same home, dock, Relief and funds rechecks, and
the receipts stay with the hull. A tier's price is credits, its old materials
folded in at the conversion prices (Price). The strongest tiers also take an AI
core from fleet cargo as a hard requirement, shown as an icon row with Short by;
colony items never go into the ship. Other hulls are neutral on every track.

Five reserved compartments (range, gym, sick bay, lounge, brig) open by their
facility track's Restored order. A sealed one is neutral, so opening one is
already a gain, and other hulls read as sealed. The range opens trials aboard
and then sharpens company infantry's primary fire; the gym makes them faster
and, at its top tier, steadier under fire. With the mess's morale recovery these
freeze onto every squad at launch as one hull-welfare record and reach the fight
as one named unit modifier. The sick bay brings a share of the would-be dead home
wounded when a battle is settled. The lounge raises the aptitude cargo
replacements arrive with. The brig opens its cells; interrogation waits on a
battle that takes prisoners, since no defender is ever captured and the outcome
records none. The briefing's Ship row names each room that changes the
operation.

The **Mech Lab tracks** reach the company's own mechs. Nothing there starts
at a deficit: an absent bench and a Restored one are both neutral, and the
Improved tiers add fire-control calibration (mech weapon accuracy, then with
the tactical core link a faster torso traverse onto a new target), the coolant
plant (flux dissipation) and the armor shop (armor capacity, which every
section's plate is a share of). These are frozen into each owned mech's
deployment at launch, beside its fit's own frame values, so a battle never
reads the ship; a mech the company does not own deploys neutral. Field repair
stays in the campaign: a repair's clock is shorter, and with the spares rack a
replacement weapon assembly (what refitting a mount lost in battle comes to)
costs less to fabricate; the clock is read when settlement starts a repair. The
selected mech's sheet in the Mech Lab shows the deployed figures and names the
refits in its header, and the briefing line carries the same text. The vehicle
bay is a track with no orders until company vehicles can deploy.

Interior restoration is saved by the original hull's identity, independently of
company designation and vanilla hull damage. Moving company, reopening screens and
loading saves cannot reset it or replenish the starting depot. The original boat
and its fittings survive; ordinary hull repairs do not restore this bay. Existing
custom-start saves acquire the reserved damaged bay while retaining their first boat.
Other hulls retain their ordinary facilities. `cerberus-bay-restoration.md` owns
live acceptance of this bounded restoration path.

The variant owns conversion state; `ship-interiors-nouns.md` owns its room program.
`cerberus-company-start.md` retains the uncompleted live start/save acceptance.

## Presentation boundaries

Company reporting surfaces are read-only explanations. Barracks, Fleet Armory,
Mech Lab and Boat Deck provide explicit authoring through the roster, armory and
workshop authorities. Barracks owns home-officer organization, saved profile
intent and bulk replenishment.
Their UIs do not mutate those facts independently. Mission
command owns battle assignments; `Selection` remains view state. Campaign
surfaces consume the live roster, while battle and results surfaces consume the
frozen deployment. A UI must not silently regroup marines, invent persistence,
or feed presentation state back into simulation.

The eventual formation snapshot should give campaign HQ, deployment, battle
rollup, and after-action views one shared organizational vocabulary without
forcing every surface into one widget. Detailed equipment, aptitude, experience,
and career presentation remains coordinated with `progression-nouns.md`.

## Debug company

The developer mission launcher is a separate catalog reached from HQ or the
DEBUG client. It exposes authored story chapters, both branch outcomes,
repeatable work, standard operation tiers, and diagnostic scenarios without
campaign prerequisites or travel. Location filters group missions by their
canonical planet; variable-location work stays explicitly unfixed. Story numbers
are relative to the planet: Jangala runs 01 through 05, with 04A evacuation and
04B freight as alternatives. Repeatable work uses R labels outside that sequence.
Corvus chapters retain their interplanetary predecessor hints. These labels are
navigation aids, not campaign mission identities.

Choosing a mission starts a fresh default
company, boat manifest, and support selection; returning to the same mission
resets prior overrides. Story presets use starter infantry equipment and the
recovered chaingun Hound where the chapter expects it. Counterattack retains
its on-site Hound recovery rather than supplying one before the mission.

Debug stories use the production maps, objectives, and arrival machinery with
separate debug identities and fresh rescue/freight ledgers. Their supplies,
personnel, boats, and support are detached from the campaign. Results return to
the catalog and cannot grant salvage, consume fleet resources, advance stories,
or write casualties back. Authored map shapes stay fixed; generic operations
retain their developer scale and map controls.

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

The stage bounds what each squad is dealt, not what the briefing may issue. Once
dealt, the debug company holds every weapon card and tactic sheet, and no warrant
withholds any of them, so any squad can be given any card. The briefing's kit
picker shows each squad's two cards as chips and each bay slot as a picture of
what stands in it; choosing one opens a modal of every option drawn the way the
game draws it: the Armory's own loadout cards, or the Mech Lab's chassis and hull
previews. The choice can be issued to one squad or to every squad at once.

The briefing can also field an operation commander for one battle. That
commander is a top-rank officer with every training point the game grants, and
the developer assigns its specialties on the same tree Barracks draws. An
untrained one leads as a junior officer would. It is a setting, not a person: a
fresh officer is built at launch, joins the detached company only while the
deployment freezes its training into each squad's command profile, and is gone
again. It is off by default, so squads fight under their acting NCOs as before.

## Invariants for future work

- `MarineRoster` remains authoritative for enduring personnel and organization.
- Squad and marine identity are frozen across the deployment seam; battle code
  never reaches back into campaign state.
- Fire-team membership is derived from billet order and never persisted twice.
- Fire teams are equipment/AI/lift units, not player command targets.
- Weapon cards and tactic sheets are complete, developer-authored squad issues the
  player collects; a learned card is freely fitted by the Machine Shop.
- Mech weapon assemblies and subsystems are finite physical stock; installed copies
  remain counted, while their fabrication spends player cargo through one campaign
  authority. The Mech Lab cannot create a second inventory or commodity-art catalog.
- A failed squad equipment issue changes neither materialized kit nor either doctrine id.
- Per-soldier kit remains the battle-facing materialization until the deployment
  seam explicitly adopts another representation.
- A weapon doctrine may issue special equipment, but AI use follows faction-neutral
  battle policy rather than a hidden instruction encoded by its presentation card.
- Officer capacity is checked per named command; remaining squads retain
  independent acting-NCO leadership rather than blocking deployment.
- Reserve personnel do not count as formations or field-ready strength.
- Campaign views read live state; battle and result views read frozen mission
  state.
- Missing or unavailable information is explained, not rendered as zero.
- Presentation conveys organization but never changes simulation authority.

## Extension boundaries

Future extensions may add a shared formation read model, safe late-arrival
rejoin, live acceptance of participating-officer outcomes, broader formation grouping,
and a sector contract board. Each extension must preserve the canonical
company organization and frozen deployment identities rather than persisting
a second presentation-owned roster.

Boat fittings and door guns may later be authored in data rather than in a code
catalog — that extends the same campaign authority rather than adding a second one, and none of them makes the room the owner of what the
company has. Recovering a downed boat's hull or crew is deliberately not on that
list: a lost boat is lost.

Equipment authoring remains bounded by collected templates and atomic free fitting;
read surfaces remain projections of their owning campaign or frozen mission
state. A contract board may present offers and obligation clocks, but it does
not become their authority or introduce a second clock lifecycle.


## Campaign lesson continuity

`CampaignLessons` derives post-Relief guidance from `FirstRefit`, `MechBay` and
`CampaignLessonProgress` on `MarineRoster`. HQ, Barracks and briefing present the
next unfinished handoff; Equipment hosts the actual first issue and Barracks the founding,
and Mech Lab reselects Wayward when reopening an unfinished fitting exercise.
The portrait popover uses `PreparationGuide` and real `UiSpotlight` apertures.
Return/navigation remains available for resupply; leaving never completes work.
Barracks suspends its general management advice while campaign guidance is
present, preserving the required founding induction's precedence.

First Refit still requires its ordinary technology claim and actual equipment
issue. The gantry lesson uses the existing once-only SRM and fit/remove/refit
receipts. Missing Wayward does not trap the company at a nonexistent gantry.
Optional investments are explanatory: no second bay, shuttle upgrade or paid
workshop restoration is required by these lessons. Two-formations preparation
uses the existing real founding bill, wounded billets and deployment minimum.
Briefing confirmation records preparation once two ready formations are
selected; the lance rides with them whenever Mech Support is available, with no
call to select, and absent heavy support does not gate an otherwise legal
infantry departure. These receipts do not own launch permission.

Campaign lessons end before deployment. Battle presentation leaves the mission's
story dialogue unobstructed by preparation popovers or command-tray spotlights.
Mech Support remains available through its ordinary command card and landing
controls, without a field-tutorial acknowledgement or a reminder on later contracts.

Opening and company-screen prose and labels use `Strings`; `LessonCopy` delegates
its complete variable messages to `Strings.format`. The marineOps catalog owns
copy, including markup labels through `{i18n.key}`. Formatting uses MessageFormat
numbered arguments (and choice patterns for quantities); plain `get` preserves
literal apostrophes. Existing printf-based cost displays retain printf patterns.
Rank/specialty labels and `@key` equipment catalog display fields resolve when read,
not during static initialization. This includes weapons, armor, special items,
integral systems, mech hardware and their derived template and recipe names.
Add-on literal copy remains supported. The management guide stores titleKey/textKey
beside stable narration IDs; synthesis text is recording direction, not UI copy.
Saved identifiers, authored/player names and resource transactions are independent
of translated display text. `alpha-localization.md` owns the remaining
project-wide migration. `castle-campaign-lessons.md` retains live acceptance;
`campaign-learning-curve.md` records the wider progression gaps without silently
changing economy or officer thresholds.
