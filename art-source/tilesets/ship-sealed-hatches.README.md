# Sealed ship hatches

Built-in ImageGen reskin of `../templates/ship-sealed-hatch-guide.png`, using
`ship-interior-materials.raw.png` only as the material reference. Exact request:
`ship-sealed-hatches.prompt.txt`. Rebuild the guide with its adjacent Python script.

The accepted raw output is 1254 square, with genuine alpha. Export preserves the
entire square frame: horizontal closed leaves and a locking bar run across its
center; empty margins belong to the placement contract. Runtime rotates that
same sprite by a quarter turn for a vertical wall. No color keying or repainting.

Export through `tools/authoring.cmd tileset_export -` with
`{"name":"ship-sealed-hatches"}` on stdin. The one-cell prop overlays the blocked
doorway; compartment condition still owns navigation and sight. Only sealed
personnel entrances receive it. Launch apertures retain their existing closure.

Review through `createSnapshots -Psnapshot=cerberus-start`: buried versus cleared
workshop and damaged versus repaired second bay. Reopening removes the seal and
exposes the ordinary doorway; this is not an animated or player-operated door.
