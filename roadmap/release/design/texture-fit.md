# Texture fit — shipped images sized to how they are drawn

Status: ACTIVE
Written: 2026-10-23

The release and deploy builds ship every image no larger than the largest size
it is ever drawn at. The art under `mod/graphics` stays full resolution as
the source of truth for further work. Only the build output under
`build/optimized-png/` is fitted.

## Why

An image costs a player video memory, not disk. Starsector pads every texture
to the next power of two on each axis, so `army-tank.png` (3072x768, 1.8 MB on
disk) occupies 4096x1024x4 = 16.8 MB once loaded. Across `mod/graphics`
(2026-10-23) the 906 PNGs are 163 MB on disk, 604 MB as RGBA and 1,121 MB
padded. Players running several mods hit that ceiling first. An audit found
almost every image is used, so the only lever left is fitting each one to its
use. WebP was rejected: `loadTexture` cannot read it, and it saves no video
memory.

## The model

**The usage record** (`art-source/texture-usage/usage.json`, checked in) keeps,
for every image path, the largest size the *whole image* reaches on screen,
in screen pixels. Each figure is scaled to the **reference conditions**: a
1920x1080 screen, the closest battle zoom (`BattleCamera.MAX_ZOOM`, 16x) and
the largest UI scale (assumed 200%). A draw is measured where it is made, in
whatever framing that happened to be, and scaled to those conditions. A
whole-map review frame and a close-up in the game therefore measure the same
art the same way. `need = display / source pixels` is the screen-pixels-per-
source-pixel figure the fit reads.

Two domains, because they scale differently (`TextureUsage`):

- **World** draws scale with the camera. The closest a battle gets is 16x the
  cover-fitted cell of *that map*. On a 1920x1080 screen that cell is
  independent of the UI scale, because the battle canvas is fitted to the
  physical screen. Small maps zoom close: a 560x336 Conquest cell is 55 px at
  16x, a 299x84 ship deck's is 206 px. A grid smaller than the smallest real
  battle (`MIN_PLAY_MAP_WIDTH x MIN_PLAY_MAP_HEIGHT`, a ship deck) is measured
  as if it were that size, so a behaviour scene's toy ground cannot ask for
  more than play can show.
- **UI** draws are in retained-document units. At 1920x1080 those are UI units
  at any UI scale, so the reference is the document size x 2.

Every figure is kept **per source label**: `snapshot:<suite>`,
`commander-<mission>`, `game`. A context that draws art larger than play ever
does stays visible and can be ignored in the policy (`ignoreSources`) instead
of quietly deciding the fit. The record only grows: merging keeps the larger
figure. Delete an entry to measure it afresh.

**The fit** (`tools/optimize_pngs.py`, behind `optimizePngs`) takes, per image,
the smallest policy scale at or above `need x margin` (1.15). It never
upscales. The scales are multiples of 1/16 (1, 3/4, 1/2, 3/8, 1/4, 3/16, 1/8),
so a sheet cut on a 16 px grid stays on whole pixels. A larger scale that pads
to the same power-of-two texture costs no memory, so it is taken for free.
Resampling is Lanczos on premultiplied alpha. An image is shipped **whole**,
only losslessly recompressed, when it is:

- **unmeasured**, meaning no run recorded drawing it. The report lists these,
  so a gap shows up instead of silently shrinking art;
- **engine-loaded**, meaning it is named by data the game itself reads
  (`.ship`, `.wpn`, `.csv`, `settings.json`, ...) or sits in a folder the
  engine draws from (portraits, crests, illustrations, icons, ship and weapon
  sprites). Some of those are drawn at native size, so they cannot shrink;
- **never-fit** by policy: font pages, and normal and height maps, which are
  sampled at their albedo's coordinates;
- **sliced at load**, meaning its frames are found from its pixels at runtime
  (`SpriteSheetSlicer`). Slicing a resampled image can move a frame edge.

A fully opaque image the code loads may ship as **JPG** when that saves a fifth
of its PNG. Tilesets and normal or height maps are excluded, where JPG blur
would show. The manifest keeps the `.png` name the code and data use.

## The law: code addresses images in source pixels

Every cut, frame grid, layer layout, tileset document and hand-placed offset
is written in the source image's pixels. The fit must not move any of them.
So the build writes `data/config/marines_texture_fit.json` (`ModTextures.MANIFEST_PATH`)
naming each fitted image's **logical** size (its source size), its shipped
file and its shipped size, and the code reads images only through
`ModTextures`:

- `ModTextures.sprite(path)` hands a fitted sprite out already
  `setSize`-d to its logical size. Every batch computes
  `u = srcPx / sheet.getWidth() * sheet.getTextureWidth()`, and
  `getTextureWidth` still reports the shipped texture's own fraction, so the
  ratio comes out the same.
- `ModTextures.readImage(path)` decodes and resamples back to logical size, so
  a slicer or a size read sees source coordinates.
- `ModTextures.loadTexture` / `file` resolve a re-encoded (JPG) image.
- Anything that **copies texels** works in shipped size: `SpriteAtlas` reads a
  texture back at its real size (`ModTextures.shippedWidth`). `UnitAtlas`
  slots whole images at shipped size, so the atlas shrinks with the art.
  `GroundAtlas`, whose callers add source-pixel cuts to a slot's origin, keeps
  logical slots and resamples into them.

A new texture load that bypasses `ModTextures` breaks the law silently: under
a fitted build it reads its image at the wrong size. In a checkout no manifest
exists and every image is its own source.

## Measuring

`-PtextureUsage` on `createSnapshots`, `commanderEvidence` (with
`-PsnapshotEveryTicks`, so it draws) or `renderEvidence` writes
`build/texture-usage/<label>-<pid>.json`. A dev-mode game
(`-Dmarines.dev=true`, which `runStarsector` passes) writes
`saves/common/marines_texture_usage.data` every 30 s while battles draw.
`gradlew.bat mergeTextureUsage` folds both into the record. `sceneEvidence`
plays without a renderer, so it records nothing. The same scenes draw under
`createSnapshots`.

The hooks sit on the shared draw paths, not on call sites: every `DrawList`
add (live and headless collection alike), `BattleRenderer.collectWorld`
(which frame and map the draws belong to), `CanvasContext.sprite` (every
retained UI image and canvas sprite, with the headless battle drain's own
canvas sprites suppressed, since those were measured when collected) and the
impact-particle draw. GL custom passes that bypass the draw list (engine
plumes, the night pass, the resident meshes) are not hooked. Their textures
are measured where the headless drain draws them through the draw list
instead.

## Proving a fit

`-PtextureFitOverlay` draws headless frames from `build/optimized-png`,
resampled to logical size the way `setSize` plus bilinear sampling would
(`FittedTextureOverlay`). Running a suite twice with `-PsnapshotRawFrames`,
once plain and once overlaid, and comparing with `RawFrameDump` measures the
fit and nothing else.

## Reading the report

`build/reports/texture-fit/report.md` gives before and after on disk, as RGBA
and padded to powers of two. It lists the largest savings by decoded memory
with the source label that decided each one, then every unmeasured image by
size and what was kept and why.
