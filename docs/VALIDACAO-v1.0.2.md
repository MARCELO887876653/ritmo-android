# Validação — Ritmo 1.0.2 (build 3)

Executada em 7 de outubro de 2026.

- `testDebugUnitTest`: 33 testes, 0 falhas, 0 erros, 0 ignorados.
- `lintDebug`, `lintVitalRelease` e `assembleRelease`: concluídos com sucesso.
- Lint debug: 0 erros e 16 avisos. Incluem estilo KTX, `commit()` intencional no dispatcher IO para persistir downloads, target 36 com compile 37 e configurações/dependências anteriores. Não houve atualização geral de dependências neste ajuste.
- APK verificado com apksigner: assinatura v2 válida, RSA 3072, mesmo certificado de 1.0.0 e 1.0.1.
- APK verificado com aapt: `com.ritmo.treinos`, versionCode 3, versionName 1.0.2, minSdk 26, targetSdk 36, INTERNET e REQUEST_INSTALL_PACKAGES.
- Room continua no schema 2, sem alterações de entidades. A migration 1→2 permanece registrada e testada.

## Testes executados

| Grupo | Quantidade | Cobertura |
|---|---:|---|
| WorkoutRepositoryTest | 7 | Modelos, sessões, séries, exclusão com preservação de histórico, transações e backup |
| MigrationTest | 1 | Migration Room 1→2 preserva os dados |
| UpdateInfoTest | 7 | JSON, URL oficial, versão instalada, cache e falha de rede |
| AppFlowLocalTest | 3 | Criar exercício/treino, registrar duas sessões, reabrir, navegar offline e excluir com confirmação |
| UpdateUiLocalTest | 3 | Atualização opcional, obrigatória e cache obrigatório offline |
| ApkDownloadsTest | 10 | Progresso, restauração, pausa offline, retry, tamanho, cancelamento, assinatura/pacote/versão, permissão e FileProvider privado |
| DownloadUiTest | 2 | Botões/progresso/cancelamento/instalação e atualização obrigatória sem Depois |

Também foi corrigido um acesso prematuro à navegação: os atalhos ficam desabilitados enquanto os dados iniciais carregam e antes de o NavHost estar disponível. Os testes aguardam o carregamento e a tela de sessão antes de editar os campos.

Os testes Android/Compose foram executados com Robolectric API 35. A transferência usa backend controlado nos testes; não foi testado download real de uma release 1.0.2 ainda não publicada, nem instalação em celular físico. O teste do instalador verifica permissão do Android, Intent, MIME, concessão de leitura e conteúdo do arquivo privado; a instalação final depende da confirmação do usuário no Android.

A Release 1.0.2 está preparada para publicação pelo workflow. O manifesto público da raiz permanece na versão 1.0.1 até o upload do APK; `distribution/release.json` contém 1.0.2, build 3, minimumVersionCode 1, forceUpdate false. A publicação exige enviar os commits ao GitHub conforme `PUBLICAR-v1.0.2.md`.

APK SHA-256: `4c7e43e7967b3e4d8954b984445ec792a0baa2440116f143c9363762f8d49049`

Certificado SHA-256: `0174c95cab08402c23be939195b5974879cd58c1043f095fa76cc752daf930ce`

Nenhuma chave privada, senha ou token foi incluído no projeto de distribuição. A chave privada original continua necessária para assinar as próximas versões.
