# U4 — `.mlx` components, bindings, and reload

Status: IN PROGRESS — first workbench conversion ready for live acceptance
Written: 2026-08-23
Updated: 2026-08-23

Read `ui-nouns.md` and `ui-toolkit.md` first. Depends on U1–U3.

## Outcome

Durable Marine Ops surfaces are authored as MoonLight-style single-file components:
markup and scoped styles live together, typed Java view models provide state and
handlers, and development reload replaces presentation without replacing domain or
view-model state.

## Scope

- `.mlx` files containing one `<template>` and optional `<style>`.
- A closed HTML-shaped element and attribute subset with load-time refusal of unknown
  nouns.
- Whole-value dotted-path expressions, declared props, event-handler references, and
  repeated keyed children.
- Signals, derived values, bindings, binding scopes, and keyed reconciliation copied
  or adapted from MoonLightEngine where Java 17 and package boundaries permit.
- Component style scoping, theme-last cascade order, and explicit reload initiated by
  a dev action rather than a polling watcher.
- Loader parity tests proving markup builds the same tree as the Java API.

## Acceptance

- A mistyped prop, unknown element/property, missing key, or invalid expression fails
  with file/line/column context before a surface opens.
- One signal write updates only bindings that read it.
- Keyed rows preserve hover, focus, scroll, and transition identity across reorder.
- Reloading a component replaces its tree and scoped rules while its view model and
  domain state survive.
- Everything expressible in markup remains expressible through the Java API.

## Implemented slice

- The MoonLight reactive graph is adapted to Java 17: mutable and computed signals,
  precise dependency recollection, binding scopes, cycle refusal, and keyed child
  reconciliation all have headless coverage.
- A positioned, strict `.mlx` parser accepts one template, optional scoped style,
  declared props, whole-value dotted expressions, handler references, and keyed
  repetition over the retained `div` / `button` / `canvas` subset.
- The builder emits ordinary `UiElement` instances and ordinary component sheets.
  It has no private layout, paint, input, or cascade path.
- The retained UI workbench hierarchy and component CSS now live in
  `data/ui/components/dev/ui-workbench.mlx`. Java retains only host navigation,
  the procedural canvas producer/input, `stack` and exceptional alignment wiring
  that the current CSS subset cannot express.
- `Reload UI` explicitly reparses the known component set and constructs a fresh
  document from the same screen/view-model state. A parse or build refusal keeps
  the previous document installed.

## Live acceptance requested

- Open the retained UI workbench and confirm its geometry, typography, theme
  switching, keyboard actions, scrolling, and canvas drag match the previous proof.
- Change team/template/theme selection, press `Reload UI`, and confirm the selection,
  active theme, and canvas issue marker survive while the authored tree is replaced.
