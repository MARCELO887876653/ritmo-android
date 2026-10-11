# Publicar Ritmo 1.1.0 — somente após o backend real passar nos testes

O backend real já está implantado e a configuração pública está em `backend.properties.example`. O APK conectado continua candidato de validação; não publique antes de concluir Auth/e-mails, concorrência e instalação sobre a 1.0.4. O `version.json` público e o fluxo de release existentes foram mantidos, sem anunciar a 1.1.0.

1. Conclua `docs/RANKING-v1.1.0.md` e `docs/GOOGLE-E-CONFIRMACAO.md`, incluindo habilitação de Google, URLs de retorno e testes remotos de dois usuários.
2. Confira que a versão ainda não foi usada em uma release: 1.1.0 / build 6. Se algum build 6 já tiver sido publicado, aumente o versionCode.
3. Copie `backend.properties.example` para `backend.properties` (URL/chave pública já preenchidas). Configure `keystore.properties` com a **chave original**. Não envie esses arquivos privados ao repositório.
4. Execute `./gradlew testDebugUnitTest lintDebug assembleRelease` e os testes do backend. Verifique a assinatura com `apksigner verify --print-certs`.
5. O certificado deve coincidir com o APK 1.0.4. Preserve `com.ritmo.treinos`. Faça backup local pelo app antes de testar a instalação por cima.
6. Copie `app/build/outputs/apk/release/app-release.apk` para `ritmo-1.1.0.apk`. Publique uma release `v1.1.0` no repositório `MARCELO887876653/ritmo-android`, com esse APK.
7. Confira o download público e somente depois substitua `version.json` pelo arquivo preparado `release-candidate/version-1.1.0.json`.
8. Mantenha `forceUpdate=false`: esta é uma atualização opcional. Não altere o `minimumVersionCode` sem uma razão técnica validada.

A chave original fica fora do projeto. Nunca perca o `.jks`, alias e senhas. Uma chave diferente impede atualizar a instalação anterior sem desinstalar.
