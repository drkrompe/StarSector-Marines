# The bore assault

Status: IN PROGRESS

Written: 2026-10-25

## Model

`heart-nouns.md` (The Burn, Lane, the bore's mouth) and `heart-allied-commander.md`
(what the lanes' commander decides). Machines are driven and fought by allied
command (`allied-command.md`, posts and stands).

## Why

A Burn lane's armour was posted four cells *ahead* of the lane's aim, so the
militia would not stand in its guns' line. Once the guns have passed a bore the
aim is the bore itself, so every hull's post was four cells past the bore's
centre, in the mouth its defenders spawn into. The militia attack-moved straight
at the bore, and the armour's bore was picked from the lane's start line, not
from where it stood.

## The assault

A lane attacks its bore in phases, the lanes' commander's tactical layer; which
bore a lane takes, the waves and the calls for armour stay the mission's.

1. **Moving up.** The armour drives to its stand-off posts first. After a bore
   has fallen the militia wait where they stand until the armour is set (or a
   minute has passed); otherwise they go straight to the support line.
2. **Base of fire.** Each hull and mech stands on a **stand-off ring** round the
   bore, facing it: outside the mouth by a margin (at least 15 cells), at 0.7 of
   the hull's gun reach where that is farther, on the lane's near side (within 60° of the way from the bore to the lane),
   spread along the ring, with a line of fire to the bore preferred. Allied
   command's stands slide a post along the ring or back from it, never in. The
   militia hold a **support line** five cells behind the ring, toward the lane.
3. **Assault.** A hurt bore surges at most every twelve seconds. The militia go in
   on a **spent surge**: a surge has come out, three seconds have passed, and the
   mouth is **quiet**, holding no more of the swarm than half the lane's militia
   (at least four). A bore that has not surged for forty seconds with its mouth
   that quiet is gone in on as well. A lane that has held its support line a
   minute goes in whatever the mouth holds, except in a surge's first seconds:
   on Broad Front a mother's mouth never emptied, the ring could not see the
   mother inside its building, and the lanes stood on their support lines until
   the battle timed out.
4. **Falling back.** The bore's next surge sends the militia back to the support
   line, and the lane waits for that surge to be spent.
5. **After the fall** the lane takes its next bore from where it stands, armour
   first.

On The Burn the assault takes a lane only where the guns allow it: never a bore
past the barrage's hold. Until then the lane follows the barrage as before. On
Broad Front the lane's formation still goes between bores, screened by country,
but it rallies on the bore's support line instead of on the bore, and the
assault takes the lane from there. Waves joining
the lane march to its support line, not to the bore. Behind
`battle.heart.boreAssault`; `false` is the control, the old posts.

## Acceptance

- Unit tests: a ring post lies outside the mouth, on the near side, spread from the
  others; the spent-surge rule opens and closes the window.
- One Burn run and one Broad Front run whose summaries report, per lane, the phases entered and the
  assaults launched on a spent surge, on a quiet mouth and after the longest wait, the lane-seconds in each phase, and the time hulls spent inside a mouth.
- Eyes on The Burn: armour stands off and fires, militia go in behind a surge.

## Next

- A Matriarch out of the mouth: the armour steps back a ring.
