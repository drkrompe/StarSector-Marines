# Render evidence: `shaderEvidence` and `renderEvidence`

Status: ACTIVE

Written: 2026-10-24

The two opt-in tasks that run real OpenGL: what each proves, how to read it,
and how to profile a frame with Nsight. Both need an accelerated driver and
report that they skipped where no context can be made. They use LWJGL 2 from
the game's own install (`HeadlessGl`), so they run the mod's render classes
rather than a re-implementation of them.

## `shaderEvidence`

Compiles and links every shader the mod defines, checks that every uniform the
ground composite uploads exists in the linked program, and runs the state
brackets against the state the game hands a UI hook. It is the only evidence
that runs GL rather than modelling it: the snapshot suites draw through Java2D
and the shader oracles re-implement the arithmetic on the CPU, which proves
geometry and is blind to a syntax error, a construct one driver rejects, or a
renamed uniform (`glUniform*` on an unknown location silently does nothing).

**A clean context proves nothing about the brackets.** A fresh context starts at
GL defaults, which are very nearly what `GlStateBracket.applyTextured2DState`
sets, so dropping any normalisation leaves a clean-context test green while the
game renders wrong. `GlStateBracketHostileStateEvidence` sets the documented
incoming state (alpha masked out of the colour mask, a foreign blend function)
and asserts both that our draw lands correctly and that the caller's state comes
back untouched, with an unbracketed control so it cannot pass by measuring
nothing. Scissor is inherited on purpose by the tile brackets (they draw into
the game's own clipped target) and disabled by the FBO brackets (that target is
ours). A fixture can only encode the hostile values we know about; the rest
wants a live pass.

## `renderEvidence`

What a frame costs, per layer, through the shipping `BattleRenderer` (the real
collect and drain) into a `HeadlessGl` context at three framings: a compound,
a lane and the whole map. Per layer: commands, quads, sprites, custom passes,
draw calls, texture binds, and the collect and drain milliseconds, plus a
whole-frame wall clock with the GPU waited on. It writes
`build/reports/render/summary.md` and `summary.json`, and each framing's GL frame
as a PNG under `frames/` to diff a lever against its control.

The maps: the canonical 560x336 Conquest, a 280x168 control, colonies with
creep and fire, a body pile, raised ground, The Burn by day and night, and the
stress scene. `-PrenderMaps=<words>` profiles only the maps whose ids contain
them (and skips the creep look evidence that shares the tag);
`-PrenderMaxTicks`, `-PrenderFrames`, `-PrenderDir`, `-PrenderBurnSeconds` and
`-PrenderPileBodies` (prices `BodyPileCap`) adjust it.

### The fast path: `-PrenderMaps=stress`

About 45 seconds, against twenty minutes for the whole matrix, which mostly
plays battles before drawing. `RenderStressScene` stands a dense battle up
with no playout: the verdant 400x240 colony, bores with their spore colonies,
matured creep, a mixed crowd of every body kind with a third of it dead, a few
settle ticks for shots in flight, and a fire aged on its own field to The
Burn's shape (crowns and walls hot enough to throw embers, burnt and smoking
cells). It is a render-cost fixture only, with no balance meaning.

### Reading it

- **Counts repeat; times are measured.** The task asserts the same command
  count every frame at a framing, and the same counts when Conquest or the
  stress scene is stood up a second time. That needs the serial scheduler,
  which the task forces and the harness asserts.
- **Compare a lever against its own run's control**
  (`-Dbattle.render.doodadAtlas=false`, `-Dbattle.render.creep=false`,
  `-Dbattle.render.cliffLook=false` with `-PrenderMaps=overlook`,
  `-Dbattle.render.settledBodyMesh=false`,
  `-Dbattle.render.collectCulling=false`, `-Dbattle.render.pagedBatch=false`),
  never against a number in a document, and never against a delta under the
  run-to-run spread (about 0.06 ms per layer). Times are medians of nine frames
  after thirty warm-up frames; fewer warm-up frames measure the JIT.
- **A lever that can drop something is accepted on pixels.** A wrongly culled
  body is silent, so each framing is read back culled and unculled and asserted
  identical, and a marine is walked out through the viewport edge in half-cell
  steps to put a straddling body there. The readbacks run after every timed
  frame on a map, never between them.
- **The world is still between frames**, so a resident layer's patch cost is
  charged once and every later frame reads as steady state. Expect delta-patched
  layers to cost more in a live battle.
- **Our share is not the frame.** Per-layer times are CPU: building and handing
  over the stream. The whole-frame column is there because a lever can trade
  our cost for the driver's (a resident mesh draws the whole map however close
  the camera is), which every other column would call a win.
- The summary opens with **Atlases** (each atlas's size and fill, and every
  image it left out and why) and closes with **Where the runs break** (per
  layer, quads by sheet, what each flush was, and whole sprites by image).
  Read those before guessing why a layer has more binds than it should.

## Profiling a frame with Nsight Systems

Launch the JVM, not Gradle. Every `renderEvidence` run writes
`build/nsys/render-evidence.cmd`, which replays that run (classpath, heap,
properties and map selection) as one JVM through `RenderEvidenceMain`. Gradle
under nsys injects into every JVM it forks, and its toolchain probe hung there.
After one `renderEvidence -PrenderMaps=stress`, from the worktree:

```powershell
& 'C:\Program Files\NVIDIA Corporation\Nsight Systems 2026.4.1\target-windows-x64\nsys.exe' profile --trace=opengl-annotations --force-overwrite=true -o "$PWD\build\nsys\stress" "$PWD\build\nsys\render-evidence.cmd"
```

Both paths must be absolute: nsys starts the target outside the current
folder. Extra `-D` arguments to the script go to the JVM. Each frame is a
`KHR_debug` group named `<map> / <framing> / frame N` (`GlFrameMarkers`).
nsys 2026.4 records the push and pop calls with their labels on the OpenGL API
row but builds no KHR range rows from them, so read a frame as push to pop.
Traced frames run several times slower; take times from the report, not the
capture. Verified 2026-10-24: the capture holds every GL call and draw, and
GPU workload ranges.
