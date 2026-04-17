# Escenarios de Prueba - CVE Resolver Agent v1.5.0

Este documento describe los escenarios de prueba ejecutados para validar el comportamiento del agente.

## Resumen de Pruebas

| ID | Escenario | Estado | Descripción |
|----|-----------|--------|-------------|
| 1 | Variable Nueva (Happy Path) | ✅ PASSED | Agregar nueva variable correctamente |
| 2 | Actualización Existente | ✅ PASSED | Actualizar variable existente |
| 3 | Versión Ya Actualizada | ✅ PASSED | Omitir cuando la versión ya está correcta |
| 4 | Consolidación Múltiples CVEs | ✅ PASSED | Ordenar por severidad y consolidar |
| 5 | Grupo No Mapeado | ✅ PASSED | Ignorar CVEs de grupos no mapeados |
| 6 | Modo Simulación (Dry Run) | ✅ PASSED | No modificar archivos en dry-run |
| 7 | Sin Validación | ✅ PASSED | Saltar compilación con --no-validate |
| 8 | Formato de Variables | ✅ PASSED | Variables sin comentarios adicionales |
| 9 | Formato de Cierre | ✅ PASSED | Llaves de cierre correctamente formateadas |
| 10 | Reporte JSON Completo | ✅ PASSED | Estructura correcta del reporte |
| 11 | Rollback Automático | ✅ PASSED | Restaurar archivos cuando falla compilación |
| 12 | Gradle No Encontrado | ✅ PASSED | Manejar cuando no hay Gradle |
| 13 | Archivo CVE por Defecto | ✅ PASSED | Usar snyk_cves_for_agent.json por defecto |
| 14 | Validación de CVE ID | ✅ PASSED | Omitir CVEs sin ID |
| 15 | Validación de Versión | ✅ PASSED | Advertir sobre versiones SNAPSHOT/RC |
| 16 | Severidad Case-Insensitive | ✅ PASSED | Normalizar severidad a mayúsculas |
| 17 | Apache Commons Específico | ✅ PASSED | Verificar artifact name para Apache Commons |
| 18 | Caracteres Especiales | ✅ PASSED | Escapar caracteres especiales en because |
| 19 | Auto-Detección Java | ✅ PASSED | Encontrar Java en macOS automáticamente |
| 20 | Versión Semántica Incompleta | ✅ PASSED | Aceptar versiones sin patch (ej: "6.2") |
| **21** | **Monorepo Auto-detección** | **✅ PASSED** | **Detectar ms_* en carpeta raíz** |
| **22** | **Monorepo Lista Específica** | **✅ PASSED** | **Procesar microservicios con --ms** |
| **23** | **Monorepo Reporte Consolidado** | **✅ PASSED** | **Generar reporte consolidado** |
| **24** | **Monorepo Error Parcial** | **✅ PASSED** | **Continuar si un MS falla** |
| **25** | **Monorepo Backups Separados** | **✅ PASSED** | **Backups independientes por MS** |

---

## Escenarios Básicos (v1.0.0 - v1.1.0)

### Escenario 1: Variable Nueva (Happy Path)

**Objetivo**: Verificar que el agente agrega una nueva variable correctamente

**Pre-condición**:
- `build.gradle`: No tiene `nettyVersion`
- `dependencyMgmt.gradle`: No tiene bloque useVersion para `io.netty`

**Archivo CVE**:
```json
{
  "cves": [{
    "cve_id": "CVE-2026-33871",
    "library_name": "netty-codec-http2",
    "group": "io.netty",
    "fixed_version": "4.1.132.Final",
    "severity": "CRITICAL"
  }]
}
```

**Comando**: `python3 cve_resolver_agent.py /ruta/proyecto --apply`

**Resultado Esperado**:
```
   ✓ nettyVersion: ADDED → 4.1.132.Final
   💾 Backup: build.gradle.YYYYMMDD_HHMMSS.backup
   ✓ Added useVersion for io.netty
   🔨 Compilando proyecto para validar cambios...
   ✅ Compilación exitosa
```

**Estado**: ✅ PASSED

---

### Escenario 2: Actualización de Variable Existente

**Objetivo**: Actualizar una variable existente

**Pre-condición**:
- `build.gradle`: Tiene `nettyVersion = '4.1.100.Final'`

**Comando**: `python3 cve_resolver_agent.py /ruta/proyecto --apply`

**Resultado Esperado**:
```
   ✓ nettyVersion: 4.1.100.Final → 4.1.132.Final
```

**Estado**: ✅ PASSED

---

### Escenario 3: Versión Ya Actualizada (Skip)

**Objetivo**: Omitir cuando la versión ya está correcta

**Pre-condición**:
- `build.gradle`: Tiene `nettyVersion = '4.1.132.Final'`

**Resultado Esperado**:
```
   ⏭️  nettyVersion: Ya está en versión 4.1.132.Final
```

**Estado**: ✅ PASSED

---

### Escenario 4: Consolidación Múltiples CVEs

**Objetivo**: Consolidar múltiples CVEs del mismo grupo

**Archivo CVE**:
```json
{
  "cves": [
    {"cve_id": "CVE-2024-A", "group": "io.netty", "severity": "CRITICAL", ...},
    {"cve_id": "CVE-2024-B", "group": "io.netty", "severity": "HIGH", ...},
    {"cve_id": "CVE-2024-C", "group": "io.netty", "severity": "LOW", ...}
  ]
}
```

**Resultado Esperado**:
- CVEs ordenados: CRITICAL > HIGH > LOW
- Una sola variable `nettyVersion`
- Reporta CVE-2024-A como el prioritario

**Estado**: ✅ PASSED

---

### Escenario 5: Grupo No Mapeado

**Objetivo**: Ignorar CVEs de grupos no mapeados

**Archivo CVE**:
```json
{
  "cves": [{
    "cve_id": "CVE-2024-XXXX",
    "group": "com.unknown.group",
    "severity": "CRITICAL"
  }]
}
```

**Resultado Esperado**:
- Sin errores
- 0 actualizaciones
- Grupo ignorado silenciosamente

**Estado**: ✅ PASSED

---

### Escenario 6: Modo Simulación (Dry Run)

**Objetivo**: No modificar archivos en modo dry-run

**Comando**: `python3 cve_resolver_agent.py /ruta/proyecto` (sin --apply)

**Resultado Esperado**:
- Consola muestra cambios propuestos
- Archivos NO modificados
- Backups NO creados
- Reporte JSON: `dry_run: true`

**Estado**: ✅ PASSED

---

### Escenario 7: Sin Validación (--no-validate)

**Objetivo**: Saltar compilación

**Comando**: `python3 cve_resolver_agent.py /ruta/proyecto --apply --no-validate`

**Resultado Esperado**:
- Cambios aplicados
- NO aparece "Compilando proyecto..."
- Reporte JSON: `compilation_success: null`

**Estado**: ✅ PASSED

---

### Escenario 8: Formato de Variables

**Objetivo**: Variables sin comentarios adicionales

**Resultado Esperado**:
```gradle
nettyVersion = '4.1.132.Final'
```

**NO debe aparecer**:
```gradle
// CVE fix: CVE-2026-33871
nettyVersion = '4.1.132.Final'
```

**Estado**: ✅ PASSED

---

### Escenario 9: Formato de Cierre

**Objetivo**: Llaves de cierre correctamente formateadas

**Resultado Esperado**:
```gradle
        nettyVersion = '4.1.132.Final'
    }
}
```

**NO debe aparecer**:
```gradle
        nettyVersion = '4.1.132.Final'}
    }
```

**Estado**: ✅ PASSED

---

### Escenario 10: Reporte JSON Completo

**Objetivo**: Estructura correcta del reporte

**Resultado Esperado**:
```json
{
  "total_cves": 3,
  "updates": [{
    "file": "build.gradle",
    "action": "ADDED|UPDATED",
    "variable": "nettyVersion",
    "cve": "CVE-2026-33871",
    "new_version": "4.1.132.Final"
  }],
  "compilation_success": true,
  "rollback_performed": false,
  "dry_run": false
}
```

**Estado**: ✅ PASSED

---

### Escenario 11: Rollback Automático

**Objetivo**: Restaurar archivos cuando falla compilación

**Archivo CVE**:
```json
{
  "cves": [{
    "cve_id": "CVE-ROLLBACK",
    "group": "io.netty",
    "fixed_version": "9.9.999.Final"
  }]
}
```

**Resultado Esperado**:
```
   ✓ nettyVersion: ADDED → 9.9.999.Final
   💾 Backup creado
   🔨 Compilando...
   ❌ Fallo de compilación: Could not find io.netty:9.9.999.Final

⚠️  La compilación falló. Iniciando rollback automático...
   ↩️  Rollback exitoso. Archivos restaurados: build.gradle
```

**Reporte JSON**:
```json
{
  "compilation_success": false,
  "rollback_performed": true
}
```

**Estado**: ✅ PASSED

---

### Escenario 12: Gradle No Encontrado

**Objetivo**: Manejar cuando no hay Gradle

**Pre-condición**: Sin `gradlew`, Gradle no en PATH

**Resultado Esperado**:
```
   ❌ Gradle no encontrado. Instala Gradle o usa el wrapper (gradlew)
```

**Estado**: ✅ PASSED

---

### Escenario 13: Archivo CVE por Defecto

**Objetivo**: Usar `snyk_cves_for_agent.json` por defecto

**Comando**: `python3 cve_resolver_agent.py /ruta/proyecto`

**Pre-condición**: Archivo `snyk_cves_for_agent.json` existe junto al script

**Resultado Esperado**: Carga CVEs correctamente sin errores

**Estado**: ✅ PASSED

---

## Escenarios v1.2.0 (Nuevos)

### Escenario 14: Validación de CVE ID

**Objetivo**: Omitir CVEs sin ID

**Archivo CVE**:
```json
{
  "cves": [{
    "cve_id": "",
    "group": "io.netty",
    "fixed_version": "1.0.0"
  }]
}
```

**Resultado Esperado**:
```
   ⚠️  1 CVEs omitidos por errores:
      - Sin CVE ID: test-lib
   0 CVEs únicos cargados
```

**Estado**: ✅ PASSED

---

### Escenario 15: Validación de Versión SNAPSHOT

**Objetivo**: Advertir sobre versiones SNAPSHOT/RC/Milestone

**Archivo CVE**:
```json
{
  "cves": [
    {"cve_id": "CVE-SNAP", "fixed_version": "2.0.0-SNAPSHOT"},
    {"cve_id": "CVE-RC", "fixed_version": "2.0.0-RC1"},
    {"cve_id": "CVE-M", "fixed_version": "2.0.0-M1"}
  ]
}
```

**Resultado Esperado**:
```
   ⚠️  Advertencia en CVE-SNAP: SNAPSHOT versions are not allowed in production
   ⚠️  Advertencia en CVE-RC: Release Candidate versions should be reviewed manually
```

**Estado**: ✅ PASSED

---

### Escenario 16: Severidad Case-Insensitive

**Objetivo**: Normalizar severidad a mayúsculas

**Archivo CVE**:
```json
{
  "cves": [{
    "cve_id": "CVE-TEST",
    "severity": "critical",
    ...
  }]
}
```

**Resultado Esperado**:
```
   [CRITICAL] CVE-TEST: ...
```

**Estado**: ✅ PASSED

---

### Escenario 17: Apache Commons Específico

**Objetivo**: Verificar artifact name para Apache Commons

**Archivo CVE**:
```json
{
  "cves": [{
    "cve_id": "CVE-2024-26308",
    "library_name": "commons-compress",
    "group": "org.apache.commons",
    ...
  }]
}
```

**Resultado Esperado**:
```gradle
if (details.requested.group == 'org.apache.commons' && details.requested.name == 'commons-compress') {
    details.useVersion "${commonsCompressVersion}"
    details.because "Fix: CVE-2024-26308"
}
```

**Estado**: ✅ PASSED

---

### Escenario 18: Caracteres Especiales

**Objetivo**: Escapar caracteres especiales en because

**Archivo CVE**:
```json
{
  "cves": [{
    "cve_id": "CVE-2024-X",
    "description": "<script>alert('xss')</script> & \"quotes\""
  }]
}
```

**Resultado Esperado**:
- CVE ID escapado en el because
- No hay errores de sintaxis Gradle

**Estado**: ✅ PASSED

---

### Escenario 19: Auto-Detección Java

**Objetivo**: Encontrar Java en macOS automáticamente

**Pre-condición**: Java instalado en `/usr/local/opt/openjdk@21`

**Comando**: Ejecutar sin `JAVA_HOME` definido

**Resultado Esperado**:
```
   📝 Usando Java: /usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
```

**Estado**: ✅ PASSED

---

### Escenario 20: Versión Semántica Incompleta

**Objetivo**: Aceptar versiones sin patch

**Archivo CVE**:
```json
{
  "cves": [{
    "cve_id": "CVE-TEST",
    "fixed_version": "6.2"
  }]
}
```

**Resultado Esperado**:
```
   ✓ springFrameworkVersion: 6.1.5 → 6.2
```

**Estado**: ✅ PASSED

---

## Escenarios v1.4.0 (Soporte Monorepo)

### Escenario 21: Monorepo Auto-detección

**Objetivo**: Detectar automáticamente carpetas `ms_*` en una carpeta raíz

**Pre-condición**:
```
/ruta/proyectos/
├── ms_auth/
│   └── build.gradle
├── ms_upload/
│   └── build.gradle
├── ms_core/
│   └── build.gradle
└── docs/              # Ignorado
```

**Comando**: `python3 cve_resolver_agent.py --folder /ruta/proyectos`

**Resultado Esperado**:
```
🔍 Auto-detectados 3 microservicio(s) en /ruta/proyectos:
   - ms_auth
   - ms_core
   - ms_upload
────────────────────────────────────────────────────────────
📦 Microservicio 1/3: ms_auth
📁 Ruta: /ruta/proyectos/ms_auth
────────────────────────────────────────────────────────────
...
```

**Estado**: ✅ PASSED (test_cve_resolver_agent.py)

---

### Escenario 22: Monorepo Lista Específica (--ms)

**Objetivo**: Procesar microservicios específicos de una lista

**Pre-condición**: Misma estructura que escenario 21

**Comando**: `python3 cve_resolver_agent.py --ms ms_auth,ms_core --folder /ruta/proyectos --apply`

**Resultado Esperado**:
```
🚀 CVE Resolver Agent v1.4.0 - Modo Monorepo
🔧 Procesando 2 microservicio(s)
────────────────────────────────────────────────────────────
📦 Microservicio 1/2: ms_auth
...
────────────────────────────────────────────────────────────
📦 Microservicio 2/2: ms_core
...
```

**Verificación**:
- Solo `ms_auth` y `ms_core` procesados
- `ms_upload` NO procesado

**Estado**: ✅ PASSED (test_cve_resolver_agent.py)

---

### Escenario 23: Monorepo Reporte Consolidado

**Objetivo**: Generar reporte consolidado con todos los microservicios

**Comando**: `python3 cve_resolver_agent.py --folder /ruta/proyectos --apply`

**Resultado Esperado**:

**Reporte Consolidado** (`cve_resolver_consolidated_report.json`):
```json
{
  "timestamp": "2026-04-17T10:30:00",
  "mode": "monorepo",
  "microservices_processed": 3,
  "microservice_names": ["ms_auth", "ms_core", "ms_upload"],
  "summary": {
    "total_cves": 25,
    "total_updates": 42,
    "successful_compilations": 3,
    "failed_compilations": 0,
    "rollbacks": 0
  },
  "results": {
    "ms_auth": {"total_cves": 8, "updates": [...], ...},
    "ms_core": {"total_cves": 5, "updates": [...], ...},
    "ms_upload": {"total_cves": 12, "updates": [...], ...}
  }
}
```

**Reportes Individuales**:
- `/ruta/proyectos/ms_auth/cve_resolver_report.json`
- `/ruta/proyectos/ms_core/cve_resolver_report.json`
- `/ruta/proyectos/ms_upload/cve_resolver_report.json`

**Estado**: ✅ PASSED (test_cve_resolver_agent.py)

---

### Escenario 24: Monorepo Error Parcial

**Objetivo**: Continuar procesando cuando un microservicio falla

**Pre-condición**:
```
/ruta/proyectos/
├── ms_auth/
│   └── build.gradle
├── ms_incomplete/      # No tiene build.gradle
│   └── src/
└── ms_core/
    └── build.gradle
```

**Comando**: `python3 cve_resolver_agent.py --folder /ruta/proyectos --apply`

**Resultado Esperado**:
```
🔍 Auto-detectados 2 microservicio(s) en /ruta/proyectos:
   - ms_auth
   - ms_core
...
⚠️  Saltando: No es un proyecto Gradle (falta build.gradle)
...
📊 RESUMEN CONSOLIDADO
✅ Microservicios procesados: 2
```

**Verificación**:
- `ms_incomplete` omitido sin detener ejecución
- `ms_auth` y `ms_core` procesados exitosamente
- Código de salida: 0 (éxito)

**Estado**: ✅ PASSED (test_cve_resolver_agent.py)

---

### Escenario 25: Monorepo Backups Separados

**Objetivo**: Crear backups independientes para cada microservicio

**Comando**: `python3 cve_resolver_agent.py --ms ms_auth,ms_upload --folder /ruta/proyectos --apply`

**Verificación**:
```
/ruta/proyectos/ms_auth/.cve_resolver_backups/
    ├── build.gradle.20260417_120530.backup
    └── dependencyMgmt.gradle.20260417_120530.backup

/ruta/proyectos/ms_upload/.cve_resolver_backups/
    ├── build.gradle.20260417_120531.backup
    └── dependencyMgmt.gradle.20260417_120531.backup
```

**Estado**: ✅ PASSED (test_cve_resolver_agent.py)

---

### Escenario 25b: Monorepo Completo (5 Microservicios)

**Objetivo**: Procesar todos los microservicios del monorepo supplier_documents_backend

**Pre-condición**:
```
supplier_documents_backend/
├── ms_upload_documents/    # Existente
├── ms_auth/                # Nuevo
├── ms_core/                # Nuevo
├── ms_notifications/     # Nuevo
└── ms_reports/             # Nuevo
```

**Comando**: `python3 cve_resolver_agent.py --folder ../supplier_documents_backend --apply`

**Resultado Esperado**:
```
🔍 Auto-detectados 5 microservicio(s) en supplier_documents_backend:
   - ms_auth
   - ms_core
   - ms_notifications
   - ms_reports
   - ms_upload_documents

# Procesa cada microservicio secuencialmente
```

**Estado**: ✅ PASSED (manual con monorepo completo)

---

## Escenarios v1.5.0 (Integración Git)

### Escenario 26: Detección de Repositorio Git

**Objetivo**: Detectar si el directorio es un repositorio Git

**Pre-condición**:
- Directorio con repositorio git inicializado
- Directorio sin repositorio git

**Comando**: Ejecutar `GitCommitManager.is_git_repository()`

**Resultado Esperado**:
- Directorio con `.git/`: `True`
- Directorio sin `.git/`: `False`

**Estado**: ✅ PASSED (test_cve_resolver_agent.py)

---

### Escenario 27: Generación de Nombre de Rama

**Objetivo**: Generar nombre de rama con formato correcto

**Comando**: `python3 cve_resolver_agent.py --folder /proyectos --apply --commit`

**Usuario Git**: `Juan Perez`

**Resultado Esperado**:
```
🌿 Creando rama: feature/fix_vulnerabilidad_17042026_juan.perez
```

**Verificación**:
- Formato: `feature/fix_vulnerabilidad_DDMMYYYY_username`
- Fecha: 8 dígitos
- Username: minúsculas, espacios reemplazados por puntos

**Estado**: ✅ PASSED (test_cve_resolver_agent.py)

---

### Escenario 28: Creación de Commit Exitoso

**Objetivo**: Crear commit automático con todos los cambios

**Pre-condición**:
```
/proyectos/
├── .git/                    # Repo inicializado
├── ms_auth/
│   ├── build.gradle
│   └── ...
└── ms_upload/
    ├── build.gradle
    └── ...
```

**Comando**: `python3 cve_resolver_agent.py --folder /proyectos --apply --commit`

**Resultado Esperado**:
```
📝 GIT COMMIT AUTOMÁTICO
🌿 Creando rama: feature/fix_vulnerabilidad_17042026_juan.perez
   ✅ Rama creada y activada: feature/fix_vulnerabilidad_17042026_juan.perez
📦 Agregando cambios al staging area...
   ✅ Todos los cambios agregados
💬 Creando commit...
   ✅ Commit creado: a1b2c3d
🎉 Commit creado exitosamente
```

**Verificación**:
```bash
git log -1 --oneline
# a1b2c3d security: fix vulnerabilities in ms_auth

git branch
# * feature/fix_vulnerabilidad_17042026_juan.perez
```

**Estado**: ✅ PASSED (test_cve_resolver_agent.py)

---

### Escenario 29: Mensaje de Commit Descriptivo

**Objetivo**: Generar mensaje de commit con información completa

**Archivo CVE**:
```json
{
  "cves": [
    {"cve_id": "CVE-2024-26308", "library_name": "commons-compress", "group": "org.apache.commons", ...},
    {"cve_id": "CVE-2024-1234", "library_name": "netty-codec-http", "group": "io.netty", ...}
  ]
}
```

**Comando**: `python3 cve_resolver_agent.py /proyecto --apply --commit`

**Mensaje Esperado**:
```
security: fix vulnerabilities in ms_auth

Fixed 2 CVE(s) affecting 2 dependency groups

Resolved vulnerabilities:
  - CVE-2024-26308: org.apache.commons:commons-compress
    Severity: CRITICAL
    Updated: 1.26.0 → 1.27.0
  - CVE-2024-1234: io.netty:netty-codec-http
    Severity: HIGH
    Updated: 4.1.86.Final → 4.1.132.Final

Modified files:
  - build.gradle
  - dependencyMgmt.gradle

Generated by CVE Resolver Agent v1.5.0
```

**Estado**: ✅ PASSED (test_cve_resolver_agent.py)

---

### Escenario 30: Commit sin Cambios

**Objetivo**: Manejar caso donde no hay cambios para commitear

**Pre-condición**:
- Todas las dependencias ya están actualizadas
- No hay CVEs para procesar

**Comando**: `python3 cve_resolver_agent.py /proyecto --apply --commit`

**Resultado Esperado**:
```
ℹ️  No hay cambios para commitear.
```

**Estado**: ✅ PASSED (test_cve_resolver_agent.py)

---

### Escenario 31: Commit sin Git Repository

**Objetivo**: Manejar caso donde no es un repositorio Git

**Pre-condición**:
```
/proyectos/
├── ms_auth/              # No es repo git
│   └── build.gradle
└── ms_upload/
    └── build.gradle
```

**Comando**: `python3 cve_resolver_agent.py --folder /proyectos --apply --commit`

**Resultado Esperado**:
```
⚠️  /proyectos no es un repositorio git. No se creará commit.
```

El agente continúa procesando CVEs pero no intenta crear commit.

**Estado**: ✅ PASSED (manual)

---

### Escenario 32: Commit sin --apply

**Objetivo**: Advertir que --commit requiere --apply

**Comando**: `python3 cve_resolver_agent.py /proyecto --commit` (sin --apply)

**Resultado Esperado**:
```
⚠️  --commit requiere --apply. Ignorando commit.
```

**Estado**: ✅ PASSED (test_cve_resolver_agent.py - implícito)

---

## Escenarios v1.5.2 (Procesamiento Paralelo con 5 Microservicios)

### Escenario 33: Procesamiento Paralelo Básico

**Objetivo**: Procesar CVEs en paralelo para todos los microservicios del monorepo

**Pre-condición**:
```
supplier_documents_backend/
├── ms_auth/
├── ms_core/
├── ms_notifications/
├── ms_reports/
└── ms_upload_documents/
```

**Comando**: `python3 cve_resolver_agent.py --folder ../supplier_documents_backend --apply --parallel`

**Resultado Esperado**:
```
🚀 CVE Resolver Agent v1.5.2 - Modo Paralelo
🔧 5 microservicios x 3 CVEs
⚡ Max workers: 5

🔒 CVE 1/3: CVE-2024-XXXX [CRITICAL]
   org.xxx:library-name
   1.0.0 → 2.0.0
   ✅ ms_auth: 2 actualizaciones
   ✅ ms_core: 2 actualizaciones
   ✅ ms_notifications: 2 actualizaciones
   ✅ ms_reports: 2 actualizaciones
   ✅ ms_upload: 2 actualizaciones

🔒 CVE 2/3: CVE-2024-YYYY [HIGH]
   ...

# Tiempo total significativamente menor que modo secuencial
```

**Speedup Esperado**: ~5x más rápido que modo secuencial

**Estado**: ✅ PASSED (manual con monorepo completo)

---

### Escenario 34: Paralelo con Configuración de Workers

**Objetivo**: Ajustar número de workers para optimizar rendimiento

**Comando**:
```bash
# 10 workers (más paralelismo, más recursos)
python3 cve_resolver_agent.py --folder ../supplier_documents_backend --apply --parallel --max-workers 10

# 2 workers (menos paralelismo, menos recursos)
python3 cve_resolver_agent.py --folder ../supplier_documents_backend --apply --parallel --max-workers 2
```

**Resultado Esperado**:
```
⚡ Max workers: 10
# o
⚡ Max workers: 2
```

**Estado**: ✅ PASSED (manual)

---

### Escenario 35: Paralelo con Rollback

**Objetivo**: Verificar que el rollback funciona correctamente en modo paralelo

**Pre-condición**: Uno de los microservicios tiene una versión inexistente que causará fallo de compilación

**Comando**: `python3 cve_resolver_agent.py --folder ../supplier_documents_backend --apply --parallel`

**Resultado Esperado**:
```
🔒 CVE X/3: CVE-TEST [CRITICAL]
   org.test:library: 1.0.0 → 9.9.999
   ✅ ms_auth: 1 actualizaciones
   ❌ ms_core: Compilación fallida, rollback ejecutado
   ✅ ms_notifications: 1 actualizaciones
   ✅ ms_reports: 1 actualizaciones
   ✅ ms_upload: 1 actualizaciones
```

**Verificación**: Los microservicios que fallaron deben tener sus archivos restaurados

**Estado**: ✅ PASSED (manual)

---

### Escenario 36: Comparativa Secuencial vs Paralelo

**Objetivo**: Comparar tiempos de ejecución entre modo secuencial y paralelo

**Comando Secuencial**:
```bash
time python3 cve_resolver_agent.py --folder ../supplier_documents_backend --apply
# Tiempo: ~5 minutos (aproximado)
```

**Comando Paralelo**:
```bash
time python3 cve_resolver_agent.py --folder ../supplier_documents_backend --apply --parallel --max-workers 5
# Tiempo: ~1 minuto (aproximado)
```

**Resultado Esperado**:
```
Modo Secuencial:
⏱️  Tiempo de ejecución: 5m 23s

Modo Paralelo:
⏱️  Tiempo de ejecución: 1m 8s

Speedup: ~5x más rápido
```

**Estado**: ✅ PASSED (manual con monorepo de 5 MS)

---

## Checklist de Validación Final

### Funcionalidad Básica
- [x] Escenario 1: Variable nueva se agrega correctamente
- [x] Escenario 2: Variable existente se actualiza
- [x] Escenario 3: Versión correcta se omite
- [x] Escenario 4: Múltiples CVEs se consolidan
- [x] Escenario 5: Grupos no mapeados se ignoran

### Modos de Ejecución
- [x] Escenario 6: Modo simulación no modifica archivos
- [x] Escenario 7: --no-validate salta compilación
- [x] Escenario 13: Archivo CVE por defecto funciona
- [x] Escenario 21: Auto-detección ms_* funciona
- [x] Escenario 22: --ms con lista específica funciona

### Formato y Salida
- [x] Escenario 8: Variables sin comentarios
- [x] Escenario 9: Formato de cierre correcto
- [x] Escenario 10: Reporte JSON completo
- [x] Escenario 23: Reporte consolidado generado correctamente

### Validación y Errores
- [x] Escenario 11: Compilación fallida con rollback automático
- [x] Escenario 12: Gradle no encontrado manejado correctamente
- [x] Escenario 24: Error parcial no detiene procesamiento

### Validación de CVEs (v1.2.0)
- [x] Escenario 14: CVEs sin ID se omiten
- [x] Escenario 15: Versiones SNAPSHOT/RC advierten
- [x] Escenario 16: Severidad normalizada
- [x] Escenario 17: Apache Commons verifica artifact
- [x] Escenario 18: Caracteres especiales escapados
- [x] Escenario 19: Auto-detección Java funciona
- [x] Escenario 20: Versiones sin patch aceptadas

### Backups
- [x] Backups se crean antes de modificar
- [x] Backups tienen formato correcto
- [x] Backups se guardan en .cve_resolver_backups/
- [x] Escenario 25: Backups separados por microservicio

### Compilación
- [x] Compilación automática con --apply
- [x] Timeout de 5 minutos
- [x] Mensaje claro de éxito/fallo
- [x] Rollback automático en fallo

### Integración Git (v1.5.0)
- [x] Escenario 26: Detección de repositorio Git
- [x] Escenario 27: Generación de nombre de rama
- [x] Escenario 28: Creación de commit exitoso
- [x] Escenario 29: Mensaje de commit descriptivo
- [x] Escenario 30: Sin cambios para commitear
- [x] Escenario 31: No es repositorio Git
- [x] Escenario 32: --commit sin --apply

### Tests Unitarios
- [x] TestDiscoverMicroservices: 7 tests
- [x] TestParseMicroservices: 7 tests
- [x] TestSnykCVEProcessor: 5 tests
- [x] TestBackupManager: 3 tests
- [x] TestGradleCVEUpdater: 3 tests
- [x] TestIntegration: 4 tests
- [x] TestGitCommitManager: 6 tests
- [x] Total: 35 tests passed

### Procesamiento Paralelo (v1.5.2) - Monorepo 5 MS
- [x] Escenario 33: Procesamiento paralelo básico
- [x] Escenario 34: Configuración de workers (--max-workers)
- [x] Escenario 35: Paralelo con rollback
- [x] Escenario 36: Comparativa secuencial vs paralelo (5x speedup)

---

## Comandos de Ejecución

### Ejecutar todas las pruebas
```bash
cd /ruta/al/cve_resolver_agent

# Probar con diferentes escenarios
python3 cve_resolver_agent.py /ruta/proyecto --apply
python3 cve_resolver_agent.py /ruta/proyecto --apply --no-validate

# Verificar resultados
ls .cve_resolver_backups/
cat cve_resolver_report.json
grep "nettyVersion" build.gradle
```

---

## Notas de Implementación

### Versiones Estables
- **Agente**: v1.5.2
- **Python**: 3.8+
- **Gradle**: 7.x+ (o wrapper)
- **Java**: 17+ (para validación)
- **Git**: Cualquier versión con soporte para `git checkout -b`
- **Monorepo de prueba**: 5 microservicios (ms_auth, ms_core, ms_notifications, ms_reports, ms_upload_documents)

### Cambios v1.5.2
- Procesamiento paralelo: Nuevo argumento `--parallel` para procesar CVEs simultáneamente en múltiples microservicios
- Optimización para monorepo: Cada CVE se aplica a todos los microservicios simultáneamente (5x speedup con 5 MS)
- Configurable: `--max-workers` para controlar el número de hilos paralelos
- 5 microservicios de prueba añadidos al supplier_documents_backend para testing multihilo

### Cambios v1.5.1
- Tiempo de ejecución: Muestra tiempo total al final del log (segundos, minutos u horas)

### Cambios v1.5.0
- Integración Git: Nuevo argumento `--commit` para crear commits automáticos
- Rama automática: Crea rama `feature/fix_vulnerabilidad_{ddmmyyyy}_{username}`
- Mensaje descriptivo: Commit incluye microservicios modificados, CVEs resueltos, severidad y archivos cambiados
- Detección de usuario: Detecta automáticamente el nombre de usuario de git
- Validación de repo: Verifica que sea un repositorio git antes de intentar commitear
- 35 tests unitarios (6 nuevos para integración Git)

### Cambios v1.4.0
- Soporte para monorepo (múltiples microservicios)
- Nuevo argumento `--ms` para especificar carpetas de microservicios
- Nuevo argumento `--folder` para especificar carpeta raíz
- Auto-detección de carpetas `ms_*` cuando se usa `--folder` sin `--ms`
- Reporte consolidado con estadísticas agregadas
- Reportes individuales por microservicio
- Backups independientes para cada microservicio
- 29 tests unitarios cubriendo todas las funcionalidades nuevas

### Cambios v1.2.0
- Agregada validación de CVEs (cve_id no vacío, versión válida)
- Normalización de severidad a mayúsculas
- Advertencias para versiones SNAPSHOT/Milestone/RC
- Escapado de caracteres especiales en because
- Auto-detección de Java en macOS
- Fix: Regex para detectar bloque ext en build.gradle
- Fix: Manejo especial de org.apache.commons con verificación de artifact

### Cambios v1.1.0
- Rollback automático si la compilación falla
- Reporte JSON incluye rollback_performed

### Cambios v1.0.0
- Agregada validación por compilación automática (--apply)
- Flag --no-validate para saltar compilación
- Reporte JSON incluye compilation_success

---

## Archivos de Prueba

### test_scenarios.json
Ubicación: `cve_resolver_agent/test_scenarios.json`

Contiene 10 escenarios de prueba predefinidos para validación automática.

### test_rollback.json
Ubicación: `cve_resolver_agent/test_rollback.json`

CVE con versión inexistente (9.9.999.Final) para probar rollback.
