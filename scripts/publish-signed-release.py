#!/usr/bin/env python3
"""Publish a locally signed APK; this workflow never receives the signing key."""
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
manifest = json.loads((root / 'distribution/release.json').read_text())
version = manifest['versionName']
assert re.fullmatch(r'\d+\.\d+\.\d+', version)
assert type(manifest['versionCode']) is int and manifest['versionCode'] > 0
assert manifest['forceUpdate'] is False
repo = os.environ['GITHUB_REPOSITORY']
assert repo == 'MARCELO887876653/ritmo-android'
tag = 'v' + version
filename = f'ritmo-{version}.apk'
apk = root / 'distribution' / filename
checksum = (root / 'distribution' / (filename + '.sha256')).read_text().split()[0]
assert re.fullmatch(r'[0-9a-f]{64}', checksum)
assert hashlib.sha256(apk.read_bytes()).hexdigest() == checksum, 'APK checksum mismatch'
assert manifest['downloadUrl'] == f'https://github.com/{repo}/releases/download/{tag}/{filename}'

def gh(*args, input_text=None):
    return subprocess.run(['gh', *args], input=input_text, text=True, check=True, capture_output=True).stdout

view = subprocess.run(['gh', 'release', 'view', tag, '--repo', repo, '--json', 'isDraft'], text=True, capture_output=True)
if view.returncode == 0:
    # Never overwrite an existing release asset. Reruns must find identical bytes.
    with tempfile.TemporaryDirectory() as tmp:
        gh('release', 'download', tag, '--repo', repo, '--pattern', filename, '--dir', tmp)
        assert hashlib.sha256((Path(tmp) / filename).read_bytes()).hexdigest() == checksum, 'Existing APK differs'
else:
    gh('release', 'create', tag, str(apk), '--repo', repo, '--target', os.environ['GITHUB_SHA'],
       '--draft', '--title', f'Ritmo {version}', '--notes-file', str(root / 'docs' / f'RELEASE-{tag}.md'))
gh('release', 'edit', tag, '--repo', repo, '--draft=false', '--latest')

# Advertise an update only after its signed APK is publicly downloadable.
current = json.loads(gh('api', f'repos/{repo}/contents/version.json?ref=main'))
old = json.loads(base64.b64decode(current['content']))
assert old['versionCode'] <= manifest['versionCode'], 'Refusing to downgrade update metadata'
if old != manifest:
    payload = {'message': f'release: advertise {version} after APK upload', 'branch': 'main',
               'sha': current['sha'], 'content': base64.b64encode((json.dumps(manifest, ensure_ascii=False, indent=2) + '\n').encode()).decode()}
    gh('api', '--method', 'PUT', f'repos/{repo}/contents/version.json', '--input', '-', input_text=json.dumps(payload))
print(f'Published {tag}: signed APK uploaded and version.json updated.')
