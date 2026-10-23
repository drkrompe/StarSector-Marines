# GPU review drain

Status: DRAFT

Written: 2026-10-23

Draw the snapshot catalog's battle review frames on the GPU, through the
shipping `BattleRenderer`, instead of rasterizing them with Java2D. The Java2D
drain stays as the fallback and the deterministic reference.

## Where the time goes today

The whole catalog on 2026-10-23 (89 suites, Windows workstation): 2,892s
rendering and 446s writing, 55.6 minutes. **The 50 suites that draw through
`BattleReviewFrameRenderer` are 2,356s of the rendering and 424s of the
writing: 83% of the run.** Every behaviour scene is one of them.

Inside those suites the time is the Java2D drain rather than the simulation
or the encoders. Three representative suites, each run alone with
`-Dsnapshot.profile=true`:

| Suite | Frames | Suite render | Drawing (ms/frame × frames) | Left for the simulation | Writing |
|---|--:|--:|--:|--:|--:|
| `colony-threat` | 330 | 327s | 914 ms → 302s (92%) | ~25s | 15s |
| `fire-line` | 328 | 238s | 678 ms → 222s (93%) | ~16s | 8s |
| `black-sluice` | 858 | 195s | 134 ms → 115s (59%) | ~80s | 54s |

Where a frame's drain goes is the GL-only custom passes, which Java2D
re-implements on the CPU: in `colony-threat` the creep pass is 13.9 s of
worker time per frame across its bands, in `fire-line` the scorch pass 6.7 s
and the creep 3.5 s. `black-sluice` has no creep; its frames are sheet quads
(70,000 floor tiles a frame) and its simulation is a larger share.

**The same suites through the GPU backend**, same machine, same commit:

| Suite | Suite render, Java2D | Suite render, GL | Frame, Java2D | Frame, GL (collect+drain / readback) | Writing, Java2D → GL |
|---|--:|--:|--:|--:|--:|
| `colony-threat` | 327s | **33s** | 914 ms | 17.8 ms (5.7 / 7.5) | 15s → 10s |
| `fire-line` | 238s | **28s** | 678 ms | 14.9 ms (8.1 / 5.4) | 8s → 13s |
| `black-sluice` | 195s | **133s** | 134 ms | 64.9 ms (58.4 / 4.8) | 54s → 47s |

The two creep-heavy suites are ten times faster and are now bound by their
simulation. `black-sluice` was already half simulation, and its GL frame is
still 58 ms, most of it collection (28 ms a frame under Java2D as well), which
is CPU work the backend does not change. Writing (GIF palette and LZW) is
the same work on either backend. A suite of a few stills each on a different
battle (`fire-buildings`: 5 frames, 8s → 37s) pays the context and per-battle
atlas cost once per battle and gets slower; that cost is fixed, so it only
matters for tiny suites.

Projected over the catalog, not measured: if drawing is about 80% of the
review suites' 2,356s and falls to a tenth, they render in about 660s; with
the other suites' 536s and 446s of writing unchanged, the 55 minutes become
roughly 27. What is left is simulation, GIF encoding and the 39 suites that do
not use this renderer.

## Which GL: LWJGL 2 now, LWJGL 3 only for the Mac

**Windows: LWJGL 2, from the game's install, through `HeadlessGl`.** It is
already the context `shaderEvidence` and `renderEvidence` draw the shipping
renderer into, it runs the mod's own classes rather than a copy, and it costs
no dependency. A pbuffer on the workstation's RTX 3080 reads a 1920x1080
frame back in about 1.2 ms. Nothing here needs LWJGL 3.

**The Mac cannot use that context as things stand**, measured over SSH on
2026-10-23 (MacBook Pro, arm64, macOS 26.6):

- The game's LWJGL 2 native (`Contents/Resources/Java/native/macosx/liblwjgl.jnilib`)
  is **x86_64 only**. The JDK the project builds with there is arm64
  (Temurin 26), and an arm64 JVM cannot load an x86_64 library. The game runs
  on its own bundled x86_64 Zulu 17 under Rosetta.
- `HeadlessGl` also looks for natives under `starsector-core/native/`, which is
  not where the Mac app keeps them, and Java 17+ looks for `liblwjgl.dylib`
  where the file is named `.jnilib`. Both are fixable in a line each.
- Run under the game's own x86_64 JRE with those fixed, LWJGL 2 segfaults in
  `Display`'s static initializer (`MacOSXDisplay.nGetCurrentDisplayMode`,
  in `CFStringCompare`) before a pbuffer is attempted. An SSH shell has no
  window-server session, so this is expected there and says nothing about a
  Terminal on the Mac's own desktop. **That case is untested**; the probe is
  one command (below) for the owner to run in Terminal.
- Even if it works, this route runs the whole simulation under Rosetta, and
  the simulation is the part of a run the GPU does not speed up.

**LWJGL 3 is the Mac route if the Mac needs one.** It ships arm64 macOS
natives, and CGL can make an offscreen context with no window (it should not
need the window-server session `Display` does; unverified). The obstacle is
the one the brief names: the mod is compiled against LWJGL 2, and the two
cannot share a classpath. Measured, it is smaller than it sounds. Of the 124
distinct LWJGL members the compiled mod references, 105 link unchanged
against LWJGL 3.4.2 (`GL11`'s fixed-function calls, `GL13`–`GL30`, shader and
FBO entry points: LWJGL 3 kept the names and descriptors). What does not:

| Reference | Where | On the battle path | Fix |
|---|---|---|---|
| `glVertexPointer` / `glTexCoordPointer` / `glColorPointer(size, stride, FloatBuffer)` | `QuadBatch`, `LineBatch`, `SolidQuadBatch` | yes | the explicit-type `ByteBuffer` overload, which both libraries have |
| `glGetInteger(int, IntBuffer)` | `BridgeRenderer` | no | `glGetInteger(int)` (both have it), or leave it |
| `GL20.glUniformMatrix4` | planet and cube drawables | no | leave it |
| `Display.getWidth/getHeight/isCreated/isActive` | `GroundParallaxPipeline`, `NightLightingShader`, hosts | yes (two) | a stub class on the tool classpath answering "no display" |
| `Keyboard`, `Mouse` | battle UI | no | stubs on the tool classpath |

`lwjgl_util.jar` (`Vector2f`) is plain Java and can stay. So the LWJGL 3
backend is: a separate tool classpath that swaps the game's `lwjgl.jar` for
LWJGL 3 plus a three-class stub jar, three batch classes moved to the
overload both libraries share, and a CGL context class beside `HeadlessGl`.
The shaders are all `#version 120`, which the legacy 2.1 context macOS gives
compiles. Apple's GL is a translation layer over Metal and deprecated; it is
what the game itself runs on there, so it is no worse a target than the game.

The probe for the Mac's desktop session (it needs a `GlProbe.class` built
against the game's `lwjgl.jar`, and a `native/liblwjgl.dylib` link to the
game's `.jnilib`):

```bash
/Applications/Starsector.app/Contents/Home/bin/java -Dorg.lwjgl.librarypath=$PWD/native -cp .:/Applications/Starsector.app/Contents/Resources/Java/lwjgl.jar GlProbe pbuffer
```

## Design

**The seam is one interface.** `BattleReviewFrameRenderer` owns the camera fit,
the layer selection and everything painted over the world (markers, wrecks,
ordnance, annotations, caption, the review feed's particles). The world itself
comes from a `ReviewSceneBackend`: a scene (simulation, camera, layers, clock,
feed) in, a top-row-first `BufferedImage` out. Two implementations:

- `Java2dReviewBackend` — the existing drain, unchanged: collect, then replay
  through `HeadlessBattleSceneRenderer` in fixed bands. The default, and the
  deterministic reference.
- `GlReviewDrain` — the shipping `BattleRenderer.renderWorld` into a
  `HeadlessGl` pbuffer, read back with one packed BGRA `glReadPixels`.

A run chooses with `-Dsnapshot.backend=java2d|gl` (Gradle already forwards
`snapshot.*`). `gl` on a machine with no context prints one line and uses
Java2D, so the same command works on both machines. A third backend (LWJGL 3
for the Mac) is a third class behind the same interface, plus its classpath.

**Every suite that draws through the review renderer moves at once**: the 50
of 89 suites that account for 83% of the catalog's time, including every
behaviour scene and every commander-evidence GIF. Nothing is migrated suite by
suite, because a suite never names its drain. The 39 other suites (deck props,
elevation looks, armory sheets, tileset previews) have their own renderers and
are out of scope; the largest two, `elevation-look` and `deck-props`, are 9%
of the run between them.

**One thread owns the context.** A GL context is current on one thread, and a
suite may render from any thread, so the drain runs every frame on its own
daemon thread and the caller waits. The simulation does not move while it
waits. The CPU the Java2D drain used to fill with bands is free for the
simulation of the next frame, the GIF encoders, and other sessions.

**One renderer per battle.** A `BattleRenderer` bakes resident ground, roofs,
fog and a decoration atlas for the map it drew last. The drain keeps a small
least-recently-drawn set of renderers keyed by simulation and releases the
oldest the way `BattleScreen.detach` does, so a suite that alternates between
battles (matchup recordings, close and wide views) does not rebuild them every
frame.

**What a GL frame gains** over the Java2D table in CLAUDE.md ("What a review
frame draws"): every GL custom pass drawn by the code that ships rather than a
CPU model of it (creep, scorch, night lighting, shield domes, engine plumes,
hull lights, roofs and the fort's building art), and the sprite and ribbon
batches as the driver draws them. With the profile switched to one with
surface relief (a follow-up below), also terrain relief, sun shading and
normal-mapped lighting, which Java2D never draws. Muzzle and impact lights
need the review feed to drive `GroundLightService` as `BattleScreen.advance`
does; that is a feed change, not a drain change, and the same for both.

**What it gives up.** Pixels are a function of the driver as well as the
battle, so the two machines may differ in the last bit, and the Java2D drain's
band-for-band reproducibility does not carry over. That is acceptable because
raw-frame comparisons (`-PsnapshotRawFrames`, `RawFrameDump`) are opt-in A/B
runs on one machine, never tests. A comparison across a renderer change is
still made with both arms on the same backend on the same machine.

**Opt-in stays opt-in.** `createSnapshots` is already an opt-in evidence task.
The backend switch adds no test and no task; `shaderEvidence` and
`renderEvidence` keep their own contexts.

### Follow-ups, in order

1. Make `gl` the default on a machine where the context comes up, once the
   owner has looked at a few suites drawn both ways.
2. A review host profile with surface relief on (`OBSERVER_SCENE` turns it
   off), so a GL frame shows the terrain the player sees.
3. Feed `GroundLightService` from `ReviewImpactFx`, so night battles' shots
   light the ground.
4. The Mac: run `tools/gl-probe/gl-probe-mac.sh` in Terminal on its desktop.
   If LWJGL 2 comes up under Rosetta, point `HeadlessGl` at the Mac layout and
   decide whether a Rosetta simulation is worth it (probably not). Otherwise,
   or anyway, the LWJGL 3 backend above.
