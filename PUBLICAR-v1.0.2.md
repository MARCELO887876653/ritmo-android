# Publicar a versão 1.0.2

O projeto contém o APK de release pronto em `distribution/ritmo-1.0.2.apk`, assinado com a mesma chave de 1.0.0 e 1.0.1. A chave privada não está neste projeto. Não precisa recompilar nem configurar secrets de assinatura para publicar este APK.

Extraia o ZIP do projeto em uma pasta nova. No Termux com Git e GitHub CLI, entre na pasta `ritmo` que contém este arquivo e execute:

```bash
gh auth setup-git
git push origin main
```

Use a conta autorizada a escrever em `MARCELO887876653/ritmo-android`. O workflow **Publish signed APK** confere o SHA-256, publica `v1.0.2` com `ritmo-1.0.2.apk` e só depois atualiza `version.json`. A atualização é opcional: versionCode 3, minimumVersionCode 1, forceUpdate false. Nenhuma chave ou senha é enviada ao GitHub; o job usa o token temporário do próprio Actions.

Para acompanhar:

```bash
gh run list --repo MARCELO887876653/ritmo-android --workflow publish-release.yml
gh release view v1.0.2 --repo MARCELO887876653/ritmo-android
```

Se o push for recusado porque main recebeu novos commits, use `git pull --rebase origin main`, resolva eventuais conflitos e envie novamente. Nunca force o envio. Não substitua o manifesto público antes de publicar o APK. O arquivo `distribution/release.json` já contém os metadados novos; o `version.json` da raiz conserva a última versão publicada até o workflow concluir.

Para ativar o download interno, instale esta versão uma vez por cima da anterior, sem desinstalar. Nas próximas versões, o Ritmo baixa o APK, mostra progresso e abre a confirmação do Android. A publicação continua necessária para que os usuários recebam o aviso.

Para compilar futuras versões assinadas, use a chave privada original e siga o README. Próxima versão sugerida: 1.1.0 / build 4.
