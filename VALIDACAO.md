# Validação da versão 1.0.0 (build 1)

Verificações executadas no ambiente de desenvolvimento:

- `assembleDebug`: compilação concluída, APK debug gerado.
- `testDebugUnitTest`: **19 testes, 0 falhas, 0 erros** (JUnit + Android/Robolectric API 35).
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
`f45fb5ea29cc62f92cb854d077051889fd12f05a0961335922adb57eb82da02a`

SHA-256 do certificado permanente:
`0174c95cab08402c23be939195b5974879cd58c1043f095fa76cc752daf930ce`

## Testes de interface

**5 testes de interface passaram no Android/Robolectric API 35, executando Compose e MainActivity:**

- Cadastro de exercício e modelo, registro/conclusão de duas sessões do mesmo exercício, cópia de séries anteriores e retorno correto ao início.
- Recriação da Activity com treino ativo e navegação para histórico/progresso offline.
- Modal opcional com “Depois”, modal obrigatório sem dispensa e ausência de aviso quando a versão já está instalada.

O teste completo detectou e levou à correção de navegação duplicada após finalizar um treino; o ViewModel é agora o único responsável por essa transição.

A suíte de instrumentação foi compilada. A tentativa de `connectedDebugAndroidTest` em emulador externo sem KVM foi **bloqueada pelo ambiente**: Android Emulator conectado informou “Unknown API Level”, e o runner recusou o dispositivo. Não há confirmação de execução em celular físico/emulador completo nesta entrega. O CI inclui essa suíte para execução manual em runner com aceleração. Os 19 testes aprovados são os testes de Android simulado, não testes instrumentados no dispositivo.

## Publicação

Projeto, manifesto e release estão preparados localmente para `MARCELO887876653/ritmo-android`. Ainda não foram publicados. O conector disponível permite ler/escrever em repositórios existentes, mas não oferece criar repositório nem criar/upload de Releases. A URL de distribuição só ficará acessível após a publicação; até lá, falhas na consulta liberam o uso offline normalmente.
