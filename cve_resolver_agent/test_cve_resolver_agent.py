#!/usr/bin/env python3
"""
Tests unitarios para CVE Resolver Agent v1.0.0

Ejecución:
    python3 -m pytest test_cve_resolver_agent.py -v
    python3 -m pytest test_cve_resolver_agent.py -v --tb=short
    python3 test_cve_resolver_agent.py  # Ejecuta tests básicos sin pytest

Requisitos:
    - Python 3.8+
    - pytest (opcional, para reporte detallado)
"""

import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

# Importar el agente directamente (mismo directorio)
# Usar import absoluto para evitar conflictos con nombre de carpeta
import importlib.util
spec = importlib.util.spec_from_file_location("cve_resolver_agent_module", str(Path(__file__).parent / "cve_resolver_agent.py"))
cve_resolver_agent_module = importlib.util.module_from_spec(spec)
sys.modules["cve_resolver_agent_module"] = cve_resolver_agent_module
spec.loader.exec_module(cve_resolver_agent_module)

# Importar clases del módulo
CVEEntry = cve_resolver_agent_module.CVEEntry
SnykCVEProcessor = cve_resolver_agent_module.SnykCVEProcessor
GradleCVEUpdater = cve_resolver_agent_module.GradleCVEUpdater
BackupManager = cve_resolver_agent_module.BackupManager
GitCommitManager = cve_resolver_agent_module.GitCommitManager
discover_microservices = cve_resolver_agent_module.discover_microservices
parse_microservices = cve_resolver_agent_module.parse_microservices


class TestDiscoverMicroservices(unittest.TestCase):
    """Tests para la función discover_microservices()"""

    def setUp(self):
        self.temp_dir = tempfile.mkdtemp()
        self.temp_path = Path(self.temp_dir)

    def tearDown(self):
        shutil.rmtree(self.temp_dir)

    def test_no_microservices_found(self):
        """Cuando no hay carpetas ms_*"""
        result = discover_microservices(self.temp_path)
        self.assertEqual(result, [])

    def test_discover_single_microservice(self):
        """Detecta un único microservicio con build.gradle"""
        ms_dir = self.temp_path / "ms_auth"
        ms_dir.mkdir()
        (ms_dir / "build.gradle").write_text("// gradle file")

        result = discover_microservices(self.temp_path)
        self.assertEqual(len(result), 1)
        self.assertEqual(result[0].name, "ms_auth")

    def test_discover_multiple_microservices(self):
        """Detecta múltiples microservicios con prefijo ms_"""
        ms_names = ["ms_auth", "ms_upload", "ms_core", "ms_notifications"]
        for name in ms_names:
            ms_dir = self.temp_path / name
            ms_dir.mkdir()
            (ms_dir / "build.gradle").write_text("// gradle file")

        result = discover_microservices(self.temp_path)
        self.assertEqual(len(result), 4)
        result_names = [p.name for p in result]
        self.assertEqual(sorted(result_names), sorted(ms_names))

    def test_ignore_non_ms_folders(self):
        """Ignora carpetas que no comienzan con ms_"""
        # Crear carpetas
        (self.temp_path / "ms_auth").mkdir()
        (self.temp_path / "ms_auth" / "build.gradle").write_text("// gradle file")
        (self.temp_path / "otro_proyecto").mkdir()
        (self.temp_path / "otro_proyecto" / "build.gradle").write_text("// gradle file")
        (self.temp_path / "docs").mkdir()
        (self.temp_path / "config").mkdir()

        result = discover_microservices(self.temp_path)
        self.assertEqual(len(result), 1)
        self.assertEqual(result[0].name, "ms_auth")

    def test_ignore_ms_without_build_gradle(self):
        """Ignora carpetas ms_* que no tengan build.gradle"""
        (self.temp_path / "ms_auth").mkdir()
        (self.temp_path / "ms_auth" / "build.gradle").write_text("// gradle file")
        (self.temp_path / "ms_incomplete").mkdir()
        # Sin build.gradle

        result = discover_microservices(self.temp_path)
        self.assertEqual(len(result), 1)
        self.assertEqual(result[0].name, "ms_auth")

    def test_sorted_result(self):
        """Los resultados están ordenados alfabéticamente"""
        ms_names = ["ms_zebra", "ms_alpha", "ms_beta"]
        for name in ms_names:
            ms_dir = self.temp_path / name
            ms_dir.mkdir()
            (ms_dir / "build.gradle").write_text("// gradle file")

        result = discover_microservices(self.temp_path)
        result_names = [p.name for p in result]
        self.assertEqual(result_names, ["ms_alpha", "ms_beta", "ms_zebra"])

    def test_nonexistent_folder(self):
        """Maneja carpetas que no existen"""
        result = discover_microservices(Path("/ruta/que/no/existe"))
        self.assertEqual(result, [])


class TestParseMicroservices(unittest.TestCase):
    """Tests para la función parse_microservices()"""

    def setUp(self):
        self.temp_dir = tempfile.mkdtemp()
        self.temp_path = Path(self.temp_dir)

        # Crear estructura de microservicios
        for name in ["ms_auth", "ms_upload", "ms_core"]:
            ms_dir = self.temp_path / name
            ms_dir.mkdir()
            (ms_dir / "build.gradle").write_text("// gradle file")

    def tearDown(self):
        shutil.rmtree(self.temp_dir)

    def test_mode_legacy_single_project(self):
        """Modo legacy: project_path como microservicio único"""
        result = parse_microservices(None, None, str(self.temp_path / "ms_auth"))
        self.assertEqual(len(result), 1)
        self.assertEqual(result[0].name, "ms_auth")

    def test_mode_ms_without_folder(self):
        """Modo --ms sin --folder: busca en directorio actual"""
        # Cambiar al directorio temporal para que encuentre las carpetas
        original_cwd = os.getcwd()
        os.chdir(self.temp_dir)
        try:
            result = parse_microservices("ms_auth,ms_upload", None, ".")
            self.assertEqual(len(result), 2)
            names = [p.name for p in result]
            self.assertEqual(sorted(names), ["ms_auth", "ms_upload"])
        finally:
            os.chdir(original_cwd)

    def test_mode_ms_with_folder(self):
        """Modo --ms con --folder: busca en la carpeta especificada"""
        result = parse_microservices("ms_auth,ms_core", str(self.temp_path), ".")
        self.assertEqual(len(result), 2)
        names = [p.name for p in result]
        self.assertEqual(sorted(names), ["ms_auth", "ms_core"])

    def test_mode_folder_without_ms(self):
        """Modo --folder sin --ms: auto-detecta ms_*"""
        result = parse_microservices(None, str(self.temp_path), ".")
        self.assertEqual(len(result), 3)
        names = [p.name for p in result]
        self.assertEqual(sorted(names), ["ms_auth", "ms_core", "ms_upload"])

    def test_mode_ms_ignores_nonexistent(self):
        """Modo --ms ignora microservicios que no existen"""
        result = parse_microservices("ms_auth,ms_inexistente", str(self.temp_path), ".")
        self.assertEqual(len(result), 1)
        self.assertEqual(result[0].name, "ms_auth")

    def test_empty_result_when_nothing_found(self):
        """Retorna lista vacía cuando no encuentra nada"""
        empty_dir = self.temp_path / "empty"
        empty_dir.mkdir()
        result = parse_microservices(None, str(empty_dir), ".")
        self.assertEqual(result, [])

    def test_ms_with_spaces(self):
        """Maneja espacios en la lista de microservicios"""
        result = parse_microservices("ms_auth, ms_upload , ms_core", str(self.temp_path), ".")
        self.assertEqual(len(result), 3)


class TestSnykCVEProcessor(unittest.TestCase):
    """Tests para SnykCVEProcessor"""

    def setUp(self):
        self.temp_dir = tempfile.mkdtemp()
        self.temp_path = Path(self.temp_dir)

    def tearDown(self):
        shutil.rmtree(self.temp_dir)

    def create_cve_file(self, data):
        """Helper para crear archivo CVE temporal"""
        cve_file = self.temp_path / "test_cves.json"
        cve_file.write_text(json.dumps(data))
        return cve_file

    def test_load_valid_cves(self):
        """Carga CVEs válidos correctamente"""
        data = {
            "cves": [
                {
                    "cve_id": "CVE-2024-1234",
                    "library_name": "netty-codec-http",
                    "group": "io.netty",
                    "current_version": "4.1.86.Final",
                    "fixed_version": "4.1.132.Final",
                    "severity": "CRITICAL"
                }
            ]
        }
        cve_file = self.create_cve_file(data)
        processor = SnykCVEProcessor(cve_file)
        cves = processor.load_cves()

        self.assertEqual(len(cves), 1)
        self.assertEqual(cves[0].cve_id, "CVE-2024-1234")
        self.assertEqual(cves[0].severity, "CRITICAL")

    def test_skip_empty_cve_id(self):
        """Omite CVEs sin ID"""
        data = {
            "cves": [
                {"cve_id": "", "library_name": "test", "fixed_version": "1.0"},
                {"cve_id": "CVE-2024-5678", "library_name": "test", "fixed_version": "1.0"}
            ]
        }
        cve_file = self.create_cve_file(data)
        processor = SnykCVEProcessor(cve_file)
        cves = processor.load_cves()

        self.assertEqual(len(cves), 1)
        self.assertEqual(cves[0].cve_id, "CVE-2024-5678")

    def test_skip_empty_fixed_version(self):
        """Omite CVEs sin versión fija"""
        data = {
            "cves": [
                {"cve_id": "CVE-2024-9999", "library_name": "test", "fixed_version": ""},
                {"cve_id": "CVE-2024-1111", "library_name": "test", "fixed_version": "1.2.3"}
            ]
        }
        cve_file = self.create_cve_file(data)
        processor = SnykCVEProcessor(cve_file)
        cves = processor.load_cves()

        self.assertEqual(len(cves), 1)
        self.assertEqual(cves[0].cve_id, "CVE-2024-1111")

    def test_normalize_severity(self):
        """Normaliza severidad a mayúsculas"""
        data = {
            "cves": [
                {"cve_id": "CVE-1", "library_name": "test", "fixed_version": "1.0", "severity": "critical"},
                {"cve_id": "CVE-2", "library_name": "test", "fixed_version": "1.0", "severity": "HIGH"},
                {"cve_id": "CVE-3", "library_name": "test", "fixed_version": "1.0"}
            ]
        }
        cve_file = self.create_cve_file(data)
        processor = SnykCVEProcessor(cve_file)
        cves = processor.load_cves()

        severities = [c.severity for c in cves]
        self.assertIn("CRITICAL", severities)
        self.assertIn("HIGH", severities)
        self.assertIn("UNKNOWN", severities)

    def test_sort_by_severity(self):
        """Ordena CVEs por severidad (CRITICAL primero)"""
        data = {
            "cves": [
                {"cve_id": "CVE-LOW", "library_name": "test", "fixed_version": "1.0", "severity": "LOW"},
                {"cve_id": "CVE-CRIT", "library_name": "test", "fixed_version": "1.0", "severity": "CRITICAL"},
                {"cve_id": "CVE-MED", "library_name": "test", "fixed_version": "1.0", "severity": "MEDIUM"}
            ]
        }
        cve_file = self.create_cve_file(data)
        processor = SnykCVEProcessor(cve_file)
        cves = processor.load_cves()

        self.assertEqual(cves[0].cve_id, "CVE-CRIT")
        self.assertEqual(cves[1].cve_id, "CVE-MED")
        self.assertEqual(cves[2].cve_id, "CVE-LOW")


class TestBackupManager(unittest.TestCase):
    """Tests para BackupManager"""

    def setUp(self):
        self.temp_dir = tempfile.mkdtemp()
        self.temp_path = Path(self.temp_dir)
        self.project_path = self.temp_path / "project"
        self.project_path.mkdir()

    def tearDown(self):
        shutil.rmtree(self.temp_dir)

    def test_create_backup(self):
        """Crea backup correctamente"""
        test_file = self.project_path / "build.gradle"
        test_file.write_text("original content")

        manager = BackupManager(self.project_path)
        backup_path = manager.create_backup(test_file)

        self.assertTrue(backup_path.exists())
        self.assertEqual(backup_path.read_text(), "original content")
        self.assertIn("build.gradle", backup_path.name)

    def test_rollback(self):
        """Restaura archivos desde backup"""
        test_file = self.project_path / "build.gradle"
        test_file.write_text("original content")

        manager = BackupManager(self.project_path)
        manager.create_backup(test_file)

        # Modificar archivo
        test_file.write_text("modified content")
        self.assertEqual(test_file.read_text(), "modified content")

        # Rollback
        success, msg = manager.rollback()
        self.assertTrue(success)
        self.assertEqual(test_file.read_text(), "original content")

    def test_rollback_no_backups(self):
        """Rollback sin backups no falla"""
        manager = BackupManager(self.project_path)
        success, msg = manager.rollback()
        self.assertTrue(success)
        self.assertIn("No hay backups", msg)


class TestGradleCVEUpdater(unittest.TestCase):
    """Tests para GradleCVEUpdater"""

    def setUp(self):
        self.temp_dir = tempfile.mkdtemp()
        self.temp_path = Path(self.temp_dir)
        self.project_path = self.temp_path / "project"
        self.project_path.mkdir()

        # Crear build.gradle básico
        (self.project_path / "build.gradle").write_text("""
buildscript {
    ext {
        nettyVersion = '4.1.86.Final'
    }
}
""")

    def tearDown(self):
        shutil.rmtree(self.temp_dir)

    def test_find_all_build_gradle_files(self):
        """Encuentra todos los build.gradle incluyendo submódulos"""
        # Crear estructura de submódulos
        sub1 = self.project_path / "sub1"
        sub1.mkdir()
        (sub1 / "build.gradle").write_text("// sub1")

        sub2 = self.project_path / "sub2"
        sub2.mkdir()
        (sub2 / "build.gradle").write_text("// sub2")

        updater = GradleCVEUpdater(self.project_path, dry_run=True)
        files = updater._find_all_build_gradle_files()

        self.assertEqual(len(files), 3)  # raíz + 2 submódulos

    def test_get_version_variable(self):
        """Obtiene variable de versión correcta"""
        updater = GradleCVEUpdater(self.project_path, dry_run=True)

        self.assertEqual(updater._get_version_variable("io.netty"), "nettyVersion")
        self.assertEqual(updater._get_version_variable("org.springframework"), "springFrameworkVersion")
        self.assertIsNone(updater._get_version_variable("unknown.group"))

    def test_group_by_version_var(self):
        """Agrupa CVEs por variable de versión"""
        cves = [
            CVEEntry("CVE-1", "netty-codec", "io.netty", "1.0", "2.0", "HIGH"),
            CVEEntry("CVE-2", "netty-handler", "io.netty", "1.0", "2.0", "MEDIUM"),
            CVEEntry("CVE-3", "jackson-core", "com.fasterxml.jackson.core", "1.0", "2.0", "LOW"),
        ]

        updater = GradleCVEUpdater(self.project_path, dry_run=True)
        grouped = updater._group_by_version_var(cves)

        self.assertIn("nettyVersion", grouped)
        self.assertIn("jacksonVersion", grouped)
        self.assertEqual(len(grouped["nettyVersion"]), 2)
        self.assertEqual(len(grouped["jacksonVersion"]), 1)


class TestIntegration(unittest.TestCase):
    """Tests de integración que simulan escenarios completos"""

    def setUp(self):
        self.temp_dir = tempfile.mkdtemp()
        self.temp_path = Path(self.temp_dir)

        # Crear estructura de monorepo
        for name in ["ms_auth", "ms_upload"]:
            ms_dir = self.temp_path / name
            ms_dir.mkdir()
            (ms_dir / "build.gradle").write_text("""
buildscript {
    ext {
        nettyVersion = '4.1.86.Final'
    }
}

configurations.all {
    resolutionStrategy.eachDependency { details ->
        // existing
    }
}
""")
            (ms_dir / "dependencyMgmt.gradle").write_text("""configurations.all {
    resolutionStrategy.eachDependency { details ->
    }
}
""")

    def tearDown(self):
        shutil.rmtree(self.temp_dir)

    def test_monorepo_discovery(self):
        """Descubre microservicios en estructura de monorepo"""
        discovered = discover_microservices(self.temp_path)

        self.assertEqual(len(discovered), 2)
        names = [p.name for p in discovered]
        self.assertIn("ms_auth", names)
        self.assertIn("ms_upload", names)

    def test_parse_microservices_with_folder(self):
        """Parsea microservicios con carpeta raíz"""
        result = parse_microservices("ms_auth,ms_upload", str(self.temp_path), ".")

        self.assertEqual(len(result), 2)
        self.assertTrue(all(p.exists() for p in result))

    def test_cve_update_in_multiple_projects(self):
        """Actualiza CVEs en múltiples proyectos"""
        cves = [
            CVEEntry("CVE-2024-1234", "netty-codec-http", "io.netty", "4.1.86.Final", "4.1.132.Final", "CRITICAL")
        ]

        for ms_dir in [self.temp_path / "ms_auth", self.temp_path / "ms_upload"]:
            updater = GradleCVEUpdater(ms_dir, dry_run=False)
            results = updater.update(cves)

            # Verificar que se actualizó
            self.assertGreater(len(results['updated']), 0)

            # Verificar contenido actualizado
            content = (ms_dir / "build.gradle").read_text()
            self.assertIn("4.1.132.Final", content)

    def test_backup_created_for_each_project(self):
        """Crea backups separados para cada proyecto"""
        cves = [
            CVEEntry("CVE-2024-1234", "netty-codec-http", "io.netty", "4.1.86.Final", "4.1.132.Final", "CRITICAL")
        ]

        for ms_dir in [self.temp_path / "ms_auth", self.temp_path / "ms_upload"]:
            updater = GradleCVEUpdater(ms_dir, dry_run=False)
            updater.update(cves)

            # Verificar que existe el backup
            backup_dir = ms_dir / ".cve_resolver_backups"
            self.assertTrue(backup_dir.exists())
            backups = list(backup_dir.glob("*.backup"))
            self.assertGreater(len(backups), 0)


class TestGitCommitManager(unittest.TestCase):
    """Tests para GitCommitManager"""

    def setUp(self):
        self.temp_dir = tempfile.mkdtemp()
        self.temp_path = Path(self.temp_dir)

        # Inicializar un repositorio git
        subprocess.run(["git", "init"], cwd=self.temp_path, capture_output=True)
        subprocess.run(["git", "config", "user.email", "test@test.com"], cwd=self.temp_dir, capture_output=True)
        subprocess.run(["git", "config", "user.name", "Test User"], cwd=self.temp_dir, capture_output=True)

        # Crear archivo inicial y commit
        (self.temp_path / "README.md").write_text("# Test")
        subprocess.run(["git", "add", "."], cwd=self.temp_path, capture_output=True)
        subprocess.run(["git", "commit", "-m", "Initial commit"], cwd=self.temp_path, capture_output=True)

    def tearDown(self):
        shutil.rmtree(self.temp_dir)

    def test_is_git_repository(self):
        """Detecta si es un repositorio git"""
        manager = GitCommitManager(self.temp_path)
        self.assertTrue(manager.is_git_repository())

        # Crear un directorio sin git (en otra ubicación temporal)
        non_git_temp = tempfile.mkdtemp()
        try:
            non_manager = GitCommitManager(Path(non_git_temp))
            self.assertFalse(non_manager.is_git_repository())
        finally:
            shutil.rmtree(non_git_temp)

    def test_get_git_user_name(self):
        """Obtiene el nombre de usuario de git"""
        manager = GitCommitManager(self.temp_path)
        username = manager.get_git_user_name()
        self.assertIsNotNone(username)
        self.assertEqual(username, "test.user")

    def test_generate_branch_name(self):
        """Genera nombre de rama correctamente"""
        manager = GitCommitManager(self.temp_path)
        branch_name = manager.generate_branch_name("juan.perez")

        # Verificar formato: feature/fix_vulnerabilidad_DDMMYYYY_username
        self.assertTrue(branch_name.startswith("feature/fix_vulnerabilidad_"))
        self.assertIn("juan.perez", branch_name)

        # Verificar que la fecha tiene 8 dígitos
        date_part = branch_name.split("_")[-2]
        self.assertEqual(len(date_part), 8)

    def test_has_changes(self):
        """Detecta cambios sin commitear"""
        manager = GitCommitManager(self.temp_path)

        # Inicialmente no hay cambios
        self.assertFalse(manager.has_changes())

        # Crear un nuevo archivo
        (self.temp_path / "nuevo.txt").write_text("nuevo contenido")

        # Ahora debe detectar cambios
        self.assertTrue(manager.has_changes())

    def test_generate_commit_message(self):
        """Genera mensaje de commit descriptivo"""
        manager = GitCommitManager(self.temp_path)

        cves = [
            CVEEntry("CVE-2024-1234", "netty-codec-http", "io.netty", "4.1.86.Final", "4.1.132.Final", "CRITICAL"),
            CVEEntry("CVE-2024-5678", "jackson-core", "com.fasterxml.jackson.core", "2.15.0", "2.17.2", "HIGH")
        ]

        results = {
            'total_cves': 2,
            'updates': [
                {'file': 'build.gradle', 'variable': 'nettyVersion', 'cve': 'CVE-2024-1234'},
                {'file': 'build.gradle', 'variable': 'jacksonVersion', 'cve': 'CVE-2024-5678'}
            ],
            'compilation_success': True,
            'rollback_performed': False
        }

        message = manager.generate_commit_message(results, "ms_auth", cves)

        # Verificar que contiene información clave
        self.assertIn("security: fix vulnerabilities in ms_auth", message)
        self.assertIn("CVE-2024-1234", message)
        self.assertIn("CVE-2024-5678", message)
        self.assertIn("CRITICAL", message)
        self.assertIn("HIGH", message)
        self.assertIn("io.netty:netty-codec-http", message)
        self.assertIn("build.gradle", message)

    def test_generate_commit_message_empty_cves(self):
        """Genera mensaje de commit cuando no hay CVEs"""
        manager = GitCommitManager(self.temp_path)

        results = {
            'total_cves': 0,
            'updates': [],
            'compilation_success': True
        }

        message = manager.generate_commit_message(results, "ms_test", [])

        self.assertIn("security: fix vulnerabilities in ms_test", message)
        self.assertIn("Fixed 0 CVE(s)", message)


# Test runner básico para ejecución sin pytest
def run_basic_tests():
    """Ejecuta tests básicos sin necesidad de pytest"""
    print("=" * 60)
    print("CVE Resolver Agent - Tests Unitarios v1.0.0")
    print("=" * 60)

    loader = unittest.TestLoader()
    suite = unittest.TestSuite()

    # Agregar todas las clases de test
    suite.addTests(loader.loadTestsFromTestCase(TestDiscoverMicroservices))
    suite.addTests(loader.loadTestsFromTestCase(TestParseMicroservices))
    suite.addTests(loader.loadTestsFromTestCase(TestSnykCVEProcessor))
    suite.addTests(loader.loadTestsFromTestCase(TestBackupManager))
    suite.addTests(loader.loadTestsFromTestCase(TestGradleCVEUpdater))
    suite.addTests(loader.loadTestsFromTestCase(TestIntegration))
    suite.addTests(loader.loadTestsFromTestCase(TestGitCommitManager))

    runner = unittest.TextTestRunner(verbosity=2)
    result = runner.run(suite)

    print("\n" + "=" * 60)
    if result.wasSuccessful():
        print("✅ Todos los tests pasaron exitosamente")
    else:
        print(f"❌ Fallaron {len(result.failures)} test(s)")
    print("=" * 60)

    return result.wasSuccessful()


if __name__ == "__main__":
    success = run_basic_tests()
    sys.exit(0 if success else 1)
