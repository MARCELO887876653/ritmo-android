# Publicar a versão 1.0.1

O APK já está compilado, assinado com a mesma chave da versão 1.0.0 e validado. Não precisa compilar nem configurar secrets de assinatura.

Extraia este projeto em uma pasta nova. No Termux, entre na pasta `ritmo` que contém este arquivo e execute:

```bash
gh auth setup-git
git push origin main
```

O workflow **Publish signed APK** publica a release `v1.0.1` com `ritmo-1.0.1.apk` e só depois atualiza o `version.json`. A atualização é opcional. Nenhuma chave de assinatura é enviada ao GitHub.

Para acompanhar:

```bash
gh run list --repo MARCELO887876653/ritmo-android --workflow publish-release.yml
gh release view v1.0.1 --repo MARCELO887876653/ritmo-android
```

Se o push for recusado porque o repositório recebeu novos commits, execute `git pull --rebase origin main` e resolva eventuais conflitos antes de enviar novamente. Nunca force o envio.

Você também pode instalar o APK de entrega diretamente por cima da versão anterior, sem desinstalar o aplicativo.
