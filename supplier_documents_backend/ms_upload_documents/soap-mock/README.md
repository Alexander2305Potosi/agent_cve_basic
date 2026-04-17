# Mock del Servicio SOAP Externo

Este proyecto SoapUI simula el servicio SOAP externo que consume `ms-upload-documents`. Soporta múltiples escenarios para pruebas de reintentos.

## Iniciar el Mock

### Opción 1: SoapUI Desktop
1. Abrir `ExternalDocumentService-soapui-project.xml` en SoapUI
2. Doble click en `DocumentServiceMock` → Click en el botón verde de play

### Opción 2: Maven (soapui-runner)
```bash
cd soap-mock
mvn com.smartbear.soapui:soapui-maven-plugin:5.7.2:mock
```

### Opción 3: Docker
```bash
docker run -p 8081:8081 -v $(pwd)/soap-mock:/project smartbear/soapuios mock -p 8081 /project/ExternalDocumentService-soapui-project.xml
```

## Escenarios de Prueba

El mock usa el campo `DocumentType` para determinar la respuesta:

| DocumentType | Escenario | Comportamiento |
|--------------|-----------|----------------|
| `INV` | **Éxito** | Respuesta 200 OK con datos válidos |
| `ERR` | **Error permanente** | Respuesta SOAP con Success=false (no reintenta) |
| `TMO` | **Timeout** | Retraso de 35s (triggers timeout del cliente) |
| `FLK` | **Flaky service** | HTTP 503 Service Unavailable (reintenta) |
| `SRV` | **Server Error** | HTTP 500 Internal Server Error (reintenta) |
| `REF` | **Connection Refused** | Cierra conexión abruptamente (reintenta) |
| `RND` | **Aleatorio** | 50% éxito, 50% error |

## Ejemplos de Uso

### Test de Reintentos Exitosos
```bash
# Primero falla con timeout, luego éxito
# DocumentType: TMO (timeout) → luego INV (éxito)
```

### Test de Error No Recuperable
```bash
# DocumentType: ERR - error de validación, no reintenta
curl -X POST http://localhost:8081/ws/documents \
  -H "Content-Type: text/xml" \
  -d '<?xml version="1.0"?>
  <soapenv:Envelope>
    <soapenv:Body>
      <doc:DocumentSubmissionRequest>
        <doc:DocumentData>
          <doc:DocumentType>ERR</doc:DocumentType>
        </doc:DocumentData>
      </doc:DocumentSubmissionRequest>
    </soapenv:Body>
  </soapenv:Envelope>'
```

### Test de Reintentos por 503
```bash
# DocumentType: FLK - el cliente reintentará 3 veces
# Cada reintento espera: 500ms → 1000ms → 2000ms (backoff exponencial)
```

## Configuración del Cliente

El adaptador está configurado para:
- **3 reintentos máximos**
- **Backoff inicial**: 500ms
- **Backoff máximo**: 10s
- **Jitter**: 50%

Reintenta automáticamente en:
- `SocketTimeoutException`
- `WebServiceException`
- Errores con mensaje: "timeout", "connection", "unavailable", "refused"

## Logs del Mock

En SoapUI → pestaña "Script Log" puedes ver:
```
Mon Nov 20 10:30:45 CET 2024:INFO:Dispatching to mock response [Success Response]
Mon Nov 20 10:31:12 CET 2024:INFO:Dispatching to mock response [Timeout Response]
```

## URLs

- **Mock**: `http://localhost:8081/ws/documents`
- **WSDL**: `http://localhost:8081/ws/documents?wsdl`
