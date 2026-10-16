"""The four weapon mounts: three small, one medium, at vanilla Cerberus XY.

Naval-industrial gun houses rather than boxes: a faceted barbette seated on
whatever hull slope lies beneath it, a traverse gap, a turret house lofted
from a chamfered-nose plan with a sloped glacis and raked sides, a mantlet,
and a stepped barrel ending in a muzzle brake. A rear bustle carries the
ammunition feed and vents. Silhouette first; greebles stay sparse.
"""
import math

# Vanilla warhound.ship weapon slot XY positions, mount scale, medium flag.
# Small mounts are one family; turret 4 is the medium twin-gun house.
MOUNTS = [(-11,20,1.0,False),(-11,-20,1.0,False),(41,11,1.0,False),(36,-16,1.3,True)]

# Turret-house plan (scaled), counter-clockwise from above: chamfered nose
# at the bottom ring, glacis and raked sides pulled in at the roof ring.
HOUSE_BASE = [(3.0,-1.3),(3.0,1.3),(1.7,2.5),(-2.5,2.5),(-2.5,-2.5),(1.7,-2.5)]
HOUSE_ROOF = [(1.3,-.85),(1.3,.85),(.5,1.95),(-2.25,1.95),(-2.25,-1.95),(.5,-1.95)]
HOUSE_H = 2.0


def loft(kit, name, base, roof, z0, z1, mat, bevel=.12):
    """Prism lofted between two CCW plans of equal vertex count."""
    n = len(base)
    verts = [(x,y,z0) for x,y in base] + [(x,y,z1) for x,y in roof]
    faces = [(i,(i+1)%n,n+(i+1)%n,n+i) for i in range(n)]
    faces += [tuple(reversed(range(n))), tuple(range(n,2*n))]
    return kit.mesh(name, verts, faces, mat, bevel=bevel)


def seat(kit, i, x, y, r):
    """Highest and lowest hull Z under the barbette footprint."""
    zs = []
    for k in range(13):
        a = k*math.tau/12
        px,py = (x,y) if k == 12 else (x+r*math.cos(a), y+r*math.sin(a))
        hit = kit.surface(px,py)
        if hit is not None:
            zs.append(hit[0].z)
    if kit.surface(x,y) is None:
        raise RuntimeError(f'Turret {i} has no supporting hull surface')
    return max(zs), min(zs)


def barrel(kit, i, tag, ox, oy, z, s, heavy):
    """Recoil sleeve, two tapering tube sections and a slotted muzzle brake."""
    L = 1.25 if heavy else 1.0
    sections = [(.6,1.7,kit.steel,'recoil sleeve'),(.44,2.6*L,kit.steel,'barrel chase'),
                (.34,2.3*L,kit.steel,'barrel tube')]
    x = ox
    for r,length,mat,label in sections:
        kit.cylinder(f'Turret {i} {tag} {label}',(x+length*s/2,oy,z),r*s,length*s,mat,'X',20,.05)
        x += length*s
    kit.cylinder(f'Turret {i} {tag} breech band',(ox+1.5*s,oy,z),.68*s,.3*s,kit.dark,'X',20,.04)
    kit.box(f'Turret {i} {tag} muzzle brake',(x+.5*s,oy,z),(1.0*s,.95*s,.72*s),kit.steel,.08*s)
    kit.box(f'Turret {i} {tag} muzzle brake port',(x+.5*s,oy,z),(.3*s,1.0*s,.5*s),kit.dark,.03)


def build(kit):
    for i,(x,y,s,heavy) in enumerate(MOUNTS,1):
        R = 3.1*s
        zmax,zmin = seat(kit,i,x,y,R)
        # Barbette: a faceted foot sunk into the slope, an armoured collar
        # proud of the highest ground, and a dark traverse gap above it.
        kit.cylinder(f'Turret {i} barbette foot',(x,y,(zmin-.4+zmax+.25)/2),R+.45*s,zmax+.65-zmin,kit.steel,'Z',12,.12)
        kit.cylinder(f'Turret {i} armoured collar',(x,y,zmax+.6*s),R,.9*s,kit.steel,'Z',12,.1)
        kit.cylinder(f'Turret {i} traverse gap',(x,y,zmax+1.15*s),R-.35*s,.3*s,kit.dark,'Z',24,0)
        zb = zmax+1.25*s
        P = lambda pts,dx=0: [(x+(px+dx)*s,y+py*s) for px,py in pts]
        bustle = 1.6 if heavy else 1.0
        loft(kit,f'Turret {i} gun house',P(HOUSE_BASE),P(HOUSE_ROOF),zb,zb+HOUSE_H*s,kit.steel)
        # Rear bustle: ammunition handling, lower than the house roof.
        bb = [(-2.4,-2.1),(-2.4,2.1),(-2.4-bustle,1.9),(-2.4-bustle-.6,1.3),(-2.4-bustle-.6,-1.3),(-2.4-bustle,-1.9)]
        br = [(-2.3,-1.8),(-2.3,1.8),(-2.3-bustle,1.6),(-2.3-bustle-.35,1.1),(-2.3-bustle-.35,-1.1),(-2.3-bustle,-1.6)]
        loft(kit,f'Turret {i} rear bustle',P(bb),P(br),zb+.15*s,zb+1.7*s,kit.steel)
        for k in range(3):
            kit.box(f'Turret {i} bustle vent slat',(x+(-2.6-bustle*.5)*s,y+(k-1)*.95*s,zb+1.72*s),((bustle+.1)*s,.45*s,.06*s),kit.dark,0)
        kit.box(f'Turret {i} ammunition feed trunk',(x-(2.9+bustle)*s,y-1.3*s,zb+.3*s),(.9*s,.8*s,1.6*s),kit.dark,.08)
        # Mantlet across the glacis, then the gun(s).
        gz = zb+.95*s
        guns = [(.85,'port gun'),(-.85,'starboard gun')] if heavy else [(0,'gun')]
        mw = 3.4 if heavy else 1.9
        # Rounded trunnion mantlet: a drum across the glacis the guns pierce.
        kit.cylinder(f'Turret {i} mantlet drum',(x+2.3*s,y,gz),.78*s,mw*s,kit.steel,'Y',24,.12*s)
        kit.box(f'Turret {i} mantlet face',(x+2.75*s,y,gz),(.5*s,(mw-.3)*s,1.2*s),kit.steel,.12*s)
        for gy,tag in guns:
            barrel(kit,i,tag,x+3.0*s,y+gy*s,gz,s,heavy)
        # Roof: commander's hatch aft, rangefinder/optics blister to port.
        kit.cylinder(f'Turret {i} roof hatch',(x-1.2*s,y-.7*s,zb+HOUSE_H*s+.08*s),.6*s,.16*s,kit.steel,'Z',16,.04)
        ox = 1.95 if heavy else 1.35
        kit.box(f'Turret {i} optics blister',(x-.2*s,y+ox*s,zb+HOUSE_H*s+.3*s),(1.5*s,.8*s,.6*s),kit.steel,.18*s)
        kit.box(f'Turret {i} optics lens',(x+.56*s,y+ox*s,zb+HOUSE_H*s+.33*s),(.08,.55*s,.32*s),kit.glass,0)
        if heavy:
            kit.box(f'Turret {i} rangefinder arm',(x-.6*s,y,zb+HOUSE_H*s+.18*s),(.7*s,4.6*s,.36*s),kit.steel,.1*s)
            for side in (1,-1):
                kit.box(f'Turret {i} rangefinder window',(x-.6*s,y+side*2.31*s,zb+HOUSE_H*s+.18*s),(.45*s,.06,.24*s),kit.glass,0)
            kit.box(f'Turret {i} starboard optics blister',(x-.2*s,y-ox*s,zb+HOUSE_H*s+.3*s),(1.5*s,.8*s,.6*s),kit.steel,.18*s)
