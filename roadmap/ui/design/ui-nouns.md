# Marine Ops UI nouns

Status: SHIPPED — retained UI foundation proven in-engine

Written: 2026-08-23

Updated: 2026-10-12 — Barracks contains the equipment workspace and its unread opportunities.

Earlier 2026-09-01 — the shipboard-room shell carries a Boat Deck route, gated
on the hangar the way the Mech Lab is gated on the vehicle bay.

Earlier 2026-08-31 — primary world selection now distinguishes a click from a
drag marquee and deterministically selects one player squad or combat Mech
inside the dragged area.

Earlier 2026-08-31 — the selected infantry plate now arms a Defend Area
placement mode whose next world click previews and places a 40-cell-diameter
circle; right-click or Escape cancels placement.

Earlier 2026-08-31 — a stationary right-click inside an uncaptured Conquest
compound now resolves the selected infantry squad's gesture as capture rather
than bare ground movement.

## Purpose

The Marine Ops screens are a full-canvas application hosted inside a Starsector
custom visual dialog. Starsector owns the window, dialog lifetime, and the final
rectangle. The mod owns the document inside that rectangle: layout, paint order,
input routing, navigation, and the projection of game state.

This feature owns the reusable presentation machinery. It does not own company,
mission, roster, armory, or battle facts. A surface reads those domains through a
view model and invokes their commands; it never becomes a second authority for
them.

## Vocabulary

- A **host viewport** is the final rectangle Starsector grants to a custom panel.
  Its origin is expressed in Starsector's bottom-left, Y-up UI coordinates.
- A **takeover request** asks Starsector for the complete reported screen so the
  application can use space that would otherwise remain as inert black margin. It is
  a request, not layout authority: the resulting host viewport may still be smaller.
- A **document** is one retained element tree laid out against a host viewport.
  Its own coordinate space starts at the top-left and grows downward so authored
  layout follows the same convention as HTML and CSS.
- An **element** is a retained node with stable identity, ordered children,
  presentation state, and optional interaction behavior.
- A **layout box** is the computed border rectangle of one element. Painting and
  hit-testing consume the same box; neither independently derives geometry.
- A **layout context** arranges a parent's children. The current contexts are row,
  column, responsive grid, and stack. `position: absolute` is the bounded escape
  from all four: such a child leaves its parent's flow entirely, is placed at
  `left` / `top` inside the parent's content box, and therefore cannot move a
  sibling. The containing block is always the immediate parent — there is no
  positioned-ancestor search and no `right` / `bottom` pair — which is what a
  document-level overlay needs and all one layout pass can honour.
- **Overflow** is CSS's relationship between a box and content that exceeds it.
  `visible` is the default; `hidden` and `scroll` establish the same padding-box
  clip, while `scroll` additionally promises navigation chrome and input.
- A **painter** traverses the laid-out document once and emits boxes, text,
  clipping, canvas, focus, and scroll chrome to a paint target.
- A **paint target** projects those document-space operations into either
  Starsector's UI pass or a controlled headless raster. The live target alone
  owns OpenGL state and host-space conversion.
- A **theme** maps semantic component roles and interaction states to typography,
  color, spacing, and decoration. Domain screens do not own a second palette.
- A **binding** is an explicit invalidation edge from view-model state to one
  element mutation. It updates retained identity rather than rebuilding the
  document.
- A **signal** is a value in one reactor graph. Reading it while a binding or
  computed value evaluates records the dependency; writing a different value
  invalidates only those readers.
- An **MLX component** is one authored template plus an optional component-scoped
  style sheet. It builds ordinary retained elements and receives no privileged
  layout, input, paint, or cascade behavior.
- A **reload** is an explicit development transaction: every known edited component
  parses successfully before the registry changes, then a fresh document is built
  from the existing view model. Domain and view-model state are not component state.
- A **canvas element** is the procedural escape hatch for visuals that do not fit
  ordinary boxes: formation connectors, graphs, paper dolls, or transaction flows.
- An **image element** shows one whole authored asset at a path — an icon, a
  crest, a portrait — with no procedural drawing behind it. It is the ordinary
  answer to "put this picture here"; a canvas producer is what a composed or
  computed visual still requires. Its source is an ordinary bindable attribute,
  so a subject that carries no asset simply supplies nothing and the element
  draws nothing rather than reserving a placeholder.
- A **commodity presentation** is the read-only name and icon projection of a
  Starsector commodity specification. A view model may compose it with cargo
  quantities, but neither markup nor the view model hardcodes a parallel catalog;
  tests provide deterministic paths through the same seam.
- A **surface** is a document plus its view model, navigation behavior, and host
  lifecycle. Fleet Armory, Company HQ, Mech Lab, Boat Deck, and the UI workbench
  are surfaces.
- A **shipboard room** is the fiction and navigation identity of a player-facing
  company surface. The bridge Company HQ, Barracks, Mech Lab, and
  Boat Deck are current
  rooms; the retained document remains the implementation surface underneath that
  spatial frame. Fleet Armory is the Equipment workspace within Barracks, reached
  from Equipment or a squad inspection rather than its own top-level tab. Equipment
  notices belong to Barracks and clear only when the relevant offer is reviewed.
- A **page route** is one button on the shell's persistent navigation, and a
  route that names a compartment is answered by the ship rather than by the page:
  a hull that has no such place shows the button greyed and unreachable, and one
  whose deck is still being laid out shows it waiting and keeps its action.
  `BOATS` names the hangar exactly as `MECH LAB` names the vehicle bay, so the
  gating is a property of the route rather than something each screen remembers.
- A **spec sheet** is the bounded hover overlay that says what one catalog item
  is: a heading with the item's crest, a subtitle of designation, role, tier,
  grade or access, stat rows with meters measured against catalog-wide
  ceilings, and the item's field note. It is a value with no reference to the
  item and no behaviour, written by one copy factory from the item's owning
  catalog — a weapon at a grade, an armour pattern, a special item, an integral
  system, a command power, a mech chassis or component, or an equipment template card, which
  resolves to the equipment it names. No screen assembles one by hand from
  catalog fields, and no item's prose lives in markup; a mech's note is data
  beside the weapon and armour catalogs, not a field on its enum.
- A **subject** is any catalog item a spec sheet can be written for. A marine,
  a squad, or a doctrine template is a dossier rather than a subject: it is
  described by its own screen, and only the catalog items it names carry
  sheets. Compact selection controls may reuse the sheet presentation for a
  contextual hint: the row's existing detail and selection explanation, rather
  than a substitute dossier or a second catalog authority.
- The **spec-sheet layer** is one floating element per document, above every
  screen element, placed beside the hovered subject on the side with room and
  clamped to the document, sized to its measured text rather than an estimate,
  and transparent to the pointer so it cannot take the hover that opened it.
  Show and hide are class toggles so stylesheet transitions apply.
- A **binding** ties one retained element to one sheet, or to a supplier of
  one when the element is fixed and the subject under it changes with the
  projection. Bindings are made from Java where a screen builds the element's
  row; there is no markup attribute, because the sheet is data the screen
  already holds and an attribute would need a second way to name the subject.
  The deepest bound element on the hover chain wins, and a screen that binds
  nothing pays nothing.
- A **preview fixture** assembles a surface from controlled domain state for UX
  evidence. It is presentation input, never a replacement campaign authority.
- A **snapshot suite** is one named, deterministic collection of visual evidence.
  The suite owns its fixtures and render recipes; the shared snapshot workflow
  owns discovery, selection, output layout, overwrite policy, and PNG writing.
- An **authoring page** is a discoverable desktop-tool contribution with one
  component, dirty-state contract, and close lifecycle. The generic workbench
  owns page discovery, top-level navigation, project paths, status reporting,
  and aggregate unsaved-change protection; a domain page owns its document,
  validation, preview, and save transaction.

## Coordinate spaces

There are three distinct spaces:

1. **Framebuffer pixels**, used by OpenGL scissors and offscreen targets.
2. **Starsector UI coordinates**, used by `PositionAPI` and `InputEventAPI`.
3. **Document pixels**, used by retained layout with a top-left origin.

One host adapter owns each conversion. Layout never reads the framebuffer, and
elements never add the dialog origin themselves. UI scale is observed at the host
boundary rather than guessed from the physical monitor.

Every full Marine Ops takeover entry requests 100% of the screen dimensions reported
by Starsector. Small notification and choice dialogs remain deliberately bounded.
The granted custom-panel rectangle still owns live geometry, and each retained root
authors its own narrow safe-area padding inside that rectangle rather than spending a
second percentage-based margin at the host seam.

Physical resolution fit and user UI scale are different inputs. A physically
smaller host may uniformly shrink the entire reference presentation so ordinary
resolution changes do not create a different composition. That fit never enlarges
content above its authored size. Starsector's explicit `getScreenScaleMult()` still
changes the virtual host space presented to layout, so a player asking for larger UI
may intentionally trigger responsive tracks, bounded scrolling, or a denser
composition. Paint, clips, canvas output, and input all share the same document-to-
host transform.

A headless image is a document-pixel raster, so it requires no fourth layout
space. Its target consumes document coordinates directly; requested viewport
dimensions and controlled fixture state make the result reproducible.

## End-to-end flow

For a stable frame:

```
domain invalidation -> view model -> bindings -> layout if geometry changed
                                              -> paint if appearance changed

Starsector input -> host coordinate conversion -> reverse-order hit test
                                                   -> element behavior

authored MLX + fixture state -> retained document -> shared painter
                                                 -> Starsector paint target
                                                 -> headless raster target

saved data + controlled fixtures -> selected snapshot suites -> shared writer
                                                           -> visual evidence root
```

The first implementation may repaint the whole document after a change. The
standing contract is still that unchanged state preserves element identity and
does not reconstruct the tree. Incremental paint ranges are an optimization, not
the retained model.

## Design laws

1. **One geometry authority.** Layout boxes drive paint, clipping, hover, click,
   focus, and procedural-canvas coordinate mapping.
2. **Retain identity.** Selection, hover, focus, scroll, and transition state live
   on enduring elements and survive ordinary view-model updates.
3. **Projection is not authority.** A surface may stage text-field state and
   selection, but roster and armory mutations still cross their existing command
   seams.
4. **The host boundary is explicit.** Nothing assumes control outside the granted
   custom-panel rectangle or assumes that a requested screen fraction was granted.
5. **Resolution fit is not UI preference.** Physically smaller panels uniformly
   fit the reference presentation before layout gives up its composition; larger
   panels do not silently inflate it. The player's Starsector UI-scale setting
   remains a separate density input that responsive tracks and bounded regions may
   answer deliberately.
6. **Paint order and hit order agree.** Later content paints above earlier content
   and is tested first. Invisible or clipped content cannot receive a click. The
   one exception is declared rather than inferred: `pointer-events: none` makes an
   element and its descendants untargetable while still painting, which is what an
   overlay opened by a hover needs so it cannot take that hover away from the
   element it describes and flicker.
7. **OpenGL state is borrowed.** A painter or canvas producer restores programs,
   buffers, textures, scissors, blend state, and matrices that it changes.
8. **Standard vocabulary wins.** When HTML or CSS already names a layout or input
   concept, the toolkit uses that name and meaning. The supported surface may be a
   subset; it does not become a private lookalike dialect. Unsupported authored
   capabilities fail explicitly rather than silently parsing to different behavior.
9. **Java is the authority.** Any later markup format must produce the same tree
   the Java API produces and receives no privileged layout or behavior path. MLX
   property traversal is opt-in through an explicit view-model property surface;
   it never reflects over arbitrary domain objects.
10. **Exceptional drawing stays exceptional.** A new visual does not require a new
    element kind when a canvas producer can express it.
11. **Preview the production path.** Headless UX evidence uses the production
    document, layout, cascade, font metrics, clip calculation, and canvas producer.
    A screenshot-specific reconstruction cannot establish retained-view parity.
12. **One snapshot workflow.** Feature suites may provide different fixtures and
    images, but they register with one catalog and writer. Command-line generation
    and authoring tools select from that same catalog rather than inventing
    feature-specific build commands or output policy.
13. **One desktop authoring host.** New data editors contribute pages through the
    authoring-page seam instead of adding Gradle launch tasks or coupling the
    generic workbench to a mod-domain catalog. Closing the host consults every
    page's dirty state and closes every created page exactly once.
14. **Shipboard navigation is spatial.** Company surfaces identify the flagship room
    the captain occupies and phrase transitions as movement between real destinations.
    A persistent top shell owns `RETURN` and routes among real rooms, marks the current
    room, and keeps the right edge for location context. Drill-down breadcrumbs remain
    page-specific beneath it; the bottom edge belongs to page content rather than global
    navigation. Future room names do not appear as dead controls before their surfaces exist.
15. **Session-ending actions are staged.** A compact exit may remain visually quiet,
    but abandoning live work requires a nearby confirmation state that names the
    consequence and offers cancellation. Confirmation invokes the feature command;
    the retained surface does not synthesize an outcome or become lifecycle authority.

## Shared console presentation

Player surfaces share the briefing's metal housing, dark instrument wells and
restrained amber, green and red status accents. Raised command keys perform an
action; inset selections and small lamps communicate a latched choice. Room
navigation uses the same vocabulary, with absent and preparing rooms still
visibly distinct. Mechanical SVG plates frame major sections rather than adding
ornament to every data item. Battle overlays retain their bounded footprint and
transparent world surround while sharing the console materials.

Ordinary headings use warm silver lettering on dark gunmetal plates with a
shallow edge highlight. Broad glossy bands and cyan heading accents compete with
the instruments; cyan belongs to signal activity and readouts, while amber marks
selection and emphasis. The high-contrast theme keeps its distinct palette.

Information determines the layout. Related counts and labels remain close in
compact cards or groups; an expanding viewport is not a reason to stretch a
sparse row. Action labels fit their control, and primary actions remain distinct
from navigation and cancellation. Empty content reserves no large filler panel.
Lists scroll when their useful content exceeds the allotted area, rather than
because fixed padding inflated an otherwise fitting screen.

Recognition comes from existing art: officer portraits, hull sprites, faction
crests, commodity icons and the same command-power symbols in briefing and battle.
Essential state, cost, scope and consequences stay visible; existing spec sheets
and contextual hints own secondary detail. Presentation never supplies another
catalog or gameplay authority. Player-facing visual reviews use 1920x1080 at
100% scale; live viewport fitting still honors the host and user settings.

Company preparation groups personnel actions in Barracks before organization
administration. A transfer review separates receiving and donor selections and
keeps projected strength visible before its commit. The company officer's optional
first-use guide is a bounded, replayable callout beside the relevant screen control,
with Castle's portrait, speaker name and current roster context beside the lesson.
A gold outline draws attention to the next real action. The callout moves clear
of it and avoids the other lesson regions where space permits. Each lesson declares
one or more groups of real controls and readouts: their clipped live bounds form
openings in a darkened backdrop. The shared `UiSpotlight` builds disjoint shade
tiles around their union, so overlapping regions never accumulate extra darkness.
It tracks scrolling and viewport changes without moving the underlying screen.
The same declared elements constrain pointer targets, keyboard focus, armed
activation and pointer capture through `UiDocument.inputRegions`. A grouped
opening does not authorize unrelated controls that happen to lie inside its bounds.
Scrolling from an allowed control may move its enclosing pane; darkened areas
consume input, and pointer events do not bubble into unrelated ancestor actions.
Castle's dialogue controls remain available. Escape (including from a search field)
or Dismiss immediately removes the restriction; Company Guide resumes it.
Successful preparation orders advance the guide. Next skips an unnecessary lesson
without issuing an order. Guidance never owns a gameplay transition or requires audio.
Mission preparation navigation preserves the existing salvage settlement gate.

Interactive tutorials use this spotlight and callout treatment. Whenever a character is talking, show
their portrait with their name and readable dialogue to draw attention to the
speaker; audio alone is not the character presentation. This convention applies
to future tutorial and conversation surfaces as well as existing story subtitles.

## Intrinsic text and typography

Text is measured through one document-owned seam used by both layout and paint. The
default `white-space: nowrap` preserves control and value labels as a single line.
Explicit `white-space: normal` wraps prose at the available content width; the same
measured line sequence determines intrinsic height and paint positions, so headless
and live targets cannot disagree about line breaks. In a row, an auto-sized nowrap
element contributes its glyph advance plus padding and border before free space is
distributed; in a column, its measured block height contributes on the main axis. A
declared size still wins. Changing retained text marks geometry dirty so the next
document update buys one layout pass rather than leaving a stale box.

Typography roles remain ordinary semantic classes and inherited CSS properties. The theme supplies
a regular body face for controls, values, and prose and a display face for headings. Casing belongs
to authored copy: all caps is a heading treatment, not a global font policy. Horizontal placement is
`text-align`; button widgets center their measured line box vertically inside the content box.
For composed controls, `align-items` places row or column children on the cross
axis with `start`, `center`, `end`, or `stretch`. Centered legends use their
intrinsic line height, so padding does not become a second alignment authority.

## Console materials

The standard shipboard theme evokes a late-1990s military computer: gunmetal
housings, raised keys, recessed instrument readouts, muted phosphor text, and
amber selection cues. Bright readiness and danger colors still communicate
state. Mechanical decoration stays at the shell and surface boundaries, away
from small labels and operational information. SVG locking plates and vented
end caps belong to the persistent room rail. Decoration is never an input
target or a source of domain state.

Materials use the ordinary cascade. Uniform `border-style: outset` and
`inset` light opposite edges of the same border box; pressed controls reverse
the relief without moving their content or hit areas. `solid` preserves the
existing flat treatment. `background-image` supports a single CSS
`linear-gradient` toward the bottom or right, with explicit ordered
percentage color stops within 0–100%, or `none`. Equal stop positions create
hard edges; alpha interpolation is premultiplied. Other image functions,
angles, and border styles fail explicitly. Gradients compile once into shared
geometry and fill the padding box above the background color. They do not
inherit or affect layout. High contrast keeps flat surfaces and bright
outlines instead of material shading.

The contract picker separates clients, selectable operation cards, and the
selected operation's dossier into bounded columns. Mission prose and facts wrap
inside inset readouts; lists and the dossier scroll independently while their
navigation and briefing actions remain available. The dossier's SVG scope is
decorative framing for the planet image, never a tactical map or an intelligence
claim. Authored story dossiers replace that scope with a framed, aspect-fitted
location illustration and a location-file caption. Debriefs can place the same
pre-operation image above personnel records with an archive caption; outcome
text and casualty facts remain separate and authoritative. Art never sits behind
readable prose or occupies the tactical survey. Inapplicable story
art uses the ordinary planetary view and reserves no debrief image panel.

The briefing groups a compact mission dossier beside the selected force and its
command deck, with a tactical survey on the right. Contract payment is a prominent
employer authorization readout, using the negotiated cash multiplier and salvage
rights rather than a second reward calculation. Zero-payment operations say so.
Labeled employer and opposition identities use the factions' existing crest
resources. The employer belongs to the contract patron or verified authored-job
client; opposition belongs to the prepared scenario, including its defender
override. Unknown and unaffiliated forces never borrow the local world's flag.
Compact section heights avoid scrolling through unused space at 1920x1080 and
100% scale; larger rosters and content retain their bounded scrolling areas.
The survey is an immutable projection of the unstarted production battlefield:
terrain, strategic objective footprints, insertion berths, and authored approach
routes. It exposes no enemy unit positions. Dense maps emphasize the command
objective and lanes instead of labelling every berth and building; the accompanying
summary retains strategic-site counts. Small objective-driven missions identify
their actual target sites and extraction endpoints.

Wayline's voiced chapter briefings begin Mara's recorded narration on entry and
expose Replay Briefing and Stop Voice in the command rail. A compact contact panel
above the dossier shows her existing portrait and name, distinct from the force's
selected officers. Its speaking indicator follows narration playback, including
replay, stop and natural completion; the portrait remains as the recording's
identity while idle. Other missions reserve no speaker-panel or voice-control
space. Text remains available independently of playback; terrain refreshes and
viewport changes do not restart the recording.

Preparation runs asynchronously from campaign-free captured construction inputs.
The briefing pins its seed, shows a pending survey while generation runs, and
requires a ready scenario before deployment. Relevant input changes supersede the
old preparation; stale results and abandoned scenarios are released. A failed
preparation offers an explicit retry. Deploy consumes the same scenario the player
surveyed, then applies campaign personnel and resources at acceptance. Background
preparation never commits a contract or spends campaign resources.

Operation-scale chevrons and risk skulls retain their independent
mission meanings and readable labels. A segmented personnel meter compares selected
ready Marines with the recommendation; it caps its visual fill while retaining exact
counts, and never becomes a launch gate or a prediction of victory. Experience,
equipment, command capacity, and support remain separate facts.
The force section owns its assignment action. Compact Back and Deploy controls sit
at the left and right of the bottom rail, outside the scrollable planning area;
readiness or the existing blocker appears beside Deploy. Reinforcement and assignment
fallbacks retain their existing commands, with their explanation outside the button. Command keys sit in a recessed rail with raised edges and grip
plates; readiness belongs to a small status lamp rather than flooding the key
face. Selectable briefing options show a latched surface and an illuminated
rocker whose position also distinguishes selection without relying only on
color. Tier and drawer indicators follow that same illuminated-state vocabulary.
Disabled controls remain subdued, and keyboard focus retains a distinct outline.
The force breakdown uses compact read-only cards that keep each commander's name,
squad and Marine counts, and attachment state together. The captain's existing
portrait anchors the card beside its text and also appears in the commanding-officer
picker. Portrait identity comes from the captain; an unassigned force never borrows
another officer's image. Acting-NCO cards remain valid independent groups, and
an explicitly omitted operation officer remains omitted across screen rebuilds.
Cards visibly distinguish the operation commander, sub-commanders and acting
NCOs. A brief always-visible explanation names Conquest advance, regrouping and
objective coordination, while the commander picker explains unattached-squad
leadership and rank capacity. Essential role and behavior meaning never relies
on discovering a hover target. Hints expand these rules without promising a
passive stat bonus or permanent ownership of a compass lane. Cards keep a bounded
reading width as the briefing expands; they wrap into a scrolling gallery instead
of spreading a name and its counts across the screen. Existing readiness warnings
remain on their values. An empty officer breakdown reserves no gallery space.
Landing Craft reports the company ship's available boats with their existing hull
image, mission seats per boat trip, planned company trips, and host ship. Trips
come from the deployment manifest, including personnel-sized ordinary operations;
they are neither the number of physical boats nor a count of simultaneous waves.
Employer craft and their trips remain a separate contribution. Stationed forces
report local lift, and debug fixtures explicitly label their override. A contextual
hint explains cycling and the Boat Deck's ownership of losses and fittings.
The command deck keeps a distinct SVG ability symbol, short name, selection, and
slot cost visible on compact cards. These pictograms and their detail sheets share
one presentation mapping. Fleet Support identifies each contributing ship with its
name and the existing hull-spec sprite, and logistics use bounded groups sized to
their information; empty fighter cover is a quiet textual state, not an empty panel.
Activation costs, cooldowns, use limits, and explanatory copy belong to a shared
popup; source and carrier rows use that same layer for their secondary details.
These briefing hints open on hover or visible keyboard focus. They remain
pointer-transparent and bounded to the document, and disabled power hints explain
why a selection is unavailable. Power copy reads the existing power metadata;
compact presentation never merges deck capacity with activation resources.
Normal missions have neither a debug control nor a reserved debug lane. Debug
missions start with their drawer closed and expose a compact header latch with
a directional chevron. Opening it overlays the right-hand survey with the instrument
rack; closing it reveals the survey again. Force controls keep the same position. The closed subtree is
absent from layout and keyboard navigation, and toggling preserves focus on the
latch. Selecting another mission resets the drawer to closed.
The rack contains a tier selector, labelled adjustment banks, and a separate
scrolling air support list. The adjustment list scrolls so a longer mission fixture cannot
hide its later controls. Empty optional step buttons collapse instead of posing
as controls. These surfaces project the existing mission and debug commands;
their materials and layout do not alter mission availability or development
gating. Aircraft rows identify each craft with both its name and the existing
base-game sprite referenced by its airframe profile. No duplicate artwork or
presentation-only aircraft catalog is introduced.
Mission presentation is authored and visually reviewed at 1920 x 1080 with
100% UI scale. The dedicated mission preview suite uses only that presentation.

## Responsive grids

`display: grid` is the retained fixed-item gallery context. The widest immediate
child's preferred border-box width defines a column; the available content width
determines how many complete columns fit, and document order fills each row before
wrapping. Each row takes its tallest child's preferred height. `column-gap` and
`row-gap` remain ordinary CSS lengths, and wrapped rows contribute to the existing
vertical `scrollHeight` contract. The first production consumer is Fleet Armory's
owned-company portrait-card gallery.

## Overflow and clipping

An element paints its own background and border under the clip inherited from its
ancestors. Its content and descendants then inherit the intersection of that clip
and its padding box when `overflow` is `hidden` or `scroll`. This is the CSS rule:
the border remains visible while content cannot paint through it.

The one `UiLayoutEngine.clipForChildren` expression is consumed by both the painter
and reverse-order hit test. A clipped-away child is therefore neither visible nor
clickable. Only the live paint target converts that document rectangle into physical
framebuffer pixels; the conversion observes Starsector UI scale and flips the
top-left document Y axis exactly once. A headless target applies the same clip in
document pixels.

Story dialogue has a non-interactive subtitle panel inset from the battlefield's
top-left corner, showing the contact's existing portrait beside the speaker name
and retaining the complete line while the recording plays. It remains visible
in direct control and when audio is muted. The panel
uses the shared theme and does not consume world-selection input. Its lifetime
and factual validity follow the presentation queue in `battle-radio-nouns.md`.

The standalone battle treats HUD chrome as an overlay rather than as space removed
from the world. Its square-cell camera cover-fits the granted host viewport and pans
the cropped map axis instead of shrinking to a centred map rectangle with dead bars.
Primary world selection resolves on release. Motion within the pointer threshold
remains the ordinary nearest-unit click, including defender inspection and empty-
ground deselection. Crossing that threshold paints a bounded marquee and considers
only live player infantry squads and combat Mechs; convoy vehicles, hostiles, and
mission payloads are not drag candidates. The one candidate nearest the marquee
center wins, with stable entity identity breaking an exact tie. This preserves the
single-selection command model while leaving room for a later multi-selection
authority rather than pretending a set exists today.
Player-facing Conquest command intent occupies the top-left opposite the top-right
time/objective rail. When organizational groups are published, this bounded
readout shows named leaders, deployed squad strength, task and regrouping state;
additional groups are explicitly summarized rather than extending over the
battlefield. Extraction, civilian rescue (including Last Lift), and Silent Colony
use a shared public mission picture: named objective branches, current step,
completion conditions, measured progress, and observed blockers. The briefing,
world beacons, and per-objective Locate action refer to the same authored places;
shelter beacons identify the entrance where release is triggered. A moving
friendly payload receives its own beacon. Off-camera active objectives retain
an edge indicator, and Locate moves only the camera.

The right rail keeps independent branches visible together; an archive does
not acquire a fictitious extraction step. Cohort counts separate boarded,
remaining and lost people, including partial-survival completion. Blockers come
from the systems that actually gate recovery, movement and boarding, rather than
being inferred from a commander's intent. Terminal results clear obsolete wait
messages. The left rail lists friendly squad assignments and local contact,
scrolling when needed instead of silently dropping squads. Player orders take
precedence immediately. Assigned task is distinct from confirmed execution.
Both surfaces remain bounded and preserve a clickable battlefield between them.
This common presentation is extensible by mission family; other families retain
their current displays until adapted. `shared-mission-objectives.md` tracks that
remaining migration.

Raid's right rail instead projects its two objective steps,
seizure progress and return instruction. Numbered world markers and the briefing
survey identify the same target and landing zone. Its Locate control centers
the current objective without issuing a tactical order; completion and progress
remain mission-owned. Essential mission meaning never requires debug visibility.
The Conquest command readout uses semantic target labels to keep internal zone IDs out of the player HUD.
Legacy scenes without group identities retain the approach view. Tick Profile
and DEBUG share one centred developer cluster.
The selected player squad replaces the force plate with a 3-column by 4-slot
fire-team roster; its default cards expose health and equipment shorthand while
hover reveals the marine's full primary, special equipment, suit system, profile,
role, armour, and readiness without enlarging the persistent HUD. Selecting an
exact live player Mech replaces that infantry roster with a compact Mech plate:
variant, deployed doctrine, effective doctrine, the lance-wide Form on Lead or
Free Reign order, Brawler, Tank, Long Range Support, and Balanced choices, plus
Reset Doctrine. A separate full-width tactical-order section arms lance-wide
Defend Area placement; it is vertically separated from coordination and doctrine
so the additional action cannot compress either control family. Tank is
presentation shorthand for Frontline Support. Scope is
visible beside the controls: the lance order affects the whole selected battle
lance, Defend Area affects that whole lance's temporary assignment, and doctrine
affects only the exact selected mech. The plate projects
the simulation's effective state and sends serialized battle-command requests;
it never mutates the squad, loadout, campaign default, assignment, or contact
picture directly, and it is absent for enemies, infantry, rescue payloads, and
stale selections. With that exact friendly Mech selected, a stationary
right-click on world ground requests a one-shot tactical move; the simulation's
resolved reachable destination is shown as a cyan cell cue until arrival.
Crossing the pointer threshold instead keeps RMB-drag camera panning, and HUD
chrome blocks the world request behind it. A selected friendly infantry squad
uses the same gesture and cue at squad scope, whether selection came from one
world member or the squad roster; the surface supplies only the requested cell
and does not mutate mission authority or combat state. When that cell lies
inside an uncaptured Conquest compound, the simulation resolves the gesture to
the compound's authoritative capture room and keeps the cue there until capture
completes; a Marine-held compound remains ordinary ground. Selecting a squad
opens a bounded, scrollable GOAP diagnostic beneath the right rail;
it reports the decision sequence and predicates without reintroducing path-cell
highlight controls or per-slot assignment inventories.

The selected-infantry plate and selected-Mech plate expose **Defend Area**
as a deliberate two-step command rather than overloading the contextual
right-click gesture. Infantry issues it for the selected squad; a Mech issues
one shared order for its whole battle lance.
The infantry summary row is reserved for squad identity, strength, and morale;
its contextual controls live in a separate tactical-order row so adding or
renaming an order cannot compress the status readout.
Arming it gives the next primary world click to a circular twenty-cell-radius
reticle; right-click, Escape, or selection change cancels without issuing an
order. Placement returns the input seam to ordinary world selection, while the
selected squad's accepted circle remains visible as a center and perimeter cue
until another order or withdrawal supersedes it. Presentation supplies only
the squad identity and clicked cell; the simulation validates and snaps the
center and owns the defensive behavior.

The direct-control entry plate reserves a stable position above the taller of
infantry and Mech/lance selection panels. It offers takeover of an exact eligible
player Marine, combat Mech, or deployed APC. Its button or C enters; C or Escape
returns authority to autonomous behavior. The action camera follows the controlled body with smooth, bounded cursor
look-ahead beyond a dead zone and retains wheel zoom; carrier-specific movement and firing obey
`direct-control-nouns.md`.

Strategic and action views share the extended close-zoom range. Strategic
wheel zoom anchors the cursor; action zoom preserves the controlled body’s
framing and viewport-relative lead. All pointer aim uses the resulting camera
projection. Look-ahead resets on takeover and eases back when the cursor enters
chrome or leaves the battlefield.

Active control uses a thin bottom-center equipment strip: color-only armor and
health or structure lines above the weapon groups, with armor on top. Filled
lengths read the exact controlled body; armor depletion empties its line without
changing the health line, and armorless bodies have no armor fill. Identity,
durability labels, and numeric capacity values do not occupy this strip.
Equipped Marine shield and smoke cards share its weapon row without increasing
the strip height. E and G identify their keyboard commands; the cards are
readouts, so pointing at them does not choose a smoke target. They display the
equipment's actual readiness, active or throw clock, shield soak, and remaining
canisters. Unequipped abilities have no placeholder card.
A controlled Marine also has a read-only Shift sprint card with a distinct
stamina bar. It projects that individual's battle resource, including active
sprint and exhaustion, within the existing row height. Sprint belongs to every
controllable Marine and is absent for Mechs and APCs; it is not inferred from
the armor or health bars. Both Shift keys are held inputs, with releases
observed before chrome consumes events. Input cancellation clears sprint and
physical release reconciliation never activates it.
Weapon cards show authored firing patterns, resource units, and
recovery or burst state without claiming legal aim or barrel clearance. A Mech
offers all direct mounts or one installed hardpoint through buttons and keys
1–4; indirect mounts are read-only and retain their unavailability in manual
control. The selection projects the battle-owned session and does not edit
equipment or AI doctrine. All remains with the Mech weapon groups; Exit and Pause
occupy separate top-right buttons. The world retains health bars, the controlled bracket,
and the aim crosshair. Camera framing reserves clearance above the strip; when
world-edge clamping would leave the body under it, the strip docks at the top
below the session buttons.

A thin, faint world aim lane runs from the controlled weapon's firing origin
toward the pointer. A small amber marker reports the first observed structural
projectile stop; a still fainter dashed continuation communicates the blocked
bearing. The host's actual pointer eligibility hides the lane over chrome or
outside the viewport even when simulation intent retains an aim point. It
continues to project pointer and camera changes while paused. Geometry styling
ends at current observation, so hidden blockers receive no marker or transition.
This readout adds no input target or HUD space and makes no readiness or hit
guarantee. `direct-control-nouns.md` owns carrier origins and selection scope.

Strategic panels, commander/objective rails, power cards, retreat, communications,
and developer panels are hidden together with their input and pointer bounds.
Selection and command/debug cell overlays also yield. Hidden projections continue
to refresh, so handback restores current information and the existing selection.
Entry cancels armed command-power and Defend Area targeting, clears retained focus
and pointer capture, and dismisses retreat confirmation without abandoning the
battle. The equipment strip and session buttons own only their separate visible
bounds; the intervening world remains interactive. Movement and fire stop over
either surface. The host snapshots releases before chrome consumption, so a swallowed
release cannot leave a key or trigger held.

Control allows pause or 1x; exit restores the entry rate unless the player explicitly
chose a time setting during control. Window focus loss, screen detach, battle
completion, or simulation release exits control and clears held input. The plate
and input host project the battle-owned session; neither owns unit movement,
eligibility, damage, or squad membership.

## Vertical scrolling

`scrollTop` is retained content state on the element, while `scrollHeight` is a
layout result on its box. Layout clamps the effective offset to
`max(0, scrollHeight - content height)` and translates descendants only: the
scroll surface, padding-box clip, and host viewport do not move.

Wheel input uses the geometric target chain rather than a clickable-only hit. It
walks from the deepest element outward, skips `overflow: hidden`, and moves the
nearest `overflow: scroll` surface that has room in the requested direction. At a
nested boundary the next movable ancestor receives the delta. At the Starsector
host seam, a wheel aimed at a retained scroll surface is still consumed when every
candidate is at its boundary, preventing the campaign layer from receiving the
same notch.

The scrollbar thumb is overlay chrome painted after descendants and clipped to the
surface's padding box. It advertises position and range but does not reserve layout
space or participate in hit-testing.

## Input, focus, and capture

Starsector `InputEventAPI` objects and LWJGL integer key codes end at one host
adapter. The retained tree receives document-space coordinates, pointer buttons
named by role, named keys, and a modifier set. Printable `eventChar` values cross a
separate character-input method only while a retained `input` owns focus; key codes
remain navigation/edit commands rather than text.

Geometric targeting and action eligibility are distinct. The deepest painted box
under the pointer is the event target even when it is a non-clickable label; pointer
events bubble through its retained parents, while click and focus defaults search
for the nearest eligible ancestor. Hover is likewise an ancestor chain, and a
consumer that wants the innermost element carrying some property reads that chain
rather than asking every candidate whether it is hovered.

Each document has at most one focused element. Tab traverses eligible elements in
document order, `tabIndex=-1` remains directly focusable but is skipped, and
disabled controls are ineligible. Enter confirms the focused control, Space uses a
press/release action, and unmodified Escape belongs to the document cancel seam.
Focus acquired by keyboard is visibly distinguished from focus acquired by pointer.
The MLX `input` subset binds `value`, `oninput`, and `maxlength`; it supports
printable characters, Backspace, Tab, Enter-to-blur, and Escape-to-blur without file
access, reflection, a native widget, or a second text-state authority.

Pointer capture is explicit rather than an automatic consequence of pressing.
While held, pointer movement, hover, and release retarget to the captured element;
primary release dispatches before ending capture. Leaving a screen clears active
input state, while a resize/position callback only relayouts the enduring document.

## Procedural canvas

A canvas has two independent sizes. Its integer surface width and height define the
producer's local coordinate space; its retained content box defines where that
surface is rendered. With no authored rendered size, the surface provides the
canvas's intrinsic layout size. If both differ, each axis maps by its own ratio just
as an HTML canvas stretched by CSS would.

`CanvasMetrics` owns both directions of this mapping. Document pointer coordinates
are never decorated with invented canvas fields; a canvas handler explicitly calls
`toCanvasX` and `toCanvasY`. Samples delivered under capture may therefore be
negative or beyond the surface edge. UI scale does not alter the mapping because
both sides are document-space quantities; device-pixel ratio is separate metadata.

A document-owned registry associates attached canvas identity with one Java
producer. The producer draws deterministic projection state through a bounded
`CanvasContext`, not raw OpenGL, after the element background and before following
content. Its output is clipped to the canvas content box intersected with ancestor
clips, and its `visibleBounds` is expressed in canvas-local units. Whole-texture
sprites are another bounded canvas primitive: canvas metrics place and scale them,
the producer supplies the domain composition, and the retained painter continues
to own clipping. A sprite operation carries both its stable asset path and, when
live, its loaded `SpriteAPI` handle. The Starsector canvas consumes the handle and
restores borrowed OpenGL state; the headless canvas resolves the path to the source
PNG. Both therefore exercise one producer, layout, pose, occlusion, and
actor-composition recipe. Canvas sprite operations may select a normalized
source region for atlas and flipbook art and declare normal or additive
blending. Those are producer-owned visual intents rather than backend
shortcuts: the live and headless targets apply the same region, RGB tint,
opacity, rotation, and blend contract.

Authored SVG assets are another canvas primitive. A producer loads and compiles
an asset once, then aspect-fits its cached geometry inside a canvas box with a
color tint. Solid shapes, full SVG path commands, groups, affine transforms,
fill rules, and strokes share one triangle geometry in live and headless painting.
This is an explicit vector subset rather than a second markup tree: unsupported
constructs fail when loading the asset. Gradients, references, masks, clip paths,
and opacity that requires compositing overlapping paints are outside the current
subset.

A surface may animate cached vector artwork and procedural primitives with its
presentation clock. Motion advances independently of painting; rendering the same
state twice cannot consume an event or advance an effect. The clock and transient
effect state belong to the surface rather than the rebuilt document or campaign.
HQ receiver motion follows this model: continuous instrument activity remains
visually distinct from report-arrival bursts, and selection sweeps leave the
readable dossier stable after their short reveal. Motion evidence uses the same
producer, document and clock at 1920x1080 and 100% scale.

SVG text comes from the host resource reader: Starsector resource access in
game, an explicitly installed disk reader in headless tooling. Changing hosts
invalidates cached assets; shipped rendering code never opens filesystem paths.

An image element paints through that same sprite primitive rather than teaching
the painter about textures, so both backends already agree on what an asset looks
like. It resolves the drawable through the paint target: the live target asks a
document-installed resolver that loads a texture once per path and remembers a
failure, and the headless target reads the file the source path names. The asset
is aspect-fitted and centred inside the content box, never stretched to it,
because a square icon in a rectangular box would otherwise read as a different
symbol. An unresolvable or absent source draws nothing at all.

An SVG image source compiles through the same host reader and vector primitive
when its binding changes, outside the paint loop. It follows the image element's
aspect-fit, clipping, and opacity rules. Malformed or unsupported SVG fails at
binding time, matching authored canvas assets; it does not silently fall back to
a raster or an invisible decoration.

An existing renderer that already owns a complete GL lifecycle may use the canvas's
bounded live-host pass. The pass receives the absolute content-box viewport plus
the canvas surface dimensions, remains inside the painter's active clip, and must
restore its local GL state. It does not receive input or document ownership. A
headless target declines the pass, so the producer must emit deterministic canvas
evidence from the same scene dimensions and camera contract. That viewport is
answerable before any pass runs, so a producer may place a backdrop under the
world as readily as an overlay over it.

**A world drawn by a host pass owns the coordinate space, and everything a
producer draws around it converts into that space rather than the reverse.** The
pass projects in host pixels with Y running up; canvas primitives are in surface
units with Y running down, and the two axes are stretched by different factors
whenever the surface aspect and the content box disagree. Independent canvas
stretching may not distort a battle renderer's square cells, so a camera framed
on the surface instead of on the host viewport is wrong even before it moves:
its cells are square in a space nothing is drawn in. A camera the player flies is
therefore stated on the host rect, pointer samples are converted into it, and
overlay and backdrop geometry converts back out of it per axis.

The failure this describes is quiet. Centred and unzoomed, a surface-stated
camera agrees with the pass by coincidence, and the disagreement appears only
once the view moves — as a backdrop sliding against its own map at a rate set by
the stretch, and travelling the wrong way entirely in the vertical.

Layered character authoring is a separate desktop concern rather than another game
screen. A unit-layer document separates a unit's equipment variants from its named
animation clips, so playback cannot accidentally treat a loadout change as motion.
Each clip owns ordered keyframes of sprite layers in normalized actor coordinates,
including source path, offset, independent scale, angle, pivot, visibility, and
transition duration. Playback smoothsteps matching layer transforms, including the
independent scale used by an articulated mech thigh stretching toward its foot. The
authoring workbench edits that contract, plays one selected clip, and renders
combined PNG sheets in a controlled Java2D context. Live render adapters remain
responsible for consuming the same contract. The editor keeps bounded whole-document
undo/redo history, while JSON and image overwrites require explicit confirmation;
the tool never reaches into a running battle or treats an editor-only transform as
shipped behavior.

Turret authoring is another page in that same desktop workbench, not another
application. It stages the linked weapon, mount, structure, FX, and emplacement
layout catalogs as one undoable document while preserving their authority on
disk. Its live turret preview is the same deterministic catalog projection used
by snapshot evidence. The workbench host knows only the page lifecycle; the
root-project contribution owns all turret and map-generation types.

## Authority boundaries

- Starsector owns the campaign UI, custom-dialog placement, callback cadence, and
  final screen composition.
- The retained UI toolkit owns document geometry, paint/hit ordering, interaction
  state, styling, backend-neutral paint traversal, and host-space conversion inside
  the granted rectangle.
- Each game feature owns its view model and commands. The toolkit imports no
  company, campaign, or battle type.
- Existing immediate widgets remain supported while surfaces migrate. A retained
  surface may coexist with legacy widgets in an explicit render order, but one
  interactive region must have one input owner.

Retained documents replace `WidgetRoot` for long-lived interactive surfaces that
need stable identity, nested layout, scrolling, focus, or reactive updates.
Immediate widgets remain valid for small legacy and diagnostic surfaces; adoption
is driven by a surface's interaction needs rather than a flag-day rewrite.

## Evidence and acceptance

Headless geometry and input tests prove document behavior without Starsector or an
OpenGL context. Selectable snapshot suites capture deterministic visual evidence
through the shared runner from either the command line or authoring workbench.
Resolution pairs must preserve composition under uniform fit; separate user-scale
snapshots prove intentional responsive behavior. The in-game workbench proves host
coordinates, viewport grants, UI scaling, input routing, and GL-state seams. Live
screenshot review proves final presentation and feel. Passing one evidence layer
does not substitute for the others.

## Extension points

- Add layout or style capabilities only when a real surface needs them.
- Add procedural visuals through the canvas seam before expanding the ordinary
  element vocabulary.
- Add authored component files only after the Java tree, layout, paint, and input
  contracts are proven in-engine.
- A new screen adopts the retained stack as one surface; migration does not require
  a flag day across every Marine Ops screen.
- A new kind of catalog item becomes a spec-sheet subject by gaining one copy
  factory; every bound screen shows it without change.
