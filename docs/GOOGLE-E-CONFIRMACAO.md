# Google e confirmação de e-mail no Ritmo 1.1.0

## Estado

O APK inclui **Entrar com Google** e retorno da confirmação de cadastro ao aplicativo. O código foi integrado ao Auth e à navegação existentes; nenhum dado Room foi removido. O fluxo Google abre o navegador do Android, permite escolher a conta e retorna ao Ritmo. Não utiliza WebView nem pede senha Google dentro do app.

Em 11/10/2026 UTC, a API real `/auth/v1/settings` informou `external.google=false`: o provedor Google ainda precisa de configuração. O conector disponível não expõe edição de configurações Auth nem criação de credenciais Google. Não foi usado token administrativo alternativo, nem alegada ativação do provedor. Testes com uma conta Google real continuam pendentes.

## Corrigir o endereço localhost da confirmação

1. Abra o projeto **Ritmo** no [painel de URLs do Supabase](https://supabase.com/dashboard/project/cccpzlvnxmrjrmwvayqw/auth/url-configuration).
2. Em **Site URL**, troque `http://localhost:3000` por `ritmo://auth/confirm` e salve.
3. Em **Redirect URLs**, adicione separadamente e salve:

   - `ritmo://auth/confirm`
   - `ritmo://auth/recovery`
   - `ritmo://auth/google`

4. Use o template padrão de confirmação em Authentication → Email Templates. O botão deve apontar para `{{ .ConfirmationURL }}`, que verifica o e-mail no servidor antes do retorno. Não substitua por um link direto para o app ou para localhost. Se houver template personalizado, confira o destino e o uso de RedirectTo.
5. Instale o APK atualizado e solicite um cadastro/link novo. Abra-o no mesmo aparelho. Um link emitido anteriormente pode ter sido consumido e não passa a conter os novos parâmetros PKCE.

Se o navegador exibiu localhost depois do clique, a confirmação pode já ter ocorrido. Tente entrar com e-mail e senha antes de criar outra conta. A consulta agregada ao Auth encontrou uma conta confirmada e uma pendente; ela não identifica qual conta corresponde ao usuário da conversa.

O app solicita explicitamente `redirect_to=ritmo://auth/confirm` e usa PKCE. Ao concluir no mesmo aparelho, troca o código pelo Auth e abre Meu Perfil. Em links antigos sem PKCE, o app não importa tokens de fragmentos: orienta a entrada por e-mail e senha. O prazo local do pedido de cadastro é 24 horas; a validade e o uso único do código de retorno também são verificados pelo servidor. Pedidos Google e recuperação têm prazo local de 10 minutos.

## Habilitar Google

1. Abra o [Google Cloud Console](https://console.cloud.google.com/) com a conta responsável pelo app. Se necessário, crie um projeto para Ritmo.
2. Em **Google Auth Platform**, configure **Branding** com o nome Ritmo e e-mail de suporte. Em **Audience**, escolha público externo se usuários fora da organização forem entrar. Durante Testing, inclua seu Gmail em Test users.
3. Em **Data Access**, solicite somente `openid`, e-mail e perfil básicos. Ritmo não solicita Drive, Gmail, contatos nem acesso offline às APIs Google.
4. Em **Clients → Create client**, escolha **Web application**. O cliente web é usado pelo servidor Supabase no fluxo OAuth pelo navegador, mesmo sendo um aplicativo Android.
5. Em **Authorized redirect URIs**, adicione exatamente:

   `https://cccpzlvnxmrjrmwvayqw.supabase.co/auth/v1/callback`

6. Copie Client ID e Client Secret diretamente para **Authentication → Sign In / Providers → Google** no [projeto Supabase](https://supabase.com/dashboard/project/cccpzlvnxmrjrmwvayqw/auth/providers). Habilite Google e salve. Não envie Client Secret por chat, não coloque no backend.properties e não publique no GitHub.
7. Confirme que `ritmo://auth/google` está na lista de Redirect URLs. O endereço HTTPS do passo 5 é o retorno Google → Supabase; o endereço `ritmo://` é o retorno Supabase → Android.
8. No APK, toque **Entrar com Google**, escolha uma conta autorizada e conclua. Verifique retorno ao perfil, participação opcional, logout e preservação dos treinos. Cancele a escolha e confira se continua desconectado. Teste também após encerrar o processo enquanto o navegador está aberto.

Este fluxo não exige Client Secret ou Client ID Google dentro do APK, nem um cliente Android com SHA-1. Não desative a confirmação de e-mail/senha para habilitar Google. O servidor valida a identidade recebida pelo provedor.

## Segurança e testes

Os verificadores PKCE de Google, confirmação e recuperação são separados e persistem em preferências privadas excluídas de backup. Código sem pedido local válido, URI com outro host/caminho/porta/userinfo, pedido expirado e repetição após sucesso são recusados. Entrar por senha ou sair cancela pedidos pendentes. Um novo pedido da mesma modalidade substitui o anterior. Tokens recebidos em fragmentos não são importados; erros do provedor recebem mensagens fixas. Tokens de API Google e metadados de perfil são descartados. Somente a sessão Supabase e o e-mail/id necessários à conta são persistidos cifrados pelo AndroidKeyStore.

Google ainda não habilitado gera uma explicação no app e mantém disponíveis e-mail/senha e treinos offline. A verificação pública do provedor exige internet; falhas não removem dados locais. Nenhuma alteração SQL ou migração Room adicional foi necessária.

Consulte `VALIDACAO-1.1.0.md` para resultados automatizados e pendências. O funcionamento completo dos links e da conta Google exige a configuração acima e teste no aparelho; não foi declarado aprovado sem essa evidência.

## Referências oficiais

- https://supabase.com/docs/guides/auth/social-login/auth-google
- https://supabase.com/docs/guides/auth/redirect-urls
- https://supabase.com/docs/guides/auth/native-mobile-deep-linking
- https://supabase.com/docs/guides/auth/sessions/pkce-flow
- https://github.com/supabase/auth-js/blob/master/src/GoTrueClient.ts
