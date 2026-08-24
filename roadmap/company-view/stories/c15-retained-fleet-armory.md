# C15 — Retained Fleet Armory

Status: IN PROGRESS — company/squad/fire-team drill-down and template issue migrated; broader parity remains
Written: 2026-08-23
Updated: 2026-08-24 — Company selection now opens a squad-card gallery, squad
selection opens the fire-team workspace, and template selection projects all four
billets as battle-composed equipment mannequins.

Read `company-view-nouns.md`, `ui-nouns.md`, and
`c14-fire-team-equipment-templates.md` first.

## Problem

Fleet Armory's underlying template and arrangement operations now work, but the
screen remains organized around legacy tabs, individual paper-doll mutation, and a
flat absolute-positioned widget list. The presentation does not make company ->
squad -> fire team -> billet the primary path, and routine changes rebuild the whole
screen. The newly shipped Mech Lab correctly uses campaign authorities but shares
that legacy presentation stack, so its mech-squad and finite-stock workflow must
also survive the retained migration.

## Outcome

Fleet Armory becomes the first production retained surface. Its landing view is a
responsive grid of literal portrait company cards; selecting one enters a matching
squad-card overview for that company, and selecting a squad enters its three-team
template/refit workspace. A clickable breadcrumb keeps every completed level of this
drill-down reachable. Templates remain plans, never collectible cards.

## Information architecture

- **Owned-company overview:** a responsive `display: grid` gallery of selectable
  portrait cards at an approximately 1:3 width-to-height ratio. Each card names the
  company and reports marine squads, mech squads, RTD strength, stationing, and a
  derived readiness state before the player enters its workspace. The campaign
  currently owns one authoritative roster, so the first shipped gallery has one real
  card through a list-shaped view-model contract; it does not manufacture additional
  companies or persistence.
- **Company squad overview:** a matching responsive gallery of literal squad cards.
  Each card reports strength, equipped-team count, officer command, whereabouts, and
  readiness before the player enters that squad's equipment workspace.
- **Fire-team context:** Alpha, Bravo, and Charlie report strength and current
  assignment together; the selected fire team is the transaction target.
- **Template library:** reusable designs with fielded and ready-to-issue counts,
  searchable/filterable as library size grows.
- **Workspace:** the selected squad's Alpha, Bravo, and Charlie teams sit beside the
  reusable library. Selecting a template projects all four billets simultaneously as
  portrait mannequins with armour, primary, special-equipment sockets, and the
  battlefield-composed marine without changing campaign state.
- **Transaction rail:** free stock, returned issue, required issue, exact shortfall,
  and the one explicit apply action.
- **Squad arrangements:** a fast squad-level composition view using the same templates
  and transaction model, not a separate deck or persistence system.
- **Marine inspector:** aptitude, career, wounds, and materialized billet equipment;
  no routine per-marine equipment authoring after C14 retires it.
- **Mech Lab mode:** active support squad -> chassis -> installed loadout, with fixed
  hardpoints presented beside the finite subsystem inventory and its explicit
  install action. It invokes `MechBay` rather than adapting fire-team templates.

## Scope

- Rebuild the surface with `.mlx` components, theme roles, view-model bindings,
  retained keyed lists, bounded scroll, focus, and keyboard actions.
- Preserve every C14 inventory and assignment authority; the UI invokes existing
  preview/apply operations rather than recomputing their answers.
- Preserve the mech lab's `MechBay` ownership, installed/free accounting, atomic
  refit command, and read-only chassis/hardpoint boundary.
- Integrate C14 conformance and replacement presentation when Slice 5 has landed.
- Remove legacy Armory UI code only after feature parity and live acceptance.

## Acceptance

- The player reaches any squad in two selections from the company collection and any
  one of its fire teams with one further target selection.
- Breadcrumb actions return directly to the company collection or selected company's
  squad overview without routing through footer utilities.
- Selecting teams and templates preserves scroll/focus and does not rebuild unrelated
  panes.
- Designing remains legal without stock; assigning remains stock-gated and atomic.
- Template and arrangement previews exactly match the operation that applies them.
- Company overview cards are literal selectable presentation containers. Fire-team
  templates and squad arrangements never use card/deck/hand/consume terminology or
  behavior.
- The company gallery auto-fills fixed portrait cards into columns, wraps into rows as
  the viewport narrows, and scrolls vertically without silent truncation.
- The template breakdown shows all four billets together, including candidate armour,
  primary grade, and special equipment; every mannequin shares battlefield layer
  recipes rather than a UI-only pose table.
- The same billet recipe renders deterministic PNGs without a game or OpenGL context;
  live acceptance remains responsible only for host scaling and feel.
- Layout remains usable for a large company and at UI scales 1.0, 1.25, and 1.5.
- Body copy and values use the regular mixed-case face; compact display faces are
  reserved for section headings and the screen title, with geometry sized from their
  measured line heights rather than the legacy 20-pixel minimum.
- Mouse, keyboard, and drag interactions reach the same domain commands.
- Mech subsystem counts and enabled install actions come from `MechBay`; the
  retained surface neither recomputes stock nor creates another loadout authority.
- Legacy per-marine equipment mutation is removed only when every catalog item is
  expressible through templates.
