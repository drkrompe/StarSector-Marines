"""Deterministic composition guide; replace its surface with retained ImageGen art."""
from pathlib import Path
import math
from PIL import Image
OUT=Path(__file__).resolve().parent
def main():
    (OUT/'guides').mkdir(exist_ok=True)
    im=Image.new('RGB',(1920,1080)); p=im.load()
    for y in range(1080):
        for x in range(1920):
            dx=(x-1530)/1170;dy=(y-1070)/1170;r=math.hypot(dx,dy)
            if r<1:
                z=math.sqrt(1-r*r); light=.35+.65*max(0,(-dx-dy+z)*.577)
                land=math.sin(dx*12+math.sin(dy*10)*2)+.6*math.sin(dy*19+dx*7)+.35*math.sin(dx*41-dy*23)
                c=(37,88,57) if land>.45 else (19,59,95)
                clouds=max(0,math.sin(dx*24+dy*13+2*math.sin(dy*17))-.72)*2
                c=tuple(int((v*(1-clouds)+220*clouds)*light) for v in c)
                edge=max(0,1-abs(r-.996)/.007)
                p[x,y]=tuple(min(255,int(v+edge*a)) for v,a in zip(c,(35,90,140)))
            elif r<1.008:
                a=(1.008-r)/.008;p[x,y]=(int(a*14),int(a*45),int(a*78))
    im.save(OUT/'guides/planet-layout.png')
if __name__=='__main__':main()
