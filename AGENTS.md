# AGENTS.md — SOS (Simple Object Storage)

## Visão geral

O SOS é uma API HTTP de armazenamento simples de arquivos. Os arquivos são gravados no sistema de arquivos local, organizados por *buckets*, e seus metadados são persistidos no MongoDB.

Principais capacidades:

- upload de arquivos públicos ou privados;
- geração de identificador e nome interno para cada arquivo;
- acesso ao conteúdo e às informações do arquivo;
- listagem paginada por bucket;
- exclusão de arquivos;
- metadados JSON opcionais por arquivo;
- autenticação por API key nas operações de upload e exclusão.

O projeto está em desenvolvimento. Preserve alterações existentes no worktree e faça mudanças pequenas, coerentes com o código atual.

## Stack e execução

- Java 21 (toolchain configurada no Gradle);
- Spring Boot 3.5.9;
- Spring Web e Spring Security;
- Spring Data MongoDB;
- Gradle Wrapper (`./gradlew`);
- Docker para empacotamento da aplicação.

Comandos usuais:

```bash
./gradlew test
./gradlew build
./gradlew bootRun
```

O artefato executável é `build/libs/application.jar`. O `Dockerfile` espera que esse arquivo já tenha sido construído.

## Configuração

As configurações ficam em `src/main/resources/application.yaml` e são preenchidas por variáveis de ambiente. Use `.env.example` como referência; não versione credenciais reais.

Variáveis principais:

| Variável | Finalidade |
| --- | --- |
| `MONGO_HOST` | Host do MongoDB |
| `MONGO_PORT` | Porta do MongoDB; padrão `27017` |
| `MONGO_USER` / `MONGO_PASSWORD` | Credenciais do MongoDB |
| `SERVER_BASE_URL` | URL base usada nas respostas de upload |
| `SERVER_API_KEYS` | Lista de API keys separada por vírgulas |
| `STORAGE_ROOT_PATH` | Diretório raiz dos arquivos armazenados |

O MongoDB usa o banco `filestorage` e `authSource=admin`. O limite multipart configurado é de 50 MB por arquivo e 55 MB por requisição.

A porta atualmente configurada no `application.yaml` é `8088`. Há uma divergência a ser considerada antes de alterar deploy ou documentação: `.env.example` e o `Dockerfile` mencionam `8080` (o `Dockerfile` também expõe `8080`). Ao trabalhar nessa área, confirme qual porta deve ser a oficial e mantenha os três arquivos alinhados.

## Estrutura do código

```text
src/main/java/com/alves_dev/sos/
├── SosApplication.java       # inicialização do Spring Boot
├── config/                   # propriedades e cadeia de segurança
├── controller/               # endpoints de API e entrega de arquivos
├── exception/                # exceções de domínio e handler global
├── model/                    # entidade FileMetadata e DTOs
├── repository/               # repositórios MongoDB
├── security/                 # filtro e validação de X-API-Key
├── service/                  # filesystem e metadados
└── util/                     # geração de nomes e chaves
```

Responsabilidades principais:

- `FileApiController`: upload, informações, exclusão e listagem por bucket;
- `FileController`: entrega do conteúdo em `/files/{fileId}`;
- `FileStorageService`: cria diretórios, grava, lê e remove arquivos no filesystem;
- `FileMetadataService`: acesso aos metadados no MongoDB;
- `ApiKeyAuthFilter`: exige `X-API-Key` somente em upload e exclusão;
- `GlobalExceptionHandler`: converte exceções da aplicação em respostas HTTP padronizadas.

## Contrato HTTP

Todas as respostas da API usam `ApiResponse`, exceto o streaming do conteúdo do arquivo.

### Upload

`POST /api/files/upload` — exige `X-API-Key` válido.

Multipart/form-data:

- `file`: obrigatório;
- `bucket`: obrigatório, apenas letras, números, `_` e `-`, com até 50 caracteres;
- `filename`: opcional, com letras, números, `.`, `_` e `-`;
- `isPublic`: opcional, padrão `true`;
- `metadata`: opcional, objeto JSON válido.

O arquivo recebe um `fileId` e um nome interno gerado. Arquivos privados recebem uma `accessKey`; a URL privada retornada usa `?key=...`.

### Leitura

- `GET /files/{fileId}`: entrega o arquivo inline. Arquivo privado exige `?key=<accessKey>`;
- `GET /api/files/{fileId}/info`: retorna informações e metadados. Arquivo privado também exige `?key=<accessKey>`;
- `GET /api/files/bucket/{bucketName}?page=0&size=20`: lista arquivos paginados do bucket.

### Exclusão

`DELETE /api/files/{fileId}` — exige `X-API-Key` válido. Para arquivos privados, exige também `X-Access-Key` igual à chave do arquivo.

A exclusão remove primeiro o arquivo do filesystem e depois o documento do MongoDB. Mudanças nesse fluxo devem considerar o risco de inconsistência entre os dois armazenamentos.

## Regras de segurança e integridade

- Nunca exponha API keys, senhas do MongoDB ou access keys em logs, testes versionados ou documentação de exemplo real.
- Não remova a validação de bucket, filename ou access key sem revisar riscos de path traversal e acesso indevido.
- `X-API-Key` autentica a operação administrativa; `accessKey` autoriza o acesso ao arquivo privado.
- Upload, download e exclusão usam o `fileId`; o nome físico é interno e não deve ser tratado como contrato público.
- Ao alterar DTOs ou mensagens de erro, verifique o `GlobalExceptionHandler` e os consumidores da API.
- Ao alterar o modelo `FileMetadata`, revise entidade, repository, serviços e todos os DTOs relacionados.

## Desenvolvimento e testes

Antes de concluir uma alteração:

1. execute `./gradlew test`;
2. se alterar build, configuração ou empacotamento, execute também `./gradlew build`;
3. teste manualmente endpoints afetados quando houver MongoDB e diretório de storage disponíveis;
4. confira `git diff` e não inclua arquivos gerados, credenciais ou conteúdo de `storage/` sem intenção explícita.

Os testes atuais ficam em `src/test/java` e cobrem principalmente o carregamento do contexto. Para novas funcionalidades, prefira testes de controller/service que cubram sucesso, validação, autorização e falhas de persistência.

## Diretrizes para agentes

- Leia primeiro `README.md`, `build.gradle`, `application.yaml` e os arquivos da camada que será alterada.
- Siga o padrão existente de injeção por construtor, records de configuração e respostas `ApiResponse`.
- Não introduza dependências ou abstrações novas se uma solução compatível com Spring/Java já existente resolver o problema.
- Mantenha o código e as mensagens voltados à API em inglês, salvo solicitação explícita; este documento é a orientação em pt-BR para agentes.
- Não faça alterações destrutivas no storage, no MongoDB ou no Git.
- Em caso de divergência entre documentação e implementação, trate a implementação/configuração atual como evidência, registre a divergência e atualize a documentação somente quando a intenção estiver clara.
