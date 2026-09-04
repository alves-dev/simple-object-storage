<!--
template_name=header-code
template_version=v1
-->

<h1 align="center" style="color:#F1F5F9;">
  SOS - Simple Object Storage
</h1>

<p align="center">
  <span style="color:#94A3B8; font-size:16px;">
    Serviço simples de armazenamento e disponibilização de arquivos com suporte a acesso público e privado, organização por buckets e persistência de metadados em MongoDB.
  </span>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/topics-10B981?style=flat-square&logoColor=white"/> <img src="https://img.shields.io/badge/java-1E293B?style=flat-square&logoColor=white"/> <img src="https://img.shields.io/badge/spring-1E293B?style=flat-square&logoColor=white"/> <img src="https://img.shields.io/badge/mongodb-1E293B?style=flat-square&logoColor=white"/> <img src="https://img.shields.io/badge/http-1E293B?style=flat-square&logoColor=white"/> <img src="https://img.shields.io/badge/docker-1E293B?style=flat-square&logoColor=white"/> <img src="https://img.shields.io/badge/gradle-1E293B?style=flat-square&logoColor=white"/>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/status-development-10B981?style=flat-square"/>
</p>

<p align="center" style="margin-top: 4px;">
  <span style="color:10B981; font-size:13px;">
    🛠️ Actively worked on. New features may come and things can change.
  </span>
</p>


<hr/>

## 📚 API documentation

The OpenAPI documentation is disabled by default and is available only with the `dev` profile:

```bash
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

With the application running in that profile, the interactive Swagger UI is available at:

```text
http://localhost:8088/swagger-ui/index.html
```

The generated OpenAPI contract is available in JSON and YAML formats:

```text
http://localhost:8088/v3/api-docs
http://localhost:8088/v3/api-docs.yaml
```

The Swagger UI documents the `X-API-Key` header used by V2 JSON operations and the
`key` query parameter accepted by private content URLs.

<hr/>

## API V2

JSON management endpoints under `/api/v2/**` require a client API key in `X-API-Key`.
`GET /api/v2/buckets/{bucketName}/random-image` is public and returns only public images.
API keys are stored only as SHA-256 hashes. Create the first administrative client with:

```bash
./gradlew bootRun --args='create-client developer-admin "Developer Admin" --admin'
```

When running the packaged application in a container, execute the command inside the
container instead. `--server.port=0` prevents a port conflict with the running instance:

```bash
java -jar /app/application.jar --server.port=0 create-client developer-admin "Developer Admin" --admin
```

The key is displayed once. Do not add it to source control or logs.

Main endpoints:

```text
POST   /api/v2/files
GET    /api/v2/files/{fileId}/info
DELETE /api/v2/files/{fileId}
POST   /api/v2/files/{fileId}/temporary-url

GET    /api/v2/buckets
GET    /api/v2/buckets/{bucketName}
DELETE /api/v2/buckets/{bucketName}
GET    /api/v2/buckets/{bucketName}/files
GET    /api/v2/buckets/{bucketName}/random-image
```

Content remains available without client authentication at:

```text
GET|HEAD /files/{fileId}
GET|HEAD /files/{bucketName}/{fileId}
GET|HEAD /files/{bucketName}/{filename}
```

Private content requires a permanent `?key=...` or a signed temporary `?token=...`.
Responses support `ETag`, `If-None-Match`, `HEAD`, and appropriate public/private cache headers.

Browser applications may fetch public content from the origins configured in
`CORS_ALLOWED_ORIGINS` (a comma-separated list). CORS is intentionally limited to
`GET` and `HEAD` requests under `/files/**`; it does not grant access to management APIs.

The former `/api/files/**` JSON endpoints were removed. The historical
`/files/{fileId}` content URL remains supported for file delivery.

## V2 migration

Back up MongoDB and the storage directory before running the real migration:

```bash
./gradlew bootRun --args='migrate-v2 --dry-run'
./gradlew bootRun --args='migrate-v2'
```

The migration preserves file IDs, access keys, physical names, and historical URLs. It can be
executed repeatedly; filename conflicts are resolved using a deterministic file-ID prefix.

Valkey is optional. When unavailable, content is served directly from the filesystem. Storage
integrity checks run on the configured schedule and only report orphan files; they never delete
stored content automatically.

Required environment variables and optional cache/integrity settings are documented in
`.env.example`.

<hr/>



<!--
template_name=stack-default
template_version=v1
-->

<div align="center">

<div align="center">

<h2>⚙️ Tech Stack</h2>

<img src="https://img.shields.io/badge/Language-10B981?style=flat-square&logoColor=white"/><img src="https://img.shields.io/badge/java-3B82F6?style=flat-square"/>
<img src="https://img.shields.io/badge/Framework-10B981?style=flat-square&logoColor=white"/><img src="https://img.shields.io/badge/spring-10B981?style=flat-square"/>

---

**💾 Database** · **🔌 Protocols** · **🛠️ Tools**  
<sub>Persistence layer</sub> · <sub>Communication layer</sub> · <sub>Infrastructure & tooling</sub>

<img src="https://img.shields.io/badge/mongodb-10B981?style=flat-square"/> <img src="https://img.shields.io/badge/http-6366F1?style=flat-square"/> <img src="https://img.shields.io/badge/docker-0EA5E9?style=flat-square"/> <img src="https://img.shields.io/badge/gradle-0EA5E9?style=flat-square"/>

</div>

</div>

<!--
template_name=footer-default
template_version=v1
-->

---

<p align="center">
  <img src="https://img.shields.io/badge/licença-NO%20LICENSE-3B82F6.svg" alt="license"/>
</p>

---

<p align="center">
  <strong style="font-size:16px;">Igor Moreira Alves</strong><br/>
  <span style="color:#94A3B8;">Back-end Software Engineer • Java • Python • Spring Boot • AWS</span>
</p>

<p align="center">
  <a href="https://alves-dev.com" target="_blank">
    <img src="https://img.shields.io/badge/site-alves--dev.com-10B981?style=for-the-badge&logo=google-chrome&logoColor=white"/>
  </a>
  <a href="https://www.linkedin.com/in/alves-dev/" target="_blank">
    <img src="https://img.shields.io/badge/linkedin-Igor%20Moireira-3B82F6?style=for-the-badge&logo=linkedin&logoColor=white"/>
  </a>
  <a href="https://github.com/alves-dev" target="_blank">
    <img src="https://img.shields.io/badge/github-alves--dev-1E293B?style=for-the-badge&logo=github&logoColor=white"/>
  </a>
</p>

<p align="center">
  <sub style="color:#94A3B8;">
    Atualizado em 2026-02-17 10:50
  </sub>
</p>
