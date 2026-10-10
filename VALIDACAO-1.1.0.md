# Validação do Ritmo 1.1.0 — build 6

## Estado real

Implementação feita sobre o projeto original 1.0.4. O backend foi implantado no projeto **Ritmo**, referência `cccpzlvnxmrjrmwvayqw`, organização ApolloCraft, região São Paulo, confirmado ACTIVE_HEALTHY. A migration `20261010233000_global_ranking.sql` e a Edge Function `delete-account` estão ativas. A URL e a chave publishable reais estão em `backend.properties.example` e no APK conectado.

O ranking público real responde. A implementação permanece **candidata a validação, sem publicação**: cadastro/login/refresh/logout/exclusão com sessões reais, entrega de e-mail, concorrência entre conexões independentes e instalação sobre a 1.0.4 em aparelho real ainda não foram concluídos. O plano da organização é Free; não foi criada assinatura paga nesta sessão. O usuário criou o projeto pelo painel.

## Testes no backend hospedado

- PostgreSQL real: **27 verificações aprovadas** em uma transação com fixtures sintéticas de usuários, sessões e claims JWT, revertida com ROLLBACK. Não são testes de login pelo Auth HTTP. Cobrem 100/25/0 XP, limite diário, idempotência, RLS entre contas, períodos, privacidade, recusa de sessão revogada e cascatas de exclusão.
- API real: **11 verificações aprovadas, 0 falhas**. Ranking semanal/mensal/geral, estrutura pública, configuração de Auth, limites de parâmetros e recusa de dados privados/exclusão sem sessão válida.
- Após os testes, o banco foi consultado: 0 usuários Auth, perfis, eventos e registros de XP. Nenhum dado de fixture foi mantido.
- Supabase Security Advisors: nenhuma ocorrência após a implantação e os testes.
- Performance Advisors: informação sobre três índices ainda não usados em banco recém-criado, sem recomendações de remoção neste estágio. Referência: https://supabase.com/docs/guides/database/database-linter?lint=0005_unused_index.
- Auth por e-mail está habilitado; confirmação de e-mail continua obrigatória (`mailer_autoconfirm=false`). SMTP e redirect de recuperação precisam ser configurados/confirmados no painel e testados.
- Uma tentativa adicional de repetir a suíte SQL após melhorar a seleção de datas perto da meia-noite retornou `Invalid or expired requestState` pelo conector. Não foi contada como sucesso. A primeira execução de 27 verificações tem evidência salva; a consulta posterior confirmou novamente 0 dados mantidos.
- Evidências em `backend-tests/evidence/hosted-postgres.json` e `hosted-rest.json`. Scripts em `backend-tests/hosted-transaction.sql` e `hosted-public-checks.py`.
- A aprovação automática rejeitou a implantação e a chamada de uma função temporária de testes com privilégios administrativos, JWT de gateway desativado e token próprio. O motivo foi a exposição de operações de criação/exclusão de contas e escrita administrativa sem autorização específica. **A função não foi implantada.** A única Edge Function presente é `delete-account`. Testes por essa abordagem aguardam autorização explícita; nenhuma chave service_role foi exportada para o cliente.

## Resultados locais

- Android/JUnit/Robolectric API 35: **63 testes, 0 falhas, 0 erros, 0 ignorados**.
- PostgreSQL/PGlite: **40 verificações aprovadas**, usando fixtures do schema Auth.
- Edge Function em TypeScript/Node: **8 verificações aprovadas**, com chamadas de Auth/REST simuladas.
- Room: esquema 3 gerado; migrações 1 → 2 → 3 e 2 → 3 exercitadas com dados históricos e treino ativo.
- Não foi instalado em celular físico nem em emulador Android real nesta etapa.

| Classe Android | Testes | Falhas |
|---|---:|---:|
| ApkDownloadsTest | 10 | 0 |
| AppFlowLocalTest | 3 | 0 |
| DownloadUiTest | 2 | 0 |
| HistoryDeletionTest | 4 | 0 |
| HistoryDeletionUiTest | 3 | 0 |
| MigrationTest | 2 | 0 |
| OnlineServiceTest | 9 | 0 |
| RankingQueueTest | 7 | 0 |
| RankingSyncTest | 3 | 0 |
| RankingUiTest | 3 | 0 |
| UpdateInfoTest | 7 | 0 |
| UpdateUiLocalTest | 3 | 0 |
| WorkoutRepositoryTest | 7 | 0 |

## Compilação e assinatura

- `testDebugUnitTest lintDebug assembleRelease`: recompilação conectada concluída com `BUILD SUCCESSFUL` em 9m1s. Os 63 testes foram executados novamente, sem falhas.
- Android Lint: **0 erros, 24 avisos**. Avisos são sugestões de KTX/SharedPreferences e targetSdk 36 mantido da base; nenhuma falha bloqueante.
- `lintVitalRelease`: aprovado.
- `apksigner verify --verbose --print-certs`: assinatura válida, certificado idêntico ao APK 1.0.4.
- Certificado SHA-256: `0174c95cab08402c23be939195b5974879cd58c1043f095fa76cc752daf930ce`.
- `aapt dump badging`: `com.ritmo.treinos`, versionCode 6, versionName 1.1.0, minSdk 26, compileSdk 37, targetSdk 36.
- APK conectado, assinado e recompilado nesta etapa: SHA-256 `303b2e3e90401900d95daf472f5fd8273dba172509027e7de99a6372ac749eda`. URL e chave publishable reais foram verificadas no DEX; nenhuma chave administrativa foi encontrada.
- Essa comparação confirma compatibilidade de certificado/pacote, não substitui a instalação sobre a 1.0.4 em um aparelho real.

## Cobertura e limites

A suíte Android verifica preservação de exercícios/treinos/histórico/cargas/anotações, reabertura do banco, backup/restauração, exclusões seletivas, atualização opcional/obrigatória e instalador. Os testes novos verificam login/logout/refresh com transporte simulado, PKCE, apelido/perfil, cache por conta, fila offline, resposta perdida após aceitação, ausência de envio de XP pelo cliente, troca de conta e ausência de XP retroativo.

A suíte SQL aplica a migration em PostgreSQL embutido, testa concessão 100/25/0, limite 125, UUID duplicado, eventos sobrepostos/fora da janela, RLS entre usuários, proibição de escrita direta em XP, períodos semanal/mensal/geral, Top 10/100, posição própria fora do Top 100, privacidade, revogação de sessão e cascade na exclusão de usuário.

O runtime PGlite serializa chamadas: a prova de concessão concorrente com conexões independentes em um Supabase hospedado permanece pendente. A proteção por bloqueio de perfil foi implementada e revisada, mas não constitui teste de produção.

Os testes da Edge Function verificam falta de autorização, usuário inválido/anônimo, sessão revogada, falha de exclusão e sucesso. A chamada real ao Supabase Admin Auth para excluir uma conta válida ainda não foi executada. O endpoint hospedado foi exercitado e recusou corretamente chamadas sem token ou com token inválido.

## Pendências antes de lançar

1. Concluído: projeto Ritmo criado pelo usuário, migration/RLS/RPC e Edge Function implantadas, testes SQL/REST públicos aprovados.
2. URL/chave publishable já preenchidas. Ainda confirmar redirect `ritmo://auth/recovery`, Site URL HTTPS e SMTP próprio no Auth para cadastros públicos.
3. Testar confirmação de e-mail, cadastro/login, refresh, recuperação, logout e exclusão com dois usuários reais.
4. Advisors e RLS com fixtures hospedadas concluídos; ainda exercitar acesso cruzado com JWTs de dois logins reais e concorrência independente.
5. Testar offline → reconexão em aparelho real, inclusive WorkManager e retomada após reinício.
6. Instalar o APK configurado por cima da 1.0.4 em aparelho/emulador real e conferir dados e AndroidKeyStore.
7. Publicar o APK somente após os testes acima; anunciar a atualização somente depois do download público estar disponível.

## Publicação

`version.json` e `distribution/release.json` foram preservados. O manifesto candidato 1.1.0 está em `release-candidate/version-1.1.0.json`. Nenhuma versão foi enviada ao GitHub. Backend Ritmo implantado; a publicação aguarda os testes pendentes acima.
