# SOS API V2 — Plano detalhado de implementação

Este documento transforma `SOS V2 - Especificação em construção.md` em tarefas implementáveis sobre o código atual.

Ele não substitui a especificação funcional. Em caso de dúvida:

1. a especificação define o comportamento;
2. este plano define como implementar;
3. uma divergência nova deve ser registrada em `SOS V2 - Perguntas e pontos pendentes.md` antes de alterar o contrato.

## 1. Estratégia geral

A implementação será incremental. Cada fase deve:

- manter `GET /files/{fileId}` funcionando;
- compilar e executar os testes;
- não exigir que todas as fases seguintes já estejam prontas;
- evitar migração destrutiva;
- adicionar campos aos documentos existentes sem remover os nomes atuais;
- incluir testes da funcionalidade introduzida;
- terminar com `./gradlew test`;
- executar também `./gradlew build` quando alterar dependências, configuração ou empacotamento.

Ordem recomendada:

1. fundação do modelo e autenticação por cliente;
2. buckets e permissões;
3. migração dos dados atuais;
4. upload e endpoints V2;
5. URLs de conteúdo e cache HTTP;
6. substituição segura;
7. URLs temporárias;
8. rastreamento de acesso;
9. Valkey;
10. imagem aleatória;
11. integridade e órfãos;
12. desativação final dos endpoints V1 e documentação.

## 2. Organização de pacotes

Manter o pacote raiz:

```text
com.alves_dev.sos
```

Estrutura sugerida:

```text
config/
├── CacheConfig.java
├── FileAccessTrackingConfig.java
├── IntegrityConfig.java
├── ServerConfig.java
├── StorageConfig.java
└── TemporaryUrlConfig.java

controller/
├── FileApiController.java             # V1 deprecated
├── FileContentController.java         # conteúdo legado e V2
├── v2/
│   ├── BucketControllerV2.java
│   └── FileControllerV2.java

exception/
├── AccessDeniedException.java
├── BucketNotEmptyException.java
├── BucketNotFoundException.java
├── ClientDisabledException.java
├── DuplicateFilenameException.java
├── FileNotFoundException.java
├── InvalidTemporaryTokenException.java
├── StorageException.java
└── ...

model/
├── Bucket.java
├── BucketPermission.java
├── Client.java
├── FileMetadata.java
├── OrphanStorageFile.java
├── StorageStatus.java
└── dto/
    └── v2/

repository/
├── BucketPermissionRepository.java
├── BucketRepository.java
├── ClientRepository.java
├── FileMetadataRepository.java
└── OrphanStorageFileRepository.java

security/
├── ApiKeyAuthFilter.java
├── AuthenticatedClient.java
├── ClientAuthenticationService.java
└── ClientContext.java

service/
├── BucketAuthorizationService.java
├── BucketService.java
├── FileAccessTrackingService.java
├── FileContentCacheService.java
├── FileMetadataService.java
├── FileStorageService.java
├── FileV2Service.java
├── StorageIntegrityService.java
└── TemporaryUrlService.java

util/
├── ApiKeyGenerator.java
├── FileNameGenerator.java
├── FilenameNormalizer.java
└── HashUtils.java
```

Não é obrigatório criar todos os arquivos antecipadamente. Cada um deve surgir apenas na fase que precisa dele.

## 3. Modelos MongoDB

### 3.1 Client

Coleção:

```text
clients
```

Campos:

```text
id                  String/ObjectId
clientId            String
name                String
apiKeyHash          String
enabled             boolean
admin               boolean
createdAt           Instant
updatedAt           Instant
```

Índices:

```text
clientId unique
apiKeyHash unique
```

Regras:

- normalizar `clientId` para minúsculas;
- validar com `^[a-z0-9._-]+$`;
- gerar pelo menos 32 bytes aleatórios com `SecureRandom`;
- chave exibida: `sos_` + Base64 URL-safe sem padding;
- persistir SHA-256 da string completa recebida;
- comparar hashes, nunca a chave em texto;
- não incluir `apiKeyHash` em DTOs ou logs;
- atualizar `updatedAt` em alterações manuais.

### 3.2 Bucket

Coleção:

```text
buckets
```

Campos:

```text
id                  String/ObjectId
name                String
createdByClientId   String
enabled             boolean
createdAt           Instant
updatedAt           Instant
```

Índice:

```text
name unique
```

Regras:

- converter o nome para minúsculas antes de validar e consultar;
- validar com `^[a-zA-Z0-9_-]+$`;
- máximo de 50 caracteres;
- `enabled=true` por padrão.

### 3.3 BucketPermission

Coleção:

```text
bucket_permissions
```

Campos:

```text
id          String/ObjectId
clientId    String
bucketName  String
actions     Set<BucketAction>
createdAt   Instant
updatedAt   Instant
```

Enum:

```text
UPLOAD
DELETE
```

Índices:

```text
clientId + bucketName unique
bucketName
```

O criador recebe `UPLOAD` e `DELETE`. Admin não precisa de registros explícitos.

### 3.4 FileMetadata

Preservar os campos físicos atuais durante a V2 para evitar uma migração destrutiva:

```text
id
fileId
bucket
originalFileName
storedFileName
filePath
mimeType
fileSize
isPublic
accessKey
uploadedAt
metadata
```

Adicionar:

```text
filename                   String
normalizedFilename         String
friendlyUrlEnabled         boolean
contentHash                String
version                    long
createdByClientId          String
updatedAt                  Instant
lastDirectAccessDate       LocalDate
directAccessCount          long
recentDirectAccessCount    long
recentAccessWindowStart    LocalDate
storageStatus              StorageStatus
lastStorageCheckAt         Instant
missingDetectedAt          Instant
```

Mapeamento sem renomear os dados atuais:

```text
bucket             continua sendo o bucketName lógico
originalFileName   continua sendo originalFilename
storedFileName     continua sendo internalFilename
mimeType           continua sendo contentType
fileSize           continua sendo contentLength
uploadedAt         funciona como createdAt para legados
```

Novos getters podem usar a terminologia V2 sem alterar imediatamente os nomes persistidos.

Valores padrão para legado:

```text
filename                 derivado de originalFileName
normalizedFilename       lowercase(filename)
friendlyUrlEnabled       false
version                  1
createdByClientId        developer-admin
updatedAt                uploadedAt
directAccessCount        0
recentDirectAccessCount  0
storageStatus            AVAILABLE
```

Índices:

```text
fileId unique
bucket + normalizedFilename unique
bucket
createdByClientId
lastDirectAccessDate
storageStatus + lastStorageCheckAt
missingDetectedAt
```

O índice atual `bucket + storedFileName` pode continuar existindo.

### 3.5 OrphanStorageFile

Coleção:

```text
orphan_storage_files
```

Campos:

```text
id
relativePath
bucketName
detectedAt
lastSeenAt
fileSize
status
```

Status:

```text
DETECTED
RESOLVED
IGNORED
```

Índice:

```text
relativePath unique
```

Registros não são removidos automaticamente.

## 4. Autenticação e contexto do cliente

### 4.1 Resolução da API key

Fluxo:

1. ler `X-API-Key`;
2. se ausente, retornar `401 UNAUTHORIZED`;
3. calcular SHA-256;
4. consultar `Client` por `apiKeyHash`;
5. se não existir, retornar `401 INVALID_API_KEY`;
6. se `enabled=false`, retornar `403 CLIENT_DISABLED`;
7. criar `AuthenticatedClient(clientId, admin)`;
8. armazená-lo como atributo da request ou `Authentication` do Spring Security;
9. nunca propagar a chave original além do filtro.

É preferível usar um objeto `Authentication` simples no `SecurityContext`, porque controllers e serviços podem obter o cliente sem reler headers. Não é necessário implementar login, sessão ou JWT.

### 4.2 Rotas protegidas

Exigem API key:

```text
/api/v2/**
```

Permanecem públicas no Spring Security:

```text
GET/HEAD /files/**
/swagger-ui/**
/v3/api-docs/**
```

O download privado valida `key` ou `token` dentro do serviço de conteúdo.

### 4.3 Criação manual do primeiro cliente

Implementar um comando Gradle ou modo CLI da aplicação, por exemplo:

```bash
./gradlew bootRun --args='create-client developer-admin "Developer Admin" --admin'
```

O comando deve:

1. validar que `clientId` ainda não existe;
2. gerar a chave;
3. persistir somente o hash;
4. imprimir uma única vez:

```text
Client created: developer-admin
API key: sos_...
```

Não imprimir URI do MongoDB, senha ou outros clientes.

Uma alternativa aceitável é um script em `scripts/` que invoque um `ApplicationRunner` com profile específico. Não criar endpoint HTTP administrativo.

## 5. Autorização por bucket

Centralizar as regras em `BucketAuthorizationService`:

```text
requireUpload(client, bucket)
requireDelete(client, bucket)
requireOwnerOrAdmin(client, bucket)
requireAnyPermissionOrAdmin(client, bucket)
```

Regras:

- admin sempre passa;
- novo upload em bucket existente exige `UPLOAD`;
- exclusão de arquivo exige `DELETE`;
- substituição exige `createdByClientId == clientId` ou admin;
- exclusão de bucket exige criador ou admin;
- informação privada e URL temporária privada exigem qualquer permissão no bucket ou admin;
- listagem de buckets e arquivos exige somente cliente autenticado;
- bucket desabilitado bloqueia upload e substituição;
- bucket desabilitado permite leitura e exclusão.

Quando um bucket é criado implicitamente:

1. inserir `Bucket`;
2. inserir `BucketPermission` com `UPLOAD` e `DELETE`;
3. se a segunda inserção falhar, remover o bucket recém-criado ou concluir a permissão em retry local;
4. índice único resolve eventual corrida de criação.

## 6. Contratos HTTP V2

Todas as respostas JSON usam `ApiResponseDto`.

### 6.1 Upload e substituição

```http
POST /api/v2/files
X-API-Key: ...
Content-Type: multipart/form-data
```

Campos:

```text
file          obrigatório
bucket        obrigatório
filename      opcional
isPublic      opcional, default true
metadata      opcional, objeto JSON
forceReplace  opcional, default false
friendlyUrl   opcional, default false
```

Resposta:

```json
{
  "success": true,
  "data": {
    "fileId": "a079...",
    "bucket": "documents",
    "originalFilename": "curriculo-2026.pdf",
    "filename": "curriculo.pdf",
    "url": "https://host/files/documents/a079...",
    "friendlyUrl": "https://host/files/documents/curriculo.pdf",
    "isPublic": true,
    "accessKey": null,
    "privateUrl": null,
    "contentLength": 12345,
    "contentType": "application/pdf",
    "contentHash": "sha256-hex",
    "version": 1,
    "createdAt": "2026-07-18T12:00:00Z",
    "updatedAt": "2026-07-18T12:00:00Z"
  },
  "message": null,
  "error": null
}
```

Regras:

- `friendlyUrl` é `null` quando não solicitada;
- `friendlyUrlEnabled` registra se a resolução pública pelo filename foi solicitada;
- arquivos privados retornam a `accessKey` apenas na criação;
- na substituição, não retornar novamente a access key;
- `metadata={}` preserva o valor atual em substituição;
- `forceReplace=true` sem arquivo duplicado cria arquivo novo normalmente;
- duplicidade é definida por bucket + normalizedFilename;
- substituição não altera visibilidade, bucket ou filename.

Status:

```text
201 novo arquivo
200 substituição
400 validação/multipart/metadata
401 API key inválida
403 sem permissão, cliente ou bucket bloqueado
409 filename duplicado sem substituição ou conflito de versão
500 falha inesperada
503 storage temporariamente indisponível, quando aplicável
```

### 6.2 Informações do arquivo

```http
GET /api/v2/files/{fileId}/info
X-API-Key: ...
```

Regras:

- arquivo público: qualquer cliente autenticado;
- arquivo privado: cliente com alguma permissão no bucket ou admin;
- resposta comum nunca inclui access key;
- admin recebe campo `accessKey` somente neste endpoint;
- retornar versão, hash, status e contadores, pois não foram classificados como sensíveis.

### 6.3 Exclusão de arquivo

```http
DELETE /api/v2/files/{fileId}
X-API-Key: ...
```

Regras:

- exige `DELETE` ou admin;
- não exige `X-Access-Key`;
- carregar metadados antes de autorizar;
- invalidar cache;
- excluir físico;
- excluir metadados;
- registrar operação.

Status:

```text
200 exclusão concluída
403 sem DELETE
404 arquivo inexistente
500/503 falha de storage ou persistência
```

Manter o risco conhecido da ordem filesystem → MongoDB na primeira fase; a task de integridade registra inconsistências. Não adicionar outbox nesta V2.

### 6.4 Listagem de buckets

```http
GET /api/v2/buckets?page=0&size=20
X-API-Key: ...
```

- retorna todos os buckets;
- ordena por `createdAt DESC`;
- `size` máximo 100;
- não precisa de permissão específica.

### 6.5 Detalhes do bucket

```http
GET /api/v2/buckets/{bucketName}
X-API-Key: ...
```

- normalizar nome;
- `404` se inexistente;
- retornar campos não sensíveis e contagem de arquivos;
- bucket vazio retorna `200`.

### 6.6 Exclusão do bucket

```http
DELETE /api/v2/buckets/{bucketName}
X-API-Key: ...
```

- exige criador ou admin;
- exige contagem de arquivos igual a zero;
- remover permissões associadas antes ou junto da exclusão;
- retornar `409 BUCKET_NOT_EMPTY` quando contiver arquivos;
- retornar `200` ao concluir.

### 6.7 Listagem de arquivos

```http
GET /api/v2/buckets/{bucketName}/files?page=0&size=20
X-API-Key: ...
```

- qualquer cliente autenticado;
- públicos e privados;
- ordenação `createdAt DESC`;
- nunca retornar access key;
- `404` para bucket inexistente;
- lista vazia para bucket sem arquivos.

### 6.8 URL temporária

```http
POST /api/v2/files/{fileId}/temporary-url
X-API-Key: ...
Content-Type: application/json
```

Request:

```json
{
  "expiresInSeconds": 3600
}
```

Response:

```json
{
  "success": true,
  "data": {
    "url": "https://host/files/documents/a079...?token=...",
    "expiresAt": "2026-07-18T13:00:00Z"
  },
  "message": null,
  "error": null
}
```

Regras:

- público: qualquer cliente autenticado;
- privado: alguma permissão no bucket ou admin;
- mínimo 300 segundos;
- máximo 2.592.000 segundos;
- fora do limite retorna `400`;
- URL usa bucket + fileId;
- não incluir token em logs.

### 6.9 Imagem aleatória

```http
GET /api/v2/buckets/{bucketName}/random-image
X-API-Key: ...
```

- qualquer cliente autenticado;
- sucesso retorna bytes, não `ApiResponse`;
- erros retornam `ApiResponse`;
- filtrar bucket, público, `mimeType` iniciado por `image/` e `AVAILABLE`;
- selecionar com `$sample`;
- se o físico estiver ausente, marcar `MISSING` e repetir uma vez;
- incrementar acesso somente para a imagem efetivamente entregue;
- bucket inexistente ou sem candidato retorna `404`.

## 7. URLs e entrega de conteúdo

### 7.1 Rotas

```http
GET|HEAD /files/{fileId}
GET|HEAD /files/{bucketName}/{value}
```

Na segunda rota:

1. normalizar o bucket;
2. procurar `fileId == value` e bucket;
3. se ausente, procurar `normalizedFilename == lowercase(value)`, bucket e `friendlyUrlEnabled=true`;
4. retornar `404` se não houver resultado.

Não redirecionar entre formatos.

### 7.2 Autorização do download

Arquivo público:

- acesso livre.

Arquivo privado:

- aceitar `key`;
- aceitar `token`;
- quando ambos existirem, validar token primeiro;
- autorizar se qualquer um for válido;
- chave ou token inválido retorna `403`;
- arquivo inexistente retorna `404`.

Para reduzir enumeração, resolver arquivo antes da autorização é aceitável no sistema pessoal, mas a resposta nunca deve incluir metadados sensíveis.

### 7.3 Headers

Resposta `200`:

```text
Content-Type: metadata.mimeType
Content-Length: metadata.fileSize
Content-Disposition: inline; filename="<originalFileName seguro>"
ETag: "<contentHash-ou-version>"
```

Cache:

```text
público:
Cache-Control: public, max-age=18000

privado ou temporário:
Cache-Control: private, no-store
```

Condicional:

- comparar `If-None-Match`;
- retornar `304` sem corpo quando igual;
- `HEAD` retorna os mesmos headers de `GET`, sem corpo;
- `304` e `HEAD` não incrementam acesso;
- não implementar Range inicialmente.

Sanitizar o valor de `Content-Disposition` para impedir quebra de header. Para nomes fora de ASCII, usar fallback seguro; novos filenames públicos já são ASCII, mas `originalFileName` legado pode não ser.

## 8. Substituição segura

Implementar em `FileV2Service`, não no controller.

Fluxo:

1. localizar por bucket + normalizedFilename;
2. autorizar criador do bucket ou admin;
3. validar que bucket, filename e `isPublic` não mudaram;
4. gerar novo `storedFileName`;
5. gravar em temporário dentro do mesmo diretório de bucket;
6. calcular SHA-256 enquanto grava ou após a escrita;
7. validar tamanho;
8. mover com `ATOMIC_MOVE` para o novo nome definitivo;
9. se o filesystem não suportar `ATOMIC_MOVE`, usar `REPLACE_EXISTING` no mesmo filesystem;
10. atualizar MongoDB condicionando por `fileId` e versão anterior;
11. no update, incrementar versão, trocar path/nome físico/hash/tamanho/MIME e `updatedAt`;
12. se o update não modificar exatamente um registro, apagar o novo físico e retornar `409`;
13. após sucesso no MongoDB, invalidar cache e remover o físico antigo;
14. se a remoção antiga falhar, manter a substituição bem-sucedida, registrar erro e deixar a integridade registrar o órfão;
15. registrar operação sem segredo.

O caminho antigo permanece válido até o passo 10. O novo arquivo só se torna a fonte oficial quando o MongoDB é atualizado.

Métodos necessários em `FileStorageService`:

```text
storeTemporary(...)
moveTemporaryToFinal(...)
deleteIfExists(...)
exists(...)
size(...)
resolveSafePath(...)
loadAsResource(...)
```

Todos os caminhos devem:

- partir de `STORAGE_ROOT_PATH`;
- ser absolutos e normalizados para comparação;
- permanecer dentro da raiz;
- rejeitar symlinks quando percorridos pela integridade;
- nunca usar filename público como nome físico.

## 9. Migração

### 9.1 Princípios

- executar antes de criar o índice único de filename;
- fazer backup do MongoDB e do diretório de storage;
- não renomear arquivos físicos;
- não alterar `fileId` ou `accessKey`;
- não remover campos V1;
- permitir execução repetida sem duplicar buckets ou permissões.

### 9.2 Processo

Criar comando/script explícito, não uma migração silenciosa no startup:

```bash
./gradlew bootRun --args='migrate-v2 --dry-run'
./gradlew bootRun --args='migrate-v2'
```

Dry run informa:

```text
arquivos analisados
buckets a criar
filenames a derivar
duplicatas encontradas
documentos inválidos
arquivos físicos ausentes
```

Execução:

1. garantir `developer-admin`;
2. ler todos os documentos `files` em lotes;
3. normalizar os nomes de bucket;
4. criar um `Bucket` por nome;
5. definir `createdByClientId=developer-admin`;
6. criar permissão admin somente se desejado; não é necessária para bypass;
7. derivar filename seguro do nome original;
8. para duplicatas, prefixar parte do fileId;
9. preencher os defaults V2;
10. calcular hash apenas se o arquivo físico estiver disponível; caso contrário marcar `MISSING`;
11. salvar somente campos ausentes;
12. validar contagens;
13. criar índice único `bucket + normalizedFilename`.

Idempotência:

- bucket usa upsert por nome;
- permissão usa upsert por clientId + bucket;
- arquivo já migrado, com `version` e `normalizedFilename`, não é reescrito sem necessidade;
- prefixo de conflito é determinístico a partir do fileId.

### 9.3 Tratamento de nome

Algoritmo para derivar `filename`:

1. obter `originalFileName`;
2. separar extensão;
3. substituir caracteres inválidos por `_`;
4. remover sequências repetidas de `_`;
5. impedir `.` e `..`;
6. se vazio, usar `file`;
7. preservar extensão segura;
8. limitar tamanho a um valor definido na implementação, recomendado 255;
9. gerar `normalizedFilename = lowercase(filename)` com `Locale.ROOT`;
10. em duplicidade, prefixar os primeiros 8 caracteres do fileId.

## 10. URL temporária

Formato simples, sem dependência JWT:

```text
base64url(payload-json).base64url(hmac-sha256(payload))
```

Payload:

```json
{
  "fileId": "a079...",
  "expiresAt": 178...",
  "tokenId": "random...",
  "purpose": "TEMPORARY_DOWNLOAD"
}
```

Validação:

1. separar payload e assinatura;
2. recalcular HMAC com `TEMPORARY_URL_SIGNING_SECRET`;
3. comparar assinatura em tempo constante;
4. validar JSON e propósito;
5. validar `fileId` igual ao arquivo da rota;
6. validar expiração usando `Instant`;
7. autorizar download;
8. não persistir token.

Usar API criptográfica padrão do Java (`Mac` com `HmacSHA256`), sem biblioteca JWT.

## 11. Rastreamento de acesso

### 11.1 Evento

Registrar somente depois que uma resposta de conteúdo foi preparada com sucesso:

```text
fileId
accessDate
increment=1
```

Inclui:

- URL legada;
- URL padrão;
- URL amigável;
- URL temporária;
- imagem aleatória efetivamente entregue.

Não inclui:

- `HEAD`;
- `304`;
- erros antes da entrega.

### 11.2 Acumulador

Para uma única instância, usar memória da JVM:

```text
ConcurrentHashMap<fileId, PendingAccess>
```

`PendingAccess`:

```text
accessCountIncrement
lastAccessDate
```

Flush:

- agendado a cada minuto;
- trocar/remover a entrada de forma atômica antes de persistir;
- usar `$inc` para total e contagem recente;
- usar `$set` para última data;
- em falha, devolver incremento ao acumulador;
- no shutdown normal, tentar flush final;
- aceitar perda em encerramento abrupto.

Janela recente:

- se `recentAccessWindowStart` for nulo ou tiver mais de 30 dias, iniciar nova janela com o incremento atual;
- senão usar `$inc`;
- consistência aproximada é suficiente.

## 12. Cache Valkey

Adicionar:

```gradle
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
```

Usar Redis protocol com Valkey.

Chave:

```text
sos:file-content:{fileId}:{version}
```

Valor:

```text
bytes
contentType
originalFilename
contentLength
etag
```

Para reduzir complexidade, pode ser usado um formato binário próprio simples ou serialização JSON com bytes Base64. Preferir serialização binária/Redis hash se a implementação continuar clara; evitar Java native serialization.

Fluxo:

1. metadados e autorização sempre vêm antes do cache;
2. tentar cache com timeout curto;
3. em hit, servir conteúdo;
4. em miss, carregar filesystem;
5. se tamanho `<= CACHE_MAX_FILE_SIZE`, gravar com TTL;
6. erro do Valkey apenas gera log resumido e fallback;
7. substituição e exclusão removem a chave da versão conhecida;
8. versões anteriores restantes expiram pelo TTL.

O cache nunca armazena access key ou token.

## 13. Imagem aleatória no MongoDB

Usar `MongoTemplate` para agregação:

```text
$match:
  bucket = normalizedBucket
  isPublic = true
  mimeType regex ^image/
  storageStatus = AVAILABLE

$sample:
  size = 1
```

O índice ajuda no filtro, embora `$sample` ainda tenha custo próprio:

```text
bucket + isPublic + storageStatus + mimeType
```

Não carregar o bucket inteiro em memória.

## 14. Integridade

### 14.1 Agendamento

Habilitar scheduling e configurar:

```text
enabled
cron
zone
batchSize
recheckDays
workers
maxItemsPerRun
```

Defaults:

```text
enabled=true
cron=0 0 3 * * *
zone=America/Sao_Paulo
batchSize=100
recheckDays=10
workers=4
maxItemsPerRun=5000
```

Usar `AtomicBoolean` ou lock local para impedir sobreposição.

### 14.2 MongoDB → filesystem

1. buscar documentos sem `lastStorageCheckAt` ou vencidos;
2. limitar pelo restante de `maxItemsPerRun`;
3. processar com pool fixo;
4. resolver caminho seguro;
5. verificar existência e leitura;
6. atualizar `lastStorageCheckAt`;
7. existente: `AVAILABLE`, limpar `missingDetectedAt`;
8. ausente: `MISSING`, preencher `missingDetectedAt` apenas se nulo;
9. invalidar cache na transição para `MISSING`;
10. continuar até o limite ou ausência de elegíveis.

Uma leitura normal que encontra um arquivo anteriormente `MISSING`:

- serve o conteúdo;
- atualiza imediatamente para `AVAILABLE`;
- limpa `missingDetectedAt`;
- atualiza `lastStorageCheckAt`.

### 14.3 Filesystem → MongoDB

1. caminhar a raiz sem seguir symlinks;
2. considerar somente arquivos dentro de diretórios de bucket;
3. ignorar temporários pelo padrão reservado;
4. consultar por bucket + storedFileName;
5. ausente: upsert de `OrphanStorageFile`;
6. atualizar `lastSeenAt` e tamanho;
7. ao final, órfãos não vistos podem ser marcados `RESOLVED`;
8. nunca excluir automaticamente.

Erros em um arquivo não interrompem todo o lote.

## 15. Configuração

Estrutura sugerida em `application.yaml`:

```yaml
server:
  port: 8088
  base-url: ${SERVER_BASE_URL}

storage:
  root-path: ${STORAGE_ROOT_PATH}
  max-file-size: 52428800
  allowed-buckets-regex: "^[a-zA-Z0-9_-]+$"
  allowed-filename-regex: "^[a-zA-Z0-9._-]+$"
  max-bucket-name-length: 50

temporary-url:
  signing-secret: ${TEMPORARY_URL_SIGNING_SECRET}
  min-duration: 5m
  max-duration: 30d

content-cache:
  enabled: ${CACHE_ENABLED:true}
  host: ${CACHE_HOST:localhost}
  port: ${CACHE_PORT:6379}
  max-file-size: 5MB
  ttl: 6h
  timeout: 200ms

file-access:
  flush-interval: 1m
  recent-window-days: 30

storage-integrity:
  enabled: ${STORAGE_INTEGRITY_ENABLED:true}
  cron: ${STORAGE_INTEGRITY_CRON:0 0 3 * * *}
  zone: ${STORAGE_INTEGRITY_ZONE:America/Sao_Paulo}
  batch-size: 100
  recheck-days: 10
  workers: 4
  max-items-per-run: 5000
```

Remover:

```text
SERVER_API_KEYS
server.api-keys
```

Atualizar `.env.example`:

```text
SERVER_BASE_URL=http://localhost:8088
STORAGE_ROOT_PATH=/home/storage
TEMPORARY_URL_SIGNING_SECRET=replace-with-a-long-random-secret
CACHE_ENABLED=true
CACHE_HOST=localhost
CACHE_PORT=6379
```

Alinhar:

- `application.yaml`: 8088;
- `.env.example`: URL com 8088;
- `Dockerfile`: `EXPOSE 8088`;
- imagem Java do Dockerfile com Java 21, igual ao toolchain;
- comando do Dockerfile com caminho absoluto `/app/application.jar`.

## 16. Erros estáveis

Adicionar códigos:

```text
UNAUTHORIZED
INVALID_API_KEY
CLIENT_DISABLED
ACCESS_DENIED
INVALID_BUCKET
BUCKET_NOT_FOUND
BUCKET_DISABLED
BUCKET_NOT_EMPTY
INVALID_FILENAME
DUPLICATE_FILENAME
FILE_NOT_FOUND
FILE_TOO_LARGE
INVALID_METADATA
INVALID_EXPIRATION
INVALID_TEMPORARY_TOKEN
EXPIRED_TEMPORARY_TOKEN
STORAGE_ERROR
ENDPOINT_GONE
INTERNAL_ERROR
```

Regras:

- erros JSON sempre usam `ApiResponseDto.error`;
- não incluir stack trace ou caminho físico;
- log interno pode conter exception e fileId, mas não credenciais;
- validação retorna `400`;
- autenticação `401`;
- autorização `403`;
- ausência `404`;
- conflito `409`;
- endpoint V1 removido `410`.

## 17. Desativação da V1

Manter funcional:

```http
GET|HEAD /files/{fileId}
```

Nos métodos antigos de API:

- adicionar `@Deprecated`;
- marcar OpenAPI como deprecated;
- não executar a lógica antiga;
- retornar `410 Gone`;
- incluir error code `ENDPOINT_GONE`;
- indicar a rota V2 equivalente na mensagem.

Mapeamento:

```text
POST /api/files/upload
  -> POST /api/v2/files

GET /api/files/{fileId}/info
  -> GET /api/v2/files/{fileId}/info

DELETE /api/files/{fileId}
  -> DELETE /api/v2/files/{fileId}

GET /api/files/bucket/{bucketName}
  -> GET /api/v2/buckets/{bucketName}/files
```

## 18. Testes

### 18.1 Unidade

Cobrir:

- geração e hash de API key;
- normalização de clientId, bucket e filename;
- autorização por ação, dono e admin;
- derivação de filename;
- duplicidade case-insensitive;
- criação e validação de token;
- expiração do token;
- cálculo de ETag;
- janela recente de acesso;
- resolução segura de caminhos;
- detecção de temporário;
- transições `AVAILABLE`/`MISSING`.

### 18.2 Repositórios

Usar testes de integração MongoDB quando houver infraestrutura de teste adequada. Cobrir:

- índices únicos;
- queries por fileId e filename;
- update condicionado por versão;
- `$inc` dos contadores;
- seleção de elegíveis da integridade;
- agregação de imagem aleatória.

Não introduzir Testcontainers automaticamente sem avaliar o ambiente do projeto. Se não houver MongoDB nos testes, mockar repositórios nas fases iniciais e documentar a lacuna.

### 18.3 Services

Cobrir:

- criação implícita de bucket;
- concessão inicial de permissões;
- upload autorizado e negado;
- substituição apenas por dono/admin;
- falha antes e depois do move físico;
- falha do MongoDB preservando arquivo antigo;
- falha ao apagar físico antigo;
- exclusão;
- cache indisponível;
- flush com falha e reentrada no acumulador;
- integridade sem seguir symlink.

### 18.4 Controllers

Com MockMvc:

- autenticação ausente, inválida e cliente desabilitado;
- upload novo `201`;
- duplicidade `409`;
- substituição `200`;
- validação de bucket/filename/metadata;
- listagem e paginação;
- informação pública e privada;
- URL temporária;
- imagem aleatória;
- erros padronizados;
- endpoints V1 retornando `410`.

### 18.5 Contrato de URL

Testes obrigatórios:

1. URL antiga pública continua retornando os mesmos bytes;
2. URL antiga privada continua aceitando a access key existente;
3. `/files/{bucket}/{fileId}` retorna os mesmos bytes;
4. `/files/{bucket}/{filename}` funciona somente para arquivo com URL amigável;
5. fileId tem precedência na resolução ambígua;
6. substituição preserva as três URLs;
7. `GET`, `HEAD` e `304` retornam headers coerentes;
8. URL temporária expirada falha;
9. token anterior à substituição retorna conteúdo novo;
10. nomes e segredos não quebram headers nem aparecem em logs.

### 18.6 Build e verificação manual

Em cada fase:

```bash
./gradlew test
```

Quando alterar build/configuração/Docker:

```bash
./gradlew build
```

Antes de implantação:

- executar migração dry-run;
- fazer backup;
- executar migração;
- conferir contagens;
- testar um arquivo público legado;
- testar um arquivo privado legado;
- testar upload, substituição e exclusão V2;
- desligar Valkey e confirmar fallback;
- conferir logs por vazamento de segredos;
- conferir `git diff` e ausência de arquivos de storage.

## 19. Divisão em tarefas

### Task 1 — Fundação e autenticação

Entregas:

- `Client`;
- repositório e índices;
- geração/hash de chave;
- comando manual de criação;
- filtro e contexto autenticado;
- remoção de `SERVER_API_KEYS`;
- testes de autenticação.

Aceite:

- cliente válido acessa rota V2 de teste;
- inválido recebe `401`;
- desabilitado recebe `403`;
- chave não aparece no MongoDB nem logs.

### Task 2 — Buckets e permissões

Entregas:

- `Bucket`;
- `BucketPermission`;
- autorização centralizada;
- listagem/detalhe/exclusão;
- criação implícita reutilizável.

Aceite:

- nomes normalizados;
- criador recebe permissões;
- listagem é global para autenticados;
- somente dono/admin exclui bucket vazio.

### Task 3 — Migração V2

Entregas:

- dry-run;
- execução idempotente;
- preenchimento dos novos campos;
- tratamento determinístico de duplicatas;
- criação posterior de índices.

Aceite:

- URLs antigas continuam funcionando;
- segunda execução não altera resultado;
- contagens antes/depois conferem.

### Task 4 — Upload e consultas V2

Entregas:

- DTOs V2;
- upload;
- info;
- listagem;
- validações;
- URLs padrão e amigável.

Aceite:

- novo upload `201`;
- duplicidade case-insensitive `409`;
- respostas e autorização seguem contrato.

### Task 5 — Conteúdo e HTTP cache

Entregas:

- controller unificado de conteúdo;
- três URLs;
- private key;
- HEAD, ETag e 304;
- Cache-Control.

Aceite:

- contrato legado preservado;
- filename amigável resolvido;
- headers corretos.

### Task 6 — Substituição

Entregas:

- temporário e move seguro;
- update condicionado por versão;
- limpeza do antigo;
- invalidação de cache abstrata;
- testes de falha.

Aceite:

- todas as URLs mantêm identidade;
- falha do MongoDB não derruba conteúdo antigo;
- substituição retorna `200`.

### Task 7 — URLs temporárias

Entregas:

- configuração;
- HMAC;
- endpoint de geração;
- validação no download.

Aceite:

- limites de expiração;
- token diferente em emissões iguais;
- expiração e assinatura inválida rejeitadas.

### Task 8 — Rastreamento

Entregas:

- acumulador;
- flush periódico;
- atualização atômica;
- flush de shutdown.

Aceite:

- downloads incrementam;
- HEAD/304 não incrementam;
- falha de flush não descarta imediatamente o acumulado.

### Task 9 — Valkey

Entregas:

- dependência;
- configuração;
- serialização;
- cache-aside;
- fallback.

Aceite:

- hit evita leitura física;
- arquivos grandes não entram;
- API funciona com Valkey desligado.

### Task 10 — Imagem aleatória

Entregas:

- agregação;
- streaming;
- retry único;
- rastreamento.

Aceite:

- somente imagem pública disponível;
- sem candidatos retorna `404`;
- imagem entregue incrementa acesso.

### Task 11 — Integridade

Entregas:

- scheduler;
- pool configurável;
- duas direções;
- coleção de órfãos;
- transições de status.

Aceite:

- respeita limite;
- não segue symlink;
- nunca exclui automaticamente;
- arquivo reaparecido volta a `AVAILABLE`.

### Task 12 — Depreciação, deploy e documentação

Entregas:

- V1 em `410`;
- OpenAPI;
- `.env.example`;
- Dockerfile Java 21/porta 8088;
- README;
- build completo.

Aceite:

- documentação mostra rotas V2;
- Docker usa artefato correto;
- nenhum segredo ou storage entra no Git.
