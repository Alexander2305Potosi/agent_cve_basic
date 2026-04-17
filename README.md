# Proyecto Monorepo

Este es un monorepo que contiene el CVE Resolver Agent y el backend de documentos de proveedores.

## Estructura del Proyecto

```
.
├── .gitignore                      # Configuración de archivos ignorados
├── README.md                       # Este archivo
├── cve_resolver_agent/             # Agente CVE para resolver vulnerabilidades
│   ├── cve_resolver_agent.py      # Agente principal (v1.5.1)
│   ├── test_cve_resolver_agent.py # Tests unitarios
│   ├── README.md                   # Documentación del agente
│   ├── RULES.md                   # Reglas de negocio
│   ├── TEST_SCENARIOS.md          # Escenarios de prueba
│   └── snyk_cves_for_agent.json    # Ejemplo de entrada CVE
└── supplier_documents_backend/     # Monorepo de microservicios
    └── ms_upload_documents/        # Microservicio de upload
```

## CVE Resolver Agent

Herramienta Python para resolver CVEs en proyectos Spring WebFlux + Gradle.

**Ubicación:** `cve_resolver_agent/`

**Características principales:**
- Soporte monorepo: Procesa múltiples microservicios
- Integración Git: Crea commits automáticos con `--commit`
- Auto-detección de Java
- Rollback automático si la compilación falla

**Uso básico:**
```bash
cd cve_resolver_agent/

# Simular cambios
python3 cve_resolver_agent.py /ruta/al/proyecto

# Aplicar y validar
python3 cve_resolver_agent.py /ruta/al/proyecto --apply

# Monorepo con commit automático
python3 cve_resolver_agent.py --folder /ruta/proyectos --apply --commit
```

**Ver documentación completa en:** `cve_resolver_agent/README.md`

## Supplier Documents Backend

Monorepo de microservicios para el backend de documentos de proveedores.

**Ubicación:** `supplier_documents_backend/`

### Microservicios Disponibles

| Microservicio | Puerto | Responsabilidad |
|--------------|--------|-----------------|
| `ms_upload_documents/` | 8080 | Gestión de documentos y upload de archivos |
| `ms_auth/` | 8081 | Autenticación JWT, login y autorización |
| `ms_core/` | 8082 | Lógica de negocio principal y datos |
| `ms_notifications/` | 8083 | Envío de notificaciones (email, SMS, push) |
| `ms_reports/` | 8084 | Generación de reportes (Excel, PDF) |

### Arquitectura

Todos los microservicios comparten la misma arquitectura base:
- **Spring WebFlux** + **Gradle** con Java 17
- **Spring Security** (para ms_auth)
- **R2DBC** para acceso a datos (ms_core)
- **Spring Mail** y **Thymeleaf** para notificaciones (ms_notifications)
- **Apache POI** e **iText** para reportes (ms_reports)
- Cobertura de tests con **JaCoCo** (mínimo 80%)

### Compilación

Cada microservicio puede compilarse independientemente:
```bash
cd supplier_documents_backend/ms_auth/
./gradlew compileJava --no-daemon
```

### Pruebas de Carga Multihilo

Para probar el procesamiento paralelo del CVE Resolver Agent con múltiples microservicios:
```bash
cd cve_resolver_agent/
python3 cve_resolver_agent.py --folder ../supplier_documents_backend --apply --parallel
```

## Notas para Desarrolladores

### Archivos a Subir

**Sí subir:**
- Código fuente del agente (`cve_resolver_agent/`)
- Código fuente de los microservicios (`supplier_documents_backend/`)
- Documentación (README, RULES, TEST_SCENARIOS)
- Ejemplo de entrada CVE (`snyk_cves_for_agent.json`)

**NO subir (están en .gitignore):**
- Backups (`.cve_resolver_backups/`, `*.backup`)
- Reportes generados (`cve_resolver_report.json`)
- Cache de Python (`__pycache__/`, `*.pyc`)
- Archivos de compilación (`build/`, `.gradle/`, `target/`)
- Configuración local de IDE (`.idea/`, `.vscode/`)
- Archivos de entorno (`.env`, `application-local.properties`)

### Tests

Para ejecutar los tests del agente:
```bash
cd cve_resolver_agent/
python3 test_cve_resolver_agent.py
```

## Versión Actual

**CVE Resolver Agent:** v1.5.1
- Soporte monorepo
- Integración Git
- Tiempo de ejeción

## Licencia

Uso interno - Herramienta de seguridad defensiva
