# Guía de Gestión de Dependencias y CVEs

## Índice
1. [Estructura](#estructura)
2. [Cómo Resolver un CVE](#cómo-resolver-un-cve)
3. [Verificar Dependencias](#verificar-dependencias)
4. [Ejemplos](#ejemplos)

---

## Estructura

El proyecto utiliza **tres archivos** para gestionar dependencias:

```
ms_upload_documents/
├── build.gradle                 # Configuración principal
├── dependencyMgmt.gradle        # Gestión de CVEs y versiones forzadas
├── settings.gradle              # Configuración del proyecto
└── main.gradle                  # Configuración común (opcional)
```

### Responsabilidad de cada archivo

| Archivo | Propósito |
|---------|-----------|
| `build.gradle` | Define plugins y dependencias del proyecto |
| `dependencyMgmt.gradle` | **Forzar versiones** para resolver CVEs |
| `settings.gradle` | Configuración global del proyecto Gradle |

---

## Cómo Resolver un CVE

### Paso 1: Identificar el CVE

Cuando el agente CVE detecta una vulnerabilidad, recibirás:

```
CVE-2024-22243: spring-webflux 6.1.3 → 6.1.4
CVE-2023-44487: netty-codec-http2 4.1.100 → 4.1.101
```

### Paso 2: Actualizar `dependencyMgmt.gradle`

#### Opción A: Versión ya existe en el catálogo

Si la librería ya está en el bloque `ext`, simplemente actualiza la versión:

```gradle
// dependencyMgmt.gradle

ext {
    // Antes
    nettyVersion = '4.1.100.Final'
    
    // Después (CVE fix)
    nettyVersion = '4.1.108.Final'
}
```

#### Opción B: Nueva librería

Si es una librería nueva, agrega al bloque `ext`:

```gradle
// dependencyMgmt.gradle

ext {
    // ... versiones existentes ...
    
    // Nueva librería vulnerable
    vulnerableLibVersion = '2.5.1'  // Versión que corrige CVE
}
```

Luego agrega la regla en `resolutionStrategy`:

```gradle
configurations.all {
    resolutionStrategy {
        eachDependency { DependencyResolveDetails details ->
            def requested = details.requested
            
            // ... reglas existentes ...
            
            // Nueva regla para CVE-2024-XXXXX
            if (requested.group == 'com.example' && requested.name == 'vulnerable-lib') {
                details.useVersion "${vulnerableLibVersion}"
                details.because "CVE-2024-XXXXX Fix: Force version ${vulnerableLibVersion}"
            }
        }
    }
}
```

### Paso 3: Verificar la corrección

```bash
# Limpiar y reconstruir
./gradlew clean build

# Verificar árbol de dependencias
./gradlew dependencies --configuration runtimeClasspath | grep spring-webflux

# O usar la tarea personalizada
./gradlew securityAudit
```

---

## Verificar Dependencias

### Comandos útiles

```bash
# Mostrar árbol completo de dependencias
./gradlew dependencies --configuration runtimeClasspath

# Buscar una librería específica
./gradlew dependencies --configuration runtimeClasspath | grep netty

# Generar reporte de seguridad
./gradlew securityAudit

# Reporte detallado de dependencias con versiones forzadas
./gradlew dependencyReport
```

### Entender el output

```
runtimeClasspath - Runtime classpath of source set 'main'.
\--- org.springframework.boot:spring-boot-starter-webflux -> 3.2.4
     \--- io.projectreactor.netty:reactor-netty-http:1.1.15
          \--- io.netty:netty-codec-http:4.1.101.Final -> 4.1.108.Final (forced)
```

El `->` indica que Gradle **forzó** una versión diferente a la solicitada.

---

## Ejemplos

### Ejemplo 1: Actualizar Netty por CVE

**Situación:** CVE-2024-22201 en Netty 4.1.100

**Solución:**

```gradle
// dependencyMgmt.gradle

ext {
    // Actualizar versión de Netty
    nettyVersion = '4.1.108.Final'
}

// La regla ya existe en resolutionStrategy:
if (requested.group == 'io.netty') {
    details.useVersion "${nettyVersion}"
    details.because "CVE Fix: Force Netty version ${nettyVersion}"
}
```

### Ejemplo 2: Agregar Jackson (nuevo CVE)

**Situación:** CVE-2024-XXXXX en Jackson databind

**Solución:**

```gradle
// dependencyMgmt.gradle

ext {
    // Agregar nueva versión
    jacksonVersion = '2.16.2'  // Versión que corrige CVE
}

configurations.all {
    resolutionStrategy {
        eachDependency { details ->
            // Nueva regla
            if (requested.group == 'com.fasterxml.jackson.core') {
                details.useVersion "${jacksonVersion}"
                details.because "CVE Fix: Force Jackson ${jacksonVersion}"
            }
        }
    }
}
```

### Ejemplo 3: Versión desde línea de comandos

Para probar una versión sin modificar el archivo:

```bash
./gradlew build -PspringBootVersion=3.3.4
```

Esto sobrescribe la versión definida en el build.

---

## Catálogo de CVEs Comunes

| CVE | Librería | Solución |
|-----|----------|----------|
| CVE-2024-22243 | Spring WebFlux | `springFrameworkVersion = '6.1.4'` |
| CVE-2023-44487 | Netty HTTP/2 | `nettyVersion = '4.1.108.Final'` |
| CVE-2024-26308 | Apache Commons Compress | `commonsCompressVersion = '1.26.1'` |
| CVE-2022-42889 | Apache Commons Text | `commonsTextVersion = '1.11.0'` |
| CVE-2023-2976 | Google Guava | `guavaVersion = '33.1.0-jre'` |
| Log4Shell | Log4j | `log4jVersion = '2.23.1'` |

---

## Buenas Prácticas

1. **Documentar cada cambio** con el número de CVE
2. **Verificar en staging** antes de producción
3. **Ejecutar tests** completos después de actualizar: `./gradlew test pitest`
4. **Mantener un registro** de CVEs resueltos en el CHANGELOG

---

## Integración con el Agente CVE

El agente CVE Python genera automáticamente las entradas necesarias:

```json
{
  "cves": [
    {
      "cve_id": "CVE-2024-22243",
      "library_name": "spring-webflux",
      "current_version": "6.1.3",
      "fixed_version": "6.1.4"
    }
  ]
}
```

El archivo `dependencyMgmt.gradle` está diseñado para recibir estas actualizaciones fácilmente.