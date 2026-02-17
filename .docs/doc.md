# **SOS - Simple Object Storage - Especificação Técnica**

## **Visão Geral**

Serviço simples de armazenamento e disponibilização de arquivos com suporte a acesso público e privado, organização por buckets e persistência de metadados em MongoDB.

---

## **Tecnologias**

- **Backend**: Java (Spring Boot recomendado)
- **Database**: MongoDB
- **Storage**: Sistema de arquivos local
- **Protocol**: HTTP/REST

---

## **Estrutura de Diretórios**

```
/storage-root/
  ├── bucket-name-1/
  │   ├── 20240216_abc123_file1.pdf
  │   └── 20240216_def456_image.jpg
  └── bucket-name-2/
      └── 20240216_ghi789_document.docx
```

---

## **Modelo de Dados (MongoDB)**

### **Collection: `files`**

```json
{
  "_id": "ObjectId",
  "fileId": "abc123def456",
  "bucket": "invoices",
  "originalFileName": "invoice-2024.pdf",
  "storedFileName": "20240216_abc123_invoice-2024.pdf",
  "filePath": "/storage-root/invoices/20240216_abc123_invoice-2024.pdf",
  "mimeType": "application/pdf",
  "fileSize": 2048576,
  "isPublic": false,
  "accessKey": "k8h3j2k1n4m5p6q7",
  "uploadedAt": "2024-02-16T10:30:00Z",
  "metadata": {
    "uploadedBy": "user@example.com",
    "description": "Invoice for January 2024"
  }
}
```

**Campos:**
- `fileId`: ID único do arquivo (UUID sem hífens)
- `bucket`: Nome do bucket (pasta)
- `originalFileName`: Nome original do arquivo enviado
- `storedFileName`: Nome único gerado no sistema de arquivos
- `filePath`: Caminho completo no filesystem
- `mimeType`: Tipo MIME do arquivo
- `fileSize`: Tamanho em bytes
- `isPublic`: Boolean indicando se o arquivo é público
- `accessKey`: Chave aleatória para acesso a arquivos privados (gerada apenas se `isPublic = false`)
- `uploadedAt`: Timestamp do upload
- `metadata`: Objeto opcional para informações adicionais

**Índices:**
- `fileId`: unique
- `bucket + storedFileName`: compound, unique
- `accessKey`: unique, sparse (apenas para arquivos privados)

---

## **Autenticação**

### **API Key**

Todas as operações de **upload** de arquivos requerem autenticação via API Key no header da requisição.

**Header obrigatório para upload:**

```
X-API-Key: your-api-key-here
```

**Geração de API Keys:**
- API Keys devem ser geradas e gerenciadas pelo dev
- Formato sugerido: 32 caracteres alfanuméricos
- Exemplo: `a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6`

**Armazenamento:**
- API Keys devem ser armazenadas nas propriedades da aplicação no application.yml

**Endpoints protegidos:**
- ✅ `POST /api/files/upload` - **Requer API Key**
- ✅ `DELETE /api/files/{fileId}` - **Requer API Key**
- ❌ `GET /files/{fileId}` - **Público (com validação de accessKey para privados)**
- ❌ `GET /api/files/{fileId}/info` - **Público (com validação de accessKey para privados)**
- ❌ `GET /api/files/bucket/{bucketName}` - **Público**

**Response quando API Key está ausente ou inválida (401 Unauthorized):**

```json
{
  "success": false,
  "error": {
    "code": "UNAUTHORIZED",
    "message": "Valid API Key is required"
  }
}
```

---

## **API Endpoints**

### **1. Upload de Arquivo**

**Endpoint:** `POST /api/files/upload`

**Content-Type:** `multipart/form-data`

**Headers obrigatórios:**

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `X-API-Key` | String | API Key para autenticação |

**Parâmetros:**

| Campo | Tipo | Obrigatório | Descrição |
|-------|------|-------------|-----------|
| `file` | File | Sim | Arquivo a ser enviado |
| `bucket` | String | Sim | Nome do bucket (alfanumérico, hífen e underscore) |
| `filename` | String | Não | Nome customizado (usa nome original se omitido) |
| `isPublic` | Boolean | Não | Define se arquivo é público (default: `true`) |
| `metadata` | JSON String | Não | Metadados adicionais |

**Validações:**
- `bucket`: Regex `^[a-zA-Z0-9_-]+$` (máx 50 caracteres)
- `filename`: Regex `^[a-zA-Z0-9._-]+$` (se fornecido)

**Request Example (cURL):**

```bash
curl -X POST http://localhost:8080/api/files/upload \
  -H "X-API-Key: a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6" \
  -F "file=@/path/to/file.pdf" \
  -F "bucket=documents" \
  -F "filename=my-document.pdf" \
  -F "isPublic=false" \
  -F 'metadata={"uploadedBy":"user@example.com"}'
```

**Response Success (201 Created):**

```json
{
  "success": true,
  "data": {
    "fileId": "abc123def456",
    "bucket": "documents",
    "filename": "my-document.pdf",
    "url": "http://localhost:8080/files/abc123def456",
    "isPublic": false,
    "accessKey": "k8h3j2k1n4m5p6q7",
    "privateUrl": "http://localhost:8080/files/abc123def456?key=k8h3j2k1n4m5p6q7",
    "fileSize": 2048576,
    "mimeType": "application/pdf",
    "uploadedAt": "2024-02-16T10:30:00Z"
  }
}
```

**Response Error (400 Bad Request):**

```json
{
  "success": false,
  "error": {
    "code": "INVALID_BUCKET",
    "message": "Bucket name must contain only alphanumeric characters, hyphens and underscores"
  }
}
```

**Possíveis Erros:**
- `UNAUTHORIZED`: API Key ausente ou inválida
- `INVALID_BUCKET`: Nome do bucket inválido
- `INVALID_FILENAME`: Nome do arquivo inválido
- `FILE_TOO_LARGE`: Arquivo excede limite configurado (opcional)
- `STORAGE_ERROR`: Erro ao salvar arquivo no filesystem

---

### **2. Servir Arquivo**

**Endpoint:** `GET /files/{fileId}`

**Parâmetros de Query:**

| Campo | Tipo | Obrigatório | Descrição |
|-------|------|-------------|-----------|
| `key` | String | Condicional | Obrigatório apenas para arquivos privados |

**Comportamento:**

1. **Arquivo Público**: Retorna o arquivo diretamente
2. **Arquivo Privado sem key**: Retorna `403 Forbidden`
3. **Arquivo Privado com key correta**: Retorna o arquivo
4. **Arquivo Privado com key incorreta**: Retorna `403 Forbidden`
5. **Arquivo não encontrado**: Retorna `404 Not Found`

**Request Examples:**

```bash
# Arquivo público
curl http://localhost:8080/files/abc123def456

# Arquivo privado
curl http://localhost:8080/files/abc123def456?key=k8h3j2k1n4m5p6q7
```

**Response Success (200 OK):**

```
Content-Type: application/pdf
Content-Disposition: inline; filename="my-document.pdf"
Content-Length: 2048576

[Binary file content]
```

**Response Error (403 Forbidden):**

```json
{
  "success": false,
  "error": {
    "code": "ACCESS_DENIED",
    "message": "Access key is required for private files"
  }
}
```

**Response Error (404 Not Found):**

```json
{
  "success": false,
  "error": {
    "code": "FILE_NOT_FOUND",
    "message": "File with ID 'abc123def456' does not exist"
  }
}
```

---

### **3. Obter Informações do Arquivo (Opcional)**

**Endpoint:** `GET /api/files/{fileId}/info`

**Parâmetros de Query:**

| Campo | Tipo | Obrigatório | Descrição |
|-------|------|-------------|-----------|
| `key` | String | Condicional | Obrigatório para arquivos privados |

**Response Success (200 OK):**

```json
{
  "success": true,
  "data": {
    "fileId": "abc123def456",
    "bucket": "documents",
    "filename": "my-document.pdf",
    "mimeType": "application/pdf",
    "fileSize": 2048576,
    "isPublic": false,
    "uploadedAt": "2024-02-16T10:30:00Z",
    "metadata": {
      "uploadedBy": "user@example.com"
    }
  }
}
```

---

### **4. Deletar Arquivo (Opcional)**

**Endpoint:** `DELETE /api/files/{fileId}`

**Headers:**

| Campo | Tipo | Obrigatório | Descrição |
|-------|------|-------------|-----------|
| `X-API-Key` | String | Sim | API Key para autenticação |
| `X-Access-Key` | String | Condicional | Obrigatório para arquivos privados |

**Response Success (200 OK):**

```json
{
  "success": true,
  "message": "File deleted successfully"
}
```

---

### **5. Listar Arquivos de um Bucket (Opcional)**

**Endpoint:** `GET /api/files/bucket/{bucketName}`

**Parâmetros de Query:**

| Campo | Tipo | Obrigatório | Descrição |
|-------|------|-------------|-----------|
| `page` | Integer | Não | Número da página (default: 0) |
| `size` | Integer | Não | Tamanho da página (default: 20) |

**Response Success (200 OK):**

```json
{
  "success": true,
  "data": {
    "bucket": "documents",
    "files": [
      {
        "fileId": "abc123def456",
        "filename": "my-document.pdf",
        "fileSize": 2048576,
        "isPublic": false,
        "uploadedAt": "2024-02-16T10:30:00Z"
      }
    ],
    "pagination": {
      "page": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1
    }
  }
}
```

---

## **Lógica de Geração de Nomes**

### **StoredFileName Format:**

```
{timestamp}_{randomId}_{originalOrCustomFilename}
```

**Exemplo:**
- Input: `invoice.pdf`
- Output: `20240216103045_a1b2c3d4_invoice.pdf`

**Componentes:**
- `timestamp`: Formato `yyyyMMddHHmmss`
- `randomId`: 8 caracteres alfanuméricos aleatórios
- `filename`: Nome original ou customizado

### **FileId Generation:**

```java
String fileId = UUID.randomUUID().toString().replace("-", "");
// Resultado: "abc123def456ghi789jkl012mno345pq"
```

### **AccessKey Generation (apenas para arquivos privados):**

```java
String accessKey = RandomStringUtils.randomAlphanumeric(16);
// Resultado: "k8h3j2k1n4m5p6q7"
```

---

## **Configurações da Aplicação**

### **application.yml / application.properties**

```yaml
storage:
  root-path: /var/storage/files
  max-file-size: 52428800  # 50MB em bytes
  allowed-buckets-regex: ^[a-zA-Z0-9_-]+$
  allowed-filename-regex: ^[a-zA-Z0-9._-]+$

mongodb:
  uri: mongodb://localhost:27017/filestorage
  database: filestorage

server:
  port: 8080
  base-url: http://localhost:8080
```

---

## **Fluxo de Upload**

```
1. Cliente envia POST /api/files/upload com X-API-Key header
   ↓
2. Validar API Key (verificar se existe e está ativa)
   ↓
3. Validar parâmetros (bucket, filename)
   ↓
4. Gerar fileId (UUID)
   ↓
5. Gerar storedFileName (timestamp_random_filename)
   ↓
6. Criar diretório do bucket (se não existir)
   ↓
7. Salvar arquivo no filesystem
   ↓
8. Se isPublic = false → gerar accessKey
   ↓
9. Salvar metadados no MongoDB
   ↓
10. Retornar response com URLs
```

---

## **Fluxo de Download**

```
1. Cliente acessa GET /files/{fileId}?key=xxx
   ↓
2. Buscar arquivo no MongoDB pelo fileId
   ↓
3. Se não encontrado → 404 Not Found
   ↓
4. Se isPublic = true → servir arquivo
   ↓
5. Se isPublic = false:
   ├─ Se key não fornecida → 403 Forbidden
   ├─ Se key incorreta → 403 Forbidden
   └─ Se key correta → servir arquivo
   ↓
6. Retornar arquivo com headers apropriados
```

---
## **Estrutura de Código Sugerida**

```
src/main/java/com/example/filestorage/
├── config/
│   ├── MongoConfig.java
│   ├── StorageConfig.java
│   └── SecurityConfig.java
├── controller/
│   └── FileController.java
├── service/
│   ├── FileStorageService.java
│   ├── FileMetadataService.java
│   └── ApiKeyService.java
├── repository/
│   └── FileMetadataRepository.java
├── model/
│   ├── FileMetadata.java
│   └── dto/
│       ├── UploadRequest.java
│       └── UploadResponse.java
├── security/
│   ├── ApiKeyAuthFilter.java
│   └── ApiKeyValidator.java
├── exception/
│   ├── FileNotFoundException.java
│   ├── InvalidBucketException.java
│   ├── AccessDeniedException.java
│   └── UnauthorizedException.java
└── util/
    ├── FileNameGenerator.java
    └── AccessKeyGenerator.java
```

---

## **Docker Compose para Desenvolvimento**

```yaml
version: '3.8'

services:
  mongodb:
    image: mongo:7
    container_name: filestorage_mongo
    restart: unless-stopped
    ports:
      - "27017:27017"
    environment:
      MONGO_INITDB_DATABASE: filestorage
    volumes:
      - mongo_data:/data/db
    networks:
      - filestorage_network

  app:
    build: .
    container_name: filestorage_app
    restart: unless-stopped
    ports:
      - "8080:8080"
    environment:
      SPRING_DATA_MONGODB_URI: mongodb://mongodb:27017/filestorage
      STORAGE_ROOT_PATH: /storage
    volumes:
      - storage_data:/storage
    networks:
      - filestorage_network
    depends_on:
      - mongodb

volumes:
  mongo_data:
  storage_data:

networks:
  filestorage_network:
    driver: bridge
```

---

## **Testes Importantes**

### **Casos de Teste**

**Autenticação:**
1. ✅ Upload sem API Key → 401 Unauthorized
2. ✅ Upload com API Key inválida → 401 Unauthorized
3. ✅ Upload com API Key válida → Sucesso
4. ✅ Delete sem API Key → 401 Unauthorized

**Funcionalidades:**
5. ✅ Upload arquivo público → Deve retornar URL pública
6. ✅ Upload arquivo privado → Deve retornar URL + accessKey
7. ✅ Acessar arquivo público sem key → Sucesso
8. ✅ Acessar arquivo privado sem key → 403 Forbidden
9. ✅ Acessar arquivo privado com key correta → Sucesso
10. ✅ Acessar arquivo privado com key incorreta → 403 Forbidden
11. ✅ Upload com bucket inválido → 400 Bad Request
12. ✅ Upload com mesmo nome de arquivo → Gerar nome único
13. ✅ Deletar arquivo → Remover do filesystem e MongoDB
14. ✅ Buscar arquivo inexistente → 404 Not Found


---

## **Exemplo de Uso Completo**

```bash
# 1. Upload arquivo privado
curl -X POST http://localhost:8080/api/files/upload \
  -H "X-API-Key: a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6" \
  -F "file=@document.pdf" \
  -F "bucket=confidential" \
  -F "isPublic=false"

# Response:
# {
#   "fileId": "abc123",
#   "privateUrl": "http://localhost:8080/files/abc123?key=k8h3j2k1",
#   "accessKey": "k8h3j2k1"
# }

# 2. Acessar arquivo privado
curl "http://localhost:8080/files/abc123?key=k8h3j2k1" --output downloaded.pdf

# 3. Upload arquivo público
curl -X POST http://localhost:8080/api/files/upload \
  -H "X-API-Key: a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6" \
  -F "file=@image.jpg" \
  -F "bucket=images" \
  -F "isPublic=true"

# Response:
# {
#   "fileId": "def456",
#   "url": "http://localhost:8080/files/def456"
# }

# 4. Acessar arquivo público (sem API Key necessária)
curl "http://localhost:8080/files/def456" --output downloaded.jpg

# 5. Deletar arquivo (requer API Key)
curl -X DELETE http://localhost:8080/api/files/abc123 \
  -H "X-API-Key: a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6" \
  -H "X-Access-Key: k8h3j2k1"
```

---

**Documentação criada em:** 2024-02-16  
**Versão:** 1.0  
**Status:** Pronto para implementação