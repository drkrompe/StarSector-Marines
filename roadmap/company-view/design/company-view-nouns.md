# Company view nouns

Status: ACTIVE — 11 open stories
Written: 2026-08-23
Updated: 2026-08-23 — clarified elastic battle fire-team tempo and contained-terrain authority.

## Purpose

Company view makes the player's force legible as an organization wherever a
decision depends on it: between contracts, while assembling a deployment, in
battle, and after action. Most of the domain is a read model over the campaign
roster and contract state, not a second company simulation and not a
player-order layer. The Fleet Armory is its deliberate authoring seam: it
assigns reusable equipment designs to the fire teams the organization already
contains and hosts the Mech Lab for persistent support-squad refits.

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
- **Fire-team template** — a reusable four-billet equipment design owned by the
  armory. The template is not physical inventory and is never consumed by
  assignment; each fielded copy still needs a complete physical kit.
- **Special-equipment slot** — the one optional billet position beside a
  marine's primary and armour. A template may issue a weapon-like special or a
  utility special; the slot does not make the template a battle order.
- **Billet** — one equipment position on a fire-team template. Templates describe
  positions rather than named marines; the current team materializes them.
- **Template assignment** — the template id bound to one squad's Alpha, Bravo, or
  Charlie team after an atomic inventory transaction succeeds.
- **Squad arrangement** — a planned quick-refit composition of three template
  assignments. It is not a separate kind of equipment or organization, and
  applying one leaves the three team assignments as the only fielded intent.
- **Conformance** — whether a team's current personnel and materialized issue
  match its assigned template. Wounds, vacancies, later individual mutations, and
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
- **Mech Lab** — the Fleet Armory workspace over the persistent support squad
  and finite mech subsystem stock defined by `mechs-nouns.md`. It presents
  chassis and hardpoints but mutates only inventory authorities that actually
  exist.

## Organization and leadership

The full organizational hierarchy is **company → officer command → squad → fire
team → marine**. Hosts stop at the depth their decision needs: the Fleet Armory
opens the fire-team equipment tier, while deployment and battle command remain
squad-granular.

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
leave a second stored partition behind.

`homeCaptainId` is a squad's durable organizational default, not a history of
temporary mission borrowing. Stationed squads remain under one officer because
a garrison is a posting, not a task force.

## Armory equipment templates

The Fleet Armory authors routine equipment at fire-team scale. `MarineArmory`
owns a reusable template library; `MarineSquad` persists one assigned template id for
each of its three team slots. A template has exactly four billets, and each billet
may specify primary family, grade, armour, and optional special equipment. The
current `MarineSecondary` field is the compatibility materialization of that
last concept until the id migration retires it. Special gear
therefore belongs to a scarce team design rather than a parallel per-marine
override system.

Assignment is atomic and inventory-aware. The target team's current equipment
is counted as returned before the candidate template is checked. Locked recipes or
insufficient primaries, armour, or special items leave every marine and the prior
template id untouched. A complete four-marine RTD team is currently required before a
new template can be assigned; later conformance work owns degraded and replacement
teams.

The template is intent, while `MarineSoldier` equipment remains the materialized
state consumed by deployment and battle. This preserves the campaign-to-battle
contract while the designer grows. The built-in library contains Field, Line,
Recon, Fire Support, Anti-Materiel, Screen, and Breach templates; the LOADOUTS
surface exposes Alpha/Bravo/Charlie plus each assigned template.

Templates are reusable but equipment is finite. Design itself must not be gated by
stock; the designer may save an unfieldable template, while assignment is allowed
only when the armory can supply it. Built-in templates are immutable library
fixtures and may be cloned. Player-authored templates have stable persisted ids;
their names are metadata and may change in place, while changing billet issue
is saved as a new template revision. Assigned teams therefore retain both their old
template id and materialized issue until an explicit refit succeeds. A custom template
cannot be deleted while any team or saved arrangement still references it.
Conformance and retirement of routine per-marine mutation remain in
`c14-fire-team-equipment-templates.md`.

Template availability has two deliberately different readings. **Fielded** counts
the teams whose persisted assignment names that template, even when a team is
currently degraded. **Ready to issue** counts additional complete copies that
uncommitted fleet stock can supply without assuming any target team's returns.
Selecting a specific team and template produces the contextual refit preview: free
stock before the transaction, the target issue returned, and the candidate
issue required. That preview is the same calculation used by assignment, not a
UI estimate.

Two assigned teams may exchange templates in one atomic transaction. Both teams'
current equipment is returned before either candidate issue is checked, so a
scarce kit can move directly between teams without a temporary extra copy. A
failure leaves both teams, both assignment ids, and all materialized equipment
unchanged.

A squad arrangement saves exactly one template id for Alpha, Bravo and Charlie.
It is a reusable refit plan, not a persisted fourth binding on the squad. The
player may author or capture one without owning its equipment, then target any
line squad from the arrangement management view. Preview and issue evaluate all
twelve billets as one transaction: all three current team issues return before
the candidate composition is checked. Success writes the three ordinary
template assignments and materialized kits; any invalid team, locked recipe or
stock shortfall leaves the whole squad unchanged. Deleting the arrangement
later cannot rewrite a squad that used it.

“Card” is not part of this model. Compact tiles or rows may make templates quick
to scan and assign, but the Fleet Armory has no collectible-card, deck, hand or
consumption semantics.

## Mech Lab

The Mech Lab mirrors the Armory's squad-first interaction without reusing its
personnel template model. Campaign mechs retain individual identity inside a
selected support squad of up to four chassis. The lab shows the whole squad,
then the selected mech's doctrine, fixed chassis mounts, installed subsystem,
and fleet subsystem inventory. The current first slice authors missile-
replenisher installation; chassis and weapon mounts remain visible but read-
only until acquisition and weapon-component inventories exist.

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
present, the form-up gate withholds the first advancing assignment while still
allowing self-defense. A timeout prevents a lost lift or split landing from
deadlocking the mission. Explicit rejoin behavior for a genuinely late arrival
after that point remains open in `c8-lift-capacity-and-multi-pass-drops.md`.

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
power. The null-planet host may transition directly only to the armory and
back; it must not navigate through mission, briefing, battle, result, or loot
screens that require a market or live operation. An obligation response is the
one handoff boundary: it queues the existing campaign event's deployment
request, then closes the HQ rather than routing through those screens itself.

The home has three concerns:

- **Standing** explains finances, reputation, employers, and runway from live
  campaign authorities. Only live stationing retainers count as income. Missing
  monthly data is unknown, never zero.
- **Clocks** explain obligations that can hurt the company: pending response
  deadlines and live stationing term endings. Overdue clocks are due now, never
  rendered as negative time. One contract may own both clocks.
- **Roster** summarizes organization and readiness. Strength includes living
  active and wounded marines; availability is the line-ready subset. The entire
  gap—including wounded, stationed, and reserve personnel—is unavailable. The
  reserve pool is excluded from line and stationed squad counts, and recovery
  shows the earliest known return only when recovery exists.

The clock owns obligations; the future contract board owns offers. Ignoring an
obligation can cause failure or end a retainer, while ignoring an offer merely
loses an opportunity. Responding to an obligation follows the same deployment
route as its campaign event notice. Long lists state hidden counts, and missing
derived inputs are explained rather than silently collapsed.

The HQ introduces no persisted state and no second company-card hierarchy. Its
roster area is the host that the formation, whereabouts, and contract-board
stories will expand.

## Presentation boundaries

Company reporting surfaces are read-only explanations. The Fleet Armory is the
exception that authors personnel organization and equipment through roster,
armory, and mech-bay services; its UI does not mutate those facts independently. Mission
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
- Fire-team templates are reusable designs; every assignment remains bounded by
  finite physical stock.
- Mech subsystems are finite physical stock; installed copies remain counted,
  and the Mech Lab cannot create a second inventory authority.
- A failed template or arrangement assignment changes neither issued equipment
  nor assignment.
- Per-soldier kit remains the battle-facing materialization until the deployment
  seam explicitly adopts another representation.
- A template may issue special equipment, but AI use follows faction-neutral
  battle policy rather than a hidden instruction encoded by the card.
- Officer capacity is checked per command; task forces do not flatten back into
  one officer's cap.
- Reserve personnel do not count as formations or field-ready strength.
- Campaign views read live state; battle and result views read frozen mission
  state.
- Missing or unavailable information is explained, not rendered as zero.
- Presentation conveys organization but never changes simulation authority.

## Planned direction

The remaining work is equipment authoring, presentation, and tactical
refinement: add degraded-team conformance;
derive one shared
formation snapshot; show officer grouping, squad whereabouts, battle rollup, and
after-action survival consistently; give late arrivals a safe rejoin state; make
fire teams stable AI maneuver elements; resolve outcomes for every participating
officer; make large-company assignment practical; and add the sector contract
board.
