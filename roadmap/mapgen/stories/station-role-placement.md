# Station role placement

Status: PROPOSED

Written: 2026-08-23

Read `mapgen-nouns.md` before implementing this story.

Use the station topology's entry, depth, articulation, bridge, loop, and spine
roles for one concrete placement consumer. The consumer must explain why its
chosen role makes a better tactical location than a random reachable room.

## Acceptance

- Placement reads published topology roles, not tile-coordinate heuristics.
- It has a deterministic fallback for layouts where the preferred role is absent.
- The result remains reachable and valid for its intended deployment or
  objective use.
