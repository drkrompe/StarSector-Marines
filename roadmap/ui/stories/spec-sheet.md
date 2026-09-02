# Spec sheet: one hover overlay for every catalog item

Status: IN PROGRESS

Written: 2026-09-02

Updated: 2026-09-02 — slice 2 shipped: the layer, the binder, the
`MissionFlowMlxScreen` drive point, and adoption on `PolityDoctrineScreen` with
`polity-ground-doctrine-spec-sheet-wide.png`. Placement is the standard
vocabulary rather than the margin fallback — the retained layout had neither
absolute placement nor margins, and `arrange` had a clean seam for the real
thing, so `position: absolute` with `left`/`top` (plus `pointer-events`, which
is what stops the overlay stealing its own hover) are ordinary parsed CSS now.

Read `ui-nouns.md` in full (hover is an ancestor chain on retained elements;
design laws 1, 2, 8 and 11), the dossier-overlay paragraph of
`company-view-nouns.md`, and `equipment-lore-catalog.md` for the prose every
item already has, before implementing.

## Goal

Every place the player is shown the name of a weapon, an armour pattern, a
special item, an integral system, a mech variant, or an equipment template
card should let them hover it and read what it is. Today that exists on one
screen, hand-rolled three different ways elsewhere, and not at all on the
polity doctrine panel that just shipped: eleven armour pattern names and a
mech name with nothing behind them. The copy and the stat meters behind those
descriptions are duplicated per view model.

After this story there is one **spec sheet**: a bounded hover overlay with a
heading, a crest, stat rows, and the item's field note, produced by one copy
factory from the item's owning catalog and shown by one runtime component that
any screen binds to any element in one line. The polity doctrine panel is the
first adopter; the Fleet Armory's per-card popups, the Mech Lab catalog, and
the doctrine designer's tiles migrate onto it and their private versions are
deleted.

## Vocabulary (to be folded into `ui-nouns.md`)

- A **spec sheet** is the bounded overlay describing one catalog item. It is a
  value (`SpecSheet`): title, subtitle, crest, accent, stat rows, note
  paragraphs. It carries no behaviour and no reference to the item.
- A **subject** is anything a spec sheet can be written for. The copy factory
  (`SpecSheets`) knows every subject kind; a screen never assembles a sheet by
  hand from catalog fields.
- The **spec-sheet layer** is one floating element per document, above every
  screen element, anchored beside the hovered subject element, never clipped
  by the viewport, and transparent to the pointer so it cannot steal the hover
  that opened it.
- A **binding** ties one retained element to one sheet. Bindings are made from
  Java by element, in the same place a screen builds the element's row; there
  is no markup attribute, because the sheet is data the screen already holds
  and a markup attribute would need a second way to name the subject.

## Shape (decisions made)

- **The sheet's contract is fixed first**, in `SpecSheet`, so the copy and the
  runtime can be built side by side. A stat row is a label, a value string, and
  an optional fill in 0..1 for a meter; a negative fill means no meter.
- **Copy comes from the owning catalog** through `SpecSheets`: a weapon at a
  grade (grade-scaled damage and range through `InfantryCombatStats`, the
  authored role and field note, the catalog crest), an armour pattern (tier,
  role, tradition crest, the four protection meters, the integral system named
  and summarised through `IntegralSystemCopy`), a special item (subtitle, the
  weapon behind it when it has one, its note), an integral system (delegating
  to `IntegralSystemCopy`), a mech variant, and an equipment template card
  (which resolves to the equipment it names, with the card's grade and access
  tier in the subtitle).
- **Mech prose lives in data.** `MechVariant` is an enum with no note; a small
  JSON catalog under `mod/data/mechs/` carries designation, role and field
  note per variant and per weapon component, loaded fail-loud at application
  load, with a test that every enum constant has an entry. The Mech Lab's
  synthesised provenance line stays what it is; the sheet is the field note.
- **Meters are measured against the catalog once.** The `StatMeter` record and
  its factory exist twice today, with two copies of the catalog-ceiling scans;
  they become one `CatalogCeilings` in the copy package and both view models
  consume it.
- **One layer, not one popup per card.** The runtime is a single overlay
  element per document, re-filled from the hovered binding, placed beside the
  hovered element on the side with room and clamped to the document. Show and
  hide are class toggles so stylesheet transitions apply. Text height is
  measured, not estimated by character count.
- **Placement uses the standard vocabulary.** If the retained layout can take a
  bounded `position: absolute` with `left`/`top` (law 8), add it; if not, the
  layer is a stack child of the root placed by margins, and the story says
  which was done and why.
- **One drive point per screen family.** `MissionFlowMlxScreen` owns a binder
  and updates it from `onInputProcessed`; the hand-rolled `Screen`
  implementations call the same update from `advance`. A screen that binds
  nothing pays nothing.
- **Headless evidence hovers the real element.** Every adopting screen adds one
  `ui` snapshot with the sheet open, produced by laying out, moving the
  pointer to the subject element's centre, and updating — the route
  `fleet-armory-equipment-tooltip-wide.png` already uses — so the hit test,
  the placement and the copy are all exercised by the picture.

## Slices

1. **Copy.** `SpecSheets` and `CatalogCeilings` in `ui/spec` (mod-domain copy
   may live in `ops/spec` if the generic package must stay catalog-free — the
   agent states the split), the mech catalog JSON and its loader, the stat
   dedupe in both view models. Pure; unit tests per subject kind.
2. **Runtime.** The layer, the binder, placement, measured height, the
   `MissionFlowMlxScreen` hook, the stylesheet, and adoption on
   `PolityDoctrineScreen` (each armour pattern, the lance, each release row)
   with its snapshot. Built against `SpecSheet` alone; uses a hand-written
   sheet in tests and the real factory only at adoption.
3. **Migration.** Fleet Armory (`ArmoryEquipmentTooltips` and the four popup
   divs deleted; the existing tooltip artifact now shows the shared sheet),
   Mech Lab catalog rows, doctrine designer definition tiles, and the squad
   deployment member inspector's equipment lines. Nothing hand-rolled remains.
4. **Fold.** Vocabulary into `ui-nouns.md`; the dossier-overlay paragraph in
   `company-view-nouns.md` redirected to it.

## Acceptance

- Hovering any armour pattern name, the lance, or a release row on the polity
  doctrine panel opens a sheet with that item's note and stats; moving off
  closes it; the sheet never leaves the viewport.
- A weapon sheet at Milspec shows higher damage than the same weapon at
  Surplus, and both agree with `InfantryCombatStats`.
- Every `MechVariant` has a field note, pinned by test.
- `ArmoryEquipmentTooltips` no longer exists and the Fleet Armory tooltip
  snapshot renders through the shared layer.
- The `StatMeter` record exists once.

## Out of scope

- Keyboard-focus opening of a sheet (focus is a retained state already; a
  later story can bind it).
- Sheets for marines, squads, or contracts; those are dossiers, not catalog
  items.
