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

## Related

`boarding-deck-missions.md` wants the same topology-role placement consumer for
hostile ship decks. Build one consumer over the shared layout-neutral tier rather
than a station-private and a ship-private copy.
