# SOS API V2 — Proposta de evolução

## Visão geral

A V2 do SOS evolui a API atual de armazenamento de arquivos adicionando:

* autenticação baseada em clientes;
* autorização por bucket;
* modelo explícito de buckets;
* substituição controlada de arquivos;
* URL amigável contendo o nome original;
* URLs temporárias assinadas;
* rastreamento de uso direto;
* cache de conteúdo;
* verificação periódica de integridade;
* identificação de arquivos antigos e potencialmente abandonados;
* rota para retornar uma imagem pública aleatória.

A V2 deve manter compatibilidade com o endpoint atual de leitura:

```http
GET /files/{fileId}
```

A evolução deve ser incremental e evitar mudanças destrutivas no storage e nos metadados existentes.

---

# 1. Conceitos principais

A V2 passa a trabalhar com quatro conceitos principais:

```text
Client
Bucket
BucketPermission
FileMetadata
```

## Client

Representa uma aplicação, script, serviço ou usuário administrativo que consome a API.

Exemplos:

```text
personal-site
home-assistant
portfolio-agent
backup-script
developer-admin
```

Cada cliente possui uma API key própria.

A API identifica o cliente exclusivamente pela API key. O consumidor não envia um `clientId` confiável na requisição.

Modelo sugerido:

```text
Client
├── id
├── clientId
├── name
├── apiKeyHash
├── enabled
├── admin
├── createdAt
└── updatedAt
```

Regras:

* `clientId` deve ser único;
* a API key não deve ser persistida em texto puro;
* apenas o hash da chave deve ser armazenado;
* inicialmente, cada cliente terá apenas uma API key;
* rotação de chaves não faz parte do escopo inicial;
* clientes desabilitados não podem executar operações autenticadas;
* clientes administrativos podem ignorar as restrições específicas de bucket.

O cliente especial:

```text
developer-admin
```

terá:

```text
admin = true
```

Esse cliente poderá administrar todos os buckets e arquivos.

---

# 2. Buckets

Na V2, bucket deixa de ser apenas uma string armazenada no arquivo e passa a ser uma entidade própria.

Modelo sugerido:

```text
Bucket
├── id
├── name
├── createdByClientId
├── createdAt
├── updatedAt
└── enabled
```

Regras:

* o nome do bucket deve ser único;
* um bucket pode ser criado implicitamente durante um upload;
* qualquer cliente pode propor um nome, desde que ele ainda não exista;
* o cliente que cria o bucket recebe automaticamente todas as permissões;
* um bucket pode ser acessado e alterado por mais de um cliente;
* um bucket pode existir sem arquivos;
* um bucket só pode ser excluído quando estiver vazio;
* configurações específicas por bucket poderão ser adicionadas futuramente.

Possíveis configurações futuras:

```text
maximumFileSize
allowedContentTypes
defaultVisibility
cacheEnabled
retentionDays
```

Essas configurações não fazem parte da primeira entrega da V2.

---

# 3. Permissões por bucket

Um bucket pode possuir permissões para múltiplos clientes.

Modelo sugerido:

```text
BucketPermission
├── id
├── clientId
├── bucketName
├── actions
├── createdAt
└── updatedAt
```

Ações iniciais:

```text
UPLOAD
DELETE
LIST
```

A permissão de leitura de conteúdo privado pode ser tratada separadamente pelo mecanismo já existente de `accessKey` ou por URL temporária.

Inicialmente, a listagem de qualquer bucket exige uma API key válida, mas não exige que o cliente tenha uma permissão específica sobre aquele bucket.

Ou seja:

```text
qualquer cliente autenticado pode listar qualquer bucket
```

Entretanto:

```text
UPLOAD
DELETE
```

dependem de autorização explícita para o bucket.

Essa regra pode ser revisada futuramente caso seja necessário restringir também a listagem.

## Permissões automáticas

Quando um cliente cria um bucket durante um upload, recebe:

```text
UPLOAD
DELETE
LIST
```

O cliente administrativo não precisa ter registros explícitos para cada bucket.

---

# 4. Upload e criação de buckets

Endpoint sugerido:

```http
POST /api/v2/files
```

Header:

```http
X-API-Key: <client-api-key>
```

Multipart:

```text
file
bucket
filename
isPublic
metadata
forceReplace
```

Campos:

| Campo          | Obrigatório | Descrição             |
| -------------- | ----------- | --------------------- |
| `file`         | sim         | Conteúdo do arquivo   |
| `bucket`       | sim         | Nome do bucket        |
| `filename`     | não         | Nome público desejado |
| `isPublic`     | não         | Padrão `true`         |
| `metadata`     | não         | Objeto JSON           |
| `forceReplace` | não         | Padrão `false`        |

## Fluxo

1. A API identifica o cliente pela API key.
2. Valida o nome do bucket.
3. Procura o bucket.
4. Se o bucket não existir:

    * cria o bucket;
    * define o cliente atual como criador;
    * concede todas as permissões ao cliente.
5. Se o bucket existir:

    * verifica se o cliente possui `UPLOAD`;
    * clientes administrativos ignoram essa verificação.
6. Valida o nome público do arquivo.
7. Verifica se já existe um arquivo com o mesmo nome dentro do bucket.
8. Salva ou substitui o arquivo conforme as regras definidas.

---

# 5. Unicidade do nome do arquivo

O nome público do arquivo deve ser único dentro do bucket.

Restrição lógica:

```text
bucketName + filename = único
```

Exemplo permitido:

```text
avatars/profile.png
documents/profile.png
```

Exemplo não permitido:

```text
avatars/profile.png
avatars/profile.png
```

## Comportamento padrão

Quando já existir um arquivo com o mesmo nome:

```http
409 Conflict
```

## Substituição forçada

O cliente pode enviar:

```text
forceReplace=true
```

Nesse caso, o conteúdo existente é substituído.

O cliente precisa ter permissão de upload no bucket.

A substituição deve preservar a identidade lógica do arquivo sempre que possível.

Recomendação:

* manter o mesmo `fileId`;
* manter a mesma URL pública;
* manter o mesmo nome público;
* gerar um novo nome físico interno;
* remover o conteúdo físico anterior somente após o novo arquivo ser salvo com sucesso;
* atualizar tamanho, hash, content type e datas;
* manter ou substituir os metadados conforme o contrato definido.

## Ordem segura de substituição

Fluxo recomendado:

1. gravar o novo conteúdo em um arquivo temporário;
2. validar que a escrita terminou;
3. mover o novo arquivo para o nome físico definitivo;
4. atualizar o MongoDB;
5. remover o arquivo físico anterior;
6. invalidar o cache.

Esse fluxo reduz o risco de deixar o registro sem conteúdo físico.

## Renomeação

A V2 não permitirá renomear arquivos.

Consequentemente:

* o `filename` é imutável;
* não é necessário manter redirecionamentos;
* URLs antigas não precisam sobreviver a uma renomeação.

---

# 6. URLs amigáveis com nome do arquivo

O acesso continuará utilizando um identificador aleatório.

O nome público poderá aparecer ao final da URL para melhorar legibilidade e facilitar uso em navegador, logs e downloads.

Exemplo:

```text
https://sos.alves-dev.com/files/a0792b99233b4e689cf3b78af49a955b/imagem-do-fulano.jpg
```

Para arquivo privado:

```text
https://sos.alves-dev.com/files/a0792b99233b4e689cf3b78af49a955b/imagem-do-fulano.jpg?key=3rnjmtx7fvjboho9
```

Endpoint sugerido:

```http
GET /files/{fileId}/{filename}
```

A resolução do conteúdo continua sendo feita pelo `fileId`.

O `filename` na URL é informativo e não deve ser usado como chave principal de busca.

## Validação do nome na URL

Existem duas estratégias possíveis:

### Estratégia recomendada

Caso o nome informado seja diferente do nome salvo, retornar redirecionamento:

```http
302 Found
```

para a URL canônica.

Exemplo:

```text
/files/abc123/nome-incorreto.jpg
```

redireciona para:

```text
/files/abc123/nome-correto.jpg
```

Isso mantém uma URL canônica sem impedir acesso ao conteúdo.

## Compatibilidade

O endpoint atual deve continuar funcionando:

```http
GET /files/{fileId}
```

Ele pode:

* retornar o conteúdo diretamente; ou
* redirecionar para a URL com filename.

Para preservar ao máximo o contrato existente, a primeira versão da V2 deve continuar retornando diretamente o conteúdo.

---

# 7. Acesso direto e rastreamento de uso

Cada arquivo deve registrar quando foi utilizado diretamente.

Uso direto significa uma requisição que entrega o conteúdo do arquivo.

Contam como acesso direto:

```http
GET /files/{fileId}
GET /files/{fileId}/{filename}
GET de uma URL temporária
```

Não contam como acesso direto:

```http
GET /api/v2/files/{fileId}/info
GET /api/v2/buckets/{bucketName}/files
GET /api/v2/buckets/{bucketName}/random
```

A rota de imagem aleatória não contará como acesso direto, mesmo entregando o conteúdo.

Essa é uma decisão específica do domínio para impedir que imagens sorteadas automaticamente sejam consideradas necessariamente importantes.

## Campos sugeridos

No `FileMetadata`:

```text
lastDirectAccessDate
directAccessCount
recentDirectAccessCount
recentAccessWindowStart
```

Exemplo:

```json
{
  "lastDirectAccessDate": "2026-07-17",
  "directAccessCount": 1250,
  "recentDirectAccessCount": 41,
  "recentAccessWindowStart": "2026-06-18"
}
```

## Último acesso

O campo:

```text
lastDirectAccessDate
```

pode armazenar apenas a data, sem horário.

Exemplo:

```text
2026-07-17
```

Isso é suficiente para o objetivo de identificar arquivos abandonados.

## Contagem total

O campo:

```text
directAccessCount
```

pode representar a quantidade histórica de acessos diretos.

## Contagem recente

Para permitir análise de frequência recente, a V2 pode manter uma janela simples:

```text
30 dias
```

Campos:

```text
recentDirectAccessCount
recentAccessWindowStart
```

Quando a janela ultrapassar 30 dias:

1. reinicia a contagem recente;
2. define o início da nova janela;
3. registra o acesso atual.

Essa contagem é aproximada e serve para análise operacional, não para auditoria.

Uma evolução futura pode usar agregações diárias separadas caso sejam necessárias métricas mais precisas.

---

# 8. Redução de atualizações no MongoDB

A API não deve atualizar o MongoDB a cada download.

O último acesso pode ser consolidado por dia.

## Estratégia

Utilizar cache com uma chave por arquivo e data:

```text
fileId -> data do último registro
```

Exemplo:

```text
abc123 -> 2026-07-17
xyz789 -> 2026-07-17
```

No primeiro acesso do arquivo naquele dia:

* o acesso é registrado;
* a atualização é enviada para persistência;
* acessos adicionais no mesmo dia não atualizam `lastDirectAccessDate`.

A contagem pode ser acumulada no cache e persistida periodicamente.

## Persistência periódica

Exemplo:

```text
a cada 1 minuto
```

A aplicação consolida:

```text
fileId
lastDirectAccessDate
accessCountIncrement
```

e atualiza o MongoDB em lote.

Também deve haver flush:

* durante desligamento normal da aplicação;
* antes de remover uma entrada importante do cache;
* quando o contador atingir um limite configurável.

## Consistência

Perder alguns acessos recentes por uma interrupção inesperada é aceitável.

O rastreamento tem finalidade de manutenção e análise, não de faturamento ou auditoria.

---

# 9. Arquivos candidatos à limpeza

Arquivos sem acesso direto por:

```text
365 dias
```

podem ser considerados candidatos à limpeza.

Isso não significa exclusão automática.

Critério inicial:

```text
lastDirectAccessDate <= hoje - 365 dias
```

Também devem ser considerados arquivos que nunca foram acessados diretamente.

Nesse caso, pode ser usada a data:

```text
createdAt
```

Critério completo:

```text
se lastDirectAccessDate existir:
    usar lastDirectAccessDate
senão:
    usar createdAt
```

Endpoint administrativo futuro:

```http
GET /api/v2/admin/files/cleanup-candidates
```

Parâmetros possíveis:

```text
page
size
inactiveDays
bucket
storageStatus
```

Padrão:

```text
inactiveDays=365
```

A API inicialmente apenas lista os candidatos.

Nenhum arquivo será removido automaticamente com base na idade.

---

# 10. Cache de conteúdo

A V2 terá cache do conteúdo físico dos arquivos mais utilizados.

Como os arquivos são principalmente imagens pequenas, na ordem de kilobytes, o cache pode reduzir leituras repetidas do filesystem.

## Tecnologia sugerida

Utilizar Valkey em um container separado.

Motivos:

* cache compartilhado e independente da JVM;
* limite de memória controlável;
* política de remoção nativa;
* métricas e inspeção mais simples;
* possibilidade de futura execução com mais de uma instância;
* evita ocupar diretamente o heap principal da aplicação.

Mesmo que inicialmente exista apenas uma instância da API, o Valkey oferece uma separação operacional útil.

## Limite inicial

```text
512 MB
```

Configuração recomendada no Valkey:

```text
maxmemory 512mb
maxmemory-policy allkeys-lfu
```

A política `allkeys-lfu` favorece a permanência dos arquivos usados com maior frequência.

## Conteúdo cacheado

Valor sugerido:

```text
CachedFile
├── bytes
├── contentType
├── filename
├── contentLength
├── etag
└── loadedAt
```

Chave:

```text
sos:file-content:{fileId}:{version}
```

A presença de uma versão na chave facilita invalidação durante substituição.

## TTL inicial

Sugestão:

```text
6 horas
```

O TTL pode ser renovado a cada acesso, dependendo da implementação escolhida.

Como existe política LFU e limite rígido de memória, o TTL funciona principalmente como proteção contra conteúdo esquecido.

## Tamanho máximo por item

Mesmo com arquivos geralmente pequenos, deve existir um limite por arquivo.

Sugestão inicial:

```text
5 MB
```

Arquivos maiores que isso continuam sendo entregues diretamente pelo filesystem.

Esse valor deve ser configurável:

```text
CACHE_MAX_FILE_SIZE
```

## Fluxo de leitura

1. carregar metadados;
2. validar acesso;
3. verificar cache pelo `fileId` e versão;
4. se existir:

    * retornar os bytes;
5. se não existir:

    * carregar do filesystem;
    * adicionar ao cache se estiver dentro do limite;
    * retornar o conteúdo.

## Invalidação

O cache deve ser invalidado quando:

* o arquivo for substituído;
* o arquivo for excluído;
* o arquivo for marcado como ausente;
* os metadados que impactam a resposta forem alterados.

## Falha do cache

Valkey não deve ser requisito para entregar arquivos.

Se o cache estiver indisponível:

* registrar erro sem expor dados sensíveis;
* carregar o arquivo diretamente do filesystem;
* continuar atendendo a requisição.

O cache é uma otimização, não a fonte de verdade.

---

# 11. Imagem pública aleatória por bucket

A V2 terá uma rota que retorna diretamente uma única imagem pública aleatória de um bucket.

Endpoint sugerido:

```http
GET /api/v2/buckets/{bucketName}/random-image
```

Regras:

* exige API key válida;
* qualquer cliente autenticado pode utilizar;
* o bucket é definido pelo parâmetro de rota;
* retorna uma única imagem;
* considera apenas arquivos públicos;
* considera apenas arquivos com `contentType` iniciado por `image/`;
* ignora arquivos marcados como ausentes;
* não atualiza o rastreamento de acesso direto;
* não precisa evitar repetições;
* não inclui arquivos privados.

## Resposta

Em caso de sucesso:

```http
200 OK
Content-Type: image/*
Content-Length: ...
```

O corpo contém diretamente os bytes da imagem.

Caso não existam imagens elegíveis:

```http
404 Not Found
```

## Seleção aleatória

A implementação não deve carregar todo o bucket em memória.

Para o MongoDB, pode ser usada uma agregação com amostragem:

```text
$match
$sample
```

Filtros:

```text
bucketName = bucket solicitado
isPublic = true
contentType começa com image/
storageStatus = AVAILABLE
```

A etapa:

```text
$sample: { size: 1 }
```

retorna um único arquivo.

Caso o volume futuro torne `$sample` caro, a estratégia poderá ser substituída por um campo aleatório indexável.

---

# 12. URLs temporárias

A V2 permitirá gerar URLs temporárias para qualquer arquivo, público ou privado.

Endpoint sugerido:

```http
POST /api/v2/files/{fileId}/temporary-url
```

Requer:

```http
X-API-Key
```

O cliente não precisa de uma permissão específica adicional para gerar a URL.

Por segurança, ele ainda deve ser um cliente válido e habilitado.

## Request

```json
{
  "expiresInSeconds": 3600
}
```

## Limites

Tempo mínimo:

```text
5 minutos
```

Tempo máximo:

```text
30 dias
```

Valores fora desse intervalo devem retornar:

```http
400 Bad Request
```

## Resposta

```json
{
  "url": "https://sos.alves-dev.com/files/abc123/image.png?token=...",
  "expiresAt": "2026-07-18T00:00:00Z"
}
```

## Conteúdo do token

Exemplo lógico:

```json
{
  "fileId": "abc123",
  "expiresAt": 1784332800,
  "tokenId": "random-unique-value",
  "purpose": "TEMPORARY_DOWNLOAD"
}
```

O token deve ser assinado usando um segredo próprio da aplicação.

Variável sugerida:

```text
TEMPORARY_URL_SIGNING_SECRET
```

## Não colisão

Cada URL gerada deve possuir um identificador aleatório próprio:

```text
tokenId
```

Mesmo que duas URLs sejam geradas:

* para o mesmo arquivo;
* com o mesmo tempo de expiração;
* no mesmo instante;

os tokens devem ser diferentes.

Pode ser usado:

```text
UUID
SecureRandom
```

com entropia suficiente.

## Validação

A API valida:

* assinatura;
* propósito;
* `fileId`;
* expiração.

A URL:

* não é de uso único;
* não pode ser revogada;
* não é vinculada a IP;
* não é vinculada ao cliente que a criou;
* permite apenas download;
* não permite consultar metadados;
* deixa de funcionar automaticamente após a expiração.

## Arquivo excluído ou ausente

Mesmo com token válido:

* arquivo excluído deve retornar `404`;
* arquivo marcado como ausente deve retornar `404`;
* token não deve manter acesso a um conteúdo inexistente.

## Rastreamento

O uso de URL temporária conta como acesso direto.

---

# 13. Verificação de integridade

A V2 terá uma task periódica para reconciliar MongoDB e filesystem.

Ela deve verificar as duas direções:

```text
MongoDB -> filesystem
filesystem -> MongoDB
```

## Frequência

A task pode ser executada diariamente durante a madrugada.

Exemplo:

```text
03:00
```

Como haverá inicialmente apenas uma instância da aplicação, não é necessário lock distribuído.

## Processamento MongoDB → filesystem

Objetivo:

> identificar documentos no MongoDB cujo arquivo físico não existe.

Campos sugeridos no `FileMetadata`:

```text
storageStatus
lastStorageCheckAt
missingDetectedAt
```

Status:

```text
AVAILABLE
MISSING
```

Critério de seleção:

```text
lastStorageCheckAt inexistente
OU
lastStorageCheckAt <= agora - 10 dias
```

Tamanho do lote:

```text
100 arquivos
```

Fluxo:

1. buscar até 100 documentos elegíveis;
2. processar verificações em paralelo;
3. resolver o caminho físico de forma segura;
4. executar `Files.exists`;
5. atualizar `lastStorageCheckAt`;
6. marcar `AVAILABLE` ou `MISSING`;
7. definir `missingDetectedAt` na primeira detecção;
8. remover `missingDetectedAt` se o arquivo voltar a existir;
9. invalidar o cache quando um arquivo virar `MISSING`.

Um arquivo `MISSING` pode voltar automaticamente para `AVAILABLE` se reaparecer no filesystem.

## Paralelismo

O processamento do lote deve ocorrer em paralelo.

A quantidade de threads deve ser configurável.

Sugestão inicial:

```text
4 workers
```

Variável:

```text
STORAGE_INTEGRITY_WORKERS
```

Não é necessário criar uma thread por arquivo.

Pode ser usado:

```text
ExecutorService
```

com pool fixo.

## Continuação dos lotes

A task deve continuar buscando lotes de 100 enquanto existirem arquivos elegíveis.

Pode haver um limite máximo por execução para evitar que a task ocupe a aplicação durante muito tempo.

Exemplo configurável:

```text
STORAGE_INTEGRITY_MAX_ITEMS_PER_RUN=5000
```

## Processamento filesystem → MongoDB

Objetivo:

> identificar arquivos físicos que não possuem documento correspondente no MongoDB.

Esses arquivos são considerados órfãos.

A task deve:

1. percorrer os diretórios de bucket;
2. identificar os nomes físicos;
3. consultar a existência dos respectivos metadados;
4. registrar arquivos órfãos;
5. não removê-los automaticamente.

Uma coleção separada pode ser criada:

```text
OrphanStorageFile
├── id
├── relativePath
├── bucketName
├── detectedAt
├── lastSeenAt
├── fileSize
└── status
```

Status possível:

```text
DETECTED
RESOLVED
IGNORED
```

A identificação não deve depender do nome público do arquivo.

## Segurança

Durante a leitura do filesystem:

* nunca sair de `STORAGE_ROOT_PATH`;
* normalizar todos os caminhos;
* rejeitar links ou caminhos que escapem da raiz;
* não seguir symlinks por padrão;
* ignorar arquivos temporários conhecidos;
* não excluir conteúdo automaticamente.

---

# 14. Registros ausentes e retenção

Quando um arquivo permanecer com:

```text
storageStatus = MISSING
```

por pelo menos:

```text
365 dias
```

ele poderá ser considerado candidato à remoção do MongoDB.

Critério:

```text
missingDetectedAt <= hoje - 365 dias
```

Essa remoção não será automática na entrega inicial.

Endpoint administrativo futuro:

```http
GET /api/v2/admin/files/missing-cleanup-candidates
```

A confirmação de exclusão deverá ser explícita.

---

# 15. Exclusão de arquivos

Endpoint sugerido:

```http
DELETE /api/v2/files/{fileId}
```

Requer:

```http
X-API-Key
```

Regras:

* o cliente precisa de permissão `DELETE` no bucket;
* clientes administrativos podem excluir qualquer arquivo;
* para arquivos privados, o comportamento do `X-Access-Key` deve ser preservado inicialmente para compatibilidade;
* a exclusão invalida o cache;
* a exclusão remove o conteúdo físico e os metadados.

## Fluxo atual e risco

Hoje o fluxo remove:

1. arquivo no filesystem;
2. documento no MongoDB.

Esse fluxo pode gerar inconsistência se a exclusão do MongoDB falhar.

Uma evolução futura deve avaliar:

* marcação lógica como `DELETING`;
* retry;
* outbox;
* task de reconciliação;
* exclusão em duas fases.

Na primeira versão, a task de integridade já ajuda a identificar inconsistências resultantes de falhas.

---

# 16. Modelo sugerido para FileMetadata

Exemplo consolidado:

```text
FileMetadata
├── id
├── fileId
├── bucketName
├── originalFilename
├── internalFilename
├── contentType
├── contentLength
├── contentHash
├── isPublic
├── accessKey
├── metadata
├── version
├── createdByClientId
├── createdAt
├── updatedAt
├── lastDirectAccessDate
├── directAccessCount
├── recentDirectAccessCount
├── recentAccessWindowStart
├── storageStatus
├── lastStorageCheckAt
└── missingDetectedAt
```

## Índices sugeridos

```text
fileId unique
bucketName + originalFilename unique
bucketName
createdByClientId
lastDirectAccessDate
storageStatus + lastStorageCheckAt
missingDetectedAt
```

Para a rota aleatória:

```text
bucketName + isPublic + contentType + storageStatus
```

O índice exato deve ser validado de acordo com as consultas reais do MongoDB.

---

# 17. Endpoints sugeridos

## Arquivos

```http
POST   /api/v2/files
DELETE /api/v2/files/{fileId}
GET    /api/v2/files/{fileId}/info
POST   /api/v2/files/{fileId}/temporary-url
```

## Conteúdo

```http
GET /files/{fileId}
GET /files/{fileId}/{filename}
```

## Buckets

```http
GET    /api/v2/buckets
GET    /api/v2/buckets/{bucketName}
DELETE /api/v2/buckets/{bucketName}
GET    /api/v2/buckets/{bucketName}/files
GET    /api/v2/buckets/{bucketName}/random-image
```

## Administração futura

```http
POST   /api/v2/admin/clients
PATCH  /api/v2/admin/clients/{clientId}
POST   /api/v2/admin/buckets/{bucketName}/permissions
DELETE /api/v2/admin/buckets/{bucketName}/permissions/{clientId}

GET    /api/v2/admin/files/cleanup-candidates
GET    /api/v2/admin/files/missing-cleanup-candidates
GET    /api/v2/admin/storage/orphans
```

---

# 18. Configurações sugeridas

```text
SERVER_API_KEYS
STORAGE_ROOT_PATH

TEMPORARY_URL_SIGNING_SECRET
TEMPORARY_URL_MIN_DURATION
TEMPORARY_URL_MAX_DURATION

CACHE_ENABLED
CACHE_HOST
CACHE_PORT
CACHE_PASSWORD
CACHE_MAX_FILE_SIZE
CACHE_TTL

FILE_ACCESS_FLUSH_INTERVAL
FILE_ACCESS_RECENT_WINDOW_DAYS

STORAGE_INTEGRITY_ENABLED
STORAGE_INTEGRITY_CRON
STORAGE_INTEGRITY_BATCH_SIZE
STORAGE_INTEGRITY_RECHECK_DAYS
STORAGE_INTEGRITY_WORKERS
STORAGE_INTEGRITY_MAX_ITEMS_PER_RUN

FILE_INACTIVITY_DAYS
MISSING_METADATA_RETENTION_DAYS
```

Valores iniciais sugeridos:

```text
TEMPORARY_URL_MIN_DURATION=5m
TEMPORARY_URL_MAX_DURATION=30d

CACHE_MAX_FILE_SIZE=5MB
CACHE_TTL=6h

FILE_ACCESS_FLUSH_INTERVAL=1m
FILE_ACCESS_RECENT_WINDOW_DAYS=30

STORAGE_INTEGRITY_BATCH_SIZE=100
STORAGE_INTEGRITY_RECHECK_DAYS=10
STORAGE_INTEGRITY_WORKERS=4

FILE_INACTIVITY_DAYS=365
MISSING_METADATA_RETENTION_DAYS=365
```

O limite global de 512 MB deve ser configurado diretamente no Valkey.

---

# 19. Migração da API atual

A migração deve preservar os arquivos e endpoints existentes.

## Etapa 1 — Modelo de clientes

* criar `Client`;
* migrar as API keys atuais;
* criar o cliente `developer-admin`;
* associar as chaves atuais ao cliente administrativo;
* alterar autenticação para resolver o cliente pela chave.

## Etapa 2 — Modelo de buckets

* criar coleção de buckets;
* descobrir os buckets existentes a partir de `FileMetadata`;
* criar uma entidade para cada bucket;
* atribuir inicialmente os buckets existentes ao `developer-admin`;
* criar permissões administrativas.

## Etapa 3 — Evolução do FileMetadata

Adicionar campos novos sem remover os atuais.

Valores iniciais:

```text
storageStatus = AVAILABLE
version = 1
createdByClientId = developer-admin
directAccessCount = 0
recentDirectAccessCount = 0
```

Campos de acesso podem permanecer nulos até o primeiro uso.

## Etapa 4 — Endpoints V2

Criar os novos endpoints sem remover a V1.

## Etapa 5 — Cache e rastreamento

Adicionar:

* Valkey;
* cache de conteúdo;
* consolidação de acessos.

## Etapa 6 — Integridade

Adicionar:

* verificação MongoDB → filesystem;
* verificação filesystem → MongoDB;
* registro de órfãos.

---

# 20. Ordem recomendada de implementação

1. Criar entidade `Client`.
2. Resolver cliente pela API key.
3. Criar entidade `Bucket`.
4. Criar permissões por bucket.
5. Migrar buckets existentes.
6. Implementar upload com criação dinâmica de bucket.
7. Implementar unicidade de filename.
8. Implementar `forceReplace`.
9. Adicionar URL amigável com filename.
10. Implementar rastreamento de acesso.
11. Implementar cache com Valkey.
12. Implementar URLs temporárias.
13. Implementar rota de imagem aleatória.
14. Implementar task de integridade MongoDB → filesystem.
15. Implementar task filesystem → MongoDB.
16. Criar endpoints administrativos de consulta.
17. Adicionar listagem de candidatos à limpeza.

---

# 21. Decisões consolidadas

* O cliente é identificado exclusivamente pela API key.
* Inicialmente, cada cliente possui uma única API key.
* Um bucket pode ser alterado por mais de um cliente.
* O criador recebe todas as permissões automaticamente.
* Qualquer cliente pode propor um nome de bucket ainda não utilizado.
* Bucket é uma entidade própria no MongoDB.
* Bucket só pode ser excluído quando estiver vazio.
* Bucket vazio pode continuar existindo.
* Qualquer cliente autenticado pode listar qualquer bucket.
* Upload e exclusão dependem de permissão específica.
* Arquivos privados continuam exigindo autorização para leitura.
* O endpoint atual por `fileId` será mantido.
* O filename será único dentro do bucket.
* Upload duplicado falha sem `forceReplace`.
* Com `forceReplace`, o arquivo é substituído.
* Arquivos não podem ser renomeados.
* A URL amigável contém `fileId` e filename.
* Consulta de informações não conta como uso.
* Download normal e URL temporária contam como uso.
* Acesso por URL com filename conta como uso.
* Imagem aleatória não conta como uso.
* Arquivos sem uso por 365 dias são apenas candidatos à limpeza.
* Exclusão por inatividade não será automática.
* Será mantida contagem histórica e uma contagem aproximada em janela de 30 dias.
* O cache utilizará Valkey.
* O cache terá até 512 MB.
* O cache armazenará o conteúdo dos arquivos.
* A rota aleatória retorna uma única imagem pública diretamente.
* O bucket é o escopo do sorteio.
* Apenas `image/*` públicos e disponíveis participam.
* Arquivos `MISSING` podem voltar a `AVAILABLE`.
* A integridade será verificada nas duas direções.
* O lote inicial será de 100 registros.
* As verificações do lote serão paralelas.
* A aplicação inicialmente terá uma única instância.
* Metadados ausentes por 365 dias serão candidatos à remoção.
* URLs temporárias funcionarão para arquivos públicos e privados.
* O mínimo será 5 minutos.
* O máximo será 30 dias.
* URLs temporárias não serão de uso único.
* URLs temporárias não poderão ser revogadas.
* URLs temporárias não serão vinculadas a IP ou cliente.
* Cada token terá identificador aleatório para evitar colisões.
* URLs temporárias permitirão apenas download.
