# C15 — Retained Fleet Armory

Status: IN PROGRESS — retained hierarchy, squad equipment issue, and focused Mech Lab ship; broader company administration remains
Written: 2026-08-23
Updated: 2026-08-25 — the separately routed Mech Lab now ships alongside persistent
player-authored Weapon and Armor definitions, their retained twelve-billet designer,
and non-destructive legacy intent migration.

Read `company-view-nouns.md` and `ui-nouns.md` first.

## Problem

Fleet Armory must make company -> squad -> fire team -> marine legible at large
company scale without asking the player to author or assign gear twelve people at a
time. The former administration shell duplicated the retained hierarchy and kept a
second fire-team template workflow alive. Squad equipment now needs one scalable,
inventory-honest authoring seam. Mech Lab needs an equally focused route without
recreating that catch-all shell.

## Outcome

Fleet Armory becomes the first production retained surface. Its landing view is a
responsive grid of literal portrait company cards; selecting one enters a matching
squad-card overview for that company, and selecting a squad enters its three-team
inspection workspace. One Weapon doctrine card and one Armor doctrine card compose
the whole squad's exact issue; special equipment follows Weapon. A clickable
breadcrumb keeps every completed level directly reachable.

## Information architecture

- **Owned-company overview:** a responsive `display: grid` gallery of selectable
  portrait cards at an approximately 1:3 width-to-height ratio. Each card names the
  company and reports marine squads, mech squads, RTD strength, stationing, and a
  derived readiness state before the player enters its workspace. The campaign
  currently owns one authoritative roster, so the first shipped gallery has one real
  card through a list-shaped view-model contract; it does not manufacture additional
  companies or persistence.
- **Company squad overview:** a matching responsive gallery of literal squad cards.
  Each card reports strength, squad-equipment issue state, officer command, whereabouts, and
  readiness before the player enters that squad's equipment workspace. The card body
  remains the inspect target; reinforcement is a compact secondary action rather than
  a replacement inspect button.
- **Fire-team context:** Alpha, Bravo, and Charlie report strength and current squad
  doctrine together. Selection chooses which four of twelve projected billets to
  inspect; it never narrows or resets the squad transaction.
- **Workspace:** the selected squad's Alpha, Bravo, and Charlie teams sit beside one
  primary viewer of the selected team's four persistent marines. Each card presents
  rank, name, readiness, aptitude, experience, career, candidate role, battle-composed
  appearance, equipment names, and resolved combat figures. Weapon and armor figures
  use compact capability meters normalized against their catalog ceilings while
  retaining the exact number beside each meter.
- **Equipment editing:** an always-visible squad strip owns separate Weapon and Armor
  selectors. Weapon definitions include roles, primaries, grades, and optional
  special equipment; Armor definitions include protection only. Their pair projects
  exact equipment and combat-stat deltas onto the same named marines. One squad-wide
  readiness result and one squad-wide issue action commit all twelve billets atomically.
- **Equipment designer:** a distinct breadcrumb depth edits one reusable definition
  at a time. A horizontal library, Weapon/Armor mode, three team tabs, and four live
  billet previews expose all twelve ordered positions without returning to the old
  administration shell. Naming is a retained text input; role, primary, grade,
  special, and armor controls change draft intent without consulting inventory.
- **Inventory boundary:** the roster's exact atomic preview still gates assignment,
  but this viewer omits its free-stock/returns/required-issue ledger.
- **Marine inspector:** aptitude, career, wounds, and materialized billet equipment;
  there is no routine per-marine equipment authoring.
- **Mech Lab:** active support squad -> chassis -> installed loadout, with fixed
  hardpoints beside the finite subsystem inventory and its explicit install action.
  It invokes `MechBay` rather than adapting squad equipment doctrine.

## Scope

- Continue the surface through `.mlx` components, theme roles, keyed lists, bounded
  scroll, focus, keyboard actions, and deterministic headless evidence.
- Keep `MarineRoster` and `MarineArmory` authoritative for exact finite-stock
  preview. The view model never invents a second allocation answer.
- Preserve the completed save migration from legacy per-team template intent into
  player-authored squad definitions without rewriting an existing marine's materialized kit.
- Preserve Mech Lab as a focused retained surface over `MechBay`; do not restore the
  removed catch-all administration shell.

## Acceptance

- The player reaches any squad in two selections from the company collection and any
  one of its fire teams with one further target selection.
- Breadcrumb actions return directly to the company collection or selected company's
  squad overview without routing through footer utilities.
- Selecting Alpha, Bravo, or Charlie preserves both candidate doctrines, scroll,
  focus, and the four marine/canvas element identities while only changing the
  visible four-billet slice.
- Weapon doctrine selection changes primary, grade, role, and special issue without
  changing armor; Armor doctrine selection changes protection without changing the
  Weapon half.
- Player definitions round-trip with `MarineArmory`; built-ins cannot be renamed or
  deleted, assigned custom definitions cannot be deleted, and Save as New never
  silently changes an already issued squad.
- Definition authoring succeeds without recipes or stock. Only the authoritative
  squad preview/apply transaction may reject physical issue.
- The exact twelve-billet preview is the same operation used by apply. A failed
  readiness, stationing, recipe, or stock check changes no kit and neither doctrine id.
- Company and equipment cards are literal presentation containers, without
  deck/hand/consume semantics.
- The company gallery auto-fills fixed portrait cards into columns, wraps into rows as
  the viewport narrows, and scrolls vertically without silent truncation.
- The fire-team viewer shows all four named marines together with candidate armour,
  primary grade, special equipment, and combat statistics resolved from the same
  rules used by battle.
- Damage, range, accuracy, sustained output, armor pool, armor rating, and movement
  appear as comparative meters with exact values; their fill scale is stable across
  the four marines rather than relative to only the currently selected team.
- Live selected-marine portraits cycle the authored idle clip with staggered phases;
  headless snapshots remain fixed and deterministic.
- The same twelve-billet recipe renders deterministic PNGs without a game or OpenGL context;
  live acceptance remains responsible only for host scaling and feel.
- Layout remains usable for a large company and at UI scales 1.0, 1.25, and 1.5.
- Body copy and values use the regular mixed-case face; compact display faces are
  reserved for section headings and the screen title, with geometry sized from their
  measured line heights rather than the legacy 20-pixel minimum.
- Mouse and keyboard interactions reach the same domain commands.
- Fleet Armory exposes no Armory Administration route, legacy individual-kit editor,
  template picker, or squad-arrangement editor.
- Mech subsystem counts and enabled install actions come from `MechBay`; the retained
  room neither recomputes stock nor creates another loadout authority.
