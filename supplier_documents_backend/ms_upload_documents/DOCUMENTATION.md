# MS Upload Documents - Documentación

## Índice
1. [Descripción General](#descripción-general)
2. [Arquitectura](#arquitectura)
3. [Flujo de Datos](#flujo-de-datos)
4. [Endpoints](#endpoints)
5. [Uso del API](#uso-del-api)
6. [Mock SOAP](#mock-soap)
7. [Configuración](#configuración)
8. [Testing](#testing)

---

## Descripción General

**ms_upload_documents** es un microservicio reactivo construido con **Spring WebFlux** que sigue el patrón de arquitectura:

```
RouteRest -> UseCase -> Handler -> Gateway -> Adapter
```

### Responsabilidades

- **Subir documentos** mediante API REST
- **Validar documentos** (tipo, tamaño, formato)
- **Almacenar documentos** localmente
- **Enviar documentos** a un servicio externo SOAP para procesamiento adicional

---

## Arquitectura

### Estructura de Capas

```
src/main/java/com/supplier/documents/upload/
├── application/
│   ├── dto/                    # Data Transfer Objects
│   ├── gateway/                # Interfaces Gateway (puertos)
│   │   ├── DocumentStorageGateway.java
│   │   ├── ExternalDocumentGateway.java
│   │   └── DocumentRepositoryGateway.java
│   ├── handler/                # Implementación de casos de uso
│   │   └── UploadDocumentHandler.java
│   ├── mapper/                 # Mappers
│   │   └── DocumentMapper.java
│   └── usecase/                # Interfaces de casos de uso
│       └── UploadDocumentUseCase.java
├── domain/                     # Capa de Dominio
│   ├── entity/
│   ├── valueobject/
│   ├── exception/
│   └── repository/
└── infrastructure/
    ├── rest/                   # RouteRest - Adaptadores REST
    │   └── DocumentRouteRest.java
    ├── adapter/                # Adapters - Implementaciones
    │   ├── soap/
    │   │   ├── ExternalDocumentSoapAdapter.java
    │   │   └── stub/           # Stubs JAX-WS
    │   └── persistence/
    │       └── InMemoryDocumentAdapter.java
    ├── config/                 # Configuraciones Spring
    └── exceptionhandler/
```

### Patrón de Flujo

```
┌─────────────────────────────────────────────────────────────┐
│                        INFRASTRUCTURE                        │
│  ┌─────────────┐          ┌──────────────────────────────┐ │
│  │  RouteRest  │ ───────► │         Adapter              │ │
│  │  (REST API) │          │  ┌────────┐  ┌──────────────┐ │ │
│  └──────┬──────┘          │  │ SOAP   │  │ Persistence  │ │ │
│         │                 │  │        │  │              │ │ │
│         │ Implements      │  └────────┘  └──────────────┘ │ │
│         │                 └──────────────────────────────┘ │
├─────────┼────────────────────────────────────────────────────┤
│         │              APPLICATION                         │
│         │ Uses                                              │
│         ▼                                                   │
│  ┌─────────────┐          ┌──────────────────────────────┐│
│  │   UseCase   │◄─────────│          Handler              ││
│  │  (Interface)│          │  (Implementation)             ││
│  └─────────────┘          └────────────┬───────────────────┘│
│                                        │                   │
│                                        │ Uses              │
│                                        ▼                   │
│                         ┌──────────────────────────────┐ │
│                         │        Gateway               │ │
│                         │  (Interfaces - Ports)        │ │
│                         └──────────────────────────────┘ │
└────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
                              ┌────────────────────┐
                              │      DOMAIN        │
                              │  ┌──────────────┐  │
                              │  │   Entity     │  │
                              │  │ Value Object │  │
                              │  │ Exceptions   │  │
                              │  └──────────────┘  │
                              └────────────────────┘
```

### Responsabilidades por Capa

| Capa | Componente | Responsabilidad |
|------|------------|-----------------|
| **Infrastructure** | RouteRest | Expone endpoints REST, recibe requests HTTP |
| **Infrastructure** | Adapter | Implementa gateways (SOAP, persistence) |
| **Application** | UseCase | Define contratos de casos de uso |
| **Application** | Handler | Orquesta la lógica de negocio |
| **Application** | Gateway | Define contratos para infraestructura |
| **Domain** | Entity/VO | Lógica de negocio pura |

---

## Flujo de Datos

### 1. RouteRest Recibe el Request

```java
// DocumentRouteRest.java
@PostMapping("/upload")
public Mono<DocumentUploadResponse> uploadDocument(
        @Valid @RequestBody DocumentUploadRequest request) {
    return uploadDocumentUseCase.execute(request);
}
```

### 2. UseCase Define el Contrato

```java
// UploadDocumentUseCase.java (interface)
public interface UploadDocumentUseCase {
    Mono<DocumentUploadResponse> execute(DocumentUploadRequest request);
}
```

### 3. Handler Implementa la Lógica

```java
// UploadDocumentHandler.java
@Component
public class UploadDocumentHandler implements UploadDocumentUseCase {
    
    @Override
    public Mono<DocumentUploadResponse> execute(DocumentUploadRequest request) {
        return validateRequest(request)
            .flatMap(this::createAndStoreDocument)  // Gateway
            .flatMap(this::sendToExternalService)    // Gateway
            .flatMap(this::persistAndBuildResponse); // Gateway
    }
}
```

### 4. Gateway Define Contratos

```java
// ExternalDocumentGateway.java (interface)
public interface ExternalDocumentGateway {
    Mono<ExternalDocumentResponse> send(ExternalDocumentRequest request);
}
```

### 5. Adapter Implementa

```java
// ExternalDocumentSoapAdapter.java
@Component
public class ExternalDocumentSoapAdapter implements ExternalDocumentGateway {
    
    @Override
    public Mono<ExternalDocumentResponse> send(ExternalDocumentRequest request) {
        // Implementación SOAP real
    }
}
```

---

## Endpoints

### POST `/api/v1/documents/upload`

Sube un documento y lo envía al servicio SOAP externo.

#### Request

```json
{
  "fileName": "invoice_001.pdf",
  "documentTypeCode": "INV",
  "contentType": "application/pdf",
  "base64Content": "JVBERi0xLjQK...",
  "uploadedBy": "john.doe@company.com",
  "metadata": {
    "supplierId": "SUP-12345",
    "orderNumber": "PO-2024-001"
  }
}
```

#### Response - Success (201)

```json
{
  "documentId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "SUCCESS",
  "fileName": "invoice_001.pdf",
  "externalDocumentId": "EXT-12345678",
  "externalConfirmationMessage": "Document received and validated successfully",
  "externalProcessingStatus": "PENDING_REVIEW",
  "timestamp": "2024-04-16T14:30:00"
}
```

#### Response - Partial Success (200)

```json
{
  "documentId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PARTIAL_SUCCESS",
  "fileName": "invoice_001.pdf",
  "errorMessage": "Document stored locally but external sync failed: Connection timeout"
}
```

### GET `/api/v1/documents/health`

```json
{
  "status": "UP",
  "service": "ms-upload-documents",
  "version": "1.0.0"
}
```

---

## Uso del API

### cURL

```bash
# Subir documento
curl -X POST http://localhost:8080/api/v1/documents/upload \
  -H "Content-Type: application/json" \
  -d '{
    "fileName": "invoice_001.pdf",
    "documentTypeCode": "INV",
    "contentType": "application/pdf",
    "base64Content": "'$(base64 -w 0 invoice.pdf)'",
    "uploadedBy": "john.doe@company.com",
    "metadata": {
      "supplierId": "SUP-12345"
    }
  }'
```

### Swagger UI

```
http://localhost:8080/swagger-ui.html
```

---

## Mock SOAP

### Importar en SOAP UI

1. Abrir **SOAP UI**
2. `File` → `Import Project`
3. Seleccionar: `soap-mock/ExternalDocumentService-soapui-project.xml`

### Iniciar Mock

1. Expander proyecto `External Document Service Mock`
2. Click derecho en `DocumentServiceMock`
3. Seleccionar `Start`
4. El mock escucha en `http://localhost:8081/ws/documents`

---

## Configuración

### application.yml

```yaml
server:
  port: 8080

external:
  soap:
    service:
      url: http://localhost:8081/ws/documents
      timeout:
        connection: 30000
        read: 60000

app:
  document:
    max-size: 10485760  # 10MB
```

---

## Testing

```bash
# Ejecutar tests
./gradlew test

# Coverage
./gradlew jacocoTestReport

# Mutation Testing
./gradlew pitest
```

### Estructura de Tests

```
src/test/
├── domain/           # Tests de entidades y value objects
├── handler/          # Tests de handlers (UseCase)
└── adapter/          # Tests de adapters
```

---

## Diagrama de Secuencia

```
┌─────────┐    ┌──────────┐    ┌─────────┐    ┌──────────┐    ┌──────────┐
│ Client  │    │ RouteRest│    │ Handler │    │ Gateway  │    │ Adapter  │
└────┬────┘    └────┬─────┘    └────┬────┘    └────┬─────┘    └────┬─────┘
     │              │               │               │               │
     │ ───────────►│               │               │               │
     │   POST      │               │               │               │
     │              │               │               │               │
     │              │ ─────────────►│               │               │
     │              │ execute(req)  │               │               │
     │              │               │               │               │
     │              │               │ ─────────────►│               │
     │              │               │ store(doc)    │               │
     │              │               │               │ ─────────────►│
     │              │               │               │               │
     │              │               │               │ ◄─────────────│
     │              │               │               │               │
     │              │               │ ─────────────►│               │
     │              │               │ send(req)     │               │
     │              │               │               │ ─────────────►│
     │              │               │               │    SOAP Call    │
     │              │               │               │ ◄─────────────│
     │              │               │               │               │
     │              │               │ ◄─────────────│               │
     │              │               │               │               │
     │              │ ◄─────────────│               │               │
     │              │               │               │               │
     │ ◄───────────│               │               │               │
     │   Response   │               │               │               │
     │              │               │               │               │
```
