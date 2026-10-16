import bpy,sys
from pathlib import Path
p=Path(r'C:/Users/Dillon/IdeaProjects/starsectormarines/.claude/worktrees/jangala-greybox-animatic/art-source/company-prologue/second-wind-cinematic/landing-refinement')
sys.path.insert(0,str(p));from ship_view import export_guide,apply_starboard_surface
s=bpy.context.scene
# Reuse guide lighting/hide configuration while preserving immutable guide pixels.
export_guide(s,render=False)
apply_starboard_surface(s,p/'assets/starboard-surface-v1.png')
s.render.filepath=str(p/'guides/starboard-registered-preview.png')
bpy.ops.render.render(write_still=True)
