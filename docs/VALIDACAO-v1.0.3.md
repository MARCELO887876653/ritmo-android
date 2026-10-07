# Validação — Ritmo 1.0.3 / build 4

- Compilação `testDebugUnitTest assembleRelease`: sucesso.
- 33 testes automatizados: 0 falhas, 0 erros, 0 ignorados.
- `lintVitalRelease`: aprovado.
- Dentro de `app/`, somente `build.gradle.kts` foi alterado: versionName 1.0.2 → 1.0.3; versionCode 3 → 4. Fontes, recursos, manifest, banco, migrations e dependências são idênticos à versão publicada 1.0.2.
- APK assinado com o certificado original e applicationId `com.ritmo.treinos`, minSdk 26, targetSdk 36.
- Atualização opcional: minimumVersionCode 1; forceUpdate false.
- Testes Android/Compose executados com Robolectric API 35. Não houve instalação em celular físico nesta entrega. O teste de atualização pelo usuário depende de publicar a release e o manifesto, conforme `PUBLICAR-v1.0.3.md`.

APK SHA-256: `66514aa14da02632260700dfe48310c0366bf38635f6412a3b76169deef49f8a`

Certificado SHA-256: `0174c95cab08402c23be939195b5974879cd58c1043f095fa76cc752daf930ce`
