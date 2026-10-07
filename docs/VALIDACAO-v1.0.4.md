# Validação — Ritmo 1.0.4 / build 5

Executada em 7 de outubro de 2026.

- `testDebugUnitTest lintDebug assembleRelease`: retorno 0.
- 40 testes: 0 falhas, 0 erros, 0 ignorados.
- Lint debug: 0 erros, 15 avisos de estilo/configurações existentes. `lintVitalRelease` aprovado.
- APK verificado com apksigner: assinatura v2 válida, RSA 3072, mesmo certificado das versões anteriores.
- Aapt: applicationId `com.ritmo.treinos`, versionName 1.0.4, versionCode 5, minSdk 26, targetSdk 36.
- O APK final contém as novas mensagens de confirmação e a consulta de exclusão por id limitada a treinos finalizados.
- Room continua no schema 2; os arquivos exportados dos schemas não foram alterados. Migration 1→2 permanece registrada e testada.

## Novos testes

Quatro testes de repositório verificam:

1. Exclusão de somente um treino e de suas sessões/séries em cascata, com preservação integral dos outros treinos, cadastro, modelos e treino ativo.
2. Exclusão de somente uma sessão de exercício, com preservação dos exercícios irmãos, das outras datas e recuperação correta das séries anteriores ao copiar.
3. Recusa de exclusão de treino ativo e de suas sessões, tanto no repositório quanto nas consultas DAO.
4. Remoção da última sessão de exercício mantendo o registro do treino, persistência após reabrir o banco e backup/restore compatível com esse registro vazio.

Três testes Compose verificam:

1. Lixeira do histórico geral, Cancelar e Excluir registro com preservação dos outros dados.
2. Lixeira de uma data do histórico do exercício, Cancelar e Excluir sessão; gráfico e contagem passam a usar os registros restantes.
3. Excluir treino do histórico no detalhe e retornar à tela de histórico.

Os 33 testes anteriores continuam aprovados: criação, várias sessões do mesmo exercício, persistência, backup, migrations, exclusão de cadastro, navegação, progresso, versões, cache obrigatório offline e download/instalação de atualizações.

Os testes Android/Compose foram executados com Robolectric API 35. Não houve instalação em celular físico nesta entrega. A publicação no GitHub está preparada pelo workflow e depende do envio dos commits conforme `PUBLICAR-v1.0.4.md`. O manifesto público continua na última versão publicada até o APK novo estar disponível.

APK SHA-256: `c70c108b05980a19ba8452fb39f331b5b44e18801101de9b59eb74ae086f5088`

Certificado SHA-256: `0174c95cab08402c23be939195b5974879cd58c1043f095fa76cc752daf930ce`

Nenhuma chave privada, senha ou token foi incluído no projeto de distribuição.
