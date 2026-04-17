package com.supplier.documents.upload.infrastructure.rest;

import com.supplier.documents.upload.application.dto.DocumentUploadRequest;
import com.supplier.documents.upload.application.dto.DocumentUploadResponse;
import com.supplier.documents.upload.domain.port.in.UploadDocumentInputPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * RouteRest - Adaptador REST de entrada.
 * Expone los endpoints HTTP y delega al UseCase.
 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Document Upload", description = "API for uploading documents to external SOAP service")
public class DocumentRouteRest {

    private final UploadDocumentInputPort uploadDocumentInputPort;

    /**
     * POST /api/v1/documents/upload
     * Sube un documento y lo envía al servicio externo.
     */
    @PostMapping(
            value = "/upload",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Upload document",
            description = "Uploads a document and sends it to the external SOAP service for processing."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Document uploaded successfully",
                    content = @Content(schema = @Schema(implementation = DocumentUploadResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request - validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "413",
                    description = "File too large",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "415",
                    description = "Unsupported media type",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public Mono<DocumentUploadResponse> uploadDocument(
            @Valid @RequestBody DocumentUploadRequest request) {

        log.info("[RouteRest] Received upload request for file: {}, type: {}",
                request.getFileName(),
                request.getDocumentTypeCode());

        return uploadDocumentInputPort.uploadDocument(request)
                .doOnSuccess(response ->
                        log.info("[RouteRest] Upload successful: documentId={}, status={}",
                                response.getDocumentId(),
                                response.getStatus()))
                .onErrorMap(throwable -> {
                    log.error("[RouteRest] Upload failed: {}", throwable.getMessage());
                    return new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "Document upload failed: " + throwable.getMessage(),
                            throwable);
                });
    }

    /**
     * GET /api/v1/documents/health
     * Endpoint de health check.
     */
    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Check if the service is up and running")
    @ApiResponse(responseCode = "200", description = "Service is healthy")
    public Mono<HealthResponse> health() {
        log.debug("[RouteRest] Health check requested");
        return Mono.just(new HealthResponse("UP", "ms-upload-documents", "1.0.0"));
    }

    // Record para respuesta de health check
    public record HealthResponse(String status, String service, String version) {}

    // Record para respuesta de error
    public record ErrorResponse(String code, String message, String timestamp) {}
}