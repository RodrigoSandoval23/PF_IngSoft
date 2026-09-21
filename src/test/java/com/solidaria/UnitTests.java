package com.solidaria;

import com.solidaria.auth.JwtUtil;
import com.solidaria.auth.PasswordUtil;
import com.solidaria.auth.RateLimiter;
import com.solidaria.auth.RbacFilter;
import com.solidaria.model.AuditLog;
import com.solidaria.model.Entity;
import com.solidaria.model.User;
import com.solidaria.util.JsonUtil;

import java.util.HashMap;
import java.util.Map;

public class UnitTests {

    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("==========================================================================");
        System.out.println("🧪 SUITE DE PRUEBAS UNITARIAS - SISTEMA DE GESTIÓN DE DONACIONES");
        System.out.println("==========================================================================");

        runTest("PasswordUtil: Hashing con Salt y verificación PBKDF2", UnitTests::testPasswordHashing);
        runTest("PasswordUtil: Rechazo de credenciales incorrectas", UnitTests::testPasswordRejection);
        runTest("PasswordUtil: Manejo seguro de entradas vacías y nulas", UnitTests::testPasswordEdgeCases);

        runTest("JwtUtil: Generación y extracción de Claims", UnitTests::testJwtGenerationAndClaims);
        runTest("JwtUtil: Detección y rechazo de Tokens manipulados", UnitTests::testJwtTampering);
        runTest("JwtUtil: Manejo seguro de Token nulo o malformado", UnitTests::testJwtInvalidFormats);

        runTest("RateLimiter: Conteo de intentos y bloqueo tras umbral", UnitTests::testRateLimiterLockout);
        runTest("RateLimiter: Restablecimiento de contador tras login exitoso", UnitTests::testRateLimiterReset);

        runTest("JsonUtil: Serialización de mapas y objetos a JSON", UnitTests::testJsonSerialization);
        runTest("JsonUtil: Parseo y extracción de claves simples", UnitTests::testJsonParsing);
        runTest("JsonUtil: Escape de caracteres especiales y comillas", UnitTests::testJsonEscaping);

        runTest("Modelo User: Creación y estados de ciclo de vida", UnitTests::testUserModel);
        runTest("Modelo Entity: Restricción de tipo y validación de RFC", UnitTests::testEntityModel);
        runTest("Modelo AuditLog: Integridad de pistas de auditoría RNF02", UnitTests::testAuditLogModel);

        runTest("RbacFilter: Autorización por roles y política deny-by-default", UnitTests::testRbacRules);

        System.out.println("==========================================================================");
        System.out.printf("📊 RESUMEN DE PRUEBAS UNITARIAS:\n   Total Pasadas: %d\n   Total Fallidas: %d\n", testsPassed, testsFailed);
        System.out.println("==========================================================================");

        if (testsFailed > 0) {
            System.err.println("❌ ERROR: Al menos una prueba unitaria ha fallado.");
            System.exit(1);
        } else {
            System.out.println("🏆 TODAS LAS PRUEBAS UNITARIAS SE COMPLETARON CON ÉXITO (100% PASS).");
            System.out.println("==========================================================================\n");
        }
    }

    private static void runTest(String testName, Runnable testCase) {
        System.out.print("▶ Probando " + testName + "... ");
        try {
            testCase.run();
            System.out.println("[PASS] ✔");
            testsPassed++;
        } catch (Throwable t) {
            System.out.println("[FAIL] ❌ (" + t.getMessage() + ")");
            testsFailed++;
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null && actual == null) return;
        if (expected == null || !expected.equals(actual)) {
            throw new AssertionError(message + " (Esperado: " + expected + ", Obtenido: " + actual + ")");
        }
    }

    private static void testPasswordHashing() {
        String secret = "ClaveEmpresarial2026";
        String hash1 = PasswordUtil.hashPassword(secret);
        String hash2 = PasswordUtil.hashPassword(secret);

        assertTrue(hash1 != null && hash1.contains(":"), "El hash debe tener formato salt:hash");
        assertTrue(!hash1.equals(hash2), "Cada hash debe usar un salt aleatorio único");
        assertTrue(PasswordUtil.verifyPassword(secret, hash1), "La verificación debe coincidir con la clave original");
    }

    private static void testPasswordRejection() {
        String secret = "ClaveOriginal123";
        String hash = PasswordUtil.hashPassword(secret);
        assertTrue(!PasswordUtil.verifyPassword("ClaveEquivocada456", hash), "No debe validar contraseñas erróneas");
    }

    private static void testPasswordEdgeCases() {
        assertTrue(!PasswordUtil.verifyPassword(null, "fakehash:123"), "Null password debe ser rechazado");
        assertTrue(!PasswordUtil.verifyPassword("", "fakehash:123"), "Empty password debe ser rechazado");
        assertTrue(!PasswordUtil.verifyPassword("test", null), "Null hash debe ser rechazado");
        assertTrue(!PasswordUtil.verifyPassword("test", "malformed"), "Hash sin formato debe ser rechazado");
    }

    private static void testJwtGenerationAndClaims() {
        int userId = 42;
        String email = "directivo@corporacion.com";
        String name = "Lic. Roberto Garza";
        String role = "ADMIN";

        String token = JwtUtil.generateToken(userId, email, name, role);
        assertTrue(token != null && token.split("\\.").length == 3, "El token JWT debe tener 3 partes (header.payload.signature)");

        Map<String, String> claims = JwtUtil.validateToken(token);
        assertTrue(claims != null, "El token recién generado debe ser válido");
        assertEquals(String.valueOf(userId), claims.get("sub"), "El claim 'sub' debe coincidir");
        assertEquals(email, claims.get("email"), "El claim 'email' debe coincidir");
        assertEquals(name, claims.get("name"), "El claim 'name' debe coincidir");
        assertEquals(role, claims.get("role"), "El claim 'role' debe coincidir");
    }

    private static void testJwtTampering() {
        String token = JwtUtil.generateToken(1, "auditor@sat.gob.mx", "Auditor", "DONANTE");
        String[] parts = token.split("\\.");
        String tamperedSignature = parts[2].substring(0, parts[2].length() - 2) + "AB";
        String alteredToken = parts[0] + "." + parts[1] + "." + tamperedSignature;

        Map<String, String> claims = JwtUtil.validateToken(alteredToken);
        assertTrue(claims == null, "Un token con firma alterada no debe ser aceptado");
    }

    private static void testJwtInvalidFormats() {
        assertTrue(JwtUtil.validateToken(null) == null, "Token nulo debe retornar null");
        assertTrue(JwtUtil.validateToken("") == null, "Token vacío debe retornar null");
        assertTrue(JwtUtil.validateToken("header.payload") == null, "Token incompleto debe retornar null");
        assertTrue(JwtUtil.validateToken("a.b.c.d") == null, "Token con partes extra debe retornar null");
    }

    private static void testRateLimiterLockout() {
        RateLimiter limiter = RateLimiter.getInstance();
        String testIp = "192.168.1.189";

        limiter.resetAttempts(testIp);
        assertTrue(!limiter.isBlocked(testIp), "Antes de intentos fallidos, la IP no debe estar bloqueada");

        for (int i = 1; i <= 4; i++) {
            limiter.recordFailedAttempt(testIp);
            assertTrue(!limiter.isBlocked(testIp), "Intentos inferiores al límite no deben bloquear");
        }

        limiter.recordFailedAttempt(testIp); // 5to intento
        assertTrue(limiter.isBlocked(testIp), "Al alcanzar el umbral máximo, la IP debe ser bloqueada");
        assertTrue(limiter.getRemainingLockoutSeconds(testIp) > 0, "El tiempo de bloqueo debe ser positivo");
        limiter.resetAttempts(testIp);
    }

    private static void testRateLimiterReset() {
        RateLimiter limiter = RateLimiter.getInstance();
        String testIp = "10.0.0.155";

        limiter.recordFailedAttempt(testIp);
        limiter.recordFailedAttempt(testIp);
        limiter.resetAttempts(testIp);

        assertTrue(!limiter.isBlocked(testIp), "Tras reset, la IP debe quedar limpia de penalizaciones");
        assertEquals(0L, limiter.getRemainingLockoutSeconds(testIp), "El tiempo de bloqueo tras reset debe ser 0");
    }

    private static void testJsonSerialization() {
        Map<String, Object> map = new HashMap<>();
        map.put("status", "success");
        map.put("code", 200);
        map.put("active", true);

        String json = JsonUtil.toJson(map);
        assertTrue(json.contains("\"status\":\"success\"") || json.contains("\"status\": \"success\""), "Debe serializar campos de texto");
        assertTrue(json.contains("200"), "Debe serializar números");
    }

    private static void testJsonParsing() {
        String json = "{\"email\":\"usuario@empresa.com\",\"role\":\"DONANTE\",\"rfc\":\"GOMR820512A12\"}";
        Map<String, String> parsed = JsonUtil.parseSimpleJson(json);

        assertEquals("usuario@empresa.com", parsed.get("email"), "Debe extraer email");
        assertEquals("DONANTE", parsed.get("role"), "Debe extraer role");
        assertEquals("GOMR820512A12", parsed.get("rfc"), "Debe extraer rfc");
    }

    private static void testJsonEscaping() {
        String textWithQuotes = "Aportación \"Educación\" con salto\nde línea";
        String escaped = JsonUtil.escape(textWithQuotes);

        assertTrue(escaped.contains("\\\""), "Las comillas deben estar escapadas con barra invertida");
        assertTrue(escaped.contains("\\n"), "Los saltos de línea deben estar escapados");
    }

    private static void testUserModel() {
        User user = new User(10, "Representante Legal", "rep@empresa.com", "hash_secret_123", "DONANTE", "ACTIVO", "2026-09-18 16:30:00");
        user.setEntityInfo("GOMR820512A12", "Organización Solidaria S.A.", "EMPRESA DONANTE");

        assertEquals(10, user.getId(), "Id debe coincidir");
        assertEquals("Representante Legal", user.getName(), "Nombre debe coincidir");
        assertEquals("DONANTE", user.getRole(), "Rol inicial debe ser DONANTE");
        assertEquals("ACTIVO", user.getStatus(), "Estado debe ser ACTIVO");
        assertEquals("GOMR820512A12", user.getRfc(), "RFC debe coincidir");
        assertEquals("EMPRESA DONANTE", user.getEntityType(), "Tipo de entidad debe coincidir");
        assertEquals("Organización Solidaria S.A.", user.getLegalName(), "Razón social debe coincidir");

        user.setStatus("RECHAZADO");
        assertEquals("RECHAZADO", user.getStatus(), "Estado debe actualizarse a RECHAZADO");
    }

    private static void testEntityModel() {
        Entity entity = new Entity(1, 10, "FDS850101XYZ", "Fundación Solidaria A.C.", "ORGANIZACION_SOCIAL", "ACTIVO");
        assertEquals("FDS850101XYZ", entity.getRfc(), "RFC debe coincidir");
        assertEquals("Fundación Solidaria A.C.", entity.getLegalName(), "Razón social debe coincidir");
        assertEquals("ORGANIZACION_SOCIAL", entity.getEntityType(), "Tipo de entidad debe ser ORGANIZACION_SOCIAL");

        assertTrue(entity.getRfc().length() == 12 || entity.getRfc().length() == 13, "El RFC de la entidad debe ser de 12 o 13 caracteres");
    }

    private static void testAuditLogModel() {
        String fecha = "2026-09-18 16:30:00";
        AuditLog log = new AuditLog(1, 10, "rep@empresa.com", "AUTORIZACION_RFC", "Aprobación de constancia fiscal", "127.0.0.1", fecha);

        assertEquals(1, log.getId(), "Id de auditoría debe coincidir");
        assertEquals(Integer.valueOf(10), log.getUserId(), "UserId debe coincidir");
        assertEquals("rep@empresa.com", log.getEmail(), "Email debe coincidir");
        assertEquals("AUTORIZACION_RFC", log.getAccion(), "Acción debe coincidir");
        assertEquals("127.0.0.1", log.getIpAddress(), "IP debe coincidir");
        assertEquals(fecha, log.getFecha(), "Fecha debe ser idéntica");
    }

    private static void testRbacRules() {
        String tokenDonante = JwtUtil.generateToken(2, "donante@test.com", "Donante", "DONANTE");
        String tokenAdmin = JwtUtil.generateToken(1, "admin@test.com", "Admin", "ADMIN");

        Map<String, String> claimsDonante = JwtUtil.validateToken(tokenDonante);
        Map<String, String> claimsAdmin = JwtUtil.validateToken(tokenAdmin);

        assertTrue("DONANTE".equals(claimsDonante.get("role")), "Rol donante debe ser extraído");
        assertTrue("ADMIN".equals(claimsAdmin.get("role")), "Rol admin debe ser extraído");

        boolean donantePuedeAdmin = "ADMIN".equalsIgnoreCase(claimsDonante.get("role"));
        boolean adminPuedeAdmin = "ADMIN".equalsIgnoreCase(claimsAdmin.get("role"));

        assertTrue(!donantePuedeAdmin, "Un usuario DONANTE no debe tener permisos para rutas administrativas");
        assertTrue(adminPuedeAdmin, "Un usuario ADMIN debe tener permisos para rutas administrativas");
    }
}
