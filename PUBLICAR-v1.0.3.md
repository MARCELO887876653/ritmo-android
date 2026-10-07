# Publicar e testar Ritmo 1.0.3

Esta versão altera apenas o nome/build do aplicativo: 1.0.3 / 4. O APK usa a mesma assinatura e banco da 1.0.2. Os arquivos adicionais são exclusivamente de distribuição e documentação.

Para testar a atualização dentro do aplicativo, mantenha **1.0.2** instalada no celular até publicar **1.0.3**. Não instale manualmente 1.0.3 antes do teste, pois o app já estará atualizado e não mostrará o aviso.

Extraia este projeto em uma pasta nova, fora de Downloads no Termux, entre na pasta `ritmo` e execute:

```bash
gh auth setup-git
git push origin main
```

O workflow **Publish signed APK** publica `v1.0.3`, envia `ritmo-1.0.3.apk` e só depois anuncia build 4 em `version.json`. Nenhuma chave privada ou senha é enviada ao GitHub. Não configure secrets de assinatura para publicar este APK pronto.

Para acompanhar:

```bash
gh run list --repo MARCELO887876653/ritmo-android --workflow publish-release.yml
gh release view v1.0.3 --repo MARCELO887876653/ritmo-android
```

Depois da publicação, na 1.0.2 instalada: **Configurações → Verificar agora → Atualizar agora → Instalar atualização**. Autorize o Ritmo se o Android solicitar e confirme a instalação. Verifique **Versão 1.0.3 / Build 4**. Os treinos permanecem salvos.

O manifesto na raiz conserva 1.0.2 até o upload; `distribution/release.json` contém os dados novos. Atualização opcional: minimumVersionCode 1, forceUpdate false. Se o push for recusado por novos commits, use `git pull --rebase origin main`, resolva os conflitos e tente novamente. Nunca use force push.

Preserve a chave original `ritmo-release.jks`, alias e senhas para compilar as próximas versões. O próximo versionCode deve ser 5 ou maior.
