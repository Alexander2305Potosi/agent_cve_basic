package com.supplier.documents.upload.infrastructure.adapter.soap;

import com.supplier.documents.upload.application.dto.ExternalDocumentRequest;
import com.supplier.documents.upload.application.dto.ExternalDocumentResponse;
import com.supplier.documents.upload.domain.gateway.ExternalDocumentGateway;
import com.supplier.documents.upload.infrastructure.adapter.soap.stub.*;
import jakarta.xml.ws.BindingProvider;
import jakarta.xml.ws.WebServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Adapter - Adaptador SOAP para envío de documentos externos.
 * Implementa el gateway ExternalDocumentGateway.
 *
 * <p><b>¿Por qué usar CXF/JAX-WS en lugar de WebClient para SOAP?</b></p>
 *
 * <p>1. <b>Contrato-first (WSDL)</b>: El servicio SOAP externo expone un WSDL que define
 *    el contrato exacto. CXF genera automáticamente las clases Java (stubs) a partir
 *    del WSDL, garantizando type-safety y evitando errores de serialización.</p>
 *
 * <p>2. <b>Stack SOAP completo</b>: JAX-WS maneja automáticamente headers SOAP,
 *    namespaces, WS-Addressing, WS-Security y attachment MTOM. Con WebClient
 *    tendrías que construir manualmente el XML SOAP y parsear respuestas.</p>
 *
 * <p>3. <b>Transaccionalidad</b>: SOAP soporta WS-AtomicTransaction para operaciones
 *    distribuidas. WebClient solo maneja HTTP sin semántica transaccional SOAP.</p>
 *
 * <p>4. <b>Herramientas enterprise</b>: CXF proporciona interceptores, handlers,
 *    y logging de mensajes SOAP sin código adicional.</p>
 *
 * <p>5. <b>Backpressure y bloqueo</b>: Aunque WebClient es reactivo, SOAP es inherentemente
 *    bloqueante (request/response síncrono). Usamos Schedulers.boundedElastic() para
 *    aislar el bloqueo sin saturar threads reactivos.</p>
 */
@Component
@Slf4j
public class ExternalDocumentSoapAdapter implements ExternalDocumentGateway {

    private static final int MAX_RETRIES = 3;
    private static final Duration INITIAL_BACKOFF = Duration.ofMillis(500);
    private static final double BACKOFF_MULTIPLIER = 2.0;

    private final DocumentServicePortType documentServicePort;
    private final String serviceEndpoint;

    public ExternalDocumentSoapAdapter(
            DocumentServicePortType documentServicePort,
            @Value("${external.soap.service.url:http://localhost:8081/ws/documents}") String serviceEndpoint) {
        this.documentServicePort = documentServicePort;
        this.serviceEndpoint = serviceEndpoint;
    }

    /**
     * Envía un documento al servicio SOAP externo con reintentos automáticos.
     *
     * <p><b>Flujo:</b></p>
     * <ol>
     *   <li>Construye el objeto SOAP request a partir del DTO</li>
     *   <li>Configura endpoint y timeouts en el BindingProvider</li>
     *   <li>Invoca submitDocument() en un scheduler elástico (no bloquea el thread reactivo)</li>
     *   <li>Parsea la respuesta SOAP a ExternalDocumentResponse</li>
     *   <li>Reintenta hasta 3 veces en errores transitorios (timeouts, 5xx)</li>
     * </ol>
     *
     * @param request Datos del documento a enviar
     * @return Mono con la respuesta del servicio externo
     */
    @Override
    public Mono<ExternalDocumentResponse> send(ExternalDocumentRequest request) {
        return Mono.fromCallable(() -> {
            log.info("[Adapter-SOAP] Sending document {} to external service",
                    request.getDocumentId());

            DocumentSubmissionRequest soapRequest = buildSoapRequest(request);
            configureEndpoint();

            DocumentSubmissionResponse soapResponse = documentServicePort.submitDocument(soapRequest);

            ExternalDocumentResponse response = parseSoapResponse(soapResponse);
            log.info("[Adapter-SOAP] Response received: success={}, externalId={}",
                    response.isSuccess(),
                    response.getExternalDocumentId());

            return response;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .retryWhen(createRetrySpec())
        .doOnError(error -> log.error("[Adapter-SOAP] SOAP call failed after {} retries: {}",
                MAX_RETRIES, error.getMessage()));
    }

    /**
     * Verifica la conectividad con el servicio SOAP externo.
     *
     * <p>Intenta configurar el endpoint y retorna true si no hay excepciones.
     * En producción podría invocar una operación de "ping" o "status" del servicio.</p>
     *
     * @return Mono con true si está disponible, false si hay error de conectividad
     */
    @Override
    public Mono<Boolean> healthCheck() {
        return Mono.fromCallable(() -> {
            configureEndpoint();
            // Intenta acceder al contexto del binding para verificar configuración
            if (documentServicePort instanceof BindingProvider) {
                BindingProvider bp = (BindingProvider) documentServicePort;
                return bp.getRequestContext() != null;
            }
            return true;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .onErrorResume(error -> {
            log.warn("[Adapter-SOAP] Health check failed: {}", error.getMessage());
            return Mono.just(false);
        });
    }

    /**
     * Configura el endpoint y timeouts del cliente SOAP.
     *
     * <p>El BindingProvider permite modificar propiedades del request context
     * como el endpoint URL, timeouts de conexión y recepción.</p>
     */
    private void configureEndpoint() {
        if (documentServicePort instanceof BindingProvider) {
            BindingProvider bindingProvider = (BindingProvider) documentServicePort;
            bindingProvider.getRequestContext().put(
                    BindingProvider.ENDPOINT_ADDRESS_PROPERTY, serviceEndpoint);
            bindingProvider.getRequestContext().put(
                    "javax.xml.ws.client.connectionTimeout", 30000);
            bindingProvider.getRequestContext().put(
                    "javax.xml.ws.client.receiveTimeout", 60000);
        }
    }

    /**
     * Crea la especificación de reintentos con backoff exponencial.
     *
     * <p>Reintenta en los siguientes errores transitorios:</p>
     * <ul>
     *   <li>{@link SocketTimeoutException} - Timeout de red</li>
     *   <li>{@link WebServiceException} - Errores de comunicación SOAP</li>
     *   <li>RuntimeException con mensaje de timeout</li>
     * </ul>
     *
     * @return Retry specification para usar con retryWhen()
     */
    private Retry createRetrySpec() {
        return Retry.backoff(MAX_RETRIES, INITIAL_BACKOFF)
                .maxBackoff(Duration.ofSeconds(10))
                .jitter(0.5)
                .filter(this::isRetryableError)
                .doBeforeRetry(retrySignal ->
                    log.warn("[Adapter-SOAP] Retry attempt {}/{} after error: {}",
                            retrySignal.totalRetries() + 1,
                            MAX_RETRIES,
                            retrySignal.failure().getMessage()));
    }

    /**
     * Determina si un error es recuperable y merece reintento.
     *
     * @param throwable El error ocurrido
     * @return true si se debe reintentar la operación
     */
    private boolean isRetryableError(Throwable throwable) {
        // Reintentar en timeouts de red
        if (throwable instanceof SocketTimeoutException) {
            return true;
        }

        // Reintentar en errores de comunicación SOAP
        if (throwable instanceof WebServiceException) {
            return true;
        }

        // Reintentar en RuntimeException que contengan timeout o connection
        if (throwable instanceof RuntimeException) {
            String message = throwable.getMessage();
            if (message != null) {
                String lowerMsg = message.toLowerCase();
                return lowerMsg.contains("timeout") ||
                       lowerMsg.contains("connection") ||
                       lowerMsg.contains("unavailable") ||
                       lowerMsg.contains("refused");
            }
        }

        return false;
    }

    /**
     * Construye el objeto SOAP request a partir del DTO de aplicación.
     *
     * <p>Mapea todos los campos del ExternalDocumentRequest a las clases
     * generadas por CXF (DocumentSubmissionRequest, RequestHeader, etc.)</p>
     *
     * @param request DTO con los datos del documento
     * @return DocumentSubmissionRequest listo para enviar al servicio SOAP
     */
    private DocumentSubmissionRequest buildSoapRequest(ExternalDocumentRequest request) {
        DocumentSubmissionRequest soapRequest = new DocumentSubmissionRequest();

        // Header con metadatos de la transacción
        RequestHeader header = new RequestHeader();
        header.setTransactionId(request.getDocumentId());
        header.setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        header.setSourceSystem("MS_UPLOAD_DOCUMENTS");
        header.setOperationType("DOCUMENT_SUBMISSION");
        soapRequest.setHeader(header);

        // Datos del documento
        DocumentData documentData = new DocumentData();
        documentData.setDocumentId(request.getDocumentId());
        documentData.setFileName(request.getFileName());
        documentData.setDocumentType(request.getDocumentTypeCode());
        documentData.setFileSize(request.getFileSize());
        documentData.setContentType(request.getContentType());
        documentData.setContent(request.getBase64Content());
        documentData.setChecksum(request.getChecksum());
        documentData.setUploadTimestamp(request.getUploadTimestamp());
        documentData.setUploadedBy(request.getUploadedBy());
        soapRequest.setDocumentData(documentData);

        // Metadatos adicionales como lista de items
        if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
            MetadataList metadataList = new MetadataList();
            for (Map.Entry<String, String> entry : request.getMetadata().entrySet()) {
                MetadataItem item = new MetadataItem();
                item.setKey(entry.getKey());
                item.setValue(entry.getValue());
                metadataList.getMetadataItem().add(item);
            }
            soapRequest.setMetadata(metadataList);
        }

        return soapRequest;
    }

    /**
     * Parsea la respuesta SOAP a un DTO de aplicación.
     *
     * <p>Extrae datos de ResponseHeader (success, errores) y
     * ConfirmationData (externalDocumentId, status).</p>
     *
     * @param soapResponse Respuesta del servicio SOAP
     * @return ExternalDocumentResponse DTO mapeado
     */
    private ExternalDocumentResponse parseSoapResponse(DocumentSubmissionResponse soapResponse) {
        ExternalDocumentResponse.ExternalDocumentResponseBuilder builder =
                ExternalDocumentResponse.builder();

        // Extraer estado de éxito y errores del header
        if (soapResponse.getResponseHeader() != null) {
            ResponseHeader header = soapResponse.getResponseHeader();
            builder.success(header.isSuccess());

            if (header.getErrors() != null && !header.getErrors().getError().isEmpty()) {
                ErrorType error = header.getErrors().getError().get(0);
                builder.errorCode(error.getErrorCode());
                builder.errorMessage(error.getErrorMessage());
            }
        }

        // Extraer datos de confirmación
        if (soapResponse.getConfirmationData() != null) {
            ConfirmationData confirmation = soapResponse.getConfirmationData();
            builder.externalDocumentId(confirmation.getExternalDocumentId());
            builder.confirmationCode(confirmation.getConfirmationCode());
            builder.confirmationMessage(confirmation.getConfirmationMessage());
            builder.processingStatus(confirmation.getProcessingStatus());
        }

        return builder.build();
    }
}
