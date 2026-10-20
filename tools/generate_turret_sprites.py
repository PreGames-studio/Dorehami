#!/usr/bin/env python3
"""Create transparent 64px-frame horizontal turret sheets for Construct 3.
Install Pillow, then run: python tools/generate_turret_sprites.py
Output: assets/turrets/ (100 PNG sheets + manifest.json).
"""
from PIL import Image, ImageDraw
import math, os, json

SIZE=64
TOWERS=('machinegun','artillery','rocket','laser','tesla')
ACTIONS={'idle':8,'aim':8,'fire':6,'reload':4}
# metal, shadow, level accent
PALETTE={1:((139,91,57),(75,52,40),(184,124,75)),2:((160,166,153),(83,89,84),(219,208,170)),3:((82,162,204),(34,83,122),(82,220,255)),4:((147,88,180),(61,42,91),(213,127,255)),5:((220,168,56),(112,73,28),(255,226,111))}
INK=(43,35,37,255)
def c(rgb): return (*rgb,255)
def poly(d,p,fill,outline=INK,w=2):
 d.polygon(p,fill=fill); d.line(p+[p[0]],fill=outline,width=w,joint='curve')
def ell(d,b,fill,outline=INK,w=2): d.ellipse(b,fill=fill,outline=outline,width=w)
def beam(d,a,b,fill,w): d.line((a,b),fill=fill,width=w)
def draw(t,l,act,i,n):
 metal,shadow,accent=map(c,PALETTE[l]); im=Image.new('RGBA',(64,64),(0,0,0,0)); d=ImageDraw.Draw(im)
 ph=2*math.pi*i/n
 # Shared stationary stone plinth and sandbags; identical pixels in all frames.
 ell(d,(9,40,55,58),(34,27,25,80),None,0)
 poly(d,[(8,38),(19,30),(44,30),(56,38),(52,49),(42,55),(19,54),(9,48)],c((112,107,96)))
 poly(d,[(12,38),(22,33),(42,33),(52,38),(48,44),(18,44)],c((168,157,132)))
 beam(d,(17,39),(18,45),c((86,80,72)),1); beam(d,(47,38),(45,44),c((86,80,72)),1)
 poly(d,[(12,46),(20,43),(26,46),(24,51),(17,53),(12,50)],c((161,119,72)))
 poly(d,[(37,46),(44,43),(51,46),(49,51),(42,53),(37,50)],c((205,166,104)))
 # Turret top animates independently on the fixed base.
 angle=(.2*math.sin(ph) if act=='idle' else (ph*.24 if act=='aim' else 0))
 bob=round(math.sin(ph)*1.4) if t=='artillery' and act=='idle' else 0
 cx,cy=32,35+bob; recoil=-3 if act=='fire' and i in (1,2) else (-2 if act=='reload' and i<2 else 0)
 ell(d,(20,23+bob,44,47+bob),c((59,51,44)))
 ell(d,(23,25+bob,41,43+bob),metal)
 if l>=2:
  for x in (25,38): ell(d,(x-2,30+bob,x+2,34+bob),accent,INK,1)
 if l>=3: ell(d,(29,29+bob,35,35+bob),accent,INK,1)
 if t=='rocket':
  count=(2,4,6,8,12)[l-1]; w=15+2*(l-1); h=11+l-1
  poly(d,[(cx-w/2,cy-h/2),(cx+w/2,cy-h/2),(cx+w/2,cy+h/2),(cx-w/2,cy+h/2)],metal)
  cols=2 if count<=4 else 3; rows=math.ceil(count/cols)
  for j in range(count):
   x=cx-(cols-1)*3+(j%cols)*6; y=cy-(rows-1)*2+(j//cols)*4
   ell(d,(x-1.6,y-1.6,x+1.6,y+1.6),shadow,INK,1)
   if act=='fire' and j==i%count: ell(d,(x-2,y-5,x+2,y-1),c((255,157,57)),None,0)
 else:
  if t=='machinegun': count=(1,1,2,2,3)[l-1]; length=(12,14,16,17,18)[l-1]; width=4
  elif t=='artillery': count=1; length=(10,13,16,18,20)[l-1]; width=7
  elif t=='laser': count=(1,1,2,3,1)[l-1]; length=15; width=4
  else: count=1; length=10+l; width=7
  if t=='tesla':
   ell(d,(24,26+bob,40,41+bob),shadow); ell(d,(28,28+bob,36,36+bob),accent,INK,1)
   if l>=4: ell(d,(21,22+bob,43,44+bob),None,accent,2)
   if act in ('idle','aim','fire') and i%2==0:
    beam(d,(32,26+bob),(29,21+bob),accent,2); beam(d,(29,21+bob),(34,18+bob),accent,2)
  for k in range(count):
   off=(k-(count-1)/2)*4
   sx=cx+math.sin(angle)-math.cos(angle)*off; sy=cy+math.cos(angle)+math.sin(angle)*off
   ex=cx+math.sin(angle)*length-math.cos(angle)*off+recoil*math.sin(angle); ey=cy-math.cos(angle)*length-math.sin(angle)*off-recoil*math.cos(angle)
   beam(d,(sx,sy),(ex,ey),shadow,width+2); beam(d,(sx,sy),(ex,ey),metal,width)
   ell(d,(ex-2,ey-2,ex+2,ey+2),accent,INK,1)
  if t=='artillery': poly(d,[(25,31+bob),(30,27+bob),(38,28+bob),(41,34+bob),(37,39+bob),(27,38+bob)],metal)
  elif t=='machinegun': poly(d,[(27,29+bob),(36,29+bob),(39,34+bob),(35,38+bob),(27,36+bob)],metal)
  elif t=='laser':
   for k in range(count):
    x=32+(k-(count-1)/2)*5; ell(d,(x-3,29+bob,x+3,35+bob),accent,INK,1)
 # Shot effects originate at the muzzle; action frames are purposefully readable.
 if act=='fire' and i in (0,1,2):
  if t=='laser': beam(d,(37,32),(61,25),c((82,220,255)),4); ell(d,(57,21,63,28),c((245,255,255)),None,0)
  elif t!='rocket':
   color=(255,203,66) if t=='machinegun' else (255,139,46)
   ell(d,(46,26,58,39),c(color),INK,1); poly(d,[(50,27),(57,29),(53,32),(60,34),(52,37),(54,32)],c((255,243,184)),None,0)
 if l>=3 and act in ('idle','aim'):
  for x,y in ((19,29),(44,29)):
   if (i+x)%3==0: ell(d,(x,y,x+2,y+2),accent,None,0)
 return im

def main():
 out='assets/turrets'; os.makedirs(out,exist_ok=True); manifest=[]
 for tower in TOWERS:
  for level in range(1,6):
   for action,n in ACTIONS.items():
    sheet=Image.new('RGBA',(SIZE*n,SIZE),(0,0,0,0))
    for i in range(n): sheet.alpha_composite(draw(tower,level,action,i,n),(i*SIZE,0))
    filename=f'{tower}_L{level}_{action}.png'; sheet.save(os.path.join(out,filename),optimize=True)
    manifest.append({'file':filename,'tower':tower,'level':level,'action':action,'frames':n,'frameWidth':SIZE,'frameHeight':SIZE,'layout':'horizontal','alpha':True,'fps':10 if action in ('idle','aim') else 12})
 with open(os.path.join(out,'manifest.json'),'w',encoding='utf8') as f: json.dump(manifest,f,indent=2)
 print(f'Generated {len(manifest)} sheets in {out}')
if __name__=='__main__': main()
