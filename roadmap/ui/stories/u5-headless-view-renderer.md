# U5 — headless retained-view renderer

Status: IN PROGRESS
Written: 2026-08-24

Read `ui-nouns.md` and `ui-toolkit.md` first. Depends on U1–U4.

## Outcome

An authored retained surface can be laid out and rasterized as deterministic PNG
evidence without launching Starsector. The tool exercises the production document,
styles, bitmap-font metrics, clipping rules, and canvas recipes rather than a
screenshot-specific reconstruction.

## Scope

- Separate the retained paint traversal from Starsector's fixed-function target.
- Add a Java2D target that reads shipped BMFont manifests, atlases, and sprite paths.
- Keep canvas producers backend-neutral while allowing the live target to consume a
  `SpriteAPI` handle and the headless target to consume the same asset path.
- Provide a generic Gradle preview task and Fleet Armory overview/workspace fixtures.
- Preserve the focused `renderArmoryPreviews` equipment-composition task.

## Acceptance

- `UiDocument` renders to both live Starsector and headless targets through one paint
  traversal.
- Headless layout uses the exact glyph advances and line heights that live layout uses.
- Boxes, borders, text, clipping, scrolling chrome, and procedural canvases appear in
  deterministic images without a game process or OpenGL context.
- Fleet Armory's authored overview and full workspace render through the generic task.
- Existing retained UI and focused Armory preview tests remain green.
