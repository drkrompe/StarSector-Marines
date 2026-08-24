# C15 — Retained Fleet Armory

Status: IN PROGRESS — formation, template issue, and live billet preview migrated; broader parity remains
Written: 2026-08-23
Updated: 2026-08-24 — selected template billets now drive a retained equipment doll and battlefield-composed sample soldier.

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

Fleet Armory becomes the first production retained surface. The formation rail keeps
the player's organizational context visible while a reusable template library,
four-billet designer, and exact inventory transaction preview occupy one coherent
workspace. Templates remain plans, never collectible cards.

## Information architecture

- **Formation rail:** company summary -> squad -> Alpha/Bravo/Charlie, with current
  assignment and conformance state. The selected fire team is the working context.
- **Template library:** reusable designs with fielded and ready-to-issue counts,
  searchable/filterable as library size grows.
- **Workspace:** four billets and current members, template authoring, or the selected
  template's issue preview depending on the active task. Selecting a billet projects
  its materialized equipment doll and a sample soldier through the battlefield actor
  composition path without changing campaign state.
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

- The player reaches any fire team in two selections from the company rail.
- Selecting teams and templates preserves scroll/focus and does not rebuild unrelated
  panes.
- Designing remains legal without stock; assigning remains stock-gated and atomic.
- Template and arrangement previews exactly match the operation that applies them.
- The billet preview shows candidate armour, primary grade, and special equipment;
  its sample soldier shares battlefield layer recipes rather than a UI-only pose table.
- No player-facing use of card/deck/hand/consume terminology or behavior exists.
- Layout remains usable for a large company and at UI scales 1.0, 1.25, and 1.5.
- Mouse, keyboard, and drag interactions reach the same domain commands.
- Mech subsystem counts and enabled install actions come from `MechBay`; the
  retained surface neither recomputes stock nor creates another loadout authority.
- Legacy per-marine equipment mutation is removed only when every catalog item is
  expressible through templates.
