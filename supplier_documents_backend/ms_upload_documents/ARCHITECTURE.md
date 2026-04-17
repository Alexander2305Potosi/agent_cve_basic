# Clean Architecture - MS Upload Documents

## Arquitectura Limpia Implementada

Este microservicio sigue los principios de **Clean Architecture** con una clara separación de responsabilidades.

### Flujo de Datos

```
┌─────────────────────────────────────────────────────────────────┐
│                     INFRASTRUCTURE LAYER                        │
│  ┌──────────────┐      ┌─────────────────────────────────────┐│
│  │  RouteRest     │──────▶│  Adapters (SOAP, Persistence)       ││
│  │  (Controller)  │      │  • ExternalDocumentSoapAdapter        ││
│  └──────────────┘      │  • InMemoryDocumentAdapter            ││
│         │              └─────────────────────────────────────┘│
│         │                                    │                  │
│         ▼                                    │                  │
│  ┌──────────────┐      ┌─────────────────────▼──────────────┐   │
│  │  DTOs        │      │  Infrastructure Config           │   │
│  │  (Request/   │      │  • WebServiceConfig              │   │
│  │   Response)  │      │  • OpenApiConfig                 │   │
│  └──────────────┘      │  • ExceptionHandler              │   │
│                        └──────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼ (implements)
┌─────────────────────────────────────────────────────────────────┐
│                      APPLICATION LAYER                          │
│  ┌───────────────────────────────────────────────────────────┐   │
│  │  Handlers (Implementan UseCases)                         │   │
│  │  • UploadDocumentHandler                                  │   │
│  └───────────────────────────────────────────────────────────┘   │
│  ┌───────────────────────────────────────────────────────────┐   │
│  │  DTOs & Mappers                                           │   │
│  │  • DocumentUploadRequest/Response                       │   │
│  │  • ExternalDocumentRequest/Response                     │   │
│  │  • DocumentMapper                                         │   │
│  └───────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼ (implements / uses)
┌─────────────────────────────────────────────────────────────────┐
│                        DOMAIN LAYER                             │
│  ┌───────────────────────────────────────────────────────────┐   │
│  │  UseCase Interfaces (Ports - Inbound)                   │   │
│  │  • UploadDocumentUseCase                                  │   │
│  └───────────────────────────────────────────────────────────┘   │
│  ┌───────────────────────────────────────────────────────────┐   │
│  │  Gateway Interfaces (Ports - Outbound)                  │   │
│  │  • ExternalDocumentGateway                                │   │
│  │  • DocumentStorageGateway                                 │   │
│  │  • DocumentRepositoryGateway                              │   │
│  └───────────────────────────────────────────────────────────┘   │
│  ┌───────────────────────────────────────────────────────────┐   │
│  │  Entities                                                 │   │
│  │  • Document                                               │   │
│  └───────────────────────────────────────────────────────────┘   │
│  ┌───────────────────────────────────────────────────────────┐   │
│  │  Value Objects                                            │   │
│  │  • DocumentId                                               │   │
│  │  • DocumentType                                             │   │
│  └───────────────────────────────────────────────────────────┘   │
│  ┌───────────────────────────────────────────────────────────┐   │
│  │  Repository Interface                                     │   │
│  │  • DocumentRepository                                     │   │
│  └───────────────────────────────────────────────────────────┘   │
│  ┌───────────────────────────────────────────────────────────┐   │
│  │  Exceptions                                               │   │
│  │  • DomainException                                        │   │
│  │  • DocumentValidationException                            │   │
│  │  • DocumentNotFoundException                              │   │
│  └───────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

## Capas y Responsabilidades

### 1. Infrastructure Layer (Capa Externa)

**Responsabilidad**: Manejar interacciones externas (HTTP, SOAP, persistencia)

**Componentes**:
- `DocumentRouteRest` - Controlador REST (exposición HTTP)
- `ExternalDocumentSoapAdapter` - Adaptador SOAP
- `InMemoryDocumentAdapter` - Adaptador de persistencia
- `WebServiceConfig` - Configuración SOAP
- `GlobalExceptionHandler` - Manejo de excepciones

**Reglas**:
- Depende de la capa Application
- Implementa interfaces Gateway
- No contiene lógica de negocio

### 2. Application Layer (Capa Intermedia)

**Responsabilidad**: Orquestar casos de uso

**Componentes**:
- `UploadDocumentHandler` - Implementación del caso de uso (implementa UploadDocumentUseCase)
- DTOs - Objetos de transferencia de datos
- Mappers - Transformaciones entre DTOs y Entities

**Flujo Interno**:
```
RouteRest
    ↓
UploadDocumentUseCase.execute(request)  // Defined in Domain
    ↓
UploadDocumentHandler:                   // Implemented in Application
  1. validateRequest()
  2. createAndStoreDocument() → DocumentStorageGateway (Domain Port)
  3. sendToExternalService() → ExternalDocumentGateway (Domain Port)
  4. persistAndBuildResponse() → DocumentRepositoryGateway (Domain Port)
    ↓
Response
```

**Reglas**:
- Implementa interfaces de casos de uso definidos en Domain
- Usa puertos (interfaces) definidos en Domain
- Contiene la lógica de orquestación
- No depende de frameworks externos
- Usa DTOs para comunicación

### 3. Domain Layer (Capa Interna)

**Responsabilidad**: Contener la lógica de negocio pura y definir contratos (ports)

**Componentes**:

**Ports Inbound (Use Cases)**:
- `UploadDocumentUseCase` - Interface del caso de uso

**Ports Outbound (Gateways)**:
- `ExternalDocumentGateway` - Interface hacia servicio externo
- `DocumentStorageGateway` - Interface de almacenamiento
- `DocumentRepositoryGateway` - Interface de persistencia

**Domain Model**:
- `Document` - Entidad principal
- `DocumentId` - Value Object
- `DocumentType` - Value Object
- `DocumentRepository` - Interface de repositorio
- Excepciones de dominio

**Reglas**:
- No depende de ninguna otra capa
- Define contratos (interfaces) que otras capas implementan
- Contiene reglas de negocio
- Inmutable (Value Objects)
- Validaciones de dominio

## Estructura de Paquetes

```
com.supplier.documents.upload
├── application
│   ├── dto                    # Objetos de transferencia
│   ├── handler                # Implementaciones de casos de uso
│   └── mapper                 # Transformaciones DTO <-> Entity
├── domain
│   ├── entity                 # Entidades de negocio
│   ├── exception              # Excepciones de dominio
│   ├── repository             # Interfaces de repositorio
│   ├── usecase                # Interfaces de casos de uso (puertos inbound)
│   │   └── gateway            # Puertos (interfaces) hacia infraestructura
│   │       ├── DocumentStorageGateway
│   │       ├── DocumentRepositoryGateway
│   │       └── ExternalDocumentGateway
│   └── valueobject            # Objetos de valor
└── infrastructure
    ├── adapter
    │   ├── persistence        # Adaptadores de persistencia
    │   └── soap               # Adaptadores SOAP
    │       └── stub           # Stubs generados de WSDL
    ├── config                 # Configuraciones Spring
    ├── exceptionhandler       # Manejo global de excepciones
    └── rest                   # Controladores REST
```

## Flujo de Ejecución

### Caso de Uso: Subir Documento

```
1. Cliente HTTP
   ↓
2. DocumentRouteRest.uploadDocument()
   ↓ Request DTO
3. UploadDocumentUseCase.execute()     // Interface defined in Domain Layer
   ↓ (implemented by)
4. UploadDocumentHandler.execute()     // Implementation in Application Layer
   ├─→ Validaciones
   ├─→ DocumentMapper.toEntity()
   ├─→ DocumentStorageGateway.store()
   ├─→ ExternalDocumentGateway.send()
   └─→ DocumentRepositoryGateway.save()
   ↓
5. DocumentUploadResponse
```

## Principios Aplicados

### 1. Dependency Inversion Principle (DIP)

- El Domain Layer define interfaces de casos de uso (ports inbound)
- El Domain Layer define interfaces de puertos (ports outbound)
- El Application Layer implementa interfaces de casos de uso (handlers)
- El Infrastructure Layer implementa los puertos outbound
- Las dependencias apuntan hacia el dominio

```java
// Domain Layer (defines port inbound)
public interface UploadDocumentUseCase {
    Mono<DocumentUploadResponse> execute(DocumentUploadRequest request);
}

// Domain Layer (defines port outbound)
public interface ExternalDocumentGateway {
    Mono<ExternalDocumentResponse> send(ExternalDocumentRequest request);
}

// Application Layer (implements use case)
public class UploadDocumentHandler implements UploadDocumentUseCase {
    private final ExternalDocumentGateway externalGateway;
    
    public Mono<DocumentUploadResponse> execute(DocumentUploadRequest request) {
        // Uses ExternalDocumentGateway (defined in Domain)
        return externalGateway.send(...);
    }
}

// Infrastructure Layer (implements port)
@Component
public class ExternalDocumentSoapAdapter implements ExternalDocumentGateway {
    // Implementation
}
```

### 2. Single Responsibility Principle (SRP)

- `DocumentRouteRest` - Solo maneja HTTP
- `UploadDocumentHandler` - Solo orquesta el caso de uso
- `ExternalDocumentSoapAdapter` - Solo maneja SOAP
- `Document` - Solo contiene lógica de negocio

### 3. Separation of Concerns

- **Domain**: Lógica de negocio pura
- **Application**: Orquestación de casos de uso
- **Infrastructure**: Detalles técnicos (HTTP, SOAP, BD)

## Ventajas de esta Arquitectura

1. **Independencia de Frameworks**: El dominio no depende de Spring
2. **Testabilidad**: Las interfaces permiten mocks fáciles
3. **Flexibilidad**: Se pueden cambiar adaptadores sin tocar el dominio
4. **Claridad**: Cada capa tiene responsabilidad única
5. **Mantenibilidad**: Cambios aislados por capa

## Testing

### Tests Unitarios por Capa

- **Domain**: Tests de entidades, value objects y casos de uso (interfaces)
- **Application**: Tests de handlers (implementaciones de casos de uso) con mocks de gateways
- **Infrastructure**: Tests de adaptadores (integración)

### Ejemplo de Test (Handler)

```java
@ExtendWith(MockitoExtension.class)
class UploadDocumentHandlerTest {
    @Mock
    private DocumentStorageGateway storageGateway;
    
    @Mock
    private ExternalDocumentGateway externalGateway;
    
    @Test
    void shouldExecuteSuccessfully() {
        // Given - When - Then pattern
        // Verifica la orquestación entre gateways
        // Testing the implementation of UploadDocumentUseCase from Domain
    }
}
```

## Notas sobre la Implementación

### Uso de Reactor (Reactive)

Todas las operaciones retornan `Mono<T>` para:
- Operaciones no bloqueantes
- Mejor manejo de concurrencia
- Composición funcional

### Manejo de Errores

- **Validaciones**: Domain exceptions (400 Bad Request)
- **Errores externos**: No bloquean el flujo, se marca como PARTIAL_SUCCESS
- **Errores internos**: 500 Internal Server Error

### Adaptadores SOAP

- Stubs generados desde WSDL
- Adaptador maneja la transformación DTO ↔ SOAP
- Timeout configurado (30s connect, 60s read)

## Referencias

- [Clean Architecture - Robert C. Martin](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- [Hexagonal Architecture](https://alistair.cockburn.us/hexagonal-architecture/)
- [Spring Reactive](https://docs.spring.io/spring-framework/reference/web/webflux.html)
