# Validação da versão 1.0.0 (build 1)

Verificações executadas no ambiente de desenvolvimento:

- `assembleDebug`: compilação concluída, APK debug gerado.
- `testDebugUnitTest`: **14 testes, 0 falhas, 0 erros** (JUnit + Android/Robolectric API 35).
- `lintDebug`: aprovado; relatório gerado pelo Android Lint.
- `assembleRelease`: compilação, R8 e empacotamento concluídos.
- `lintVitalRelease`: aprovado.
- `apksigner verify --verbose --print-certs`: assinatura v2 válida, RSA 3072 bits.
- `aapt dump badging`: applicationId `com.ritmo.treinos`, versionCode 1, versionName 1.0.0, minSdk 26, targetSdk 36, compileSdk 37.

## Testes de dados e atualização

6 testes do repositório: exercício reutilizado em duas sessões, várias séries, cópia de carga/repetições com conclusão desmarcada, um único treino ativo, exclusão com renumeração, nomes históricos preservados, exclusão de modelo preservando histórico, validação de valores, backup/restauração atômica, persistência de treino ativo ao fechar/reabrir o banco em disco.

1 teste de migração: cria o schema 1 exportado pelo Room, insere exercício/modelo/relação/sessão/séries/observações, abre com schema 2, executa a migration e confirma a preservação dos valores e da coluna nova.

7 testes de atualização: opcional, obrigatória, mínimo compatível, versão já instalada, JSON/URL inválidos, cache corrompido, ausência de internet, timeout e recuperação de uma consulta válida. A rede é injetada nos testes de falha para resultados reproduzíveis.

## Artefato

APK: `ritmo-1.0.0.apk` (release assinado, cerca de 1,5 MB).

SHA-256 do APK:
`59c29e062f0dc42610436dcc57ea988f5853e58b69b901eb163f3d51d9c2e8da`

SHA-256 do certificado permanente:
`0174c95cab08402c23be939195b5974879cd58c1043f095fa76cc752daf930ce`

## Testes de interface

Execução em emulador Android 15 em andamento. O resultado final será registrado antes da entrega.

## Publicação

Projeto, manifesto e release estão preparados localmente para `MARCELO887876653/ritmo-android`. Ainda não foram publicados. O conector disponível permite ler/escrever em repositórios existentes, mas não oferece criar repositório nem criar/upload de Releases. A URL de distribuição só ficará acessível após a publicação; até lá, falhas na consulta liberam o uso offline normalmente.
