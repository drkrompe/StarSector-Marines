# Sealed ship hatches

Built-in ImageGen reskin of `../templates/ship-sealed-hatch-guide.png`, using
the preceding hatch sprite only as the material reference. Exact request:
`ship-sealed-hatches.prompt.txt`. Rebuild the guide with its adjacent Python script.

The accepted raw output is 1254 square, with genuine alpha. Its visible body spans
rows 533 through 817. The measured export frame `(0, 96, 1254, 1158)` registers that
body at the tile center while retaining transparent margins and the full left/right
span. The result occupies about one quarter of a cell in thickness, rather than
the earlier half-cell slab. Runtime rotates the same sprite by a quarter turn
for a vertical wall. No color keying or repainting.

Export through `tools/authoring.cmd tileset_export -` with
`{"name":"ship-sealed-hatches"}` on stdin. The one-cell prop overlays the blocked
doorway's deck floor, never a solid bulkhead fill. Its transparent margins expose
the threshold. Compartment condition still owns navigation and sight. Only sealed
personnel entrances receive it. Launch apertures retain their existing closure.

Review through `createSnapshots -Psnapshot=cerberus-start`: buried versus cleared
workshop and damaged versus repaired second bay. Reopening removes the seal and
exposes the ordinary doorway; this is not an animated or player-operated door.
