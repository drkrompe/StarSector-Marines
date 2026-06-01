# Perf — QuadBatch/SolidQuadBatch flush → client-side vertex arrays

> **Status: ✅ SHIPPED & VERIFIED.** In-game render correct; re-profiled win
> confirmed. The deferred `QuadBatch.flush` perf spike from `next-session.md` /
> backlog § Performance — resolved.

## Result (measured)

Before/after JFR (both 2026-06-01, ~400-unit battle), normalized to share of
all wall-clock samples:

| Leaf | Before | After | Δ |
|------|-------:|------:|--:|
| `QuadBatch.flush` | 529 (12.8%) | 177 (3.8%) | −67% |
| `SolidQuadBatch.flush` | 193 (4.7%) | 1 (0.02%) | −99.5% |
| **Combined flush** | **722 (17.4%)** | **178 (3.8%)** | **−75%** |
| `append` (packing) | <24 | 22 | flat |

Flush went from the single largest CPU sink in the mod to a 3.8% afterthought.
`SolidQuadBatch` (small batches: fog, fills, HP bars) was erased entirely.
`QuadBatch`'s residual 177 is the GROUND batch's ~5 MB/frame `data[]→FloatBuffer`
memcpy + the `glDrawArrays` driver submission — exactly what the ground-FBO
follow-up ([`../stories/perf-ground-fbo-cache.md`](../stories/perf-ground-fbo-cache.md))
would eliminate (deletes GROUND's ~38k quads). The render path dropped from 37%
→ 18% of our CPU; the new ceiling is the sim LOS/pathfinding path (`fastutil`
map + `TacticalScoring`/`hasLineOfSight`), the backlog's separate sim-refactor
target.

## Spike: confirm the lever

Three JFR captures (2026-05-21, -05-29, -06-01) agree: the per-vertex
immediate-mode `flush` loop is the single largest CPU sink in the mod.

From the **2026-06-01** post-Final capture (4148 samples, 2490 our-package):

| Leaf | Samples | % our CPU | % render CPU |
|------|--------:|----:|----:|
| `QuadBatch.flush` | 529 | 21.2% | — |
| `SolidQuadBatch.flush` | 193 | 7.8% | — |
| **Combined flush** | **722** | **29.0%** | **77.9%** |
| `QuadBatch.append` (packing) | <24 | <1% | — |

The decisive finding: **`append` (all the float/UV math) is negligible**, so
the cost is unambiguously the per-vertex submission — `glColor4f` +
`glTexCoord2f` + `glVertex2f` × 4 verts = **12 LWJGL JNI crossings per quad**,
~480k native calls/frame at ~40k quads. Not float-packing, not the GPU (which
never appears in the profile — these are fill-trivial ortho quads), not
flush-count thrash (the drain already coalesces consecutive same-sheet quads
into large flushes; per-vertex cost is paid regardless of flush count).

## Option analysis (why client arrays, not VBO)

The bottleneck is **submission call-count**, so the fix is collapsing per-quad
calls into per-flush calls. Options considered:

- **A — client-side vertex arrays + `glDrawArrays`** *(chosen)*: `12N` calls →
  fixed ~handful per flush. No buffer object, no streaming sync-stalls, no
  `GL_ARRAY_BUFFER` binding to restore. Captures essentially the entire
  submission win since the GPU isn't the bottleneck.
- **B — streaming VBO**: GPU-resident data, but the GPU-fetch win is marginal
  for fill-trivial quads, and it adds buffer lifecycle + the classic
  `glBufferSubData`-into-in-flight-buffer sync-stall footgun + binding restore.
  Not worth it *for this workload*. ("No VBO" now holds for a measured reason,
  not a reflex — its advantage is on an axis we aren't bottlenecked on.)
- **C — VBO + VAO + shader**: overkill; no extra win over B (we need no
  programmable transform for 2D ortho quads) and risks polluting Starsector's
  fixed-function UI frame with a bound program.
- **D — display lists**: dead end; geometry changes every frame (camera pan).

The bigger lever is orthogonal — **reduce quad count** (frustum-cull emitted
tiles; FBO-cache the static ground). Split out as a follow-up spike:
[`perf-ground-fbo-cache.md`](perf-ground-fbo-cache.md).

## What landed

- `QuadBatch.flush` and `SolidQuadBatch.flush` rewritten: bulk-copy the
  interleaved `float[]` into a cached **direct** `FloatBuffer`
  (`BufferUtils.createFloatBuffer`), bind strided
  `glVertexPointer`/`glTexCoordPointer`/`glColorPointer`, one
  `glDrawArrays(GL_QUADS, 0, verts)`.
- `append()` is **byte-identical** — packing stays on a primitive `float[]`
  (JIT-friendly, already <1% CPU); the one added cost is a per-flush bulk
  memcpy (~5 MB/frame total, microseconds).
- Client-array hygiene: each flush brackets its enables with
  `glPushClientAttrib(GL_CLIENT_VERTEX_ARRAY_BIT)` / `glPopClientAttrib`, so no
  enable or pointer binding leaks into Starsector's polluted UI GL state. The
  server-side `GlStateBracket` is unchanged (it already saves `GL_CURRENT_BIT`,
  so current color is clean at bracket boundaries).
- **Array-buffer guard (critique chaser).** LWJGL 2's `gl*Pointer(FloatBuffer)`
  overloads call `ensureArrayVBOdisabled` and throw `OpenGLException` if a VBO is
  bound to `GL_ARRAY_BUFFER` (check on by default). The old immediate-mode path
  was immune; `glPushClientAttrib` doesn't cover that binding. Since the host
  state is polluted and a co-loaded mod could leave a VBO bound, each flush now
  saves `GL_ARRAY_BUFFER_BINDING`, unbinds for the draw, and restores after. A
  binding-state `glGetInteger` is not a GPU stall and is negligible at a few
  dozen flushes/frame.
- `GL_QUADS` kept (valid under `glDrawArrays` in Starsector's compatibility
  profile) — no index buffer, no triangle conversion, vertex layout unchanged.

Compiles against the shipped `lwjgl.jar` (signatures confirmed real for the
game's LWJGL version).

## Verification (done)

1. **In-game** — battle render confirmed correct (no black boxes / vanished
   layers; client-array bracketing holds).
2. **Re-profile** — see Result table above; −75% combined flush.

In-place rewrite, no fallback to delete. Optional further gain lives in the
ground-FBO follow-up spike, not here.
