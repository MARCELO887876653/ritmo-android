# Instalar o candidato conectado Ritmo 1.1.0 — build 6

Este APK se conecta ao backend real Ritmo, mas ainda aguarda validação completa de Auth/e-mail, concorrência e aparelho real. Não é uma publicação final.

1. Na versão 1.0.4 instalada, faça um backup pelo próprio Ritmo antes de testar a atualização. Não desinstale o app: isso apagaria os dados locais.
2. Baixe `ritmo-1.1.0.apk` e abra o arquivo no Android. Autorize a instalação pelo aplicativo usado para abrir o download, caso o Android solicite.
3. O APK usa o mesmo applicationId e a assinatura original. O build 6 é superior ao 5 da 1.0.4. A instalação sobre uma assinatura diferente (como debug) pode ser recusada; não desinstale para contornar isso sem antes preservar seu backup.
4. Verifique exercícios, treinos, séries, cargas, anotações e históricos antigos. Eles não recebem XP retroativo.
5. Abra Ranking Global. A consulta pública deve funcionar mesmo sem uma conta. Para participar, cadastre-se e confirme o e-mail neste aparelho, ou use **Entrar com Google** após habilitar o provedor. A configuração de retorno ao app e Google está em `docs/GOOGLE-E-CONFIRMACAO.md`. Se um link antigo abriu localhost, tente entrar por e-mail/senha: a confirmação pode já ter ocorrido.
6. Enquanto o SMTP próprio não for configurado, use para testes apenas um e-mail autorizado na equipe da organização Supabase. Seu e-mail não aparece no ranking; somente o apelido é público.
7. Após entrar, salve o apelido e habilite a participação. Comece um treino novo, conclua pelo menos uma série e finalize após no mínimo 60 segundos. O primeiro evento válido do dia concede 100 XP; o segundo, 25. O dia do ranking usa UTC.
8. Para testar offline, comece um treino novo com a participação ativa, desligue a internet, finalize o treino e confira a fila pendente. Reconecte e use Sincronizar. A fila usa WorkManager e UUIDs para evitar XP duplicado.
9. Teste logout e confira que os históricos locais continuam disponíveis. Não teste exclusão na sua conta principal; use uma conta de teste.

A configuração necessária de SMTP está em `docs/RANKING-v1.1.0.md`, e o passo a passo dos links/Google está em `docs/GOOGLE-E-CONFIRMACAO.md`. Consulte `VALIDACAO-1.1.0.md` para separar os testes aprovados dos pendentes. `PUBLICAR-v1.1.0.md` descreve a publicação somente depois de terminar a validação.
