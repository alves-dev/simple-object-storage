# SOS API V2 — Especificação em construção

> Documento incremental com as decisões funcionais fechadas. Novas dúvidas encontradas no detalhamento ou na implementação devem ser registradas em `SOS V2 - Perguntas e pontos pendentes.md`.

## 1. Contexto e princípios

O SOS é um sistema de armazenamento para uso pessoal. A V2 deve privilegiar:

- simplicidade de implementação e operação;
- manutenção fácil;
- preservação dos arquivos existentes;
- estabilidade das URLs já distribuídas;
- evolução em tarefas pequenas e testáveis;
- ausência de complexidade voltada a grande escala sem necessidade concreta.

Toda a proposta V2 será implementada em fases. Endpoints de candidatos à limpeza permanecem como planejamento, não como requisito da primeira disponibilização.

Não haverá plano de rollback operacional. Cada fase deverá ser bem testada antes da implantação.

## 2. Compatibilidade e ciclo de vida da V1

### 2.1 URL histórica de conteúdo

Este contrato será preservado indefinidamente:

```http
GET /files/{fileId}
```

Regras:

- continua retornando diretamente o conteúdo, sem redirecionamento;
- preserva o `fileId`;
- arquivos existentes continuam acessíveis pelas URLs já distribuídas;
- para arquivos privados, o parâmetro `?key=<accessKey>` será mantido indefinidamente;
- o controller e os serviços internos podem evoluir, desde que o comportamento compatível seja preservado.

### 2.2 Outros endpoints V1

Os endpoints abaixo não precisam ser mantidos indefinidamente:

```http
POST   /api/files/upload
GET    /api/files/{fileId}/info
DELETE /api/files/{fileId}
GET    /api/files/bucket/{bucketName}
```

Eles serão desativados desde a disponibilização da V2:

- métodos e documentação devem ser marcados como deprecated;
- chamadas devem retornar `410 Gone`;
- a resposta deve informar o endpoint V2 substituto;
- não é necessário preservar seus códigos HTTP, DTOs ou paginação.

## 3. Clientes e autenticação

Um `Client` representa o consumidor autenticado da API.

Modelo:

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

Decisões:

- cada cliente possui inicialmente uma única API key;
- o cliente é identificado exclusivamente pela API key;
- `clientId` é único e imutável;
- alterar a identidade significa criar outro cliente;
- formato de `clientId`: `[a-z0-9._-]+`;
- clientes são criados, alterados, habilitados e desabilitados manualmente, não por API;
- arquivos, buckets e permissões permanecem quando um cliente é desabilitado ou removido;
- a API pode distinguir chave inexistente de cliente desabilitado;
- as API keys atuais serão descartadas;
- `SERVER_API_KEYS` deixará de ser usado;
- a aplicação não precisa iniciar sem MongoDB;
- `developer-admin` será um cliente com `admin=true`;
- não haverá rotação sofisticada de chaves na primeira implementação;
- chaves são geradas com `SecureRandom`;
- o formato público começa com `sos_`;
- somente SHA-256 da chave é persistido;
- o hash possui índice único;
- o primeiro cliente é criado por comando ou script manual;
- a chave em texto é exibida somente no momento da criação.

Operações de upload, substituição e exclusão devem gerar logs com:

- filename;
- bucket;
- clientId;
- tipo da operação.

API keys, access keys e tokens nunca devem fazer parte desses logs.

## 4. Buckets

Bucket passa a ser uma entidade persistida.

Modelo:

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

- nome único;
- nomes são normalizados para minúsculas;
- formato permitido: `^[a-zA-Z0-9_-]+$`;
- tamanho máximo: 50 caracteres;
- pode ser criado implicitamente no upload;
- o cliente criador recebe as permissões iniciais;
- pode existir vazio;
- somente pode ser excluído quando estiver vazio e a operação for executada pelo criador ou por um admin;
- clientes autenticados podem listar todos os buckets;
- todos os metadados não sensíveis do bucket podem ser retornados;
- `enabled=false` bloqueia upload e substituição, mas não bloqueia leitura nem exclusão;
- arquivo legado continua acessível mesmo se ainda não existir entidade `Bucket` correspondente.

Embora não seja esperada concorrência de criação, a unicidade deve ser garantida por índice no MongoDB.

## 5. Permissões

Permissões são associadas a cliente e bucket.

Modelo:

```text
BucketPermission
├── id
├── clientId
├── bucketName
├── actions
├── createdAt
└── updatedAt
```

A ação `LIST` não será usada, pois qualquer cliente autenticado pode listar buckets.

Ações:

```text
UPLOAD
DELETE
```

Decisões:

- permissões são concedidas e revogadas manualmente;
- o criador pode perder suas próprias permissões;
- clientes administrativos ignoram permissões específicas;
- a exclusão de arquivo exige `DELETE`;
- a exclusão do bucket é exclusiva do criador ou de admin;
- múltiplos clientes podem possuir permissões sobre o mesmo bucket.

`forceReplace` é exclusivo do cliente indicado em `Bucket.createdByClientId` ou de um admin. Conceder `UPLOAD` a outro cliente permite novos uploads, mas não a substituição de arquivos existentes.

## 6. Modelo de nomes do arquivo

Cada arquivo terá três nomes com responsabilidades diferentes:

```text
originalFilename
filename
internalFilename
```

- `originalFilename`: nome recebido originalmente no multipart, mantido como informação;
- `filename`: nome público solicitado pelo cliente;
- `internalFilename`: nome físico gerado pelo SOS.

Regras fechadas:

- se não for enviado, `filename` é derivado de `originalFilename` e sanitizado;
- `filename` é sempre validado, inclusive quando derivado;
- `filename` é único dentro do bucket;
- a comparação de duplicidade não diferencia maiúsculas e minúsculas;
- formato permitido: `[a-zA-Z0-9._-]+`;
- espaços, barras e Unicode não são aceitos;
- `normalizedFilename` armazena a forma usada no índice único;
- arquivos não podem ser renomeados;
- nome físico não faz parte do contrato público;
- duplicatas legadas recebem um prefixo formado por parte do `fileId` no novo campo `filename`;
- essa migração não pode alterar `fileId`, arquivo físico ou URL histórica.

## 7. Upload

Endpoint V2:

```http
POST /api/v2/files
```

Autenticação:

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
friendlyUrl
```

Regras:

1. identificar o cliente pela API key;
2. normalizar e validar o bucket;
3. criar o bucket implicitamente se não existir;
4. conceder permissões iniciais ao criador;
5. para bucket existente, exigir `UPLOAD`;
6. validar o filename;
7. verificar duplicidade sem diferenciar maiúsculas e minúsculas;
8. sem substituição autorizada, duplicidade produz conflito;
9. persistir conteúdo e metadados;
10. gerar `fileId` aleatório e nome físico interno.

Respostas:

- upload novo: `201 Created`;
- duplicidade sem substituição autorizada: `409 Conflict`;
- substituição concluída: `200 OK`.

## 8. URLs de conteúdo

Três formas de URL podem coexistir:

```text
/files/{fileId}
/files/{bucketName}/{fileId}
/files/{bucketName}/{filename}
```

Regras:

- `/files/{fileId}` é o contrato legado permanente;
- a forma padrão da V2 é `/files/{bucketName}/{fileId}`;
- quando `friendlyUrl=true`, também é retornada `/files/{bucketName}/{filename}`;
- o arquivo registra `friendlyUrlEnabled=true`, e somente arquivos com essa marca podem ser resolvidos pelo filename;
- a resposta de upload retorna a URL padrão e, quando solicitada, a URL amigável;
- URL amigável funciona para arquivo público e privado;
- arquivos privados aceitam `?key=<accessKey>` em todas as formas permanentes de URL;
- bucket e filename são imutáveis;
- substituição preserva todas as formas de URL.

As duas URLs V2 possuem a mesma estrutura. A resolução de `/files/{bucketName}/{value}` segue uma regra determinística:

1. procurar `value` como `fileId` dentro do bucket;
2. se não existir, procurar como `normalizedFilename` entre arquivos com `friendlyUrlEnabled=true`;
3. se nenhum existir, retornar `404`.

O `fileId` aleatório tem precedência sobre um filename coincidente.

## 9. Resposta de conteúdo e cache HTTP

Downloads mantêm:

- `Content-Type`;
- `Content-Length`;
- `Content-Disposition: inline`;
- `originalFilename` como nome sugerido no download;
- `ETag` derivado do hash/versão;
- suporte a `If-None-Match`, com `304 Not Modified`;
- suporte a `HEAD`, sem corpo;
- sem suporte a `Range` na primeira fase.

Arquivos públicos usam cache HTTP de cinco horas. Arquivos privados e URLs temporárias usam política restritiva, sem cache público.

## 10. Substituição de conteúdo

`forceReplace=true` substitui o conteúdo lógico existente sem trocar sua identidade.

Regras:

- preservar obrigatoriamente `fileId`;
- preservar todas as URLs existentes;
- preservar bucket e filename;
- preservar `accessKey`;
- preservar `isPublic`;
- preservar `createdAt`;
- preservar `createdByClientId`;
- preservar contadores de acesso;
- preservar estado de integridade;
- preservar metadata quando o campo não for enviado;
- substituir metadata quando o campo for enviado;
- objeto de metadata vazio não limpa os valores atuais;
- manter tokens temporários válidos;
- tokens existentes passam a entregar o conteúdo novo;
- gerar novo nome físico interno;
- incrementar `version`;
- invalidar o conteúdo anterior no cache.

O caso principal é permitir uma URL permanente, como a de um currículo, cujo conteúdo pode ser atualizado regularmente.

Mesmo sendo um sistema pessoal, o incremento de `version` deve ser atômico no MongoDB. Esse cuidado é pequeno e evita perda silenciosa de atualização.

Fluxo de escrita:

1. gravar o novo conteúdo em arquivo temporário;
2. calcular o hash e validar a escrita;
3. mover atomicamente o temporário para um novo nome físico definitivo;
4. atualizar os metadados por operação atômica, trocando a referência antiga pela nova;
5. somente depois da confirmação do MongoDB, remover o físico anterior;
6. se o MongoDB falhar, manter os metadados e o arquivo anterior e remover o novo arquivo quando possível;
7. deixar a task de integridade identificar qualquer resíduo.

## 11. Arquivos privados e autorização

- exclusão exige API key com `DELETE`; `X-Access-Key` não é exigido;
- geração de URL temporária privada exige permissão no bucket ou cliente admin;
- consulta de informações privadas exige permissão no bucket ou cliente admin;
- listagens nunca expõem `accessKey`;
- consulta de informações pode expor `accessKey` somente para admin;
- `key` e token temporário são credenciais alternativas de download;
- se ambos forem enviados, o token é validado primeiro e o acesso é aceito se qualquer um for válido.

`accessKey` permanece válida indefinidamente nas URLs privadas permanentes, incluindo a URL legada, a URL padrão V2 e a URL amigável.

## 12. URLs temporárias

- podem ser emitidas para arquivos públicos e privados;
- usam a URL padrão `/files/{bucketName}/{fileId}`;
- token assinado com HMAC-SHA256;
- token contém `fileId`, expiração e identificador aleatório;
- alterar o segredo invalida todos os tokens existentes;
- excluir o arquivo invalida seu acesso;
- desabilitar o bucket não invalida o token;
- tokens existentes continuam válidos após substituição e acessam o conteúdo novo;
- a resposta JSON usa `ApiResponse`.

## 13. Respostas e paginação

- endpoints JSON usam `ApiResponse`;
- paginação começa em zero, seguindo o padrão nativo do Spring Data;
- tamanho padrão: 20;
- tamanho máximo: 100;
- ordenação padrão: `createdAt` decrescente;
- listagens autenticadas incluem públicos e privados, mas nunca `accessKey`;
- bucket inexistente retorna `404`;
- bucket existente sem arquivos retorna `200`.

## 14. Rastreamento de acesso

- contam apenas downloads concluídos com sucesso;
- URLs legada, padrão, amigável e temporária contam;
- downloads pela URL legada começam a ser contados assim que o recurso for implantado;
- `HEAD`, `304` e erros não contam;
- cache hit e cache miss contam igualmente;
- contadores são consolidados periodicamente;
- é aceitável perder alguns eventos em encerramento inesperado;
- a janela recente é fixa, aproximada e possui 30 dias;
- a entrega pela rota de imagem aleatória também conta como acesso.

## 15. Cache de conteúdo com Valkey

- TTL fixo de seis horas desde a carga;
- arquivos públicos e privados podem ser armazenados;
- chave: `sos:file-content:{fileId}:{version}`;
- não é necessário identificar ambiente na chave enquanto cada instalação usa seu próprio Valkey;
- versões antigas expiram pelo TTL;
- falha ou timeout curto do Valkey causa fallback imediato para o filesystem;
- a aplicação inicia e funciona sem Valkey;
- limite por item: 5 MB dos bytes do arquivo;
- Valkey não exige senha na rede local prevista.

## 16. Imagem pública aleatória

- exige API key válida;
- retorna bytes no sucesso;
- erros usam `ApiResponse`;
- bucket inexistente e bucket sem imagem elegível retornam `404`;
- confia no MIME type informado no upload;
- considera somente `image/*` público e disponível;
- se o arquivo sorteado estiver ausente, realiza uma única nova tentativa;
- a imagem entregue incrementa o rastreamento de acesso como qualquer download bem-sucedido.

## 17. Integridade, órfãos e retenção

### 17.1 Verificação MongoDB → filesystem

- documento legado sem `storageStatus` é tratado inicialmente como `AVAILABLE`;
- a leitura verifica a existência física e retorna `404` quando o conteúdo não existe, independentemente do status persistido;
- arquivos são correlacionados por bucket e `internalFilename`;
- a task atualiza `lastStorageCheckAt`;
- conteúdo existente é marcado como `AVAILABLE`;
- conteúdo ausente é marcado como `MISSING`;
- a primeira ausência define `missingDetectedAt`;
- se um arquivo `MISSING` reaparecer, a própria requisição de leitura volta a servi-lo e atualiza seu status para `AVAILABLE`;
- ao retornar para `AVAILABLE`, `missingDetectedAt` é removido.

### 17.2 Verificação filesystem → MongoDB

- arquivos físicos sem metadados são registrados como órfãos;
- arquivos temporários usam prefixo ou extensão reservada e são ignorados;
- symlinks não são seguidos;
- a varredura nunca pode sair de `STORAGE_ROOT_PATH`;
- órfãos nunca são removidos automaticamente;
- registros de órfãos, inclusive resolvidos, podem ser mantidos indefinidamente.

### 17.3 Agendamento e limites

- timezone configurável, com padrão `America/Sao_Paulo`;
- impedir execuções simultâneas na mesma JVM;
- lote padrão de 100 registros;
- quatro workers por padrão;
- máximo de 5.000 itens por execução;
- Valkey não é necessário para executar a verificação.

### 17.4 Retenção

- arquivos e metadados nunca são removidos automaticamente por idade;
- candidatos à limpeza são apenas listados para avaliação;
- candidatos usam `lastDirectAccessDate` quando existente;
- se nunca houve acesso direto, usam `createdAt`;
- substituir o conteúdo não reinicia a idade de inatividade;
- `createdAt` e `lastDirectAccessDate` são preservados durante `forceReplace`;
- registros `MISSING` usam `missingDetectedAt` para futura listagem de candidatos;
- candidatos e órfãos permanecem até ação manual explícita.

## 18. Logs e operação

- registrar upload, substituição e exclusão com filename, bucket e clientId;
- não registrar a execução dos scripts de cliente e permissão;
- nunca registrar URLs completas, API keys, access keys ou tokens;
- a aplicação funciona sem Valkey;
- a aplicação não inicia sem MongoDB;
- a porta oficial é `8088`; configuração, exemplo de ambiente e Dockerfile deverão ser alinhados.

## 19. Escopo funcional aprovado

A V2 será implementada por tarefas, cobrindo:

- clientes;
- autenticação;
- buckets;
- permissões;
- upload;
- substituição;
- URL amigável;
- rastreamento de acesso;
- cache com Valkey;
- URLs temporárias;
- imagem pública aleatória;
- verificação de integridade nas duas direções.

Endpoints de candidatos à limpeza são planejamento futuro. Os critérios e metadados podem ser preparados, mas nenhuma exclusão automática por idade será implementada.
