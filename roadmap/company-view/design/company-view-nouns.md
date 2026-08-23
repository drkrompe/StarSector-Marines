# Company view nouns

Status: ACTIVE — 11 open stories
Written: 2026-08-23
Updated: 2026-08-23 — folded shipped organization, deployment identity, lift, debug-company, task-force, and campaign-home work.

## Purpose

Company view makes the player's force legible as an organization wherever a
decision depends on it: between contracts, while assembling a deployment, in
battle, and after action. It is a read model over the campaign roster and
contract state, not a second company simulation and not a player-order layer.

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
  the lift-capacity unit and intended AI maneuver element, not another player
  command tier.
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

## Organization and leadership

The hierarchy presented to the player is **company → officer command → squad →
marine**. Fire teams may appear as grouping within a squad, but never as a card
level or player order target.

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

## Deployment identity

A campaign deployment is a frozen value snapshot, never a live roster
reference. Selected squads are frozen in stable roster order so their seats
remain contiguous. Each campaign seat carries its marine identity plus a
`CampaignSquadTag`: stable squad id, deploy-time label, NCO marker, and the
strength that selected squad is assembling toward. A later roster rename or
transfer cannot rewrite the battle.

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

Company surfaces are read-only explanations. Mission command owns battle
assignments; `Selection` remains view state. Campaign surfaces consume the live
roster, while battle and results surfaces consume the frozen deployment. A UI
must not silently regroup marines, invent persistence, or feed presentation
state back into simulation.

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
- Fire teams are AI/lift units, not player command targets.
- Officer capacity is checked per command; task forces do not flatten back into
  one officer's cap.
- Reserve personnel do not count as formations or field-ready strength.
- Campaign views read live state; battle and result views read frozen mission
  state.
- Missing or unavailable information is explained, not rendered as zero.
- Presentation conveys organization but never changes simulation authority.

## Planned direction

The remaining work is presentation and tactical refinement: derive one shared
formation snapshot; show officer grouping, squad whereabouts, battle rollup, and
after-action survival consistently; give late arrivals a safe rejoin state; make
fire teams stable AI maneuver elements; resolve outcomes for every participating
officer; make large-company assignment practical; and add the sector contract
board.
