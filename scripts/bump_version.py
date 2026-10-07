#!/usr/bin/env python3
"""Prepare metadata. Publish version.json only after uploading the signed APK."""
import argparse, json, pathlib, re
root = pathlib.Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser(); p.add_argument('version_name'); p.add_argument('version_code', type=int); p.add_argument('--force', action='store_true'); p.add_argument('--minimum', type=int, default=1); p.add_argument('--message', default='Nova atualização disponível.'); args = p.parse_args()
assert re.fullmatch(r'\d+\.\d+\.\d+', args.version_name), 'Use 1.1.0, por exemplo.'
f = root/'app/build.gradle.kts'; text = f.read_text(); previous = int(re.search(r'versionCode = (\d+)', text)[1])
assert args.version_code > previous, 'versionCode deve aumentar.'
assert 1 <= args.minimum <= args.version_code
text = re.sub(r'versionCode = \d+', f'versionCode = {args.version_code}', text)
text = re.sub(r'versionName = "[^"]+"', f'versionName = "{args.version_name}"', text)
repo = re.search(r'GITHUB_REPOSITORY", "\\"([^\\]+)\\"', text)[1]
manifest = dict(versionCode=args.version_code,versionName=args.version_name,minimumVersionCode=args.minimum,forceUpdate=args.force,message=args.message,downloadUrl=f'https://github.com/{repo}/releases/download/v{args.version_name}/ritmo-{args.version_name}.apk')
f.write_text(text); (root/'version.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
print(f'Preparada versão {args.version_name}, build {args.version_code}. Publique o APK antes de enviar version.json ao main.')
