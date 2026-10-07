#!/usr/bin/env bash
# Run locally with an authenticated GitHub CLI. Never put a token in this script.
set -euo pipefail
cd "$(dirname "$0")/.."
repo=MARCELO887876653/ritmo-android
expected_owner=MARCELO887876653
command -v gh >/dev/null || { echo 'Instale a CLI gh e execute gh auth login.' >&2; exit 1; }
[[ "$(gh api user --jq .login)" == "$expected_owner" ]] || { echo 'A conta autenticada não é a conta esperada.' >&2; exit 1; }
[[ -f app/build/outputs/apk/release/app-release.apk ]] || { echo 'Compile o release assinado primeiro.' >&2; exit 1; }
[[ -f keystore.properties ]] || { echo 'Configure sua chave de assinatura permanente localmente.' >&2; exit 1; }
if gh repo view "$repo" >/dev/null 2>&1; then echo 'O repositório já existe. Não será sobrescrito. Publique manualmente.' >&2; exit 1; fi
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[[ -n "$sdk" ]] || { echo 'Defina ANDROID_HOME com o caminho do SDK.' >&2; exit 1; }
"$sdk/build-tools/36.0.0/apksigner" verify --verbose app/build/outputs/apk/release/app-release.apk
[[ -z "$(git status --porcelain)" ]] || { echo 'Faça commit do código antes de publicar.' >&2; exit 1; }
gh repo create "$repo" --public --source=. --remote=origin --push
git tag v1.0.0
git push origin v1.0.0
stage_dir=$(mktemp -d)
cp app/build/outputs/apk/release/app-release.apk "$stage_dir/ritmo-1.0.0.apk"
gh release create v1.0.0 "$stage_dir/ritmo-1.0.0.apk" --repo "$repo" --verify-tag --title 'Ritmo v1.0.0' --notes-file docs/RELEASE-v1.0.0.md
echo "Publicado: https://github.com/$repo/releases/tag/v1.0.0"
