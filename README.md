# Ritmo • Android

**Seu treino. Seu ritmo.** Aplicativo Android nativo em português, sem conta, com dados locais. Kotlin, Jetpack Compose, Material 3, Room, ViewModel, Navigation Compose e Coroutines. Android 8.0+ (API 26).

## Instalação

Baixe `ritmo-1.0.0.apk` no Release `v1.0.0` quando publicado. Abra no Android e confirme. O sistema poderá pedir permissão para instalar pelo navegador usado. O app nunca instala silenciosamente.

## Funcionalidades

- Catálogo único: um `Supino reto`, com várias sessões e várias séries em cada sessão.
- Rotinas editáveis, exercícios reutilizados e ordem configurável.
- Carga, repetições, séries concluídas e observações por exercício.
- Treino ativo salvo automaticamente; fechar/reabrir permite continuar.
- Dados da última sessão e cópia das séries concluídas anteriores (novas ficam desmarcadas).
- Descanso opcional de 30/60/90/120 segundos ou personalizado até 1 hora.
- Histórico geral, detalhe de treino, histórico por exercício e gráfico cronológico de carga máxima.
- Temas escuro (padrão), claro e sistema; campos grandes para uso durante o treino.
- Backup JSON local e restauração validada, pelo seletor de arquivos Android.
- Atualizações opcionais/obrigatórias pelo GitHub, com cache e confirmação do Android.

A sugestão de treino de hoje segue a ordem dos modelos após o último utilizado. Você pode iniciar qualquer modelo em Treinos. Só um treino fica ativo por vez. O descanso usa um horário final persistido; o aviso aparece com o app aberto, sem serviço de segundo plano ou alarme externo. Internet é usada somente na consulta de versão. Sem anúncios ou metas corporais.

## Compilar

Android Studio compatível com **AGP 9.1.1**, **JDK 17**, **SDK 37**, **Build Tools 36.0.0**. Wrapper: Gradle 9.3.1. AGP 9 inclui Kotlin 2.2.10 integrado: não aplique `org.jetbrains.kotlin.android` por cima. O plugin Compose acompanha essa versão. Dependências estáveis explícitas nos arquivos Gradle: Compose BOM 2026.09.00, Material 3 1.4.0, Room 2.8.5, Navigation 2.10.2, Activity 1.13.0, Lifecycle 2.11.0, Core 1.19.1, KSP 2.3.12, Coroutines 1.10.2.

```bash
chmod +x gradlew
./gradlew testDebugUnitTest lintDebug assembleDebug
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk`. Ele usa outra assinatura; não distribua debug como atualização do release.

Com emulador/dispositivo Android 15 conectado:

```bash
./gradlew connectedDebugAndroidTest
```

## Assinatura permanente

A chave do primeiro release é entregue no arquivo separado **Ritmo-Chave-Assinatura-PRIVADA.zip**. Nunca publique esse ZIP ou seu conteúdo. Guarde duas cópias privadas seguras e a senha num gerenciador de senhas. Não perca **ritmo-release.jks**, **senha**, **alias `ritmo`** ou **keystore.properties**.

Coloque a chave em `signing/ritmo-release.jks` e o `keystore.properties` privado na raiz:

```properties
storeFile=signing/ritmo-release.jks
storePassword=SUA_SENHA_PRIVADA
keyAlias=ritmo
keyPassword=SUA_SENHA_PRIVADA
```

```bash
./gradlew testDebugUnitTest lintDebug assembleRelease
```

APK assinado: `app/build/outputs/apk/release/app-release.apk`. Sem `keystore.properties`, release produz um APK sem assinatura: **não publique**. Verifique usando `apksigner verify --verbose --print-certs` do SDK. O certificado público esperado fica em `docs/release-certificate.pem`, que pode ser publicado e não contém a chave privada.

Use sempre a mesma chave e `applicationId = "com.ritmo.treinos"`. Atualizar por cima preserva o Room; desinstalar ou limpar dados remove o banco. Exporte backup antes de trocar de celular. Nunca gere outra chave para uma atualização.

`.gitignore` exclui keystores, senhas, `.env`, credenciais locais e builds. Nenhum token está no APK. O CI valida e produz debug, sem GitHub Secrets. Release assinado é compilado localmente. Não há chave privada ou senha no workflow.

## Lançar a próxima atualização

1. Altere o app preservando applicationId e assinatura.
2. Aumente versão/build com o script (ele recusa reutilizar ou reduzir build):

```bash
python3 scripts/bump_version.py 1.1.0 2 --message 'Melhorias no registro de treinos.'
./gradlew testDebugUnitTest lintDebug assembleRelease
```

3. Se o modelo Room mudou, aumente a versão do banco, adicione migration e teste todas as versões anteriores. Nunca use `fallbackToDestructiveMigration`.
4. Confira a assinatura e copie o APK para `ritmo-1.1.0.apk`, fora do código.
5. Faça commit do código (reserve `version.json` para depois do upload), envie ao GitHub, crie tag `v1.1.0` e Release dessa tag.
6. Anexe o APK e confira o download público.
7. Só então envie `version.json` atualizado ao branch **main**. A tag identifica o código compilado; main mantém os metadados públicos de distribuição.

Fluxo: alterar → aumentar build/nome → testar/compilar com mesma chave → Release/APK → `version.json` no main.

Próxima versão: `1.1.0`/2. Seguinte: `1.2.0`/3. `minimumVersionCode` é o mínimo compatível. `forceUpdate: true` bloqueia versões anteriores à nova; um build abaixo do mínimo também bloqueia. Falha de rede, timeout ou JSON inválido não bloqueia o app, salvo exigência obrigatória de cache anteriormente validado. Uma versão já instalada nunca bloqueia. Desativar a verificação ao abrir não ignora uma obrigação já confirmada.

Manifesto público: `https://raw.githubusercontent.com/MARCELO887876653/ritmo-android/main/version.json`. APK oficial: `https://github.com/MARCELO887876653/ritmo-android/releases/download/.../*.apk`. Não coloque tokens de repositório privado no APK. Se mudar o repositório antes da distribuição, ajuste `BuildConfig.GITHUB_REPOSITORY` e o manifesto e recompile.

## Mapa do código

| Parte | Arquivo/pasta |
|---|---|
| Android, versão, dependências, assinatura | `app/build.gradle.kts` |
| Entidades e relações | `data/Models.kt` |
| Room, DAO e migration 1→2 | `data/RitmoDatabase.kt` |
| Regras e transações de treino | `data/WorkoutRepository.kt` |
| Backup lógico validado | `data/BackupManager.kt` |
| Preferências e cronômetro persistido | `data/SettingsStore.kt` |
| Verificação/cache de versão | `update/UpdateManager.kt` |
| Estado e operações MVVM | `ui/RitmoViewModel.kt` |
| Navegação e modal de update | `ui/RitmoApp.kt` |
| Telas e gráficos | `ui/` |
| Testes de banco/update | `app/src/test/` |
| Testes de interface | `app/src/androidTest/` |
| Esquemas Room versionados | `app/schemas/` |
| CI sem chave privada | `.github/workflows/android.yml` |
| Preparação da próxima versão | `scripts/bump_version.py` |

Os caminhos Kotlin são relativos a `app/src/main/java/com/ritmo/treinos/`.

## Banco e backup

`Exercise → ExerciseSession → ExerciseSet`; `WorkoutSession` agrupa a sessão de academia. `WorkoutTemplateExercise` relaciona catálogo e modelos sem duplicar exercícios. Índice único por nome normalizado impede duplicação. Transações preservam um único treino ativo. Excluir modelo ou arquivar exercício preserva histórico; nomes são copiados nas sessões.

O banco distribuído usa schema **2**, independente de versionCode 1. Schema 1 antecede `archived`; migration 1→2 adiciona a coluna sem apagar registros. Esquemas exportados são mantidos no Git. Backup inclui catálogo, modelos, relações, sessões, séries e observações. Preferências e cache de update não são importados. Restauração substitui dados após confirmação, valida o arquivo antes de escrever e usa transação. Arquivo inválido preserva o banco. Limite: 20 MB.

## Documentação consultada

- [Versões estáveis AndroidX](https://developer.android.com/jetpack/androidx/versions)
- [AGP 9.1 e compatibilidade](https://developer.android.com/build/releases/agp-9-1-0-release-notes)
- [Kotlin integrado](https://developer.android.com/build/migrate-to-built-in-kotlin)
- [Navigation](https://developer.android.com/guide/navigation)
- [Room migrations](https://developer.android.com/training/data-storage/room/migrating-db)
- [Assinatura e atualizações APK](https://developer.android.com/studio/publish/app-signing)

Resultados reais desta entrega: `docs/VALIDACAO.md`.
