# Ship crew layers

Fresh overhead crew uniforms, textured with built-in ImageGen from the retained
silhouette guide. These are independent body, head and foot layers; no legacy
engineer sheet is used as a source. `ship-crew.prompt.txt` is the exact prompt,
`ship-crew.raw.png` the unmodified returned image.

Rows: command, engineering, medical, service. Columns: body, head, foot.
All face image-up. Bodies have an empty neck opening; heads rotate independently.
The guide is `../templates/ship-crew-silhouettes.png`.

From the repository root, using the Java installation in `CLAUDE.md`:

```powershell
& "$env:JAVA_HOME/bin/java.exe" art-source/ship-crew/ShipCrewArt.java
& "$env:JAVA_HOME/bin/java.exe" art-source/ship-crew/ShipCrewArt.java export
```

The first command rebuilds the 1024x1152 silhouette guide. Export reads the
actual raw resolution, isolates each row/column, bounds alpha content, and
normalizes body/head/foot to 150x120, 72x88 and 38x52 pixels respectively.
These match the existing layered compositor's shoulder reference, shared body
and head offsets, and foot pivot (19,26). Generated alpha is retained; no color
keying or hand-painted repair. Only the twelve exported PNGs under
`mod/graphics/battle/ship-crew-modular-topdown/` ship.

`createSnapshots -Psnapshot=ship-duty` renders actual Valkyrie generation at
24 and 64 pixels per cell through the production scene and Java2D drain.
The fixture retains two named marines, checks repeated manning, and samples
essential station occupancy every second over 120 simulated seconds. Close
frames change camera scale, not the actor's size. This is headless renderer
evidence, not a running campaign capture.
