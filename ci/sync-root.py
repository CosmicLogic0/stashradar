#!/usr/bin/env python3
"""Copies the ports sources (ports/src, with //#if MC blocks) into the root 1.21.1 project as plain Java, so the
root build (build.yml, the gallery's screenshot helper) always runs exactly the code the ports build and test.
usage: python3 ci/sync-root.py   (from the repo root)"""
import os, re, shutil, sys

MC = (1, 21, 1)

def ver(s):
    return tuple(int(x) for x in s.split('.'))

def cmp(a, b):
    n = max(len(a), len(b)); a = a + (0,) * (n - len(a)); b = b + (0,) * (n - len(b))
    return (a > b) - (a < b)

def term(t):
    m = re.fullmatch(r'MC\s*(>=|<=|>|<|==|!=)\s*([\d.]+)', t.strip())
    if not m:
        raise SystemExit('unknown term: ' + t)
    c = cmp(MC, ver(m.group(2)))
    return {'>=': c >= 0, '<=': c <= 0, '>': c > 0, '<': c < 0, '==': c == 0, '!=': c != 0}[m.group(1)]

def ev(expr):
    return any(all(term(t) for t in part.split('&&')) for part in expr.split('||'))

def preprocess(text):
    out, stack, active = [], [], True
    for line in text.split('\n'):
        t = line.strip()
        if t.startswith('//#if '):
            c = ev(t[6:]); stack.append([active, c]); active = active and c; out.append(''); continue
        if t.startswith('//#elif '):
            top = stack[-1]; c = (not top[1]) and ev(t[8:])
            if c: top[1] = True
            active = top[0] and c; out.append(''); continue
        if t == '//#else':
            top = stack[-1]; active = top[0] and not top[1]; top[1] = True; out.append(''); continue
        if t == '//#endif':
            active = stack.pop()[0]; out.append(''); continue
        out.append(line if active else '')
    if stack:
        raise SystemExit('unclosed //#if')
    return '\n'.join(out)

def copy(src, dst):
    for root, _, files in os.walk(src):
        for f in files:
            if f.endswith('.java'):
                p = os.path.join(root, f)
                q = os.path.join(dst, os.path.relpath(p, src))
                os.makedirs(os.path.dirname(q), exist_ok=True)
                open(q, 'w').write(preprocess(open(p).read()))

pkg = 'com/cosmiclogic/stashradar'
for d, src in [('common', 'ports/src/common'), ('neoforge', 'ports/src/neoforge'), ('forge', 'ports/src/forge')]:
    dst = f'{d}/src/main/java'
    if d == 'common':
        shutil.rmtree(os.path.join(dst, pkg), ignore_errors=True)
    else:
        for f in os.listdir(os.path.join(dst, pkg)):
            if f.endswith('.java'):
                os.remove(os.path.join(dst, pkg, f))
    copy(src, dst)
print('root project synced from ports/src for Minecraft 1.21.1')
