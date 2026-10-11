# Mudanças da 1.0.4 para a 1.1.0 (build 6)

- Ranking opcional com filtros semanal, mensal e geral, Top 3 destacado, Top 10/100 e posição própria.
- Login/cadastro por e-mail e senha via Supabase Auth, recuperação por PKCE, logout e sessão cifrada no AndroidKeyStore.
- Botão Entrar com Google, OAuth pelo navegador com PKCE e retorno ao app; requer habilitação do provedor no servidor.
- Cadastro solicita retorno `ritmo://auth/confirm`, com PKCE e consumo único do código. Instruções para corrigir a Site URL localhost no Auth.
- Apelido público, escolha de participação e exclusão de conta online pela Edge Function.
- XP calculado no servidor: 100/25/0 por dia, com teto de 125. Níveis e conquistas por constância.
- Fila persistente Room e sincronização WorkManager sem upload de cargas, anotações ou histórico detalhado.
- Migrations SQL versionadas, permissões explícitas, RLS, índices e idempotência.
- Room 2 → 3 sem perda de dados; registros antigos não recebem XP retroativo.
- Navegação atual, atualização/download/instalação, backup, histórico por exercício, exclusão de exercício e exclusão seletiva de histórico preservados.

O backend real Ritmo já está implantado; Google, URLs de retorno e SMTP ainda exigem configuração no Auth. Nenhuma release foi publicada e o manifesto público não foi alterado. Consulte o relatório de validação para distinguir testes locais e testes remotos pendentes.
