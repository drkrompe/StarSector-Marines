"""Repeatable manually identified hardpoint least-squares affine registration.
Run with Blender background --python; pixel coordinates use top-left origin.
"""
import json
from pathlib import Path
import numpy as np
OUT=Path(__file__).resolve().parent
# Centers/corners identified on the immutable1920x1080 guide and native1672x941 master.
points=[
 ('aft bell outer lip center',(355,684),(116,623)),
 ('bay upper aft corner',(973,636),(811,563)),
 ('bay upper forward corner',(1256,642),(1134,568)),
 ('bay lower forward corner',(1256,736),(1134,681)),
 ('bay lower aft corner',(973,732),(811,676)),
 ('bow upper tip',(1608,611),(1535,545)),
 ('aft far turret center',(873,428),(699,333)),
 ('aft near turret center',(803,475),(621,384)),
 ('forward near turret center',(1390,484),(1288,398)),
 ('forward far turret center',(1421,451),(1327,363)),
 ('underbow shoe center',(1338,791),(1234,745)),
]
x=np.array([[*g,1] for _,g,_ in points],float);y=np.array([a for _,_,a in points],float)
m=np.linalg.lstsq(x,y,rcond=None)[0].T
pred=x@m.T;errors=np.linalg.norm(pred-y,axis=1)
report={'method':'Manually identified engine lip, bay corners, bow tip, four turret centers and underbow shoe; least-squares2Daffine. Not silhouettebounds fitting.', 'guide_dimensions':[1920,1080], 'native_dimensions':[1672,941], 'image':'starboard-surface-v1.png','affine_top_left_pixel_rows':m.tolist(),'rms_native_pixels':float(np.sqrt(np.mean(errors**2))),'max_native_pixels':float(max(errors)), 'landmarks':[{'name':name,'guide_xy':g,'native_xy':a,'predicted_native_xy':p.tolist(),'residual_native_px':float(e)} for (name,g,a),p,e in zip(points,pred,errors)],'limits':'Manual landmark uncertainty about2-4pixels. Global affine cannot register newly generated small panels, craters or local silhouette differences. Covers this one fixed aspect only.'}
(OUT/'starboard-image-registration.json').write_text(json.dumps(report,indent=2)+'\n', encoding='utf-8', newline='\n')
print(json.dumps(report,indent=2))
