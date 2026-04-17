# Instrucciones para Compilar y Ejecutar

## Requisitos

- Java 17 JDK instalado
- Variable JAVA_HOME configurada
- (Opcional) SoapUI 5.7+ para el mock del servicio externo

## Verificar Java

```bash
java -version
# Debe mostrar: openjdk version "17" o superior
```

## Compilar

```bash
./gradlew clean build
```

## Ejecutar

### Opción 1: Con Gradle
```bash
./gradlew bootRun
```

### Opción 2: Con Docker
```bash
docker-compose up --build
```

### Opción 3: Con Java directamente
```bash
java -jar build/libs/ms_upload_documents-1.0.0.jar
```

## Puerto de Inicio

**El microservicio inicia en el puerto: 8080**

## Endpoints disponibles

- Health: http://localhost:8080/api/v1/documents/health
- Upload: http://localhost:8080/api/v1/documents/upload
- Swagger: http://localhost:8080/swagger-ui.html

---

# Mock del Servicio SOAP Externo

Para probar la integración con el servicio SOAP externo, usar el mock en `soap-mock/`.

## Iniciar el Mock

### Opción 1: SoapUI Desktop (Recomendado)
```bash
# Abrir el proyecto
open soap-mock/ExternalDocumentService-soapui-project.xml

# En SoapUI:
# 1. Expandir "External Document Service Mock"
# 2. Doble click en "DocumentServiceMock"
# 3. Click en el botón verde de PLAY (▶)
```

### Opción 2: Línea de comandos (SoapUI TestRunner)
```bash
cd soap-mock
/path/to/soapui/bin/testrunner.sh -M -p 8081 ExternalDocumentService-soapui-project.xml
```

## Escenarios de Prueba

El mock usa el campo `documentType` para simular diferentes comportamientos:

| Tipo | Escenario | HTTP Status | ¿Reintenta? |
|------|-----------|-------------|-------------|
| `INV` | Éxito normal | 200 OK | ❌ |
| `ERR` | Error de validación | 200 + Success=false | ❌ Error permanente |
| `TMO` | Timeout de red | Delay 35s | ✅ Sí - Timeout |
| `FLK` | Servicio intermitente | 503 | ✅ Sí - Service Unavailable |
| `SRV` | Error de servidor | 500 | ✅ Sí - Internal Error |
| `REF` | Conexión rechazada | Connection closed | ✅ Sí - Connection refused |
| `RND` | Aleatorio (50/50) | 200 | Variable |

### Ejemplo: Probar reintentos

1. **Iniciar el mock en SoapUI** (asegurarse que esté en puerto 8081)

2. **Enviar request con tipo `TMO` (Timeout)**:
```bash
curl -X POST http://localhost:8080/api/v1/documents/upload \
  -H "Content-Type: application/json" \
  -d '{
    "fileName": "test.pdf",
    "documentTypeCode": "TMO",
    "contentType": "application/pdf",
    "base64Content": "dGVzdCBjb250ZW50",
    "uploadedBy": "user@test.com"
  }'
```

3. **Observar logs** - Verás que el cliente reintenta automáticamente después del timeout

4. **Enviar request con tipo `FLK` (Flaky)**:
```bash
curl -X POST http://localhost:8080/api/v1/documents/upload \
  -H "Content-Type: application/json" \
  -d '{
    "fileName": "test.pdf",
    "documentTypeCode": "FLK",
    "contentType": "application/pdf",
    "base64Content": "dGVzdCBjb250ZW50",
    "uploadedBy": "user@test.com"
  }'
```

**Resultado esperado**: El adaptador reintenta 3 veces con backoff exponencial (500ms → 1s → 2s)

## Configuración de Reintentos

El adaptador SOAP está configurado en `ExternalDocumentSoapAdapter.java`:

```java
private static final int MAX_RETRIES = 3;
private static final Duration INITIAL_BACKOFF = Duration.ofMillis(500);
private static final double BACKOFF_MULTIPLIER = 2.0;
```

Reintenta automáticamente en:
- `SocketTimeoutException`
- `WebServiceException` 
- Errores con mensajes: "timeout", "connection", "unavailable", "refused"

## URLs del Mock

- **Endpoint SOAP**: `http://localhost:8081/ws/documents`
- **WSDL**: `http://localhost:8081/ws/documents?wsdl`

## Troubleshooting

### Error: Connection refused
- Verificar que el mock esté corriendo en el puerto 8081
- Revisar firewall/antivirus bloqueando conexiones

### Error: Timeout en cada request
- El mock con tipo `TMO` intentionalmente retrasa 35 segundos
- Usar tipo `INV` para respuesta inmediata

### Verificar logs de reintentos
```bash
# Buscar en los logs del microservicio:
# "[Adapter-SOAP] Retry attempt X/3"
# "[Adapter-SOAP] SOAP call failed after 3 retries"
```
