# Proposed Locust microphone-shaped launcher

Geometry proposal awaiting the user's shape review. Do not use ImageGen on this
proposal until that review: the user explicitly requested seeing the shape first.

The forward head is a cylinder with a convex rounded cap, like a microphone
head. Eighteen small launch bores run parallel along one forward axis, elevated
25 degrees. Their openings follow the cap; they do not radiate from a sphere.
Port count, elevation and proportions are proposal choices, not gameplay data.
The left view is overhead; the right shows the same geometry at an explanatory
angle. Rear housing and hinge blocks are simple context geometry.

`build.py` runs in an isolated background Blender process with factory startup.
It writes `proposal.blend` and the transparent 1920x1080 `shape-views.png` at
100% render scale. `review.ps1` adds labels at the same resolution without
resizing. No ImageGen call is part of this proposal's workflow. The existing
finished Locust mount and ground base have not been replaced.

```powershell
& 'C:/Program Files/Blender Foundation/Blender 5.2/blender.exe' --background --factory-startup --python art-source/defences/locust-aa/proposals/microphone-head/build.py
./art-source/defences/locust-aa/proposals/microphone-head/review.ps1
```
