# Reglas del CVE Resolver Agent v1.0.0

## 1. Arquitectura de Archivos

### 1.1 build.gradle
- **Propósito**: Declarar variables de versión únicamente
- **Ubicación**: Raíz del microservicio
- **Bloque**: `buildscript { ext { ... } }`
- **Contenido**: Solo variables de versión, sin lógica de resolución
- **Formato**: `variableVersion = 'x.y.z'`

### 1.2 dependencyMgmt.gradle
- **Propósito**: Forzar versiones mediante `resolutionStrategy`
- **Ubicación**: Raíz del microservicio (importado por build.gradle)
- **Bloque**: `configurations.all { resolutionStrategy.eachDependency { ... } }`
- **Contenido**: Solo bloques `useVersion`, sin declaración de variables
- **Nota**: Algunos grupos requieren verificación adicional de artifact

## 2. Validación de CVEs

### 2.1 Campos Obligatorios
| Campo | Validación | Acción si inválido |
|-------|------------|-------------------|
| `cve_id` | No vacío, formato CVE-XXXX-NNNNN | Omitir con advertencia |
| `fixed_version` | No vacío | Omitir con advertencia |
| `group` | Presente en VERSION_MAP | Ignorar silenciosamente |
| `library_name` | Requerido para Apache Commons | Usar valor proporcionado |

### 2.2 Versiones Problemáticas
| Patrón | Mensaje | Acción |
|--------|---------|--------|
| `.*-SNAPSHOT$` | SNAPSHOT versions are not allowed in production | Advertir, procesar |
| `.*-M\d+$` | Milestone versions should be reviewed manually | Advertir, procesar |
| `.*-RC\d+$` | Release Candidate versions should be reviewed manually | Advertir, procesar |
| `^\s*$` | Empty version is not valid | Omitir |

### 2.3 Severidad
- Entrada: Cualquier case (critical, Critical, CRITICAL)
- Normalización: Convertir a MAYÚSCULAS
- Orden: CRITICAL > HIGH > MEDIUM > LOW > UNKNOWN

### 2.4 Caracteres Especiales
- Los caracteres especiales en `cve_id` y `description` deben escaparse para Gradle
- Escapado: `"` → `\"`, `'` → `\'`, `\` → `\\`
- Esto previene errores de sintaxis en el bloque `because`

## 3. Consolidación de CVEs

### 3.1 Agrupación por Grupo
- Todos los CVEs del mismo `group` se consolidan en UNA sola variable
- Ejemplo: `io.netty:netty-codec-http`, `io.netty:netty-codec-http2`, `io.netty:netty-handler` → `nettyVersion`

### 3.2 Prioridad por Severidad
- Cuando hay múltiples CVEs para el mismo grupo, se usa la versión fija del CVE con mayor severidad
- Orden: CRITICAL > HIGH > MEDIUM > LOW > UNKNOWN
- El CVE más crítico se reporta en el campo `cve` del reporte

### 3.3 Mapeo de Grupos a Variables

| Grupo | Variable | Descripción | Manejo Especial |
|-------|----------|-------------|-----------------|
| io.netty | nettyVersion | Todas las librerías Netty | Bloque useVersion por grupo |
| org.springframework | springFrameworkVersion | Framework Spring | Bloque useVersion por grupo |
| com.fasterxml.jackson.core | jacksonVersion | Librerías Jackson | Bloque useVersion por grupo |
| org.apache.commons | commonsCompressVersion | Apache Commons | ✅ Verifica `requested.name` |
| org.apache.logging.log4j | log4jVersion | Apache Log4j | Bloque useVersion por grupo |

### 3.4 Manejo Especial: Apache Commons

**Problema**: El grupo `org.apache.commons` contiene librerías independientes con versiones diferentes (ej: `commons-lang3` vs `commons-compress`).

**Solución**: Generar bloque useVersion con verificación específica:

```gradle
if (details.requested.group == 'org.apache.commons' && details.requested.name == 'commons-compress') {
    details.useVersion "${commonsCompressVersion}"
    details.because "Fix: CVE-2024-26308"
}
```

**Lógica**: Solo aplicar cuando coincidan tanto el grupo como el nombre del artifact.

## 4. Formato de Variables

### 4.1 Declaración en build.gradle
```gradle
nettyVersion = '4.1.132.Final'
```
- Sin comentarios adicionales
- Sin prefijos de CVE
- Formato limpio
- Versión entre comillas simples

### 4.2 Uso en dependencyMgmt.gradle
```gradle
details.useVersion "${nettyVersion}"
details.because "Fix: CVE-2026-33871"
```
- Usar referencia de variable Gradle: `${variable}`
- Incluir el CVE más crítico en el `because`
- Escapar caracteres especiales en CVE ID

## 5. Comportamiento del Agente

### 5.1 Modo Simulación (Default)
- No modifica archivos
- Muestra qué cambios haría
- Genera reporte JSON
- No ejecuta compilación
- No crea backups

### 5.2 Modo Aplicación (--apply)
- Crea backup antes de modificar cada archivo
- Actualiza archivos
- Ejecuta compilación para validar (a menos que se use --no-validate)
- Si compilación falla: **Rollback automático**
- Genera reporte JSON

### 5.3 Modo Aplicación Sin Validación (--apply --no-validate)
- Aplica cambios
- Crea backups
- No compila (más rápido)
- Útil cuando se confía en los cambios o se validará después

### 5.4 Detección de Variables Existentes
| Estado | Acción | Mensaje |
|--------|--------|---------|
| Variable existe, versión diferente | **ACTUALIZAR** | `✓ variable: old → new` |
| Variable existe, misma versión | **OMITIR** | `⏭️ variable: Ya está en versión` |
| Variable no existe | **AGREGAR** | `✓ variable: ADDED → version` |

### 5.5 Detección de useVersion Existente
- Si el bloque `useVersion` existe para el grupo: **OMITIR**
- Si no existe: **AGREGAR**
- Verificación: Buscar `details.requested.group == 'grupo'`

## 6. Auto-Detección de Java

### 6.1 Ubicaciones Buscadas (macOS)
1. `$JAVA_HOME` (si está definido)
2. `/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`
3. `/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`
4. `/usr/local/opt/openjdk/libexec/openjdk.jdk/Contents/Home`
5. `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`
6. `/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`
7. `/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home`
8. `/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home`
9. `/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home`
10. `/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home`

### 6.2 Fallback
Si no se encuentra Java, se intenta ejecutar Gradle con el Java del sistema (puede fallar).

## 7. Validación por Compilación

### 7.1 Comando de Compilación
```bash
./gradlew compileJava --no-daemon -q
```
O si no existe gradlew:
```bash
gradle compileJava --no-daemon -q
```

### 7.2 Timeout
- La compilación tiene un timeout de 5 minutos
- Si excede el tiempo, se reporta como fallo

### 7.3 Rollback Automático
- Si la compilación falla, el agente **ejecuta rollback automático**
- El rollback restaura los archivos desde los backups creados en la sesión
- Los backups de la sesión se mantienen (no se eliminan)
- El reporte JSON incluye `rollback_performed: true`

### 7.4 Manejo de Fallos (Rollback Fallido)
- Si el rollback falla, se muestra error crítico
- Los backups permanecen en `.cve_resolver_backups/`
- El usuario debe restaurar manualmente
- El agente termina con código de error 1

### 7.5 Desactivación de Validación
- Usar `--no-validate` para saltar la compilación
- Útil en CI/CD donde la compilación se hace en otro paso
- Más rápido pero sin validación de cambios

## 8. Backups

### 8.1 Ubicación
`.cve_resolver_backups/` en la raíz del proyecto

### 8.2 Nomenclatura
`{nombre_archivo}.{YYYYMMDD_HHMMSS}.backup`

Ejemplos:
- `build.gradle.20260417_120530.backup`
- `dependencyMgmt.gradle.20260417_120530.backup`

### 8.3 Política
- Se crea backup ANTES de cualquier modificación
- Un backup por archivo por ejecución del agente
- No se eliminan automáticamente
- Responsabilidad del usuario gestionarlos

### 8.4 Restauración Manual
```bash
cp .cve_resolver_backups/build.gradle.20260417_120530.backup build.gradle
cp .cve_resolver_backups/dependencyMgmt.gradle.20260417_120530.backup dependencyMgmt.gradle
```

## 9. Salida del Agente

### 9.1 Reporte JSON
Archivo: `cve_resolver_report.json` en el proyecto

```json
{
  "total_cves": 10,
  "updates": [
    {
      "file": "build.gradle",
      "action": "UPDATED|ADDED",
      "variable": "nettyVersion",
      "cve": "CVE-2026-33871",
      "new_version": "4.1.132.Final"
    }
  ],
  "compilation_success": true|false|null,
  "rollback_performed": true|false,
  "dry_run": false
}
```

### 9.2 Campos del Reporte
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `total_cves` | int | Número total de CVEs procesados |
| `updates` | array | Lista de actualizaciones realizadas |
| `updates[].file` | string | Archivo modificado |
| `updates[].action` | string | `UPDATED` o `ADDED` |
| `updates[].variable` | string | Nombre de la variable |
| `updates[].cve` | string | ID del CVE prioritario |
| `updates[].new_version` | string | Nueva versión aplicada |
| `compilation_success` | bool|null | Resultado de compilación (null en dry-run) |
| `rollback_performed` | bool | Indica si se ejecutó rollback |
| `dry_run` | bool | Indica si fue modo simulación |

### 9.3 Salida en Consola
- Lista de CVEs cargados por severidad
- Advertencias de validación (CVEs omitidos, versiones problemáticas)
- Acciones realizadas (ADDED/UPDATED/SKIPPED)
- Backups creados
- Ubicación de Java detectado
- Resultado de compilación (si aplica)
- Resumen final

## 10. Casos de Error

### 10.1 Archivo CVE No Encontrado
- Mensaje: "Archivo CVE no encontrado"
- Acción: Termina ejecución con código 1

### 10.2 Proyecto No Encontrado
- Mensaje: "Proyecto no encontrado"
- Acción: Termina ejecución con código 1

### 10.3 Formato JSON Inválido
- Python lanza excepción JSONDecodeError
- Acción: Termina ejecución con traceback

### 10.4 dependencyMgmt.gradle No Existe
- Comportamiento: Solo actualiza build.gradle
- No genera error

### 10.5 Gradle No Encontrado
- Mensaje: "Gradle no encontrado"
- Acción: Solo en modo validación; los cambios aplicados quedan (sin rollback automático)

### 10.6 Compilación Fallida + Rollback Exitoso
- Mensaje: "Compilación fallida - Rollback ejecutado"
- Acción: Archivos restaurados, ejecución termina normalmente

### 10.7 Compilación Fallida + Rollback Fallido
- Mensaje: "Rollback fallido: {detalle}"
- Acción: Termina con código de error 1

### 10.8 CVEs Omitidos por Validación
- Mensaje: "X CVEs omitidos por errores"
- Acción: Continúa con CVEs válidos
- Detalle: Lista de omisiones con razón

## 11. Dependencias

### 11.1 Requisitos del Sistema
- Python 3.8+
- Gradle (o wrapper gradlew) en el proyecto
- Java 17+ (para validación por compilación)

### 11.2 Requisitos de Python
- Sin librerías externas (solo stdlib)
- Usa: `json`, `re`, `os`, `sys`, `argparse`, `shutil`, `subprocess`, `pathlib`, `typing`, `dataclasses`, `datetime`

### 11.3 Archivos Requeridos
- `cve_resolver_agent.py`
- Archivo CVE JSON (por defecto: `snyk_cves_for_agent.json`)

## 12. Limitaciones

### 12.1 Alcance
- Solo proyectos Gradle con estructura build.gradle + dependencyMgmt.gradle
- No soporta Maven
- No soporta Gradle Kotlin DSL (.gradle.kts)
- Solo grupos definidos en VERSION_MAP

### 12.2 Variables
- Solo gestiona variables definidas en VERSION_MAP
- CVEs de grupos no mapeados son ignorados silenciosamente

### 12.3 Resolución
- No descarga versiones (usa repositorio local de Gradle)
- No verifica disponibilidad en Maven Central antes de aplicar
- Depende de compilación para validar que versiones existen

### 12.4 Validación
- Compila solo `compileJava`, no ejecuta tests
- Timeout de 5 minutos para compilación
- Rollback automático solo si compilación falla

## 13. Flujo de Estados

```
Inicio
  │
  ▼
Cargar CVEs ── Error ──▶ Terminar
  │
  ▼
Validar CVEs ── Error ──▶ Advertir, continuar
  │
  ▼
Actualizar Archivos
  │
  ├─ dry-run? ── Sí ──▶ Reportar ──▶ Fin
  │
  ▼
Compilar? ── No ──▶ Reportar ──▶ Fin
  │
  ▼
Compilar ── Error ──▶ Rollback ──▶ Reportar ──▶ Fin
  │
  ▼
Reportar éxito ──▶ Fin
```

## 14. Soporte Monorepo (v1.0.0)

### 14.1 Modos de Ejecución

| Modo | Argumentos | Descripción |
|------|------------|-------------|
| Single | `project_path` | Procesa un único microservicio (legacy) |
| Multi-MS | `--ms list` | Procesa microservicios específicos |
| Auto | `--folder path` | Auto-detecta `ms_*` en la carpeta |
| Mixed | `--ms list --folder path` | Busca los ms en la carpeta especificada |

### 14.2 Argumentos

#### `--ms` (Microservicios)
- **Formato**: Lista separada por comas
- **Ejemplo**: `--ms ms_auth,ms_upload,ms_core`
- **Comportamiento**: Busca las carpetas especificadas
- **Sin `--folder`**: Busca en directorio actual
- **Con `--folder`**: Busca en la carpeta especificada

#### `--folder` (Carpeta Raíz)
- **Formato**: Ruta a carpeta
- **Ejemplo**: `--folder /proyectos/microservicios`
- **Con `--ms`**: Busca los microservicios en esa carpeta
- **Sin `--ms`**: Auto-detecta carpetas `ms_*`

### 14.3 Convención de Nombres Monorepo

#### Prefijo de Microservicios
- **Patrón**: Carpeta debe comenzar con `ms_`
- **Ejemplo**: `ms_auth`, `ms_upload_documents`, `ms_core_v2`
- **Validación**: Debe contener `build.gradle` para ser reconocido

#### Estructura Esperada
```
proyectos/
├── ms_auth/
│   ├── build.gradle              # Requerido para detección
│   ├── dependencyMgmt.gradle
│   └── src/
├── ms_upload/
│   ├── build.gradle              # Requerido para detección
│   └── ...
└── otro_proyecto/                # Ignorado (no tiene prefijo ms_)
```

### 14.4 Auto-detección

#### Lógica de Detección
```
Para cada carpeta en la raíz de --folder:
  Si nombre comienza con "ms_":
    Si contiene "build.gradle":
      Agregar a lista de microservicios
    Sino:
      Ignorar (no es proyecto Gradle válido)
  Sino:
    Ignorar (no tiene prefijo de microservicio)
Ordenar lista alfabéticamente
Retornar lista ordenada
```

#### Mensajes de Consola
- **Detectados**: `"🔍 Auto-detectados N microservicio(s) en {folder}"`
- **Lista**: `"   - {nombre_microservicio}"` para cada uno
- **No encontrados**: `"⚠️ No se encontraron microservicios (carpetas ms_*) en {folder}"`

### 14.5 Procesamiento de Múltiples Microservicios

#### Orden de Procesamiento
1. Procesa microservicios en orden alfabético
2. Cada microservicio es independiente
3. Fallo en uno NO afecta a los demás
4. Reporte individual por microservicio

#### Reportes Generados
| Archivo | Ubicación | Descripción |
|---------|-----------|-------------|
| `cve_resolver_report.json` | Cada MS | Reporte individual del microservicio |
| `cve_resolver_consolidated_report.json` | Carpeta raíz | Reporte agregado de todos los MS |
| `.cve_resolver_backups/` | Cada MS | Backups de archivos modificados |

### 14.6 Reporte Consolidado

#### Estructura JSON
```json
{
  "timestamp": "2026-04-17T10:30:00",
  "mode": "monorepo",
  "microservices_processed": 3,
  "microservice_names": ["ms_auth", "ms_upload", "ms_core"],
  "summary": {
    "total_cves": 25,
    "total_updates": 42,
    "successful_compilations": 3,
    "failed_compilations": 0,
    "rollbacks": 0
  },
  "results": {
    "ms_auth": { /* ... resultado individual ... */ },
    "ms_upload": { /* ... resultado individual ... */ },
    "ms_core": { /* ... resultado individual ... */ }
  }
}
```

#### Campos
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `timestamp` | ISO8601 | Fecha/hora de ejecución |
| `mode` | string | `monorepo` o `single` |
| `microservices_processed` | int | Número de MS procesados |
| `microservice_names` | array | Lista de nombres de MS |
| `summary.total_cves` | int | Suma de CVEs de todos los MS |
| `summary.total_updates` | int | Suma de actualizaciones |
| `summary.successful_compilations` | int | MS que compilaron OK |
| `summary.failed_compilations` | int | MS con fallo de compilación |
| `summary.rollbacks` | int | Número de rollbacks ejecutados |
| `results` | object | Resultado individual por MS |

### 14.7 Manejo de Errores en Monorepo

#### Microservicio No Encontrado
- **Mensaje**: `⚠️ Microservicio no encontrado: {ruta}`
- **Acción**: Se omite y continúa con los demás
- **Reporte**: `{"error": "Proyecto no encontrado"}` en results

#### No Es Proyecto Gradle
- **Mensaje**: `⚠️ Saltando: No es un proyecto Gradle (falta build.gradle)`
- **Acción**: Se omite y continúa con los demás
- **Reporte**: `{"error": "No es proyecto Gradle"}` en results

#### Error Durante Procesamiento
- **Mensaje**: `❌ Error procesando {nombre}: {mensaje}`
- **Acción**: Se captura excepción, continúa con los demás
- **Reporte**: `{"error": "{mensaje}"}` en results

#### Código de Salida
- **Éxito total**: Código 0
- **Al menos un fallo**: Código 1
- **Mensaje final**: `"❌ Algunos microservicios tuvieron errores. Revisa el reporte consolidado."`

### 14.8 Flujo de Estados (Monorepo)

```
Inicio
  │
  ▼
Parsear Argumentos
  │
  ├─ --ms? ──▶ Usar lista especificada
  │
  ├─ --folder? ──▶ Auto-detectar ms_*
  │
  └─ Ninguno ──▶ Modo legacy (project_path)
  │
  ▼
Para Cada Microservicio:
  │
  ├─ Existe? ── No ──▶ Reportar error ──┐
  │                                        │
  ├─ Es Gradle? ── No ──▶ Reportar error │
  │                                        │
  ▼                                        │
Procesar CVEs ── Error ──▶ Reportar error │
  │                                        │
  ├─ dry-run? ── Sí ──▶ ─────────────────┤
  │                                        │
  ▼                                        │
Compilar? ── No ──▶ ─────────────────────┤
  │                                        │
  ▼                                        │
Compilar ── Error ──▶ Rollback ────────────┤
  │                                        │
  ▼                                        │
Reportar éxito ───────────────────────────┘
  │
  ▼
Generar Reporte Consolidado
  │
  ▼
Fin
```

## 15. Integración Git (v1.0.0)

### 15.1 Argumento `--commit`

- **Requiere**: `--apply` (no funciona en modo dry-run)
- **Requiere**: Repositorio Git válido en el path base
- **Opcional**: `user.name` configurado en Git

### 15.2 Formato de Nombre de Rama

```
feature/fix_vulnerabilidad_{DDMMYYYY}_{username}
```

**Componentes:**
- `feature/`: Prefijo estándar para features
- `fix_vulnerabilidad_`: Identificador de propósito
- `{DDMMYYYY}`: Fecha actual (ej: 17042026)
- `{username}`: Usuario git (limpiado: minúsculas, sin espacios)

**Ejemplos:**
- `feature/fix_vulnerabilidad_17042026_juan.perez`
- `feature/fix_vulnerabilidad_17042026_maria.garcia`

### 15.3 Detección de Usuario

**Orden de prioridad:**
1. `git config user.name` (limpiado)
2. `getpass.getuser()` (usuario del sistema)
3. `"unknown"` (fallback)

**Limpieza de nombre:**
- Convertir a minúsculas
- Reemplazar espacios con puntos
- Eliminar caracteres especiales (solo `[a-z0-9._-]`)

### 15.4 Mensaje de Commit

**Estructura:**
```
{tipo}: {título}

{resumen}

Resolved vulnerabilities:
  - {CVE_ID}: {group}:{library}
    Severity: {SEVERITY}
    Updated: {old_version} → {new_version}

Modified files:
  - {file1}
  - {file2}

Generated by CVE Resolver Agent v1.0.0
```

**Ejemplo:**
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

Generated by CVE Resolver Agent v1.0.0
```

### 15.5 Flujo de Commit

```
--commit especificado?
  │
  ├─ No ──▶ Continuar sin commit
  │
  ▼
--apply especificado?
  │
  ├─ No ──▶ Advertir "--commit requiere --apply"
  │
  ▼
Es repositorio Git?
  │
  ├─ No ──▶ Advertir "No es repositorio git"
  │
  ▼
Hay cambios para commitear?
  │
  ├─ No ──▶ Info "No hay cambios"
  │
  ▼
Generar nombre de rama ──▶ Crear rama (checkout -b)
  │
  ▼
Agregar cambios al staging (git add .)
  │
  ▼
Generar mensaje de commit
  │
  ▼
Crear commit (git commit -m "...")
  │
  ▼
Mostrar resultado:
  - Nombre de rama
  - Hash del commit
  - Comando para push
```

### 15.6 Manejo de Errores

| Condición | Mensaje | Acción |
|-----------|---------|--------|
| `--commit` sin `--apply` | `⚠️ --commit requiere --apply. Ignorando commit.` | Continúa sin commit |
| No es repo Git | `⚠️ {path} no es un repositorio git. No se creará commit.` | Continúa sin commit |
| Sin cambios | `ℹ️ No hay cambios para commitear.` | Continúa sin commit |
| Error creando rama | `❌ {mensaje}` | Intenta continuar en rama actual |
| Error en git add | `❌ {mensaje}` | Detiene flujo de commit |
| Error en commit | `❌ Error creando commit: {mensaje}` | Detiene flujo de commit |

### 15.7 Salida en Consola

**Commit Exitoso:**
```
============================================================
📝 GIT COMMIT AUTOMÁTICO
============================================================

🌿 Creando rama: feature/fix_vulnerabilidad_17042026_juan.perez
   ✅ Rama creada y activada: feature/fix_vulnerabilidad_17042026_juan.perez

📦 Agregando cambios al staging area...
   ✅ Todos los cambios agregados

💬 Creando commit...
   ✅ Commit creado: a1b2c3d

📋 Mensaje de commit:
────────────────────────────────────────────────────────────
security: fix vulnerabilities in ms_auth

Fixed 8 CVE(s) affecting 5 dependency groups
...
────────────────────────────────────────────────────────────

🎉 Commit creado exitosamente en rama: feature/fix_vulnerabilidad_17042026_juan.perez
   Para subir los cambios:
   git push origin feature/fix_vulnerabilidad_17042026_juan.perez
```

## 16. Escenarios de Prueba

Ver `TEST_SCENARIOS.md` para casos de prueba detallados.

### Tests Unitarios

Ejecutar tests con:
```bash
# Sin pytest (solo Python stdlib)
python3 test_cve_resolver_agent.py

# Con pytest (reporte detallado)
python3 -m pytest test_cve_resolver_agent.py -v
```

Cobertura de tests:
- `TestDiscoverMicroservices`: Detección de carpetas `ms_*`
- `TestParseMicroservices`: Parsing de argumentos
- `TestSnykCVEProcessor`: Procesamiento de CVEs
- `TestBackupManager`: Gestión de backups
- `TestGradleCVEUpdater`: Actualización de archivos Gradle
- `TestIntegration`: Escenarios completos de monorepo
- `TestGitCommitManager`: Integración Git (6 tests)
