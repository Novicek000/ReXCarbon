#!/usr/bin/env python3
from pathlib import Path
import re,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'app/src/main/res'
def strings(folder):
    return {e.attrib['name']:''.join(e.itertext()) for e in ET.parse(RES/folder/'strings.xml').getroot()}
en,pl=strings('values'),strings('values-pl')
assert en.keys()==pl.keys()
for key in en:
    assert sorted(re.findall(r'%[0-9]+\$[a-z]',en[key]))==sorted(re.findall(r'%[0-9]+\$[a-z]',pl[key])),key
refs=set()
for java in (ROOT/'app/src/main/java/pl/rexlauncher').glob('*.java'):
    refs.update(re.findall(r'(?<![\w.])R\.string\.(\w+)',java.read_text()))
assert refs<=en.keys(),refs-en.keys()
print(f'PASS: {len(en)} EN/PL pairs, format placeholders and Java resource references')
