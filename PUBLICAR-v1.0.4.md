# Publicar Ritmo 1.0.4 / build 5

O APK assinado já está em `distribution/ritmo-1.0.4.apk`. Esta versão permite excluir somente os registros do histórico que você escolher. Nenhuma chave privada ou senha acompanha este projeto.

Extraia o projeto em uma pasta nova, fora de Downloads no Termux. Entre na pasta `ritmo` e execute:

```bash
gh auth setup-git
git push origin main
```

O workflow **Publish signed APK** confere o SHA-256, publica `v1.0.4` com o APK e só depois atualiza `version.json`. Não precisa recompilar nem configurar secrets de assinatura para publicar este APK pronto.

Para acompanhar:

```bash
gh run list --repo MARCELO887876653/ritmo-android --workflow publish-release.yml
gh release view v1.0.4 --repo MARCELO887876653/ritmo-android
```

Depois da publicação, abra **Configurações → Verificar agora** na versão anterior para baixar e instalar pelo app. Confirme a instalação no Android. Também pode instalar diretamente o APK de entrega por cima do antigo, sem desinstalar.

`version.json` na raiz conserva a última versão publicada até o workflow concluir. `distribution/release.json` contém build 5, minimumVersionCode 1, forceUpdate false. Nunca anuncie a versão antes de o APK estar disponível.

Se o push for recusado por novos commits, use `git pull --rebase origin main`, resolva conflitos e envie novamente. Nunca use force push. Preserve a chave original e suas senhas para as próximas compilações. O próximo versionCode deve ser 6 ou maior.
