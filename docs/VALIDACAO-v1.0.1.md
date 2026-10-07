# Validação — Ritmo 1.0.1 (build 2)

- `testDebugUnitTest`: 21 testes, 0 falhas e 0 erros.
- Android/Robolectric API 35: 7 testes de repositório, 1 migration, 7 de atualização, 3 fluxos Compose e 3 modais de atualização.
- Novo fluxo Compose: clicar na lixeira, cancelar, confirmar e verificar a remoção do catálogo com preservação das séries históricas.
- Novo teste Room: exclusão remove os vínculos dos modelos, renumera os exercícios restantes e preserva integralmente as sessões antigas e o treino ativo. Reativar pelo mesmo nome mantém o mesmo id.
- `lintDebug`, `lintVitalRelease` e `assembleRelease`: aprovados.
- Assinatura v2 válida, RSA 3072, mesmo certificado de 1.0.0.
- APK conferido com aapt: `com.ritmo.treinos`, versionName 1.0.1, versionCode 2, minSdk 26, targetSdk 36.
- O schema Room continua em 2; não houve alteração do banco.
- Atualização opcional: forceUpdate false, minimumVersionCode 1.

APK SHA-256: `d3197f61d782266e0c0e7e12fbb5ca63941a38da7d1eb56b3d3b97c6c35141d2`

Certificado SHA-256: `0174c95cab08402c23be939195b5974879cd58c1043f095fa76cc752daf930ce`

Os testes foram executados em Android simulado. Não foi executado teste em celular físico nesta correção. A distribuição publica os metadados somente após disponibilizar o APK assinado. Nenhuma chave privada é enviada ao GitHub.
