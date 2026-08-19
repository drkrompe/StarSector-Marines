# Story 21 — Last-stand objective camper ✅ SHIPPED

Shipped 2026-08-19 in `ef76c7ba`.

## Slice contract

Story H closes Slice 5 with an explicit exception to normal survival logic:
a lone infantry survivor assigned to a `MUST_HOLD` tactical node keeps the
objective, does not retreat, and fires from the post until killed.

`MUST_HOLD` is immutable node authoring data, not an inference from priority,
node kind, faction, or current possession. Existing `TacticalNode` call sites
default it to false. The military-compound generator opts fortress
`COMMAND_POST` nodes in; port/city command leaves that are authored as
`ARMORY`/`BARRACKS` remain ordinary nodes. Future mission generators may opt
individual `OBJECTIVE` or other nodes in explicitly.

## Goal selection

`NODE_IS_MUST_HOLD` resolves the squad's active node in this order:

1. a commander `HOLD_NODE` assignment with a non-null target; then
2. the defender squad's spawn-time `assignedNode`.

`HoldPosition` is relevant only when that predicate is true and exactly one
infantry member remains alive. It lives in the `MISSION` priority bucket and
has higher within-bucket relevance than the ordinary garrison/mission goals,
so it defeats both `SurviveContact` and other work attached to the same node.
The one-member gate is deliberate: an intact must-hold garrison still patrols
and defends normally; the special posture begins at the actual last stand.

Ordinary garrison goals (`GuardPost`, `GarrisonAmbush`, and the already-gated
`GarrisonCompound`) yield when morale breaks. That preserves Story B for every
node that is not explicitly marked must-hold.

## Hold behavior

The plan is one perpetual `LastStandHold` action:

- the survivor returns to its authored home/post cell if displaced;
- at the post it clears movement and remains planted;
- it never investigates, pursues, searches for a firing position, or follows
  a fallback link; and
- the normal infantry opportunity-fire pass supplies legal primary/rocket
  fire, producing stanced fire while planted and moving fire only while the
  unit is returning to the post.

The action never succeeds. Death removes the final member and the normal
squad replan cleanup drops the plan.

`SquadFallbackSystem` does not trigger a `FALLBACK_TO` link from a must-hold
node. This guard applies before the 50%-strength trigger so the command-post
squad cannot be reassigned away at 2/4 and thereby lose the flag before it
reaches 1/4.

## Diagnostics

The existing goal/plan debug surfaces show `HoldPosition` and
`LastStandHold`. `NODE_IS_MUST_HOLD` is present in the dumped world state, and
the squad block exposes `assignedNodeMustHold`; the dump schema advances to
v8.

## Acceptance

- A one-member squad on an explicit must-hold node selects `HoldPosition`
  even with `MORALE_BROKEN` true.
- The same squad on an ordinary node selects `SurviveContact`.
- A must-hold squad never consumes its node's structural fallback link.
- An intact must-hold squad retains ordinary garrison behavior.
- The survivor paths back to its home cell rather than toward an enemy and,
  once home, holds still while authoring legal stanced fire.
- Generated fortress `COMMAND_POST` nodes are must-hold; other generated
  military-compound nodes are not.

## Out of scope

- New morale, surrender, capture, or rout simulation.
- Making every high-priority/command/objective node must-hold implicitly.
- Reworking multi-member garrison patrol, cover, or ambush tactics.
- A general order-cancellation/commander override system.

## Verification

`HoldPositionTest` covers must-hold versus ordinary goal selection, commander
hold-node resolution, the intact-squad gate, perpetual plan shape, planted
stanced fire, and return-to-post movement. `TacticalNodeMustHoldTest` covers
the default-false constructor and both sides of structural fallback.
`GarrisonAmbushTest`, `GarrisonCompoundTest`, and
`MilitaryCompoundLayoutTest` pin ordinary morale yielding and generator
authoring. The full `gradlew.bat build` gate passed after implementation.
