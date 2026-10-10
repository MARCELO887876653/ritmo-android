# Ritmo 1.1.0 — ranking global

## Estado desta implementação

O código foi desenvolvido sobre o projeto 1.0.4. ApplicationId: `com.ritmo.treinos`; versão: 1.1.0; build: 6. A integração usa Supabase Auth, Data API/RPC e uma Edge Function para exclusão de conta. A chave administrativa fica somente no ambiente da Edge Function.

O backend remoto foi implantado no projeto **Ritmo**, referência `cccpzlvnxmrjrmwvayqw`, na organização ApolloCraft, região São Paulo. URL pública: `https://cccpzlvnxmrjrmwvayqw.supabase.co`. As tabelas, políticas RLS, RPCs e a Edge Function `delete-account` estão ativas. O arquivo `backend.properties.example` contém somente a URL e a chave pública do projeto; copie-o para `backend.properties` antes de compilar. O CI faz essa cópia automaticamente.

Foram aprovadas 27 verificações no PostgreSQL hospedado, usando fixtures sintéticas de Auth/JWT em uma transação revertida. Os testes REST públicos estão registrados em `VALIDACAO-1.1.0.md`. **Esses testes não comprovam login real, entrega de e-mail ou exclusão por uma sessão real.** O APK conectado é candidato a validação; a publicação continua bloqueada até concluir os testes de Auth, concorrência e aparelho real.

## Dados e pontuação

- Room 2 → 3 mantém exercícios, modelos, histórico, séries e anotações. Migração 1 → 2 continua registrada. Não há reset destrutivo.
- Cada treino novo registra um UUID estável e, se a participação estiver ativa, a conta que o iniciou. A conclusão e o evento pendente são gravados na mesma transação.
- Conta convidada, histórico antigo, restauração de backup e treino iniciado antes de participar não geram XP retroativo.
- Login/logout preservam o banco local. Trocar de conta não transfere pendências. Para creditar um treino, a conta ativa na conclusão deve ser a mesma do início.
- Um treino válido contém pelo menos uma série concluída e dura de 1 minuto a 24 horas. O servidor valida os horários declarados; não recebe séries, cargas, nomes de exercícios nem anotações. Não é possível provar a execução física do treino: o ranking é recreativo.
- Primeiro evento aceito do dia: 100 XP; segundo: 25 XP; demais: 0. Limite: 125 XP. A ordem é a de aceitação no servidor, independentemente da ordem de conclusão em dispositivos diferentes.
- Dia, semana (segunda-feira) e mês seguem UTC. Às 00h UTC são 21h em Brasília. Nível = 1 + XP geral / 500, arredondado para baixo.
- Pendências podem sincronizar por até 7 dias, com tolerância de 2 minutos para horário futuro. Mantenha data/hora automática do aparelho.
- Um bloqueio por perfil no PostgreSQL serializa concessões de XP. UUIDs únicos e respostas idempotentes permitem repetir pedidos após falhas de rede. Intervalos sobrepostos são rejeitados.
- O WorkManager usa rede conectada, tentativas com recuo exponencial e verificação periódica a cada 15 minutos. A lista de ranking visível consulta a cada 30 segundos e oferece atualização manual; mostra dados salvos em caso de falha.
- Excluir um histórico local mantém o identificador do evento online e não devolve XP. A opção de excluir conta remove os dados online, preservando os treinos locais.
- Desativar participação oculta o perfil e pausa a sincronização. Ao reativar, o servidor estabelece novo instante de elegibilidade; pendências anteriores a ele não pontuam.

## Configurar o Supabase

1. O projeto dedicado Ritmo já existe e foi confirmado pelo conector como ACTIVE_HEALTHY. Não recrie nem reaplique a migration em um banco que já a possui. Para outro ambiente, use um projeto dedicado e confira o custo exibido.
2. Migration já aplicada no projeto Ritmo. Para um ambiente novo, aplique `supabase/migrations/20261010233000_global_ranking.sql` pelo conector autorizado ou Supabase CLI. O SQL não exclui tabelas existentes; usa prefixo `ritmo_` e schema interno `ritmo_private`.
3. Habilite e-mail/senha no Auth e mantenha confirmação de e-mail. Em Authentication → URL Configuration, adicione `ritmo://auth/recovery` como Redirect URL. Defina um Site URL público válido (ex.: página de apresentação do app) para o link de confirmação de cadastro. A recuperação deve ser solicitada no próprio app/aparelho; o link troca um código usando PKCE e então permite definir nova senha.
4. Para distribuição além de testes, configure um serviço SMTP próprio no Supabase Auth: o provedor de e-mail padrão tem limitações de destinatários e frequência. Não desative a confirmação para contornar essas limitações.
5. Implante a Edge Function `delete-account`, com `verify_jwt=false` conforme `supabase/config.toml`. A função faz sua própria validação do token por `/auth/v1/user` e confirma a sessão viva por RPC. Os segredos `SUPABASE_URL` e `SUPABASE_SERVICE_ROLE_KEY` são fornecidos pelo ambiente Supabase, nunca pelo APK.
6. Copie `backend.properties.example` para `backend.properties`; preencha somente a URL HTTPS do projeto e uma chave **publishable** (ou legacy anon). O Gradle rejeita chaves secret/service_role. Esse arquivo é ignorado no Git.
7. Consulte os advisors de segurança/performance e corrija achados relevantes antes de publicar.
8. Execute os testes de dois usuários reais e confira cadastro, confirmação de e-mail, recuperação, sessão, ranking, fila offline e exclusão de conta. Só então compile e publique.

CLI fixada usada na preparação: Supabase 2.120.0. Descubra parâmetros com `supabase --help`, `supabase link --help`, `supabase db push --help`, `supabase functions deploy --help`. Exemplo após autenticar:

```bash
supabase link --project-ref REF_DO_PROJETO
supabase db push
supabase functions deploy delete-account --no-verify-jwt
```

Não coloque access tokens de administração na linha de comando, no código, no APK, nos logs ou nos arquivos de entrega.

## Segurança

RLS está ativa nas três tabelas públicas. `authenticated` pode ler somente seu perfil, eventos e livro de XP, e não pode alterá-los diretamente. `anon` acessa apenas a projeção do ranking. As funções privilegiadas ficam no schema interno, usam `search_path=''`, permissões explícitas e verificação de usuário/sessão quando necessário. Os wrappers públicos são `SECURITY INVOKER`.

O ranking retorna apelido, posição, XP e nível; e-mail não é retornado. O servidor não recebe dados detalhados dos treinos. Tokens de sessão ficam cifrados com AES-GCM usando AndroidKeyStore, fora de Room/backup. Backup automático e transferência de dados do Android são explicitamente excluídos pelas regras XML. O backup manual do app continua disponível. Recuperação usa PKCE; o APK nunca instala atualizações silenciosamente.

Logout offline remove a sessão do aparelho; a revogação remota pode não ocorrer sem rede. Tokens já emitidos têm validade até o vencimento se a sessão remota não for revogada. Operações de XP também exigem uma sessão Auth viva. Excluir a conta usa o Admin Auth no servidor e a chave estrangeira remove seus dados correspondentes; as RPCs rejeitam JWT antigo quando o usuário deixa de existir.

## Testes

```bash
./gradlew testDebugUnitTest lintDebug assembleRelease
cd backend-tests
npm ci
npm test
```

Node 22.18 ou superior é necessário para os testes da Edge Function em TypeScript. Os oito testes do endpoint usam respostas de rede simuladas. A suíte PostgreSQL usa PGlite com fixtures do schema Auth; não implanta Supabase. Requisições concorrentes nesse runtime são serializadas pelo cliente: essa suíte verifica consistência, mas um teste de concorrência com conexões independentes no backend hospedado continua necessário.

## Código

| Parte | Arquivo |
|---|---|
| Backend e RLS | `supabase/migrations/20261010233000_global_ranking.sql` |
| Exclusão de conta real | `supabase/functions/delete-account/index.ts` |
| Auth/HTTPS/RPC/PKCE | `online/OnlineService.kt` |
| Armazenamento cifrado de sessão | `online/SessionVault.kt` |
| Fila Room | `online/RankingModels.kt` |
| Sincronização/WorkManager | `online/RankingSync.kt` |
| Migração 2 → 3 | `data/RitmoDatabase.kt` |
| Evento atômico na conclusão | `data/WorkoutRepository.kt` |
| Telas novas | `ui/OnlineScreens.kt` |
| Navegação | `ui/RitmoApp.kt` |

Caminhos Kotlin relativos a `app/src/main/java/com/ritmo/treinos/`. Todos os arquivos atuais de atualização, backup, histórico e exclusão seletiva foram preservados.


## Documentação consultada

- https://developer.android.com/develop/background-work/background-tasks/persistent
- https://developer.android.com/identity/data/autobackup
- https://supabase.com/changelog.md
- https://supabase.com/docs/guides/auth/passwords
- https://supabase.com/docs/guides/auth/sessions/pkce-flow
- https://github.com/supabase/auth/blob/master/openapi.yaml
- https://supabase.com/docs/guides/database/postgres/row-level-security

## Retomar os testes hospedados

A função temporária com privilégios administrativos proposta para testes foi rejeitada pela aprovação automática e não foi implantada. Ela criaria duas contas descartáveis, exercitaria login/logout/refresh, chamadas concorrentes de XP e exclusão de conta, e removeria os dados ao final. Essa abordagem permanece bloqueada sem autorização específica. A única função implantada é `delete-account`.

A alternativa executada usa `backend-tests/hosted-transaction.sql`: todos os dados e exclusões sintéticos ficam dentro de BEGIN/ROLLBACK. Execute apenas em ambiente próprio de teste; não são contas criadas pelo Auth HTTP. O script `backend-tests/hosted-public-checks.py` consulta o backend real e verifica apenas pedidos públicos e pedidos que devem ser recusados. Nenhum desses scripts exige uma chave administrativa local.

No painel Authentication → URL Configuration, autorize exatamente `ritmo://auth/recovery` em Redirect URLs. Defina uma Site URL HTTPS válida (por exemplo, a página pública do repositório), para evitar o redirecionamento padrão para localhost na confirmação. O cadastro atual exige confirmar e-mail antes do primeiro login. Em Authentication → SMTP Settings, configure um provedor SMTP próprio antes de abrir o cadastro ao público. Sem isso, o SMTP padrão aceita somente destinatários autorizados da equipe do projeto. Não desative a confirmação de e-mail para contornar a configuração.
