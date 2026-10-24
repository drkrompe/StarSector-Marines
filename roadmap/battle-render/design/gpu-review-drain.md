# GPU review drain

Status: ACTIVE

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

**Both columns ran while another session's `createSnapshots` sweep shared the
machine**, so absolute seconds are inflated, the Java2D drain's (which wants
every core) most. The ratio is too large for contention to explain, but
re-time on a quiet machine before quoting the seconds. The same goes for
suites whose time is not drawing at all: with collection kept and the raster
skipped entirely, `rural-mapgen` does not get faster (its time is map
generation) and `tank` goes from 7.1s to 4.7s. Profile a suite before
expecting the GPU to help it.

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

**Writing artifacts is no longer ImageIO** (2026-10-23). Once frames were drawn on the GPU, a
suite's writing cost as much as drawing it. Each GIF was one thread through ImageIO: a median-cut
palette, then the JDK's LZW, frame by frame. Each PNG still was read one sample at a time through
`Raster.getPixels`. Both encoders write ImageIO's exact bytes, so every artifact is byte-identical
to before:

- `AnimatedGifWriter.write` keys, quantises and compresses a recording's frames side by side on
  `SnapshotWorkers.encode()`, and only concatenates them in order. A frame's patch depends only on
  its own pixels and its predecessor's.
- The palette adds a run of one colour in one step, into an interleaved per-bin array. It
  measures each box's channels in one pass.
- The LZW dictionary is a 48 KB hash table that stays in cache. The old table was 4 MB and missed
  on nearly every lookup.
- `PngStillWriter` writes a still's unfiltered rows straight from the frame's bytes, which is all
  ImageIO does for an RGB image.

Read the effect off JFR shares, not wall clocks. On a shared machine an execution sample counts
time in Java, scheduled or not. On colony-threat plus fire-line, write-path samples went from
about 1,650 to 550 between consecutive runs. The palette went from 474 to 54, the PNG stills from
309 to 92 and LZW from 336 to 120. What is left is spread over per-pixel passes every frame needs:
LZW, BGR to ARGB, the changed-box scan and the index fill. A lower PNG deflate level was slower
than ImageIO's level 4.

## Which GL: LWJGL 2 on Windows, LWJGL 3 on the Mac

**Windows: LWJGL 2, from the game's install, through `HeadlessGl`.** It is
already the context `shaderEvidence` and `renderEvidence` draw the shipping
renderer into, it runs the mod's own classes rather than a copy, and it costs
no dependency. A pbuffer on the workstation's RTX 3080 reads a 1920x1080
frame back in about 1.2 ms.

**The Mac cannot use LWJGL 2.** The game's native
(`Contents/Resources/Java/native/macosx/liblwjgl.jnilib`) is x86_64 only, and
the JDK the project builds with there is arm64; the game itself runs on its
own x86_64 Zulu 17 under Rosetta. Run under that JRE instead, LWJGL 2 dies in
`Display`'s static initializer (`MacOSXDisplay.nGetCurrentDisplayMode`) before
it tries a pbuffer when there is no window-server session, and even where it
worked it would put the whole simulation under Rosetta, the part of a run the
GPU does not speed up.

**The Mac uses LWJGL 3, through a windowless CGL context.** Probed over SSH on
2026-10-23 (MacBook Pro, Apple M5 Max, macOS 26.6, arm64 JDK): CGL gives an
accelerated legacy 2.1 context ("2.1 Metal") with no window and no
window-server session, fixed-function drawing works, `#version 120` shaders
compile (every shader here is), framebuffer objects come from
`ARB_framebuffer_object`, and a 1920x1080 readback costs 1.85 ms. That legacy
profile on Metal is what the game itself gets there.

The mod is compiled against LWJGL 2, and the two cannot share a classpath, so
`createSnapshots` on a Mac (or anywhere with `-PsnapshotGl=lwjgl3`) runs on a
tool classpath that swaps the game's `lwjgl.jar` for LWJGL 3. That works
because LWJGL 3 kept nearly every name and descriptor: of the 123 distinct
LWJGL members the mod and the drain reference, 122 link. The rest is bridged
on the tool classpath only, never in shipped code:

| LWJGL 2 member | Who calls it | Bridge |
|---|---|---|
| `glVertexPointer` / `glTexCoordPointer` / `glColorPointer(size, stride, FloatBuffer)` | `QuadBatch`, `LineBatch`, `SolidQuadBatch`, every flush | added to a patched copy of LWJGL 3's `GL11` (`gl3patch`, `PatchLwjgl3Gl11`), each calling the typed form |
| `glGetInteger(int, IntBuffer)`, `glTexParameter(int, int, FloatBuffer)` | `BridgeRenderer`; the headless sprite tokens | the same patch, onto `glGetIntegerv` / `glTexParameterfv` |
| `Display`, `Keyboard`, `Mouse` | `GroundParallaxPipeline`, `NightLightingShader`; battle UI | stub classes in the `gl3` source set: no display, no input |
| `GL20.glUniformMatrix4` | planet and cube drawables | none: never on the battle path, and resolved only if called |

Changing the batches to the `ByteBuffer` form both libraries share was the
other option, and was rejected: `ClientArray` documents that the game's GL
bridge mods intercept exactly these client-array calls, so the shipped form
stays as it is. The surface, `Lwjgl3Surface` behind the `GlSurface` seam,
makes a CGL context on macOS and a hidden GLFW window elsewhere, and draws
into an FBO it leaves bound: CGL has no default framebuffer, and the battle
renderer restores whatever binding it found before each of its own passes.

**It runs.** `createSnapshots -Psnapshot=colony-threat` on the Mac, over SSH,
drew its 330 frames on the M5 Max at 8.6 ms each and rendered the suite in
10.2s; the same frame drawn on Windows through LWJGL 2 differs by 0.2/255 per
pixel on average, most of it the caption's font. On Windows,
`-PsnapshotGl=lwjgl3` runs the same classpath through a hidden GLFW window and
draws the same picture, which is how the bridge is checked without the Mac.

**Setting the Mac up.** The repository's `gradle.properties` names the Windows
install, and a Mac build needs `starsectorDir` to be a folder whose
`starsector-core` is the app's `Contents/Resources/Java` (where the game jars
are): make one with a link, and pass it with `-PstarsectorDir=<folder>` or put
it in `~/.gradle/gradle.properties`, which takes precedence over the
project's. The Mac's checkout also needs `gradlew` run as `sh ./gradlew`,
since the bit that makes it executable is not committed.

## Design

**The seam is one interface.** `BattleReviewFrameRenderer` owns the camera fit,
the layer selection and everything painted over the world (markers, wrecks,
ordnance, annotations, caption, the review feed's particles). The world itself
comes from a `ReviewSceneBackend`: a scene (simulation, camera, layers, clock,
feed) in, a top-row-first `BufferedImage` out. Two implementations:

- `Java2dReviewBackend` — the existing drain, unchanged: collect, then replay
  through `HeadlessBattleSceneRenderer` in fixed bands. The code's default (so
  unit tests and the workbench never open a context), and the deterministic
  reference.
- `GlReviewDrain` — the shipping `BattleRenderer.renderWorld` into a
  `HeadlessGl` pbuffer, read back with one packed BGRA `glReadPixels`.

A run chooses with `-Dsnapshot.backend=java2d|gl` (Gradle already forwards
`snapshot.*`). **`createSnapshots` defaults to `gl`**; `gl` on a machine with
no context prints one line and uses Java2D, so the same command works on both
machines. A third backend (LWJGL 3
for the Mac) is a third class behind the same interface, plus its classpath.

**Every suite that draws through the review renderer moves at once**: the 50
of 89 suites that account for 83% of the catalog's time, including every
behaviour scene and every commander-evidence GIF. Nothing is migrated suite by
suite, because a suite never names its drain. The 39 other suites (deck props,
elevation looks, armory sheets, tileset previews) have their own renderers and
are out of scope; the largest two, `elevation-look` and `deck-props`, were 9%
of the run between them and are now opt-in (`SnapshotSuite.inAll`): `all`
leaves them out and `-Psnapshot=<id>` runs them, since they answer only a look
question about their own art.

**One thread owns the context.** A GL context is current on one thread, and a
suite may render from any thread, so the drain runs every frame on its own
daemon thread and the caller waits. The simulation does not move while it
waits. The CPU the Java2D drain used to fill with bands is free for the
simulation of the next frame, the GIF encoders, and other sessions.

**One renderer per battle.** A `BattleRenderer` bakes resident ground, roofs,
fog and a decoration atlas for the map it drew last. The drain keeps up to
sixteen renderers keyed by simulation, least recently drawn first, and
releases the oldest the way `BattleScreen.detach` does. The cap has to sit
above the most battles a suite draws in turn: `aircraft-crash` draws six side
by side every frame, and at a cap of four it evicted and rebuilt atlases every
view (233s against Java2D's 36s).

**What a GL frame gains** over the Java2D table in `snapshot-suites.md` ("What a Java2D review
frame draws"): every GL custom pass drawn by the code that ships rather than a
CPU model of it (creep, scorch, night lighting, shield domes, engine plumes,
hull lights, roofs and the fort's building art), and the sprite and ribbon
batches as the driver draws them. The drain draws with `OBSERVER_GL`, the
observer's profile with the surface-relief composite and resident buffers on,
so a frame also has the composite's sun shadows, relief and normal-mapped
lighting, which Java2D never draws. `ReviewImpactFx` keeps a
`GroundLightService` fed tick by tick as `BattleScreen.advance` feeds the
renderer's (muzzles, impacts, blasts, bolts, fires, ordnance), and the drain
points the composite at it, so shots light the ground at night.
`-Dsnapshot.gl.relief=false` draws the plain `OBSERVER_SCENE` instead: the
control for what the composite costs and changes.

**Where it does not win.** A frame's GPU cost is mostly fixed, and Java2D's
scales with pixels and with the custom passes it imitates. On a creep- or
fire-heavy 1920x1080 frame GL is tens of times cheaper; on a small, plain view
it is about even. `aircraft-crash` (six 600x414 views a frame) draws at about
22 ms a frame on either backend once its renderers are kept, and the relief
composite is most of the GL share there (25 ms a frame with it, 4 without).
Starting the context and the sprite set costs under 2s once per run. It was
about 15s, nearly all of it decoding all 1,478 catalog images to read their
sizes; the catalog now reads sizes from the PNG header (or the texture-fit
manifest) and decodes only the images a run draws, which helps the Java2D
drain as much.

**Direct buffers must outlive the call.** The first broad GL run died with an
access violation inside the driver, in `SpriteAtlas.build`'s
`glTexImage2D`. LWJGL 2 passes the driver a direct buffer's address and keeps
no reference to the buffer, so once the caller is compiled the collector may
free a freshly allocated buffer while the driver is still reading it; the
review's heap churn made that likely. Every one-shot upload in the renderer
(the atlas clear and apron copies, cliff art, creep and scorch noise, creep
surface, fog, relief colours) now holds its buffer with
`Reference.reachabilityFence` across the call. The game runs the same code on
the same library, so this is a latent crash there too, only rarer.

**What it gives up.** Pixels are a function of the driver as well as the
battle, so the two machines may differ in the last bit, and the Java2D drain's
band-for-band reproducibility does not carry over. That is acceptable because
raw-frame comparisons (`-PsnapshotRawFrames`, `RawFrameDump`) are opt-in A/B
runs on one machine, never tests. A comparison across a renderer change is
still made with both arms on the same backend on the same machine.

**Opt-in stays opt-in.** `createSnapshots` is already an opt-in evidence task.
The backend switch adds no test and no task; `shaderEvidence` and
`renderEvidence` keep their own contexts.

### Not done

- The 39 suites with their own renderers (deck props, elevation looks, armory
  sheets) still draw with Java2D; `elevation-look` and `deck-props`, the only
  large ones, are opt-in rather than ported.
- `commanderEvidence` and the other `Test`-task evidence keep the Java2D
  default; they can pass `-Dsnapshot.backend=gl` when a run wants it.
