# Proposed Locust microphone-shaped launcher

Geometry proposal awaiting the user's shape review. Do not use ImageGen on this
proposal until that review: the user explicitly requested seeing the shape first.

The compact head is a short cylinder with a convex rounded cap, like a
microphone head. Eighteen small launch bores run parallel along one forward
axis, elevated 25 degrees. Their openings follow the cap; they do not radiate
from a sphere. The head is centered over the floor, with its short nose inside
the floor's footprint in the overhead pose.

The floor and both cheek supports form one yaw assembly over a circular
bearing. The cheeks clip onto opposite sides of the head at one horizontal
axle. The head, its bosses and bore interiors share a separate pitch parent,
so the launcher can elevate without tipping the floor or supports. The
script checks shell/floor clearance at 0, 25, 45 and 65 degrees; this is a
shape check, not a full mechanical collision certification.

Port count, elevation and proportions are proposal choices, not gameplay data.
Views show the overhead footprint, an angled assembly and the side axle.

`build.py` runs in an isolated background Blender process with factory startup.
It writes `proposal.blend` and the transparent 1920x1080 `shape-views.png` at
100% render scale. `review.ps1` adds labels at the same resolution without
resizing. No ImageGen call is part of this proposal's workflow. The existing
finished Locust mount and ground base have not been replaced.

```powershell
& 'C:/Program Files/Blender Foundation/Blender 5.2/blender.exe' --background --factory-startup --python art-source/defences/locust-aa/proposals/microphone-head/build.py
./art-source/defences/locust-aa/proposals/microphone-head/review.ps1
```
