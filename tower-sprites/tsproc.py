import sys, os
import numpy as np
from PIL import Image
from scipy import ndimage

HERE = os.path.dirname(os.path.abspath(__file__))
RAW = f"{HERE}/raw"; OUT = HERE
NFR = {"idle":8, "aim":8, "fire":6, "reload":4, "cool":4}

def load_keyed(src, thr=90.0):
    im = Image.open(src).convert("RGBA")
    a = np.asarray(im).astype(np.int16); rgb = a[..., :3]
    border = np.concatenate([rgb[:2].reshape(-1,3), rgb[-2:].reshape(-1,3), rgb[:,:2].reshape(-1,3), rgb[:,-2:].reshape(-1,3)])
    alpha = np.full(rgb.shape[:2], 255, np.uint8)
    remaining = border.copy(); rounds = []
    for _ in range(3):
        if len(remaining) == 0: break
        bg = np.median(remaining, axis=0); rounds.append(bg)
        close = np.sqrt(((rgb-bg)**2).sum(axis=2)) < thr
        lab, _ = ndimage.label(close)
        eg = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:,0], lab[:,-1]]))) - {0}
        if not eg: break
        alpha[np.isin(lab, list(eg))] = 0
        remaining = remaining[np.sqrt(((remaining-bg)**2).sum(axis=1)) > 120]
    return rgb.astype(np.uint8), alpha, rounds

def bands1d(mask):
    out, start, gap = [], None, 0
    for x, v in enumerate(mask):
        if v:
            if start is None: start = x
            gap = 0
        elif start is not None:
            gap += 1
            if gap >= 4: out.append((start, x-gap+1)); start = None
    if start is not None: out.append((start, len(mask)))
    return [b for b in out if b[1]-b[0] > 12]

def inner_key_bg(rgb, alpha, y0, y1, x0, x1, bgs, thr=90.0):
    sub = rgb[y0:y1, x0:x1].astype(np.int16)
    m = alpha[y0:y1, x0:x1] > 0
    close = np.zeros(sub.shape[:2], bool)
    for bg in bgs: close |= np.sqrt(((sub-bg)**2).sum(axis=2)) < thr
    full = np.zeros(alpha.shape, np.uint8)
    full[y0:y1, x0:x1] = np.where(m & ~close, 255, 0)
    return full

def crop_cell(rgb, mask, x0, x1, y0, y1, minf=0.005):
    m = mask[y0:y1, x0:x1] > 0
    lab, cnt = ndimage.label(m)
    if cnt == 0: return None
    sizes = ndimage.sum(m, lab, range(1, cnt+1)); total = sizes.sum()
    big = int(np.argmax(sizes)) + 1
    keep = [i for i in range(1, cnt+1) if i == big or sizes[i-1] >= minf*total]
    m = np.isin(lab, keep)
    ys, xs = np.nonzero(m)
    ya,yb,xa,xb = ys.min(), ys.max(), xs.min(), xs.max()
    return np.dstack([rgb[y0:y1, x0:x1][ya:yb+1, xa:xb+1], (m[ya:yb+1, xa:xb+1]*255).astype(np.uint8)[..., None]])

def pieces_from_comps(alpha, n):
    lab, cnt = ndimage.label(alpha>0)
    sizes = ndimage.sum(alpha>0, lab, range(1,cnt+1))
    boxes = ndimage.find_objects(lab)
    comps = sorted([[boxes[i][1].start, boxes[i][0].start, boxes[i][1].stop-boxes[i][1].start, sizes[i], i+1] for i in range(cnt) if sizes[i] > 400])
    if not comps: return []
    medw = sorted(c[2] for c in comps)[len(comps)//2]
    pieces = []
    for x, y, w, s, i in comps:
        sl = boxes[i-1]
        if w > 1.6*medw:
            colsum = (lab[sl[0].start:sl[0].stop, x:x+w] == i).sum(axis=0)
            a, b = w//3, 2*w//3
            cut = x + a + int(np.argmin(colsum[a:b]))
            pieces += [(x, cut, sl[0].start, sl[0].stop), (cut, x+w, sl[0].start, sl[0].stop)]
        else:
            pieces.append((x, x+w, sl[0].start, sl[0].stop))
    pieces.sort()
    return pieces

def even_pick(pcs, n):
    idx = []
    for i in range(n):
        j = round(i*(pcs-1)/(n-1))
        while j in idx: j += 1
        idx.append(j)
    return idx

def build(cells, out):
    good = [c for c in cells if c is not None]
    if not good: return None
    f = min(58.0/max(c.shape[1] for c in good), 58.0/max(c.shape[0] for c in good))
    frames = []
    for c in cells:
        cv = np.zeros((64,64,4), np.uint8)
        if c is not None:
            ch = max(1,int(round(c.shape[0]*f))); cw = max(1,int(round(c.shape[1]*f)))
            arr = np.asarray(Image.fromarray(c).resize((cw,ch), Image.LANCZOS))
            cv[62-ch:62, (64-cw)//2:(64-cw)//2+cw] = arr
        frames.append(cv)
    sh = np.concatenate(frames, axis=1)
    Image.fromarray(sh).save(out)
    return sh

def cells_from_pieces(rgb, alpha, bgs, pieces, n):
    pcs = len(pieces); mode = f"cells{pcs}"
    if pcs > n:
        idx = even_pick(pcs, n); mode += f" pick{idx}"
        pieces = [pieces[j] for j in idx]
    else:
        pieces = pieces[:n]
    cells = [crop_cell(rgb, inner_key_bg(rgb, alpha, p[2],p[3],p[0],p[1], bgs), p[0],p[1],p[2],p[3]) for p in pieces]
    while len(cells) < n: cells.append(None)
    return cells, mode

def process_single(name):
    rest = name.split("_L")[1]; n = NFR[rest[2:]]
    rgb, alpha, bgs = load_keyed(f"{RAW}/{name}.png")
    rb = bands1d((alpha>0).any(axis=1)); cb = bands1d((alpha>0).any(axis=0))
    grid = [(y0,y1,x0,x1) for y0,y1 in rb for x0,x1 in cb if (alpha[y0:y1,x0:x1]>0).mean() > 0.05]
    pieces = [(g[2],g[3],g[0],g[1]) for g in grid] if len(grid) >= n else pieces_from_comps(alpha, n)
    cells, mode = cells_from_pieces(rgb, alpha, bgs, pieces, n)
    sh = build(cells, f"{OUT}/{name}.png")
    print(f"{name:28s} {mode:16s} -> {sh.shape[1] if sh is not None else 0}x64 empty={sum(1 for c in cells if c is None)}")

def row_cells(rgb, alpha, bgs, y0, y1, n):
    colcov = (alpha[y0:y1] > 0).sum(axis=0)
    core = colcov > 0.25*(y1-y0)
    bands = [b for b in bands1d(core) if b[1]-b[0] > 8]
    if bands:
        medw = sorted(b[1]-b[0] for b in bands)[len(bands)//2]
        split = []
        for x0, x1 in bands:
            w = x1-x0
            if w > 1.6*medw:
                a, b = w//3, 2*w//3
                cut = x0 + a + int(np.argmin(colcov[x0+a:x0+b]))
                split += [(x0, cut), (cut, x1)]
            else:
                split.append((x0, x1))
        bands = split
    pieces = [(x0,x1,y0,y1) for x0,x1 in bands]
    return cells_from_pieces(rgb, alpha, bgs, pieces, n)

def process_pair(src, name_top, name_bot):
    nT = NFR[name_top.split("_L")[1][2:]]; nB = NFR[name_bot.split("_L")[1][2:]]
    rgb, alpha, bgs = load_keyed(f"{RAW}/{src}.png")
    rb = bands1d((alpha>0).any(axis=1))
    if len(rb) != 2:
        print(f"{src:30s} BAD rows={len(rb)}"); return
    for (y0,y1), name, n in [(rb[0], name_top, nT), (rb[1], name_bot, nB)]:
        cells, mode = row_cells(rgb, alpha, bgs, y0, y1, n)
        sh = build(cells, f"{OUT}/{name}.png")
        print(f"{name:28s} {mode:16s} -> {sh.shape[1] if sh is not None else 0}x64 empty={sum(1 for c in cells if c is None)}")

if __name__ == "__main__":
    args = sys.argv[1:]
    if args[0] == "pair":
        for i in range(1, len(args), 3):
            process_pair(args[i], args[i+1], args[i+2])
    else:
        for name in args:
            process_single(name)
