# CVE Resolver Agent v1.5.2

Agente Python portable para resolver CVEs en proyectos Spring WebFlux + Gradle mediante archivos de configuración de dependencias.

Soporta **monorepositorios** - procesa múltiples microservicios en una sola ejecución.

**Nuevo en v1.5.2**: **Procesamiento paralelo** - cada CVE se aplica simultáneamente a todos los microservicios para ganar tiempo en monorepos grandes.

**Nuevo en v1.5.0**: Integración Git para crear commits automáticos con mensajes descriptivos.

## Características

- Portable: Sin dependencias externas (solo Python 3.8+)
- Seguro: Solo lectura/escritura de archivos locales
- Backup automático: Crea backups antes de modificar archivos
- Rollback automático: Restaura archivos originales si la compilación falla
- Modo simulación: Previsualiza cambios antes de aplicarlos (dry-run)
- Validación automática: Compila el proyecto para verificar que los cambios funcionan
- Consolidación inteligente: Agrupa CVEs por grupo de librerías, una sola variable por grupo
- Validación de CVEs: Rechaza CVEs sin ID o con versiones inválidas
- Auto-detección de Java: Encuentra Java instalado en macOS automáticamente
- Escapado de caracteres: Maneja caracteres especiales en descripciones de CVE
- Soporte multi-SO: Compatible con macOS, Linux y Windows
- **Soporte Monorepo: Procesa múltiples microservicios con `--ms` o `--folder`**
- **Auto-detección: Detecta automáticamente carpetas `ms_*` en modo monorepo**
- **🆕🆕 Procesamiento Paralelo: Cada CVE se aplica simultáneamente a todos los MS (`--parallel`)**
- **🆕 Integración Git: Crea commits automáticos con `--commit`**
- **🆕 Ramas automáticas: Crea rama `feature/fix_vulnerabilidad_{fecha}_{usuario}`**
- **🆕 Mensajes descriptivos: Incluye CVEs resueltos y MS modificados en el commit**

## Arquitectura

El agente trabaja con dos archivos Gradle:

1. **build.gradle**: Contiene las variables de versión en el bloque `buildscript.ext`
2. **dependencyMgmt.gradle**: Contiene los bloques `useVersion` para forzar versiones seguras

### Ejemplo de flujo

```gradle
// build.gradle
buildscript {
    ext {
        nettyVersion = '4.1.132.Final'
        jacksonVersion = '2.17.2'
        springFrameworkVersion = '6.1.14'
    }
}

// dependencyMgmt.gradle
configurations.all {
    resolutionStrategy.eachDependency { details ->
        if (details.requested.group == 'io.netty') {
            details.useVersion "${nettyVersion}"
            details.because "Fix: CVE-2026-33871"
        }
    }
}
```

## Estructura del Proyecto

### Archivos del Agente

```
cve_resolver_agent/
├── cve_resolver_agent.py           # Agente principal (v1.5.2)
├── test_cve_resolver_agent.py      # Tests unitarios
├── snyk_cves_for_agent.json        # Ejemplo de entrada CVE (formato Snyk)
├── test_scenarios.json             # Escenarios de prueba para validación
├── README.md                       # Este archivo
├── RULES.md                        # Reglas de negocio del agente
└── TEST_SCENARIOS.md               # Escenarios de prueba manual
```

### Estructura de Monorepositorio Soportada

El agente soporta monorepos con múltiples microservicios. Para pruebas con procesamiento paralelo, se incluyen 5 microservicios de ejemplo en `supplier_documents_backend/`:

```
supplier_documents_backend/
├── ms_upload_documents/            # Microservicio: Gestión de documentos (Port 8080)
│   ├── build.gradle
│   ├── dependencyMgmt.gradle
│   ├── src/main/java/...
│   └── ...
├── ms_auth/                        # Microservicio: Autenticación JWT (Port 8081)
│   ├── build.gradle
│   ├── dependencyMgmt.gradle
│   ├── src/main/java/...
│   └── ...
├── ms_core/                        # Microservicio: Lógica de negocio (Port 8082)
│   ├── build.gradle
│   ├── dependencyMgmt.gradle
│   ├── src/main/java/...
│   └── ...
├── ms_notifications/               # Microservicio: Notificaciones (Port 8083)
│   ├── build.gradle
│   ├── dependencyMgmt.gradle
│   ├── src/main/java/...
│   └── ...
└── ms_reports/                     # Microservicio: Reportes (Port 8084)
    ├── build.gradle
    ├── dependencyMgmt.gradle
    ├── src/main/java/...
    └── ...
```

**Para probar procesamiento paralelo:**
```bash
# Procesar los 5 microservicios en paralelo
python3 cve_resolver_agent.py --folder ../supplier_documents_backend --apply --parallel
```

## Requisitos

- Python 3.8 o superior
- Gradle (o wrapper gradlew) en el proyecto
- Sin dependencias externas de Python (solo stdlib)

## Instalación

1. Clonar o copiar los archivos del agente
2. Asegurar que el proyecto tenga Gradle o gradlew
3. El agente está listo para usar

## Uso

El agente soporta **cuatro modos** de ejecución:

### Modo 1: Single Microservicio (Legacy)
Procesa un único proyecto Gradle.

```bash
# Simular cambios (recomendado primero)
python3 cve_resolver_agent.py /ruta/al/proyecto

# Aplicar cambios y validar con compilación
python3 cve_resolver_agent.py /ruta/al/proyecto --apply

# Aplicar cambios sin validar (más rápido)
python3 cve_resolver_agent.py /ruta/al/proyecto --apply --no-validate

# Usar un archivo CVE diferente
python3 cve_resolver_agent.py /ruta/al/proyecto /ruta/a/otro_cves.json --apply
```

### Modo 2: Múltiples Microservicios (`--ms`)
Especifica una lista de carpetas de microservicios (coma separados).

```bash
# Procesar microservicios específicos
python3 cve_resolver_agent.py --ms ms_auth,ms_upload,ms_core --apply

# Procesar microservicios en una carpeta específica
python3 cve_resolver_agent.py --ms ms_auth,ms_upload --folder /ruta/proyectos --apply
```

### Modo 3: Auto-detección Monorepo (`--folder`)
Cuando se usa `--folder` **sin** `--ms`, el agente auto-detecta todas las carpetas que comiencen con `ms_` en la raíz de la carpeta especificada.

```bash
# Auto-detectar y procesar todos los ms_* en la carpeta
python3 cve_resolver_agent.py --folder /ruta/proyectos --apply

# Estructura esperada:
# /ruta/proyectos/
#   ├── ms_auth/
#   │   └── build.gradle
#   ├── ms_upload/
#   │   └── build.gradle
#   ├── ms_core/
#   │   └── build.gradle
#   └── otro_proyecto/  (ignorado, no comienza con ms_)
```

### Modo 4: Con Commit Automático (`--commit`)
Cuando se usa `--commit` junto con `--apply`, el agente automáticamente crea una rama Git y commitea los cambios con un mensaje descriptivo.

```bash
# Single microservicio con commit
python3 cve_resolver_agent.py /ruta/proyecto --apply --commit

# Monorepo con commit
python3 cve_resolver_agent.py --folder /ruta/proyectos --apply --commit

# Rama creada: feature/fix_vulnerabilidad_17042026_juan.perez
# Mensaje incluye: microservicios modificados, CVEs resueltos, archivos cambiados
```

### Modo 5: Procesamiento Paralelo (`--parallel`)
**Optimizado para monorepos grandes.** Procesa cada CVE en paralelo para todos los microservicios, reduciendo drásticamente el tiempo de ejecución.

```bash
# Monorepo con procesamiento paralelo (5 workers por defecto)
python3 cve_resolver_agent.py --folder /ruta/proyectos --apply --parallel

# Ajustar número de workers (más workers = más paralelismo, más recursos)
python3 cve_resolver_agent.py --folder /ruta/proyectos --apply --parallel --max-workers 10

# Combinar con commit automático
python3 cve_resolver_agent.py --folder /ruta/proyectos --apply --parallel --commit
```

**Cómo funciona:**
- Cada CVE se aplica simultáneamente a todos los microservicios
- Si tienes 3 CVEs y 5 microservicios:
  - **Modo secuencial**: 15 iteraciones (1 CVE x 1 MS a la vez)
  - **Modo paralelo**: 3 iteraciones (1 CVE aplicado a 5 MS simultáneamente)
  - **Speedup**: ~5x más rápido (según número de workers)

**Recomendación:** Usar `--parallel` en monorepos con 3+ microservicios.

## Flujo de Ejecución

```
┌─────────────────┐
│  Cargar CVEs    │
│  desde JSON     │
└────────┬────────┘
         ▼
┌─────────────────┐
│ Validar CVEs    │
│ (ID, versión)   │
└────────┬────────┘
         ▼
┌─────────────────┐
│ Agrupar por     │
│ grupo/variable  │
└────────┬────────┘
         ▼
┌─────────────────┐
│ Actualizar      │
│ build.gradle    │
└────────┬────────┘
         ▼
┌─────────────────┐
│ Actualizar      │
│ dependencyMgmt  │
└────────┬────────┘
         ▼
┌─────────────────┐
│ Compilar        │
│ (validación)    │
└────────┬────────┘
         ▼
┌─────────────────┐
│ Generar reporte │
│ JSON            │
└─────────────────┘
```

## Validación de CVEs

El agente valida automáticamente los CVEs antes de procesarlos:

| Validación | Comportamiento |
|------------|----------------|
| CVE ID vacío | Omitido con advertencia |
| Versión vacía | Omitido con advertencia |
| Versión SNAPSHOT | Advertencia, pero procesado |
| Versión Milestone/RC | Advertencia, pero procesado |
| Severidad en minúsculas | Normalizada a mayúsculas |
| Caracteres especiales | Escapados en el because |

## Validación por Compilación

Cuando se usa `--apply`, el agente automáticamente:

1. Aplica los cambios a los archivos Gradle
2. Detecta automáticamente Java en ubicaciones comunes de macOS:
   - `/usr/local/opt/openjdk@21`
   - `/usr/local/opt/openjdk@17`
   - `/opt/homebrew/opt/openjdk@21`
   - Y más...
3. Ejecuta `./gradlew compileJava --no-daemon -q` (o `gradle` si no hay wrapper)
4. Si la compilación es exitosa: ✅ Cambios confirmados
5. Si la compilación falla: 🔄 **Rollback automático** a los archivos originales

El rollback automático garantiza que el proyecto siempre quede en un estado funcional si la compilación falla.

### Flujo con Rollback

```
Aplicar cambios
      ↓
Compilar
      ↓
┌─────────────────┐
│   ¿Éxito?       │
└────────┬────────┘
         │
    ┌────┴────┐
   SÍ          NO
    │           │
    ▼           ▼
Confirmar   Rollback
Cambios     Automático
    │           │
    └────┬──────┘
         ▼
    Reportar
    Resultado
```

## Formato de Entrada CVE (Formato Snyk)

### JSON Format

```json
{
  "cves": [
    {
      "cve_id": "CVE-2026-33871",
      "library_name": "netty-codec-http2",
      "group": "io.netty",
      "full_library": "io.netty:netty-codec-http2",
      "current_version": "4.1.86.Final",
      "fixed_version": "4.1.132.Final",
      "severity": "CRITICAL",
      "description": "Snyk reported vulnerability"
    }
  ]
}
```

### Campos Requeridos

- `cve_id`: Identificador del CVE (ej: CVE-2026-33871) - **Obligatorio**
- `library_name`: Nombre de la librería afectada
- `group`: Grupo de la librería (ej: io.netty)
- `current_version`: Versión vulnerable actual
- `fixed_version`: Versión que corrige el CVE - **Obligatorio**
- `severity` (opcional): CRITICAL, HIGH, MEDIUM, LOW, UNKNOWN
- `description` (opcional): Descripción del CVE

## Flujo de Trabajo

1. **Carga**: El agente carga los CVEs desde el archivo JSON
2. **Validación**: Verifica que CVEs tengan ID y versión válida
3. **Consolidación**: Agrupa CVEs por grupo de librerías (una variable por grupo)
4. **Actualización build.gradle**: Agrega o actualiza variables de versión
5. **Actualización dependencyMgmt.gradle**: Agrega bloques `useVersion` si no existen
6. **Validación**: Compila el proyecto para verificar los cambios
7. **Reporte**: Genera un reporte JSON con todos los cambios realizados

## Ejemplo Completo

### Single Microservicio

```bash
# 1. Simular cambios (sin aplicar)
python3 cve_resolver_agent.py /mi/proyecto/spring-app

# 2. Revisar reporte generado
cat /mi/proyecto/spring-app/cve_resolver_report.json

# 3. Aplicar cambios y validar
python3 cve_resolver_agent.py /mi/proyecto/spring-app --apply

# 4. Verificar backups creados
ls /mi/proyecto/spring-app/.cve_resolver_backups/
```

### Monorepo con Auto-detección

```bash
# 1. Simular cambios en todos los ms_* detectados
python3 cve_resolver_agent.py --folder /mi/monorepo/proyectos

# 2. Revisar reporte consolidado
cat /mi/monorepo/proyectos/cve_resolver_consolidated_report.json

# 3. Aplicar cambios a todos los microservicios detectados
python3 cve_resolver_agent.py --folder /mi/monorepo/proyectos --apply

# 4. Revisar reportes individuales
cat /mi/monorepo/proyectos/ms_auth/cve_resolver_report.json
cat /mi/monorepo/proyectos/ms_upload/cve_resolver_report.json
```

## Mapeo de Grupos a Variables

El agente utiliza un mapeo consolidado para crear una sola variable por grupo:

| Grupo | Variable | Manejo Especial |
|-------|----------|-----------------|
| io.netty | nettyVersion | Bloque useVersion por grupo |
| org.springframework | springFrameworkVersion | Bloque useVersion por grupo |
| com.fasterxml.jackson.core | jacksonVersion | Bloque useVersion por grupo |
| org.apache.commons | commonsCompressVersion | ✅ Verifica `details.requested.name` |
| org.apache.logging.log4j | log4jVersion | Bloque useVersion por grupo |

### Nota sobre Apache Commons

El grupo `org.apache.commons` contiene librerías independientes con versiones diferentes. El agente automáticamente genera:

```gradle
if (details.requested.group == 'org.apache.commons' && details.requested.name == 'commons-compress') {
    details.useVersion "${commonsCompressVersion}"
    details.because "Fix: CVE-2024-26308"
}
```

Esto evita aplicar la versión de `commons-compress` a otras librerías como `commons-lang3`.

## Salida

El agente genera:

### Modo Single Microservicio
1. **Reporte JSON**: `cve_resolver_report.json` en el directorio del proyecto
2. **Backups**: Archivos `.backup` en `.cve_resolver_backups/`
3. **Log en consola**: Resumen de cambios aplicados y resultado de compilación

### Modo Monorepo
1. **Reporte Consolidado**: `cve_resolver_consolidated_report.json` en la carpeta raíz
2. **Reportes Individuales**: `cve_resolver_report.json` en cada microservicio procesado
3. **Backups**: Archivos `.backup` en cada microservicio
4. **Resumen**: Estadísticas agregadas de todos los microservicios

### Ejemplo de Reporte (Single)

**Caso exitoso:**
```json
{
  "total_cves": 10,
  "updates": [
    {
      "file": "build.gradle",
      "action": "UPDATED",
      "variable": "jacksonVersion",
      "cve": "CVE-2024-22233",
      "new_version": "2.17.2"
    }
  ],
  "compilation_success": true,
  "rollback_performed": false,
  "dry_run": false
}
```

**Caso con rollback:**
```json
{
  "total_cves": 1,
  "updates": [...],
  "compilation_success": false,
  "rollback_performed": true,
  "dry_run": false
}
```

### Ejemplo de Reporte Consolidado (Monorepo)

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
    "ms_auth": {
      "total_cves": 8,
      "updates": [...],
      "compilation_success": true,
      "rollback_performed": false
    },
    "ms_upload": {
      "total_cves": 12,
      "updates": [...],
      "compilation_success": true,
      "rollback_performed": false
    },
    "ms_core": {
      "total_cves": 5,
      "updates": [...],
      "compilation_success": true,
      "rollback_performed": false
    }
  }
}
```

## Precauciones

- **Siempre ejecuta en modo simulación primero** para verificar cambios propuestos
- Revisa el reporte generado antes de aplicar cambios
- Los backups se guardan automáticamente en `.cve_resolver_backups/`
- Si la compilación falla, revisa manualmente los cambios aplicados

## Troubleshooting

### Error: "Gradle no encontrado"

Instala Gradle o asegúrate de que exista `gradlew` en el proyecto:
```bash
# Si no hay gradlew, genera el wrapper
cd /tu/proyecto
gradle wrapper
```

### Error: "Compilación fallida"

El agente automáticamente hace rollback si la compilación falla:

```
⚠️  La compilación falló. Iniciando rollback automático...

   ↩️  Rollback exitoso. Archivos restaurados: build.gradle, dependencyMgmt.gradle
   ✅ Archivos restaurados a su estado original
```

Si el rollback falla (caso extremo), los backups están disponibles para restauración manual:
```bash
cp .cve_resolver_backups/build.gradle.{timestamp}.backup build.gradle
cp .cve_resolver_backups/dependencyMgmt.gradle.{timestamp}.backup dependencyMgmt.gradle
```

### Versiones no actualizadas

Si una versión no se actualiza:
1. Verifica que el grupo de la librería esté en el mapeo de versiones (RULES.md)
2. Asegúrate de que el archivo CVE tenga el formato correcto
3. Revisa que la variable no exista ya con la misma versión

### Archivo dependencyMgmt.gradle no existe

El agente solo actualiza `dependencyMgmt.gradle` si existe. Si tu proyecto usa otro mecanismo para gestionar dependencias, el agente solo actualizará `build.gradle`.

### Java no encontrado

El agente busca Java en ubicaciones comunes de macOS. Si no lo encuentra:
1. Establece `JAVA_HOME` antes de ejecutar el agente
2. O instala Java vía Homebrew: `brew install openjdk@21`

## Integración Git (v1.5.0)

El agente puede crear automáticamente commits Git con los cambios aplicados.

### Uso Básico

```bash
# Aplicar cambios y crear commit
python3 cve_resolver_agent.py /ruta/proyecto --apply --commit

# Monorepo con commit
python3 cve_resolver_agent.py --folder /proyectos --apply --commit
```

### Requisitos

- El directorio debe ser un repositorio Git
- El usuario debe tener configurado `user.name` y `user.email` en git
- Requiere `--apply` para funcionar (no funciona en modo dry-run)

### Formato de Rama

```
feature/fix_vulnerabilidad_{DDMMYYYY}_{username}

Ejemplo: feature/fix_vulnerabilidad_17042026_juan.perez
```

### Mensaje de Commit

El mensaje incluye información detallada:

```
security: fix vulnerabilities in ms_auth

Fixed 8 CVE(s) affecting 5 dependency groups

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

### Flujo Completo

```bash
# 1. Simular cambios primero (siempre recomendado)
python3 cve_resolver_agent.py --folder /proyectos

# 2. Aplicar, validar y commitear
python3 cve_resolver_agent.py --folder /proyectos --apply --commit

# 3. Verificar la rama creada
git branch
# * feature/fix_vulnerabilidad_17042026_juan.perez

# 4. Subir cambios
git push origin feature/fix_vulnerabilidad_17042026_juan.perez
```

### Comportamiento sin Git

Si el directorio no es un repositorio Git, el agente mostrará:

```
⚠️  /ruta/proyecto no es un repositorio git. No se creará commit.
```

Y continuará con el procesamiento normal.

## Changelog

### v1.5.2
- **🆕🆕 Procesamiento Paralelo**: Nuevo argumento `--parallel` para procesar CVEs en paralelo
- **Optimización para monorepo**: Cada CVE se aplica simultáneamente a todos los microservicios
- **Configurable**: `--max-workers` para controlar el número de hilos (default: 5)
- **Mejor rendimiento**: Reduce tiempo de ejecución en monorepos grandes (~5x más rápido con 5 MS)

### v1.5.1
- **Tiempo de ejecución**: Muestra tiempo total al final del log (segundos, minutos u horas)

### v1.5.0
- **🆕 Integración Git**: Nuevo argumento `--commit` para crear commits automáticos
- **🆕 Rama automática**: Crea rama con formato `feature/fix_vulnerabilidad_{ddmmyyyy}_{username}`
- **🆕 Mensaje descriptivo**: Commit incluye microservicios modificados, CVEs resueltos, severidad y archivos cambiados
- **🆕 Detección de usuario**: Detecta automáticamente el nombre de usuario de git
- **🆕 Validación de repo**: Verifica que sea un repositorio git antes de intentar commitear

### v1.4.0
- **Soporte para monorepo**: Procesa múltiples microservicios en una sola ejecución
- **Nuevo argumento `--ms`**: Especifica carpetas de microservicios separadas por coma
- **Nuevo argumento `--folder`**: Especifica carpeta raíz que contiene microservicios
- **Auto-detección**: Detecta automáticamente carpetas `ms_*` cuando se usa `--folder` sin `--ms`
- **Reporte consolidado**: Genera reporte agregado con estadísticas de todos los microservicios
- **Reportes individuales**: Cada microservicio recibe su propio reporte
- **Procesamiento secuencial**: Procesa microservicios uno por uno con manejo de errores

### v1.3.0
- Soporte para múltiples build.gradle (submódulos)
- Actualización de dependencias directas en todos los submódulos
- Mejorado BackupManager para manejar rutas de archivos anidados

### v1.2.0
- Agregada validación de CVEs (cve_id no vacío, versión válida)
- Normalización de severidad a mayúsculas
- Advertencias para versiones SNAPSHOT/Milestone/RC
- Escapado de caracteres especiales en because
- Auto-detección de Java en macOS
- Fix: Regex para detectar bloque ext en build.gradle
- Fix: Manejo especial de org.apache.commons con verificación de artifact

### v1.1.0
- Rollback automático si la compilación falla
- Reporte JSON incluye rollback_performed

### v1.0.0
- Agregada validación por compilación automática (--apply)
- Flag --no-validate para saltar compilación
- Reporte JSON incluye compilation_success

## Licencia

Uso interno - Herramienta de seguridad defensiva
