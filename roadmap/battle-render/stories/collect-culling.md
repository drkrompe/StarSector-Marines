# Cull at collection

Status: IN PROGRESS

Written: 2026-09-03

## The measurement that points here

Nothing in the frame is submission-bound any more. `UNITS` is five draws across
two binds at every framing on either map, `ROOFS` is one custom pass and one
command, `GROUND` is three draws and no texture binds at all. What
`renderEvidence` names now is a collector rather than a driver:

| | collect ms | drain ms |
|---|--:|--:|
| `UNITS`, 280x168 close | 0.36 | 0.25 |
| `GROUND`, 280x168 mid | 0.38 | 0.18 |

The last three levers were all about submitting the same picture in fewer calls.
This one is a different question: whether a collector has to visit every body and
every cell to produce the picture at all.

## The lever

**A collector visits what the camera can see.** A body whose sprite cannot land
in the viewport is not composed, not looked up, and not emitted; a cell outside
the visible rectangle is not walked. The margin is the thing to get right — a
body's *centre* may be off screen while its sprite still overlaps the view — so
the rectangle is grown by the largest extent any body in that sweep can draw at,
stated from the authored maximum rather than guessed.

Where a sweep survives that and is still unreadable at whole-map framing, a
framing gate (law 19) is the second lever, and only where `renderEvidence` shows
it matters.

`battle.render.collectCulling` is the control.

## The defect in the instrument

The 560x336 Conquest fixture's `UNITS` command counts are **not reproducible
across runs** — 496 / 567 / 537 for the same world on unchanged code — while
within one run they repeat frame to frame, which is what the existing assertion
checks. A ceiling measured on that instrument cannot be compared against
anything.

`commanderEvidence` and `simDeterminism` play their fixtures byte-stably because
they force the serial scheduler; `renderEvidence` does not, so its 600-tick
playout runs `UnitUpdateSystem`'s parallel dispatch and lands on a different
world every run. It is fixed first, as its own chunk, because a baseline taken
on the broken instrument is worth nothing.

## Acceptance

- `renderEvidence` on both maps at three framings, on and off against its own
  control in one report: `UNITS` and `GROUND` collect at least halved at close
  and mid, nothing worse anywhere.
- The cross-run reproducibility assertion green: two stand-ups of the 560
  fixture in one task run collect identical counts.
- A pixel-equality check that a culled frame is identical to an unculled one at
  each framing, with a body deliberately straddling the view edge.
- `createSnapshots` byte-identical for every suite that renders a battle.
- `verifyModJarBoundary`, `:test`, `shaderEvidence`, `simDeterminism` green.
