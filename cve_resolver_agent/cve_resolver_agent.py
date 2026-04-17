#!/usr/bin/env python3
"""
CVE Resolver Agent v1.5.2 - Para Spring WebFlux + Gradle (Soporte Monorepo + Git + Paralelo)

Arquitectura:
- Variables de versión: SOLO en build.gradle (bloque buildscript.ext)
- useVersion blocks: SOLO en dependencyMgmt.gradle (configurations.all.resolutionStrategy)
- Actualización de dependencias directas: TODOS los build.gradle (incluye submódulos)
- Una variable por grupo de librerías (consolidado)
- Validación automática por compilación
- Rollback automático si la compilación falla
- Soporta formato Snyk JSON

Uso:
    # Procesar un solo microservicio
    python3 cve_resolver_agent.py /ruta/al/proyecto [--apply] [--no-validate]

    # Procesar múltiples microservicios específicos
    python3 cve_resolver_agent.py --ms ms_auth,ms_upload,ms_core [--apply] [--no-validate]

    # Procesar carpeta que contiene microservicios
    python3 cve_resolver_agent.py --folder /ruta/proyectos [--apply] [--no-validate]

    # Con commit automático
    python3 cve_resolver_agent.py --folder /ruta/proyectos --apply --commit

    # Modo paralelo (procesa CVEs en paralelo para múltiples MS)
    python3 cve_resolver_agent.py --folder /ruta/proyectos --apply --parallel
    python3 cve_resolver_agent.py --folder /ruta/proyectos --apply --parallel --max-workers 10

Changelog v1.5.2:
- Procesamiento paralelo: Nuevo argumento `--parallel` para procesar CVEs en paralelo
- Optimización para monorepo: Cada CVE se aplica a todos los microservicios simultáneamente
- Configurable: `--max-workers` para controlar el número de hilos (default: 5)
- Mejor rendimiento: Reduce tiempo de ejecución en monorepos grandes

Changelog v1.5.1:
- Agregado tiempo de ejecución al final del log (formato legible: segundos, minutos, horas)

Changelog v1.5.0:
- Integración Git: Nuevo argumento --commit para crear commits automáticos
- Auto-creación de rama: feature/fix_vulnerabilidad_{ddmmyyyy}_{username}
- Mensaje de commit descriptivo con microservicios modificados y CVEs resueltos
- Detección automática del nombre de usuario git
- Verificación de repositorio git antes de crear commits

Changelog v1.4.0:
- Soporte para monorepo (múltiples microservicios)
- Nuevo argumento --ms para especificar carpetas de microservicios (coma separados)
- Nuevo argumento --folder para especificar carpeta raíz de microservicios
- Auto-detección de carpetas ms_* en raíz cuando se usa --folder sin --ms
- Reporte consolidado para todos los microservicios procesados
- Soporta estructura monorepositorio con prefijo ms_

Changelog v1.3.0:
- Soporte para múltiples build.gradle (submódulos)
- Actualización de dependencias directas en todos los submódulos
- Mejorado BackupManager para manejar rutas de archivos anidadas

Changelog v1.2.0:
- Agregada validación de CVEs (cve_id no vacío, versión válida)
- Normalización de severidad a mayúsculas
- Advertencias para versiones SNAPSHOT/Milestone/RC
- Escapado de caracteres especiales en because
- Auto-detección de Java en macOS
- Fix: Regex para detectar bloque ext en build.gradle
- Fix: Manejo especial de org.apache.commons con verificación de artifact

Changelog v1.1.0:
- Rollback automático si la compilación falla
- Reporte JSON incluye rollback_performed

Changelog v1.0.0:
- Agregada validación por compilación automática (--apply)
- Flag --no-validate para saltar compilación
- Reporte JSON incluye compilation_success
"""

import json
import re
import os
import sys
import argparse
import shutil
import subprocess
import time
from pathlib import Path
from typing import Dict, List, Optional, Tuple
from dataclasses import dataclass
from datetime import datetime
from concurrent.futures import ThreadPoolExecutor, as_completed


@dataclass(frozen=True)
class CVEEntry:
    """Representa una entrada CVE."""
    cve_id: str
    library_name: str
    group: str
    current_version: str
    fixed_version: str
    severity: str = "UNKNOWN"


class SnykCVEProcessor:
    """Procesa datos CVE desde formato Snyk."""

    SEVERITY_ORDER = {'CRITICAL': 4, 'HIGH': 3, 'MEDIUM': 2, 'LOW': 1, 'UNKNOWN': 0}

    # Versiones problemáticas que deben ser rechazadas o advertidas
    BLOCKED_VERSION_PATTERNS = [
        (r'.*-SNAPSHOT$', 'SNAPSHOT versions are not allowed in production'),
        (r'.*-M\d+$', 'Milestone versions should be reviewed manually'),
        (r'.*-RC\d+$', 'Release Candidate versions should be reviewed manually'),
        (r'^\s*$', 'Empty version is not valid'),
    ]

    def __init__(self, cve_file_path: Path):
        self.cve_file_path = cve_file_path

    def load_cves(self) -> List[CVEEntry]:
        """Carga CVEs desde archivo JSON con validaciones."""
        if not self.cve_file_path.exists():
            raise FileNotFoundError(f"Archivo CVE no encontrado: {self.cve_file_path}")

        with open(self.cve_file_path, 'r', encoding='utf-8') as f:
            data = json.load(f)

        entries = []
        seen = set()
        skipped = []

        for item in data.get('cves', []):
            cve_id = item.get('cve_id', '').strip()
            fixed_version = item.get('fixed_version', '').strip()

            # Validar campos requeridos
            if not cve_id:
                skipped.append(('Sin CVE ID', item.get('library_name', 'unknown')))
                continue

            if not fixed_version:
                skipped.append(('Sin versión fija', cve_id))
                continue

            # Validar versión problemática
            version_warning = self._validate_version(fixed_version)
            if version_warning:
                print(f"   ⚠️  Advertencia en {cve_id}: {version_warning}")

            # Normalizar severidad a mayúsculas
            severity = item.get('severity', 'UNKNOWN').upper()
            if severity not in self.SEVERITY_ORDER:
                severity = 'UNKNOWN'

            cve = CVEEntry(
                cve_id=cve_id,
                library_name=item.get('library_name', ''),
                group=item.get('group', ''),
                current_version=item.get('current_version', ''),
                fixed_version=fixed_version,
                severity=severity
            )

            # Evitar duplicados por CVE + grupo
            key = (cve.cve_id, cve.group)
            if key not in seen:
                seen.add(key)
                entries.append(cve)
            else:
                skipped.append(('Duplicado', cve_id))

        if skipped:
            print(f"   ⚠️  {len(skipped)} CVEs omitidos por errores:")
            for reason, detail in skipped[:5]:  # Mostrar solo los primeros 5
                print(f"      - {reason}: {detail}")
            if len(skipped) > 5:
                print(f"      ... y {len(skipped) - 5} más")

        return self._sort_by_severity(entries)

    def _validate_version(self, version: str) -> Optional[str]:
        """Valida que la versión no tenga patrones problemáticos."""
        import re
        for pattern, message in self.BLOCKED_VERSION_PATTERNS:
            if re.match(pattern, version, re.IGNORECASE):
                return message
        return None

    def _sort_by_severity(self, entries: List[CVEEntry]) -> List[CVEEntry]:
        """Ordena CVEs por severidad (CRITICAL primero)."""
        return sorted(entries,
                     key=lambda x: self.SEVERITY_ORDER.get(x.severity, 0),
                     reverse=True)


class BackupManager:
    """Gestiona backups de archivos Gradle."""

    def __init__(self, project_path: Path):
        self.project_path = project_path
        self.backup_dir = project_path / ".cve_resolver_backups"
        self.created_backups: List[Tuple[Path, Path]] = []  # (backup_path, original_path)

    def create_backup(self, file_path: Path) -> Path:
        """Crea backup del archivo y lo registra."""
        self.backup_dir.mkdir(exist_ok=True)
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")

        # Crear un nombre único que incluya la ruta relativa para evitar conflictos
        rel_path = file_path.relative_to(self.project_path)
        safe_name = str(rel_path).replace(os.sep, '_')
        backup_filename = f"{safe_name}.{timestamp}.backup"
        backup_path = self.backup_dir / backup_filename

        shutil.copy2(file_path, backup_path)
        self.created_backups.append((backup_path, file_path))
        return backup_path

    def rollback(self) -> Tuple[bool, str]:
        """
        Restaura todos los archivos desde los backups creados.

        Returns:
            Tuple[bool, str]: (éxito, mensaje)
        """
        if not self.created_backups:
            return True, "No hay backups para restaurar"

        restored = []
        failed = []

        for backup_path, original_path in self.created_backups:
            try:
                shutil.copy2(backup_path, original_path)
                rel_path = original_path.relative_to(self.project_path)
                restored.append(str(rel_path))
            except Exception as e:
                failed.append(f"{backup_path.name}: {str(e)}")

        if failed:
            return False, f"Rollback parcial. Fallidos: {', '.join(failed)}"

        return True, f"Rollback exitoso. Archivos restaurados: {', '.join(restored)}"


class GradleCVEUpdater:
    """Actualiza versiones CVE en archivos Gradle."""

    # Mapeo: grupo de librerías -> nombre de variable de versión
    VERSION_MAP = {
        'io.netty': 'nettyVersion',
        'org.springframework': 'springFrameworkVersion',
        'com.fasterxml.jackson.core': 'jacksonVersion',
        'org.apache.commons': 'commonsCompressVersion',
        'org.apache.logging.log4j': 'log4jVersion',
    }

    def __init__(self, project_path: Path, dry_run: bool = True):
        self.project_path = project_path
        self.dry_run = dry_run
        self.updates: List[Dict] = []
        self.backup_manager = BackupManager(project_path)

    def _find_all_build_gradle_files(self) -> List[Path]:
        """Encuentra todos los archivos build.gradle en el proyecto (incluye submódulos)."""
        build_files = []
        for gradle_file in self.project_path.rglob('build.gradle'):
            if gradle_file.is_file():
                build_files.append(gradle_file)
        return sorted(build_files)

    def update(self, cves: List[CVEEntry]) -> Dict:
        """
        Actualiza archivos Gradle con las versiones fijas de CVEs.

        Args:
            cves: Lista de entradas CVE a procesar

        Returns:
            Dict con resumen de cambios realizados
        """
        results = {'updated': [], 'skipped': [], 'failed': []}

        # Agrupar CVEs por grupo para consolidar
        grouped_cves = self._group_by_version_var(cves)

        # 1. Actualizar el build.gradle raíz (variables de versión en ext block)
        root_build_file = self.project_path / "build.gradle"
        if root_build_file.exists():
            for version_var, group_cves in grouped_cves.items():
                primary_cve = group_cves[0]
                build_result = self._update_build_gradle_variables(root_build_file, version_var, primary_cve)
                if build_result:
                    results['updated'].append(build_result)

        # 2. Actualizar dependencyMgmt.gradle (useVersion)
        for version_var, group_cves in grouped_cves.items():
            primary_cve = group_cves[0]
            self._update_dependency_mgmt(version_var, primary_cve)

        # 3. Actualizar TODOS los build.gradle (incluyendo submódulos) - dependencias directas
        build_files = self._find_all_build_gradle_files()
        print(f"\n   📁 Encontrados {len(build_files)} archivo(s) build.gradle")
        for build_file in build_files:
            for cve in cves:
                direct_result = self._update_direct_dependency(build_file, cve)
                if direct_result:
                    results['updated'].append(direct_result)

        return results

    def _group_by_version_var(self, cves: List[CVEEntry]) -> Dict[str, List[CVEEntry]]:
        """Agrupa CVEs por su variable de versión correspondiente."""
        grouped = {}
        for cve in cves:
            version_var = self._get_version_variable(cve.group)
            if version_var:
                if version_var not in grouped:
                    grouped[version_var] = []
                grouped[version_var].append(cve)
        return grouped

    def _get_version_variable(self, group: str) -> Optional[str]:
        """Obtiene el nombre de variable para un grupo de librerías."""
        return self.VERSION_MAP.get(group)

    def _update_build_gradle_variables(self, build_file: Path, version_var: str, cve: CVEEntry) -> Optional[Dict]:
        """Actualiza/agrega variable de versión en build.gradle raíz (bloque ext)."""
        content = build_file.read_text(encoding='utf-8')
        original = content

        # Buscar variable existente
        pattern = rf"({re.escape(version_var)}\s*=\s*['\"])([^'\"]+)(['\"])"
        match = re.search(pattern, content)

        if match:
            old_version = match.group(2)
            if old_version == cve.fixed_version:
                print(f"   ⏭️  {version_var}: Ya está en versión {cve.fixed_version}")
                return None

            # Actualizar versión existente
            content = re.sub(pattern, rf"\g<1>{cve.fixed_version}\g<3>", content)
            action = "UPDATED"
            print(f"   ✓ {version_var}: {old_version} → {cve.fixed_version}")
        else:
            # Agregar nueva variable al bloque ext
            content = self._add_version_to_build(content, version_var, cve)
            action = "ADDED"
            print(f"   ✓ {version_var}: ADDED → {cve.fixed_version}")

        if content != original:
            if not self.dry_run:
                backup_path = self.backup_manager.create_backup(build_file)
                print(f"   💾 Backup: {backup_path.name}")
                build_file.write_text(content, encoding='utf-8')

            return {
                'file': str(build_file.relative_to(self.project_path)),
                'action': action,
                'variable': version_var,
                'cve': cve.cve_id,
                'new_version': cve.fixed_version
            }

        return None

    def _update_direct_dependency(self, build_file: Path, cve: CVEEntry) -> Optional[Dict]:
        """
        Actualiza dependencias directas en build.gradle (incluyendo submódulos).
        Busca patrones como: implementation 'group:name:version' o implementation group: '...', name: '...', version: '...'
        También soporta formato especial: 'group_library:version' (usando _ como separador)
        """
        content = build_file.read_text(encoding='utf-8')
        original = content

        file_rel = str(build_file.relative_to(self.project_path))
        updated = False
        old_version = None

        # Formato 1: Estándar 'group:name:version'
        # Ejemplo: implementation 'io.netty:netty-codec-http2:4.2.4.Final'
        pattern1 = rf"(implementation\s*['\"]){re.escape(cve.group)}:{re.escape(cve.library_name)}:([^'\"]+)(['\"])"

        # Formato 2: Con _ entre group y name 'group_name:version'
        # Ejemplo: implementation 'io.netty_netty-codec-http2:4.2.4.Final'
        pattern2 = rf"(implementation\s*['\"]){re.escape(cve.group)}_{re.escape(cve.library_name)}:([^'\"]+)(['\"])"

        # Formato 3: Map syntax implementation group: '...', name: '...', version: '...'
        pattern3 = rf"(implementation\s+group:\s*['\"]){re.escape(cve.group)}(['\"],\s*name:\s*['\"]){re.escape(cve.library_name)}(['\"],\s*version:\s*['\"])([^'\"]+)(['\"])"

        # Intentar Formato 1 (estándar)
        match1 = re.search(pattern1, content)
        if match1:
            old_version = match1.group(3)
            if old_version != cve.fixed_version:
                content = re.sub(pattern1, rf"\g<1>{cve.group}:{cve.library_name}:{cve.fixed_version}\g<3>", content)
                updated = True
            else:
                return None

        # Intentar Formato 2 (con _ como separador)
        if not updated:
            match2 = re.search(pattern2, content)
            if match2:
                old_version = match2.group(3)
                if old_version != cve.fixed_version:
                    # Normalizar a formato estándar group:name:version
                    content = re.sub(pattern2, rf"\g<1>{cve.group}:{cve.library_name}:{cve.fixed_version}\g<3>", content)
                    updated = True
                else:
                    return None

        # Intentar Formato 3 (map syntax)
        if not updated:
            match3 = re.search(pattern3, content)
            if match3:
                old_version = match3.group(5)
                if old_version != cve.fixed_version:
                    content = re.sub(pattern3, rf"\g<1>{cve.group}\g<2>{cve.library_name}\g<3>{cve.fixed_version}\g<5>", content)
                    updated = True
                else:
                    return None

        if updated and content != original:
            print(f"   ✓ [{file_rel}] {cve.group}:{cve.library_name}: {old_version} → {cve.fixed_version}")
            if not self.dry_run:
                backup_path = self.backup_manager.create_backup(build_file)
                print(f"   💾 Backup: {backup_path.name}")
                build_file.write_text(content, encoding='utf-8')

            return {
                'file': file_rel,
                'action': 'UPDATED_DIRECT',
                'library': f"{cve.group}:{cve.library_name}",
                'cve': cve.cve_id,
                'new_version': cve.fixed_version
            }

        return None

    def _add_version_to_build(self, content: str, version_var: str, cve: CVEEntry) -> str:
        """Agrega una nueva variable al bloque ext de build.gradle."""
        new_var_line = f"        {version_var} = '{cve.fixed_version}'\n"

        if 'buildscript {' in content:
            # Buscar el bloque buildscript y dentro de él el bloque ext
            # Usar un enfoque más robusto que maneje múltiples líneas
            buildscript_match = re.search(
                r'buildscript\s*\{',
                content
            )

            if buildscript_match:
                # Buscar el bloque ext dentro de buildscript
                ext_match = re.search(
                    r'(buildscript\s*\{[\s\S]*?ext\s*\{)([\s\S]*?)(\}[\s\S]*?\})',
                    content,
                    re.MULTILINE
                )

                if ext_match:
                    ext_start, ext_content, ext_end = ext_match.groups()
                    if version_var not in ext_content:
                        # Agregar variable al bloque ext existente
                        # Encontrar la última línea del ext_content
                        lines = ext_content.rstrip().split('\n')
                        new_ext_content = ext_content.rstrip() + '\n' + new_var_line
                        return content[:ext_match.start()] + ext_start + new_ext_content + ext_end + content[ext_match.end():]

                # Si no hay bloque ext, crearlo dentro de buildscript
                # Insertar después de "buildscript {"
                buildscript_start = buildscript_match.end()
                ext_block = f"\n    ext {{\n{new_var_line}    }}\n"
                return content[:buildscript_start] + ext_block + content[buildscript_start:]

        # Crear bloque buildscript completo si no existe
        buildscript = f"""buildscript {{
    ext {{{new_var_line}    }}
}}

"""
        return buildscript + content

    def _escape_gradle_string(self, text: str) -> str:
        """Escapa caracteres especiales para strings de Gradle."""
        # Reemplazar caracteres que podrían romper el string de Gradle
        return text.replace('\\', '\\\\').replace('"', '\\"').replace("'", "\\'")

    def _update_dependency_mgmt(self, version_var: str, cve: CVEEntry) -> None:
        """Agrega useVersion block en dependencyMgmt.gradle si no existe."""
        dm_file = self.project_path / "dependencyMgmt.gradle"
        if not dm_file.exists():
            return

        content = dm_file.read_text(encoding='utf-8')

        # Verificar si ya existe useVersion para este grupo
        if f"details.requested.group == '{cve.group}'" in content:
            return

        # Insertar bloque useVersion antes del cierre de resolutionStrategy
        pattern = r"(resolutionStrategy\.eachDependency\s*\{[\s\S]*?)(\n    \}\s*\})"
        match = re.search(pattern, content)

        if match and version_var:
            # Escapar el CVE ID para el because
            safe_cve_id = self._escape_gradle_string(cve.cve_id)

            # Para org.apache.commons, especificar el artifact específico
            # ya que el grupo contiene librerías independientes con versiones diferentes
            if cve.group == 'org.apache.commons':
                use_block = f"""\n        if (details.requested.group == '{cve.group}' && details.requested.name == '{cve.library_name}') {{
            details.useVersion "${{{version_var}}}"
            details.because "Fix: {safe_cve_id}"
        }}"""
            else:
                use_block = f"""\n        if (details.requested.group == '{cve.group}') {{
            details.useVersion "${{{version_var}}}"
            details.because "Fix: {safe_cve_id}"
        }}"""

            new_content = match.group(1) + use_block + match.group(2)
            content = content.replace(match.group(0), new_content)

            if not self.dry_run:
                backup_path = self.backup_manager.create_backup(dm_file)
                print(f"   💾 Backup: {backup_path.name}")
                dm_file.write_text(content, encoding='utf-8')

            print(f"   ✓ Added useVersion for {cve.group}")


class GradleCompiler:
    """Compila el proyecto Gradle para validar cambios."""

    # Ubicaciones comunes de Java en macOS
    COMMON_JAVA_PATHS = [
        "/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home",
        "/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home",
        "/usr/local/opt/openjdk/libexec/openjdk.jdk/Contents/Home",
        "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home",
        "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home",
        "/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home",
        "/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home",
        "/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home",
        "/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home",
        "/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home",
    ]

    def __init__(self, project_path: Path):
        self.project_path = project_path

    def _find_java_home(self) -> Optional[str]:
        """Busca Java en ubicaciones comunes si JAVA_HOME no está definido."""
        # Primero verificar JAVA_HOME existente
        java_home = os.environ.get("JAVA_HOME")
        if java_home and Path(java_home).exists():
            return java_home

        # Buscar en ubicaciones comunes
        for path in self.COMMON_JAVA_PATHS:
            if Path(path).exists():
                return path

        return None

    def compile(self) -> Tuple[bool, str]:
        """
        Compila el proyecto Gradle.

        Returns:
            Tuple[bool, str]: (éxito, mensaje)
        """
        print("\n🔨 Compilando proyecto para validar cambios...")

        # Preparar entorno con Java
        env = os.environ.copy()
        java_home = self._find_java_home()

        if java_home:
            env["JAVA_HOME"] = java_home
            print(f"   📝 Usando Java: {java_home}")
        else:
            print("   ⚠️  No se encontró JAVA_HOME. Intentando usar Java del sistema...")

        # Determinar comando de Gradle
        gradlew = self.project_path / "gradlew"
        gradle_cmd = ["./gradlew"] if gradlew.exists() else ["gradle"]

        # Comando para compilar sin tests (más rápido)
        cmd = gradle_cmd + ["compileJava", "--no-daemon", "-q"]

        try:
            result = subprocess.run(
                cmd,
                cwd=self.project_path,
                capture_output=True,
                text=True,
                timeout=300,  # 5 minutos timeout
                env=env
            )

            if result.returncode == 0:
                return True, "✅ Compilación exitosa"
            else:
                error_msg = result.stderr if result.stderr else "Error desconocido"
                return False, f"❌ Fallo de compilación: {error_msg}"

        except subprocess.TimeoutExpired:
            return False, "❌ Timeout: La compilación tomó más de 5 minutos"
        except FileNotFoundError:
            return False, "❌ Gradle no encontrado. Instala Gradle o usa el wrapper (gradlew)"
        except Exception as e:
            return False, f"❌ Error durante compilación: {str(e)}"


class CVEResolverAgent:
    """Agente principal para resolver CVEs en proyectos Gradle."""

    def __init__(self, project_path: Path, cve_file_path: Path, dry_run: bool = True, validate: bool = True):
        self.project_path = project_path
        self.dry_run = dry_run
        self.validate = validate
        self.cve_processor = SnykCVEProcessor(cve_file_path)
        self.gradle_updater = GradleCVEUpdater(project_path, dry_run)
        self.compiler = GradleCompiler(project_path)

    def run(self) -> Dict:
        """Ejecuta el flujo completo de resolución de CVEs."""
        print("🔍 CVE Resolver Agent")
        print("=" * 60)

        # 1. Cargar CVEs
        print("📋 Cargando CVEs desde Snyk...")
        cves = self.cve_processor.load_cves()
        print(f"   {len(cves)} CVEs únicos cargados")

        # 2. Mostrar resumen
        print("\n📊 Por severidad:")
        for cve in cves:
            print(f"   [{cve.severity}] {cve.cve_id}: {cve.group} "
                  f"({cve.current_version} → {cve.fixed_version})")

        # 3. Actualizar archivos
        print("\n🔧 Actualizando archivos Gradle...")
        results = self.gradle_updater.update(cves)

        # 4. Validar con compilación si se aplicaron cambios y no es dry-run
        compilation_success = None
        rollback_performed = False

        if not self.dry_run and results['updated'] and self.validate:
            success, message = self.compiler.compile()
            compilation_success = success
            print(f"\n   {message}")

            if not success:
                print("\n⚠️  La compilación falló. Iniciando rollback automático...")
                rollback_success, rollback_msg = self.gradle_updater.backup_manager.rollback()

                if rollback_success:
                    rollback_performed = True
                    print(f"\n   ↩️  {rollback_msg}")
                    print("   ✅ Archivos restaurados a su estado original")
                else:
                    print(f"\n   ❌ Rollback fallido: {rollback_msg}")
                    print("   ⚠️  Los backups están en: .cve_resolver_backups/")

        # 5. Reporte final
        print("\n" + "=" * 60)
        print(f"✅ Actualizaciones: {len(results['updated'])}")
        print(f"⏭️  Saltadas: {len(results['skipped'])}")

        if self.dry_run:
            print("\nℹ️  Modo SIMULACIÓN - use --apply para aplicar cambios")
        elif compilation_success is not None:
            if compilation_success:
                print(f"\n🔨 Validación: Compilación exitosa")
            else:
                if rollback_performed:
                    print(f"\n🔨 Validación: Compilación fallida - Rollback ejecutado")
                else:
                    print(f"\n🔨 Validación: Compilación fallida - Rollback fallido")

        return {
            'total_cves': len(cves),
            'updates': results['updated'],
            'compilation_success': compilation_success,
            'rollback_performed': rollback_performed,
            'dry_run': self.dry_run
        }


def get_default_cve_file() -> Path:
    """Obtiene la ruta al archivo CVE por defecto (en el mismo directorio que el script)."""
    script_dir = Path(__file__).parent.resolve()
    return script_dir / "snyk_cves_for_agent.json"


class GitCommitManager:
    """Gestiona operaciones Git para commitear cambios de CVE."""

    def __init__(self, project_path: Path):
        self.project_path = project_path
        self.original_branch: Optional[str] = None

    def _run_git_command(self, cmd: List[str], cwd: Optional[Path] = None) -> Tuple[bool, str]:
        """
        Ejecuta un comando git.

        Returns:
            Tuple[bool, str]: (éxito, stdout o mensaje de error)
        """
        try:
            result = subprocess.run(
                ["git"] + cmd,
                cwd=cwd or self.project_path,
                capture_output=True,
                text=True,
                timeout=30
            )
            if result.returncode == 0:
                return True, result.stdout.strip()
            else:
                return False, result.stderr.strip()
        except subprocess.TimeoutExpired:
            return False, "Timeout ejecutando comando git"
        except FileNotFoundError:
            return False, "Git no encontrado en el sistema"
        except Exception as e:
            return False, f"Error ejecutando git: {str(e)}"

    def is_git_repository(self) -> bool:
        """Verifica si el path es un repositorio git."""
        success, _ = self._run_git_command(["rev-parse", "--git-dir"])
        return success

    def get_current_branch(self) -> Optional[str]:
        """Obtiene el nombre de la rama actual."""
        success, output = self._run_git_command(["branch", "--show-current"])
        if success:
            return output
        return None

    def get_git_user_name(self) -> Optional[str]:
        """Obtiene el nombre de usuario configurado en git."""
        success, output = self._run_git_command(["config", "user.name"])
        if success and output:
            # Limpiar el nombre (sin espacios, minúsculas)
            return output.lower().replace(" ", ".")

        # Intentar obtener desde variable de entorno o sistema
        import getpass
        return getpass.getuser()

    def has_changes(self) -> bool:
        """Verifica si hay cambios sin commitear."""
        success, output = self._run_git_command(["status", "--porcelain"])
        if success:
            return len(output.strip()) > 0
        return False

    def create_branch(self, branch_name: str) -> Tuple[bool, str]:
        """
        Crea y cambia a una nueva rama.

        Returns:
            Tuple[bool, str]: (éxito, mensaje)
        """
        # Guardar rama actual para posible rollback
        self.original_branch = self.get_current_branch()

        # Crear y checkout a la nueva rama
        success, output = self._run_git_command(["checkout", "-b", branch_name])
        if success:
            return True, f"Rama creada y activada: {branch_name}"
        else:
            return False, f"Error creando rama: {output}"

    def add_files(self, files: List[str]) -> Tuple[bool, str]:
        """
        Agrega archivos al staging area.

        Args:
            files: Lista de paths relativos a agregar

        Returns:
            Tuple[bool, str]: (éxito, mensaje)
        """
        if not files:
            return False, "No hay archivos para agregar"

        success, output = self._run_git_command(["add"] + files)
        if success:
            return True, f"Agregados {len(files)} archivo(s)"
        else:
            return False, f"Error agregando archivos: {output}"

    def add_all_changes(self) -> Tuple[bool, str]:
        """Agrega todos los cambios al staging area."""
        success, output = self._run_git_command(["add", "."])
        if success:
            return True, "Todos los cambios agregados"
        else:
            return False, f"Error agregando cambios: {output}"

    def commit(self, message: str) -> Tuple[bool, str]:
        """
        Crea un commit con el mensaje proporcionado.

        Returns:
            Tuple[bool, str]: (éxito, mensaje)
        """
        success, output = self._run_git_command(["commit", "-m", message])
        if success:
            # Obtener hash del commit
            _, hash_output = self._run_git_command(["rev-parse", "--short", "HEAD"])
            return True, f"Commit creado: {hash_output}"
        else:
            return False, f"Error creando commit: {output}"

    def get_changed_files(self) -> List[str]:
        """Obtiene lista de archivos modificados."""
        success, output = self._run_git_command(["diff", "--name-only", "HEAD"])
        if success:
            return [f.strip() for f in output.split('\n') if f.strip()]
        return []

    def generate_commit_message(self, results: Dict, microservice_name: str, all_cves: List[CVEEntry]) -> str:
        """
        Genera un mensaje de commit descriptivo.

        Args:
            results: Resultados del procesamiento del microservicio
            microservice_name: Nombre del microservicio
            all_cves: Lista de todos los CVEs procesados

        Returns:
            str: Mensaje de commit formateado
        """
        lines = []

        # Título
        lines.append(f"security: fix vulnerabilities in {microservice_name}")
        lines.append("")

        # Resumen
        total_updates = len(results.get('updates', []))
        total_cves = results.get('total_cves', 0)
        lines.append(f"Fixed {total_cves} CVE(s) affecting {total_updates} dependency groups")
        lines.append("")

        # Detalles de CVEs
        if all_cves:
            lines.append("Resolved vulnerabilities:")
            for cve in all_cves:
                lines.append(f"  - {cve.cve_id}: {cve.group}:{cve.library_name}")
                lines.append(f"    Severity: {cve.severity}")
                lines.append(f"    Updated: {cve.current_version} → {cve.fixed_version}")
            lines.append("")

        # Archivos modificados
        updates = results.get('updates', [])
        if updates:
            lines.append("Modified files:")
            files = set(u.get('file', 'unknown') for u in updates)
            for f in sorted(files):
                lines.append(f"  - {f}")
            lines.append("")

        # Footer
        lines.append("Generated by CVE Resolver Agent v1.4.0")

        return "\n".join(lines)

    def generate_branch_name(self, username: Optional[str] = None) -> str:
        """
        Genera el nombre de la rama con el formato:
        feature/fix_vulnerabilidad_{ddmmyyyy}_{username}

        Args:
            username: Nombre de usuario (opcional)

        Returns:
            str: Nombre de rama formateado
        """
        date_str = datetime.now().strftime("%d%m%Y")

        if not username:
            username = self.get_git_user_name() or "unknown"

        # Limpiar username (solo letras, números, puntos, guiones)
        username = re.sub(r'[^a-zA-Z0-9._-]', '', str(username)).lower()

        return f"feature/fix_vulnerabilidad_{date_str}_{username}"


def discover_microservices(folder_path: Path) -> List[Path]:
    """
    Descubre microservicios en una carpeta raíz.
    Busca carpetas que comiencen con 'ms_' en la raíz de la carpeta especificada.

    Args:
        folder_path: Ruta a la carpeta raíz

    Returns:
        Lista de rutas a microservicios encontrados
    """
    if not folder_path.exists():
        return []

    microservices = []
    for item in folder_path.iterdir():
        if item.is_dir() and item.name.startswith('ms_'):
            # Verificar que sea un proyecto Gradle (tenga build.gradle)
            if (item / "build.gradle").exists():
                microservices.append(item)

    return sorted(microservices)


def parse_microservices(args_ms: Optional[str], args_folder: Optional[str], args_project_path: str) -> List[Path]:
    """
    Parsea los argumentos para determinar qué microservicios procesar.

    Prioridad:
    1. Si se especifica --ms: usar esos microservicios
    2. Si se especifica --folder sin --ms: auto-descubrir ms_* en esa carpeta
    3. Si no se especifica nada: usar project_path como microservicio único

    Args:
        args_ms: Valor del argumento --ms (coma separado)
        args_folder: Valor del argumento --folder
        args_project_path: Valor del argumento project_path

    Returns:
        Lista de rutas a microservicios a procesar
    """
    # Caso 1: Se especificaron microservicios específicos con --ms
    if args_ms:
        ms_names = [name.strip() for name in args_ms.split(',')]

        if args_folder:
            # Buscar en la carpeta especificada
            base_path = Path(args_folder)
            microservices = [base_path / name for name in ms_names]
        else:
            # Buscar en el directorio actual
            microservices = [Path(name) for name in ms_names]

        # Verificar que existan
        existing = []
        for ms in microservices:
            if ms.exists():
                existing.append(ms)
            else:
                print(f"⚠️  Microservicio no encontrado: {ms}")

        return existing

    # Caso 2: Se especificó --folder pero no --ms -> auto-descubrir
    if args_folder:
        folder_path = Path(args_folder)
        discovered = discover_microservices(folder_path)
        if discovered:
            print(f"🔍 Auto-detectados {len(discovered)} microservicio(s) en {folder_path}:")
            for ms in discovered:
                print(f"   - {ms.name}")
        else:
            print(f"⚠️  No se encontraron microservicios (carpetas ms_*) en {folder_path}")
        return discovered

    # Caso 3: Modo legacy - un solo proyecto
    return [Path(args_project_path)]


def process_single_cve_for_microservice(
    project_path: Path,
    cve: CVEEntry,
    cve_file: Path,
    dry_run: bool,
    validate: bool
) -> Dict:
    """
    Procesa un solo CVE para un microservicio específico.
    Esta función se ejecuta en paralelo para cada microservicio.

    Args:
        project_path: Ruta al microservicio
        cve: CVE a procesar
        cve_file: Ruta al archivo de CVEs (para el agente)
        dry_run: Modo simulación
        validate: Validar compilación

    Returns:
        Dict con resultados del procesamiento
    """
    result = {
        'success': False,
        'cve_id': cve.cve_id,
        'microservice': project_path.name,
        'updates': [],
        'compilation_success': None,
        'rollback_performed': False,
        'message': ''
    }

    try:
        # Crear agente y procesar SOLO este CVE
        agent = CVEResolverAgent(project_path, cve_file, dry_run=dry_run, validate=validate)

        # Procesar solo este CVE específico
        updater = agent.gradle_updater
        cves = [cve]
        update_results = updater.update(cves)

        # Si hay actualizaciones, intentar compilar
        if not dry_run and update_results['updated'] and validate:
            compiler = GradleCompiler(project_path)
            success, msg = compiler.compile()
            result['compilation_success'] = success

            if not success:
                # Rollback si falla compilación
                rollback_success, _ = updater.backup_manager.rollback()
                result['rollback_performed'] = rollback_success
                result['success'] = False
                result['message'] = f"Compilación fallida, rollback ejecutado"
                return result
        else:
            result['compilation_success'] = True if not dry_run else None

        # Determinar éxito
        if update_results['updated']:
            result['success'] = True
            result['updates'] = update_results['updated']
            result['message'] = f"{len(update_results['updated'])} actualizaciones"
        else:
            result['success'] = True
            result['message'] = "Sin cambios (ya actualizado o no aplica)"

    except Exception as e:
        result['success'] = False
        result['message'] = f"Error: {str(e)}"
        result['error'] = str(e)

    return result


def main():
    # Registrar tiempo de inicio
    start_time = time.time()

    parser = argparse.ArgumentParser(
        description="CVE Resolver Agent - Resuelve CVEs desde Snyk para Gradle",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Ejemplos:
  # Modo single microservicio (legacy)
  %(prog)s /ruta/al/proyecto                    # Simular cambios
  %(prog)s /ruta/al/proyecto --apply            # Aplicar y compilar
  %(prog)s /ruta/al/proyecto --apply --no-validate  # Aplicar sin compilar

  # Modo multi-microservicio (--ms)
  %(prog)s --ms ms_auth,ms_upload --apply       # Procesar ms_auth y ms_upload
  %(prog)s --ms ms_auth --folder /proyectos --apply  # Procesar ms_auth en /proyectos

  # Modo auto-detección (--folder sin --ms)
  %(prog)s --folder /proyectos --apply          # Auto-detectar ms_* en /proyectos

  # Con commit automático
  %(prog)s /ruta/al/proyecto --apply --commit   # Aplicar, validar y commitear
  %(prog)s --folder /proyectos --apply --commit # Commitear todos los ms modificados

  # Modo paralelo (procesa cada CVE en todos los MS simultáneamente)
  %(prog)s --folder /proyectos --apply --parallel
  %(prog)s --folder /proyectos --apply --parallel --max-workers 10
        """
    )
    parser.add_argument("project_path", nargs="?", default=".",
                        help="Ruta al proyecto Gradle (modo legacy)")
    parser.add_argument(
        "cve_file",
        nargs="?",
        default=get_default_cve_file(),
        help="Ruta al archivo de CVEs (formato Snyk). Por defecto: snyk_cves_for_agent.json"
    )
    parser.add_argument("--ms", dest="microservices", metavar="MS_LIST",
                        help="Lista de microservicios separados por coma (ej: ms_auth,ms_upload)")
    parser.add_argument("--folder", metavar="FOLDER_PATH",
                        help="Carpeta raíz que contiene los microservicios (busca ms_*)")
    parser.add_argument("--apply", "-a", action="store_true",
                       help="Aplicar cambios (sin esto solo simula)")
    parser.add_argument("--no-validate", action="store_true",
                       help="No compilar el proyecto después de aplicar cambios")
    parser.add_argument("--commit", action="store_true",
                       help="Crear commit con los cambios (requiere --apply y git repository)")
    parser.add_argument("--parallel", "-p", action="store_true",
                       help="Procesar CVEs en paralelo para múltiples microservicios (monorepo)")
    parser.add_argument("--max-workers", type=int, default=5,
                       help="Número máximo de workers para procesamiento paralelo (default: 5)")

    args = parser.parse_args()

    cve_file = Path(args.cve_file)

    if not cve_file.exists():
        print(f"❌ Error: Archivo CVE no encontrado: {cve_file}")
        sys.exit(1)

    # Determinar qué microservicios procesar
    microservices = parse_microservices(args.microservices, args.folder, args.project_path)

    if not microservices:
        print("❌ Error: No se encontraron microservicios para procesar")
        sys.exit(1)

    validate = not args.no_validate
    all_results = {}
    any_failed = False

    # Cargar CVEs para saber cuántos hay
    try:
        processor = SnykCVEProcessor(cve_file)
        all_cves = processor.load_cves()
    except Exception as e:
        print(f"❌ Error cargando CVEs: {e}")
        sys.exit(1)

    # ====================================================================================
    # Modo Paralelo: Procesar cada CVE en todos los microservicios simultáneamente
    # ====================================================================================
    if args.parallel and len(microservices) > 1:
        print("=" * 60)
        print(f"🚀 CVE Resolver Agent v1.5.2 - Modo Paralelo")
        print(f"🔧 {len(valid_microservices)} microservicios x {len(all_cves)} CVEs")
        print(f"⚡ Max workers: {args.max_workers}")
        print("=" * 60)

        # Validar microservicios primero
        valid_microservices = []
        for ms_path in microservices:
            if not ms_path.exists():
                print(f"⚠️  Saltando {ms_path.name}: Proyecto no existe")
                all_results[ms_path.name] = {"error": "Proyecto no encontrado"}
                any_failed = True
            elif not (ms_path / "build.gradle").exists():
                print(f"⚠️  Saltando {ms_path.name}: No es proyecto Gradle")
                all_results[ms_path.name] = {"error": "No es proyecto Gradle"}
                any_failed = True
            else:
                valid_microservices.append(ms_path)

        if not valid_microservices:
            print("❌ No hay microservicios válidos para procesar")
            sys.exit(1)

        # Procesar cada CVE en paralelo para todos los microservicios
        for cve_idx, cve in enumerate(all_cves, 1):
            print(f"\n{'─' * 60}")
            print(f"🔒 CVE {cve_idx}/{len(all_cves)}: {cve.cve_id} [{cve.severity}]")
            print(f"   {cve.group}:{cve.library_name}")
            print(f"   {cve.current_version} → {cve.fixed_version}")
            print(f"{'─' * 60}")

            # Procesar este CVE en todos los microservicios en paralelo
            results_by_ms = {}

            with ThreadPoolExecutor(max_workers=args.max_workers) as executor:
                # Crear tareas para cada microservicio
                future_to_ms = {
                    executor.submit(
                        process_single_cve_for_microservice,
                        ms_path,
                        cve,
                        cve_file,
                        not args.apply,  # dry_run
                        validate
                    ): ms_path for ms_path in valid_microservices
                }

                # Recolectar resultados
                for future in as_completed(future_to_ms):
                    ms_path = future_to_ms[future]
                    try:
                        result = future.result()
                        results_by_ms[ms_path.name] = result

                        status_icon = "✅" if result.get('success') else "❌"
                        print(f"   {status_icon} {ms_path.name}: {result.get('message', '')}")

                        if not result.get('success'):
                            any_failed = True

                    except Exception as e:
                        print(f"   ❌ {ms_path.name}: Error - {str(e)}")
                        results_by_ms[ms_path.name] = {"success": False, "error": str(e)}
                        any_failed = True

            # Guardar resultados parciales por CVE
            for ms_name, result in results_by_ms.items():
                if ms_name not in all_results:
                    all_results[ms_name] = {
                        'total_cves': 0,
                        'updates': [],
                        'compilation_success': True,
                        'rollback_performed': False,
                        'dry_run': not args.apply,
                        'cve_results': {}
                    }

                all_results[ms_name]['total_cves'] += 1
                all_results[ms_name]['cve_results'][cve.cve_id] = result

                if result.get('updates'):
                    all_results[ms_name]['updates'].extend(result['updates'])

                if not result.get('compilation_success', True):
                    all_results[ms_name]['compilation_success'] = False

                if result.get('rollback_performed'):
                    all_results[ms_name]['rollback_performed'] = True

        # Guardar reportes individuales
        print("\n" + "=" * 60)
        print("💾 Guardando reportes individuales...")
        print("=" * 60)

        for ms_path in valid_microservices:
            if ms_path.name in all_results:
                report_file = ms_path / "cve_resolver_report.json"
                with open(report_file, 'w', encoding='utf-8') as f:
                    json.dump(all_results[ms_path.name], f, indent=2)
                print(f"   📄 {ms_path.name}/cve_resolver_report.json")

    # ====================================================================================
    # Modo Secuencial Original (o single microservicio)
    # ====================================================================================
    else:
        print("=" * 60)
        print(f"🚀 CVE Resolver Agent v1.5.2 - Modo {'Secuencial' if len(microservices) > 1 else 'Single'}")
        print(f"🔧 Procesando {len(microservices)} microservicio(s) x {len(all_cves)} CVE(s)")
        print("=" * 60)

        for i, project_path in enumerate(microservices, 1):
            print(f"\n{'─' * 60}")
            print(f"📦 Microservicio {i}/{len(microservices)}: {project_path.name}")
            print(f"📁 Ruta: {project_path.absolute()}")
            print(f"{'─' * 60}")

            if not project_path.exists():
                print(f"⚠️  Saltando: Proyecto no existe: {project_path}")
                all_results[project_path.name] = {"error": "Proyecto no encontrado"}
                any_failed = True
                continue

            if not (project_path / "build.gradle").exists():
                print(f"⚠️  Saltando: No es un proyecto Gradle (falta build.gradle)")
                all_results[project_path.name] = {"error": "No es proyecto Gradle"}
                any_failed = True
                continue

            try:
                agent = CVEResolverAgent(project_path, cve_file, dry_run=not args.apply, validate=validate)
                results = agent.run()
                all_results[project_path.name] = results

                # Guardar reporte individual
                report_file = project_path / "cve_resolver_report.json"
                with open(report_file, 'w', encoding='utf-8') as f:
                    json.dump(results, f, indent=2)
                print(f"\n📄 Reporte individual: {report_file}")

                # Verificar si hubo fallo
                if args.apply and results.get('compilation_success') is False and not results.get('rollback_performed'):
                    any_failed = True

            except Exception as e:
                print(f"\n❌ Error procesando {project_path.name}: {str(e)}")
                all_results[project_path.name] = {"error": str(e)}
                any_failed = True

    # Reporte consolidado
    print("\n" + "=" * 60)
    print("📊 RESUMEN CONSOLIDADO")
    print("=" * 60)

    total_cves = sum(r.get('total_cves', 0) for r in all_results.values() if 'error' not in r)
    total_updates = sum(len(r.get('updates', [])) for r in all_results.values() if 'error' not in r)
    successful_compilations = sum(1 for r in all_results.values() if r.get('compilation_success') is True)
    failed_compilations = sum(1 for r in all_results.values() if r.get('compilation_success') is False)
    rollbacks = sum(1 for r in all_results.values() if r.get('rollback_performed') is True)

    print(f"\n✅ Microservicios procesados: {len(microservices)}")
    print(f"📋 Total CVEs encontrados: {total_cves}")
    print(f"🔧 Total actualizaciones: {total_updates}")

    if args.apply:
        print(f"\n🔨 Compilaciones exitosas: {successful_compilations}")
        print(f"❌ Compilaciones fallidas: {failed_compilations}")
        if rollbacks > 0:
            print(f"↩️  Rollbacks ejecutados: {rollbacks}")

    # Guardar reporte consolidado
    consolidated_report = {
        'timestamp': datetime.now().isoformat(),
        'mode': 'monorepo' if (args.microservices or args.folder) else 'single',
        'microservices_processed': len(microservices),
        'microservice_names': [ms.name for ms in microservices],
        'summary': {
            'total_cves': total_cves,
            'total_updates': total_updates,
            'successful_compilations': successful_compilations if args.apply else None,
            'failed_compilations': failed_compilations if args.apply else None,
            'rollbacks': rollbacks if args.apply else None
        },
        'results': all_results
    }

    # Guardar en la carpeta raíz (folder, project_path o directorio actual)
    if args.folder:
        report_base_path = Path(args.folder)
    elif args.microservices:
        report_base_path = microservices[0].parent if microservices else Path(".")
    else:
        report_base_path = Path(args.project_path)

    consolidated_file = report_base_path / "cve_resolver_consolidated_report.json"
    with open(consolidated_file, 'w', encoding='utf-8') as f:
        json.dump(consolidated_report, f, indent=2)
    print(f"\n📄 Reporte consolidado: {consolidated_file}")

    # Lógica de commit automático
    if args.commit:
        if not args.apply:
            print("\n⚠️  --commit requiere --apply. Ignorando commit.")
        else:
            print("\n" + "=" * 60)
            print("📝 GIT COMMIT AUTOMÁTICO")
            print("=" * 60)

            # Determinar el path base para el commit (debe ser un repo git)
            if args.folder:
                git_base_path = Path(args.folder)
            elif args.microservices:
                git_base_path = microservices[0].parent if microservices else Path(".")
            else:
                git_base_path = Path(args.project_path)

            git_manager = GitCommitManager(git_base_path)

            # Verificar si es un repositorio git
            if not git_manager.is_git_repository():
                print(f"⚠️  {git_base_path} no es un repositorio git. No se creará commit.")
            else:
                # Verificar si hay cambios
                if not git_manager.has_changes():
                    print("ℹ️  No hay cambios para commitear.")
                else:
                    # Generar nombre de rama
                    branch_name = git_manager.generate_branch_name()
                    print(f"\n🌿 Creando rama: {branch_name}")

                    # Crear rama
                    success, msg = git_manager.create_branch(branch_name)
                    if success:
                        print(f"   ✅ {msg}")
                    else:
                        print(f"   ❌ {msg}")
                        print("   Continuando sin cambio de rama...")

                    # Agregar cambios
                    print("\n📦 Agregando cambios al staging area...")
                    success, msg = git_manager.add_all_changes()
                    if success:
                        print(f"   ✅ {msg}")
                    else:
                        print(f"   ❌ {msg}")

                    # Cargar CVEs para el mensaje de commit
                    processor = SnykCVEProcessor(cve_file)
                    try:
                        all_cves = processor.load_cves()
                    except Exception:
                        all_cves = []

                    # Generar mensaje de commit
                    # Usar el primer microservicio con actualizaciones exitosas
                    commit_message = None
                    for ms_name, results in all_results.items():
                        if 'error' not in results and results.get('updates'):
                            commit_message = git_manager.generate_commit_message(results, ms_name, all_cves)
                            break

                    if not commit_message:
                        # Mensaje genérico si no hay detalles específicos
                        commit_message = f"security: fix vulnerabilities\n\nFixed CVEs in {len(microservices)} microservice(s)\n\nGenerated by CVE"

                    print("\n💬 Creando commit...")
                    success, msg = git_manager.commit(commit_message)
                    if success:
                        print(f"   ✅ {msg}")
                        print(f"\n📋 Mensaje de commit:")
                        print(f"{'─' * 60}")
                        print(commit_message[:500] + "..." if len(commit_message) > 500 else commit_message)
                        print(f"{'─' * 60}")
                        print(f"\n🎉 Commit creado exitosamente en rama: {branch_name}")
                        print(f"   Para subir los cambios:")
                        print(f"   git push origin {branch_name}")
                    else:
                        print(f"   ❌ {msg}")

    # Calcular y mostrar tiempo de ejecución
    end_time = time.time()
    elapsed_seconds = end_time - start_time

    # Formatear tiempo de ejecución
    if elapsed_seconds < 60:
        time_str = f"{elapsed_seconds:.2f} segundos"
    elif elapsed_seconds < 3600:
        minutes = int(elapsed_seconds // 60)
        seconds = int(elapsed_seconds % 60)
        time_str = f"{minutes}m {seconds}s"
    else:
        hours = int(elapsed_seconds // 3600)
        minutes = int((elapsed_seconds % 3600) // 60)
        time_str = f"{hours}h {minutes}m"

    print("\n" + "=" * 60)
    print(f"⏱️  Tiempo de ejecución: {time_str}")
    print("=" * 60)

    # Salir con error si hubo fallos
    if any_failed:
        print("\n❌ Algunos microservicios tuvieron errores. Revisa el reporte consolidado.")
        sys.exit(1)


if __name__ == "__main__":
    main()
