import sys, os
sys.path.insert(0, "/home/user/Dorehami/tower-sprites")
import numpy as np
from PIL import Image
from tsproc import load_keyed, bands1d, inner_key_bg, crop_cell, even_pick

HERE = os.path.dirname(os.path.abspath(__file__))
RAW = f"{HERE}/raw"; OUT = HERE
SPEC = [("explosion_small",6,"center"),("explosion_medium",8,"center"),("explosion_big",10,"center"),
        ("muzzle_flash",4,"center"),("laser_beam",6,"center"),("tesla_arc",6,"center"),
        ("smoke_cloud",6,"center"),("fire_flame",6,"bottom")]

def cells_all_rows(rgb, alpha, bgs, thr_cov=0.15):
    rb = bands1d((alpha>0).any(axis=1))
    out = []
    for y0,y1 in rb:
        colcov = (alpha[y0:y1] > 0).sum(axis=0)
        core = colcov > thr_cov*(y1-y0)
        bands = [b for b in bands1d(core) if b[1]-b[0] > 6]
        if not bands: continue
        medw = sorted(b[1]-b[0] for b in bands)[len(bands)//2]
        for x0,x1 in bands:
            w = x1-x0
            if w > 1.7*medw:
                a,b2 = w//3, 2*w//3
                cut = x0+a+int(np.argmin(colcov[x0+a:x0+b2]))
                out += [(x0,cut,y0,y1),(cut,x1,y0,y1)]
            else:
                out.append((x0,x1,y0,y1))
    return out, len(rb)

def build_fx(cells, out, align):
    good = [c for c in cells if c is not None]
    if not good: return None
    f = min(58.0/max(c.shape[1] for c in good), 58.0/max(c.shape[0] for c in good))
    frames = []
    for c in cells:
        cv = np.zeros((64,64,4), np.uint8)
        if c is not None:
            ch = max(1,int(round(c.shape[0]*f))); cw = max(1,int(round(c.shape[1]*f)))
            arr = np.asarray(Image.fromarray(c).resize((cw,ch), Image.LANCZOS))
            if align == "bottom":
                cv[62-ch:62, (64-cw)//2:(64-cw)//2+cw] = arr
            else:
                oy = (64-ch)//2; ox = (64-cw)//2
                cv[oy:oy+ch, ox:ox+cw] = arr
        frames.append(cv)
    sh = np.concatenate(frames, axis=1)
    Image.fromarray(sh).save(out)
    return sh

if __name__ == "__main__":
    for name, n, align in SPEC:
        rgb, alpha, bgs = load_keyed(f"{RAW}/{name}.png")
        pieces, nrows = cells_all_rows(rgb, alpha, bgs)
        pcs = len(pieces); mode = f"rows{nrows} cells{pcs}"
        if pcs > n:
            idx = even_pick(pcs, n); mode += f" pick{idx}"
            pieces = [pieces[j] for j in idx]
        else:
            pieces = pieces[:n]
        cells = [crop_cell(rgb, inner_key_bg(rgb, alpha, p[2],p[3],p[0],p[1], bgs), p[0],p[1],p[2],p[3]) for p in pieces]
        while len(cells) < n: cells.append(None)
        sh = build_fx(cells, f"{OUT}/{name}.png", align)
        print(f"{name:18s} {mode:28s} -> {sh.shape[1] if sh is not None else 0}x64 empty={sum(1 for c in cells if c is None)}")
