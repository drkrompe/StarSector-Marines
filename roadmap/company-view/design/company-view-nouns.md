# Company view nouns

Status: ACTIVE — organization, Fleet Armory authoring, and cross-surface company read models continue to evolve.

Written: 2026-08-23

Updated: 2026-08-25 — shipped the focused Mech Lab, persistent
player-authored Weapon and Armor definitions with their twelve-billet retained
designer and legacy intent migration, plus the Fleet Assault doctrine and its
single fragmentation-grenade carrier.

## Purpose

Company view makes the player's force legible as an organization wherever a
decision depends on it: between contracts, while assembling a deployment, in
battle, and after action. Most of the domain is a read model over the campaign
roster and contract state, not a second company simulation and not a
player-order layer. The Fleet Armory and Mech Lab are its deliberate authoring
seams: the former assigns reusable weapon and armor equipment definitions to
whole squads while preserving exact materialized kits; the latter refits
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
  mech subsystem stock defined by `mechs-nouns.md`. It presents
  chassis and hardpoints but mutates only inventory authorities that actually
  exist.

## Surface boundary

The retained Company -> Squad -> Fire Team hierarchy is the Fleet Armory's home.
Its selectable company and squad cards own inspection, readiness, recovery, and
reinforcement in formation context. A reinforcement control is a secondary card
action; selecting the rest of a squad card still enters that squad.

Fleet Armory is the only Armory route. The former Armory Administration shell,
its individual-kit browser, fire-team template designer, squad-arrangement editor,
and embedded Mech Lab were retired rather than retained as duplicate UI. Personnel
reinforcement now lives on formation cards. Mech Lab has returned as its own retained
surface over `MechBay`, not as a tab in a catch-all screen.

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
fitness, or relevant experience changes using rank, then experience, then a
stable identity tie-break. A wounded senior may resume the billet on return; a
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

Issue is explicit, atomic, and net of returns. The target squad's current twelve
kits return before the candidate pair is checked. A stationed squad, any vacancy or
WIA billet, a locked recipe, or insufficient primary, armor, or special stock leaves
both doctrine ids and every marine's existing kit untouched. Success writes both
ids together and materializes all twelve exact issues onto `MarineSoldier`, which
remains the battle-facing state consumed by deployment. Later inventory changes do
not silently optimize or reshuffle that materialized result.

Built-in definitions are authored deterministic distributions rather than live
best-fit allocators. This makes faction-flavored profiles such as **Sindrian
Civilian Security Equipment** or **Luddic Path Assault Equipment** explainable in
preview and stable after issue. Player-authored definitions persist in `MarineArmory`
beside that immutable built-in catalog. Authoring is free and may mix every billet;
stock, recipes, readiness, and stationing constrain only the later squad issue.
Edits remain a draft until **Save as New**, so a definition already assigned to a
squad never silently refits its materialized equipment.
**Fleet Assault Equipment** is the built-in player assault profile: its twelve
exact billets contain one Shattercap frag carrier, so the doctrine cannot multiply
scarce special stock by team. Player-authored definitions must likewise resolve to
twelve exact billets before the same issue transaction can commit.

Legacy four-billet templates, three-template arrangements, and their persisted ids
remain readable compatibility input for existing saves; they are no longer writable
player intent and have no Fleet Armory UI. Load migration composes each complete
legacy three-template intent into deterministic player-owned Weapon and Armor
definitions without rewriting any marine's current kit. The first later successful
squad issue clears stale per-team assignment ids so only one equipment authority remains.

The selectors may be presented as equipment cards in the literal base-game UI
sense, but card/deck/hand/consumption semantics do not enter the domain. Definitions
are reusable intent; every materialized copy remains bounded by finite physical
stock.

## Mech Lab

The Mech Lab is the focused squad-first surface over `MechBay`; it does not reuse
personnel doctrine. Campaign mechs retain individual identity inside a selected
support squad of up to four chassis. The retained room shows the whole squad,
selected mech, fixed chassis mounts, installed subsystem, and finite fleet subsystem
inventory.

Subsystem assignment is an atomic inventory transaction. Installed copies
count against owned quantity and the target's current component returns before
the candidate is checked. The active squad freezes into plain deployment values
only when a sourced Mech Support power is resolved, preserving the same rule as
personnel deployment: campaign objects do not enter battle.

## Deployment identity

A campaign deployment is a frozen value snapshot, never a live roster
reference. Selected squads are frozen in stable roster order so their seats
remain contiguous. Each campaign seat carries its marine identity plus a
`CampaignSquadTag`: stable squad id, deploy-time label, NCO marker, fire-team
index, and the strength that selected squad is assembling toward. A later
roster rename or transfer cannot rewrite the battle.

At landing, tagged personnel group by **campaign squad and landing zone**.
Repeated lifts to the same zone join one battle squad; a campaign squad split
across zones deliberately becomes separate labelled fragments. The frozen NCO
seeds the battle leader. Expected strength comes from the selected manifest,
while landed strength grows as each member deboards, so morale can distinguish a
squad still assembling from one already mauled.

Scenario-generated personnel—defenders, militia, employer troops, and generic
reinforcements—carry no campaign tag and retain the per-sortie fallback. A debug
company is different: it intentionally freezes a real detached roster and
therefore carries campaign-shaped identity without touching campaign state.

The standing law is **values cross the campaign-to-battle seam; campaign
objects do not**. Labels remain stable, battle code never resolves the roster,
and generated personnel never gain campaign identity by accident.

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

Transport capacity is denominated in whole four-marine fire teams. Small lifts
carry one team, medium lifts two, and the dedicated Valkyrie carries all three
teams of a twelve-marine squad. This keeps transport arithmetic aligned with the
organization the player selected.

A tagged squad may assemble over several passes. Until its frozen manifest is
present, the form-up gate suspends execution of its advancing assignment while
retaining the authoritative command directive and still allowing self-defense.
A timeout prevents a lost lift or split landing from deadlocking the mission.
Explicit rejoin behavior for a genuinely late arrival after that point remains
open in `c8-lift-capacity-and-multi-pass-drops.md`.

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
for the whole organization. A persistent top shell exposes `RETURN`, `HQ`,
`ARMORY`, and `MECH LAB`, marks the occupied room, and carries the precise location context at the
right. `RETURN` closes the shipboard UI; room routes move directly between top-level
surfaces. Armory company, squad, and fire-team breadcrumbs remain page-specific below
that shell, while the bottom of every room is reserved for its own content. Future
Barracks, Briefing Room, medical, logistics, or fabrication surfaces should join the
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
campaign deployment law. Player-side vehicle support remains deferred until a
production vehicle deployment seam exists.

## Invariants for future work

- `MarineRoster` remains authoritative for enduring personnel and organization.
- Squad and marine identity are frozen across the deployment seam; battle code
  never reaches back into campaign state.
- Fire-team membership is derived from billet order and never persisted twice.
- Fire teams are equipment/AI/lift units, not player command targets.
- Weapon and armor doctrines are reusable squad designs; every issue remains bounded
  by finite physical stock.
- Mech subsystems are finite physical stock; installed copies remain counted,
  and the Mech Lab cannot create a second inventory authority.
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

Equipment authoring remains bounded by finite stock and atomic assignment;
read surfaces remain projections of their owning campaign or frozen mission
state. A contract board may present offers and obligation clocks, but it does
not become their authority or introduce a second clock lifecycle.
