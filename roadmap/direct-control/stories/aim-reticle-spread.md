# Aim reticle spread

Status: IN PROGRESS — the scatter ring is removed and bloom is live; the spread look is on the picks page
Written: 2026-10-23

The model is `direct-control-nouns.md` (the aim reticle paragraphs). This story holds only what is left.

## Open

1. The owner's answer to the picks question `aim-reticle-spread`: bloom (recommended) or longer. The images come
   from `createSnapshots -Psnapshot=aim-reticle`.
2. Then delete the losing `DirectControlReticle.SpreadLook` arm, the enum itself, and the suite's loop over looks.

## Acceptance

- A rack's tick opens with the cursor's distance and stops at its cap; a gun's tick and a grey tick stay at rest.
