"""Regenerates res/values*/strings.xml from the iOS string catalog.

Usage (from FinTrackerAndroid/):
    python3 tools/generate_strings.py ../FinTracker/FinTracker/Resources/Localizable.xcstrings app/src/main/res

Keys that aren't valid Android resource names are skipped, Java keywords get a trailing
underscore (interface -> interface_), and %@ placeholders become %s / %1$s.
"""
import json, re, os, sys
src, dst = sys.argv[1], sys.argv[2]
d = json.load(open(src))
JAVA_KEYWORDS = {'abstract','assert','boolean','break','byte','case','catch','char','class','const','continue','default','do','double','else','enum','extends','final','finally','float','for','goto','if','implements','import','instanceof','int','interface','long','native','new','package','private','protected','public','return','short','static','super','switch','synchronized','this','throw','throws','transient','try','void','volatile','while','true','false','null'}
FMT = re.compile(r'%(\d+\$)?(lld|ld|@|d|s|f)')

def convert(v):
    specs = list(FMT.finditer(v))
    has_fmt = bool(specs)
    if has_fmt:
        # escape literal % not part of a spec
        out, i, n = [], 0, 0
        pos = 0
        for m in specs:
            chunk = v[pos:m.start()].replace('%', '%%')
            out.append(chunk)
            n += 1
            idx = m.group(1) or f'{n}$'
            t = m.group(2)
            t = 's' if t in ('@', 's') else ('d' if t in ('d', 'ld', 'lld') else 'f')
            out.append(f'%{idx}{t}' if len(specs) > 1 else f'%{t}')
            pos = m.end()
        out.append(v[pos:].replace('%', '%%'))
        v = ''.join(out)
    v = (v.replace('\\', '\\\\').replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
          .replace("'", "\\'").replace('"', '\\"').replace('\n', '\\n'))
    if v[:1] in ('@', '?'):
        v = '\\' + v
    return v, has_fmt

for lang, folder in (('en', 'values'), ('ru', 'values-ru'), ('kk', 'values-kk')):
    lines = ['<?xml version="1.0" encoding="utf-8"?>',
             '<!-- Generated from FinTracker iOS Localizable.xcstrings. Do not edit by hand; add Android-only strings to strings_android.xml. -->',
             '<resources xmlns:tools="http://schemas.android.com/tools" tools:ignore="UnusedResources">']
    for key, entry in sorted(d['strings'].items(), key=lambda kv: kv[0].lower()):
        if not re.fullmatch(r'[a-z][A-Za-z0-9_]*', key):
            continue
        name = key + '_' if key in JAVA_KEYWORDS else key
        loc = entry.get('localizations', {})
        value = (loc.get(lang) or loc.get('en') or {}).get('stringUnit', {}).get('value')
        if value is None:
            continue
        text, has_fmt = convert(value)
        attr = '' if has_fmt or '%' not in value else ' formatted="false"'
        lines.append(f'    <string name="{name}"{attr}>{text}</string>')
    lines.append('</resources>')
    os.makedirs(os.path.join(dst, folder), exist_ok=True)
    open(os.path.join(dst, folder, 'strings.xml'), 'w').write('\n'.join(lines) + '\n')
    print(folder, len(lines) - 4)
