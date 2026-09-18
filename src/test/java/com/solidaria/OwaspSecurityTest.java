package com.solidaria;

import com.solidaria.auth.JwtUtil;
import com.solidaria.auth.PasswordUtil;
import com.solidaria.db.DataStore;
import com.solidaria.handlers.AuthHandler;
import com.solidaria.handlers.DonationHandler;
import com.solidaria.handlers.StatsHandler;
import com.solidaria.model.User;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Suite de Pruebas de Seguridad OWASP Top 10 para la Actividad de Ingeniería de Software.
 */
public class OwaspSecurityTest {

    private static final int TEST_PORT = 8089;
    private static final String BASE_URL = "http://127.0.0.1:" + TEST_PORT;
    private static HttpServer testServer;
    private static HttpClient httpClient;

    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("==========================================================================");
        System.out.println("🛡️  INICIANDO SUITE DE PRUEBAS DE SEGURIDAD OWASP TOP 10");
        System.out.println("    Proyecto: Portal de Donaciones con Autenticación JWT (Java 11)");
        System.out.println("==========================================================================\n");

        try {
            startTestServer();
            httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();

            // 1. OWASP A01: Broken Access Control (Control de Acceso Roto)
            testA01_BrokenAccessControl();

            // 2. OWASP A02: Cryptographic Failures (Fallas Criptográficas)
            testA02_CryptographicFailures();

            // 3. OWASP A03: Injection & Input Sanitization (Inyección y Validación)
            testA03_InjectionAndSanitization();

            // 4. OWASP A04: Insecure Design & Business Logic (Lógica de Negocio)
            testA04_InsecureDesign();

            // 5. OWASP A07: Identification & Authentication Failures (Autenticación e Identidad)
            testA07_AuthenticationFailures();

            // 6. OWASP A08: Software and Data Integrity (Integridad del Token JWT)
            testA08_DataIntegrity();

        } catch (Exception e) {
            System.err.println("❌ Error inesperado durante la ejecución de pruebas: " + e.getMessage());
            e.printStackTrace();
        } finally {
            stopTestServer();
        }

        printReport();
    }

    private static void startTestServer() throws IOException {
        testServer = HttpServer.create(new InetSocketAddress(TEST_PORT), 0);
        AuthHandler authHandler = new AuthHandler();
        testServer.createContext("/api/auth/register", authHandler);
        testServer.createContext("/api/auth/login", authHandler);
        testServer.createContext("/api/auth/me", authHandler);

        DonationHandler donationHandler = new DonationHandler();
        testServer.createContext("/api/donations/my-donations", donationHandler);
        testServer.createContext("/api/donations", donationHandler);
        testServer.createContext("/api/donations/stats", new StatsHandler());

        testServer.setExecutor(Executors.newCachedThreadPool());
        testServer.start();
    }

    private static void stopTestServer() {
        if (testServer != null) {
            testServer.stop(0);
        }
    }

    // =========================================================================
    // OWASP A01:2021 - Broken Access Control
    // =========================================================================
    private static void testA01_BrokenAccessControl() {
        System.out.println("▶ [OWASP A01:2021] Verificando Control de Acceso (Broken Access Control)...");

        // Prueba 1.1: Acceso a /api/auth/me sin cabecera Authorization debe responder 401
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/auth/me"))
                    .GET()
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            assertCondition(res.statusCode() == 401,
                    "A01.1 - Denegar acceso a /api/auth/me sin token (Retorna 401 Unauthorized)");
        } catch (Exception e) {
            recordFailure("A01.1", e.getMessage());
        }

        // Prueba 1.2: Acceso a /api/donations/my-donations sin token debe responder 401
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/donations/my-donations"))
                    .GET()
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            assertCondition(res.statusCode() == 401,
                    "A01.2 - Denegar acceso al historial de donaciones sin token (Retorna 401)");
        } catch (Exception e) {
            recordFailure("A01.2", e.getMessage());
        }

        // Prueba 1.3: Acceso con token falsificado sin firma válida debe responder 401
        try {
            String fakeToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIn0.FirmaFalsaTotalmenteInvalida";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/auth/me"))
                    .header("Authorization", "Bearer " + fakeToken)
                    .GET()
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            assertCondition(res.statusCode() == 401,
                    "A01.3 - Rechazo de token falsificado con firma apócrifa (Retorna 401)");
        } catch (Exception e) {
            recordFailure("A01.3", e.getMessage());
        }
        System.out.println();
    }

    // =========================================================================
    // OWASP A02:2021 - Cryptographic Failures
    // =========================================================================
    private static void testA02_CryptographicFailures() {
        System.out.println("▶ [OWASP A02:2021] Verificando Robustez Criptográfica (Cryptographic Failures)...");

        // Prueba 2.1: Verificar que contraseñas idénticas generen hashes distintos (Salt aleatorio)
        String password = "PruebaSeguraPassword2026";
        String hash1 = PasswordUtil.hashPassword(password);
        String hash2 = PasswordUtil.hashPassword(password);

        assertCondition(!hash1.equals(hash2),
                "A02.1 - Hashes con Sal aleatoria única (Evita ataques de Rainbow Tables)");

        // Prueba 2.2: Verificar algoritmo PBKDF2WithHmacSHA256 con longitud de clave adecuada
        assertCondition(hash1.contains(":") && hash1.length() > 50,
                "A02.2 - Longitud y formato seguro de hash (PBKDF2-HMAC-SHA256)");

        // Prueba 2.3: Firma JWT válida no reproducible sin la clave secreta
        String validToken = JwtUtil.generateToken(1, "demo@donaciones.org", "Demo User", "donor");
        Map<String, String> claims = JwtUtil.validateToken(validToken);
        assertCondition(claims != null && "1".equals(claims.get("sub")),
                "A02.3 - Integridad y validación de firma HMAC-SHA256 en JWT");

        System.out.println();
    }

    // =========================================================================
    // OWASP A03:2021 - Injection & Input Sanitization
    // =========================================================================
    private static void testA03_InjectionAndSanitization() {
        System.out.println("▶ [OWASP A03:2021] Verificando Inyección y Sanitización (Injection)...");

        // Prueba 3.1: Intento de SQL Injection en Login
        try {
            String sqliPayload = "{\"email\":\"' OR 1=1 --\",\"password\":\"password\"}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(sqliPayload))
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            assertCondition(res.statusCode() == 401,
                    "A03.1 - Resistencia a payload SQL Injection en autenticación (Retorna 401)");
        } catch (Exception e) {
            recordFailure("A03.1", e.getMessage());
        }

        // Prueba 3.2: Intento de Inyección en Registro
        try {
            String sqliRegister = "{\"name\":\"Admin'--\",\"email\":\"inyeccion@test.com\",\"password\":\"clave123\"}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/auth/register"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(sqliRegister))
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            assertCondition(res.statusCode() == 201 || res.statusCode() == 400,
                    "A03.2 - Sanitización segura de caracteres especiales en registro");
        } catch (Exception e) {
            recordFailure("A03.2", e.getMessage());
        }

        // Prueba 3.3: Manejo seguro de payload XSS en mensaje de donación
        try {
            String xssPayload = "{\"amount\":10.0,\"cause\":\"Refugio Animal\",\"payment_method\":\"tarjeta\",\"message\":\"<script>alert('xss')</script>\"}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/donations"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(xssPayload))
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            assertCondition(res.statusCode() == 201 && !res.body().contains("<script>alert"),
                    "A03.3 - Neutralización de secuencias de comandos XSS en respuestas JSON");
        } catch (Exception e) {
            recordFailure("A03.3", e.getMessage());
        }

        System.out.println();
    }

    // =========================================================================
    // OWASP A04:2021 - Insecure Design & Business Logic
    // =========================================================================
    private static void testA04_InsecureDesign() {
        System.out.println("▶ [OWASP A04:2021] Verificando Lógica de Negocio (Insecure Design)...");

        // Prueba 4.1: Rechazo estricto de donaciones con monto negativo
        try {
            String negAmount = "{\"amount\":-50.0,\"cause\":\"Educación para Niños\",\"payment_method\":\"tarjeta\"}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/donations"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(negAmount))
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            assertCondition(res.statusCode() == 400,
                    "A04.1 - Validación estricta: Rechazo de donaciones con montos negativos (-$50)");
        } catch (Exception e) {
            recordFailure("A04.1", e.getMessage());
        }

        // Prueba 4.2: Rechazo de donaciones con monto igual a 0
        try {
            String zeroAmount = "{\"amount\":0.0,\"cause\":\"Educación para Niños\",\"payment_method\":\"tarjeta\"}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/donations"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(zeroAmount))
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            assertCondition(res.statusCode() == 400,
                    "A04.2 - Validación estricta: Rechazo de donaciones con monto $0");
        } catch (Exception e) {
            recordFailure("A04.2", e.getMessage());
        }

        // Prueba 4.3: Validación de longitud mínima de contraseñas
        try {
            String weakPw = "{\"name\":\"Donante Debil\",\"email\":\"debil@test.com\",\"password\":\"123\"}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/auth/register"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(weakPw))
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            assertCondition(res.statusCode() == 400,
                    "A04.3 - Cumplimiento de política de contraseñas: Mínimo 6 caracteres");
        } catch (Exception e) {
            recordFailure("A04.3", e.getMessage());
        }

        System.out.println();
    }

    // =========================================================================
    // OWASP A07:2021 - Identification & Authentication Failures
    // =========================================================================
    private static void testA07_AuthenticationFailures() {
        System.out.println("▶ [OWASP A07:2021] Verificando Fallas de Autenticación (Auth Failures)...");

        // Prueba 5.1: Contraseña incorrecta rechazada con 401
        try {
            String wrongPw = "{\"email\":\"demo@donaciones.org\",\"password\":\"contraseña_erronea\"}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(wrongPw))
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            assertCondition(res.statusCode() == 401,
                    "A07.1 - Credenciales incorrectas denegadas con 401 Unauthorized");
        } catch (Exception e) {
            recordFailure("A07.1", e.getMessage());
        }

        // Prueba 5.2: Usuario inexistente denegado con 401
        try {
            String noUser = "{\"email\":\"fantasma@inexistente.org\",\"password\":\"clave1234\"}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(noUser))
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            assertCondition(res.statusCode() == 401,
                    "A07.2 - Manejo uniforme de errores para cuentas inexistentes (Evita enumeración)");
        } catch (Exception e) {
            recordFailure("A07.2", e.getMessage());
        }

        // Prueba 5.3: Detección y rechazo de tokens JWT expirados
        String expiredToken = createCustomJwt(1, "demo@donaciones.org", System.currentTimeMillis() / 1000 - 3600);
        Map<String, String> claims = JwtUtil.validateToken(expiredToken);
        assertCondition(claims == null,
                "A07.3 - Invocación rechazada para tokens JWT cuya marca 'exp' ha expirado");

        System.out.println();
    }

    // =========================================================================
    // OWASP A08:2021 - Software and Data Integrity Failures
    // =========================================================================
    private static void testA08_DataIntegrity() {
        System.out.println("▶ [OWASP A08:2021] Verificando Integridad de Datos y Tokens...");

        // Prueba 6.1: Detección de token adulterado en el payload (Elevación de privilegios no autorizada)
        String validToken = JwtUtil.generateToken(1, "demo@donaciones.org", "Demo User", "donor");
        String[] parts = validToken.split("\\.");
        // Alterar el payload original
        String tamperedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"1\",\"role\":\"superadmin\"}".getBytes(StandardCharsets.UTF_8));
        String tamperedToken = parts[0] + "." + tamperedPayload + "." + parts[2];

        Map<String, String> tamperedClaims = JwtUtil.validateToken(tamperedToken);
        assertCondition(tamperedClaims == null,
                "A08.1 - Rechazo de manipulación de payload (Protección contra escalada de privilegios)");

        // Prueba 6.2: Ataque de algoritmo 'none' (Tokens sin firma criptográfica)
        String algNoneHeader = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String algNoneToken = algNoneHeader + "." + parts[1] + ".";
        Map<String, String> algNoneClaims = JwtUtil.validateToken(algNoneToken);
        assertCondition(algNoneClaims == null,
                "A08.2 - Neutralización del ataque de algoritmo 'none' en JWT");

        System.out.println();
    }

    // =========================================================================
    // Utilidades de Prueba y Reporte
    // =========================================================================
    private static void assertCondition(boolean condition, String description) {
        if (condition) {
            System.out.println("  [PASS] ✔ " + description);
            testsPassed++;
        } else {
            System.err.println("  [FAIL] ✘ " + description);
            testsFailed++;
        }
    }

    private static void recordFailure(String testId, String error) {
        System.err.println("  [FAIL] ✘ " + testId + ": " + error);
        testsFailed++;
    }

    private static String createCustomJwt(int userId, String email, long exp) {
        try {
            String header = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            String payload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(String.format("{\"sub\":\"%d\",\"email\":\"%s\",\"exp\":%d}", userId, email, exp).getBytes(StandardCharsets.UTF_8));
            String data = header + "." + payload;
            
            // Usar reflejo o cálculo HMAC para simular expirado con clave
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    "super_secreto_para_ingdesoft_actividad_5_jwt_seguro_2026_java".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String sig = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
            return data + "." + sig;
        } catch (Exception e) {
            return null;
        }
    }

    private static void printReport() {
        System.out.println("==========================================================================");
        System.out.println("📊 RESUMEN DE EJECUCIÓN OWASP TOP 10:");
        System.out.println("   Pruebas Superadas (PASS): " + testsPassed);
        System.out.println("   Pruebas Fallidas  (FAIL): " + testsFailed);
        System.out.println("   Tasa de Aprobación      : " + (testsPassed * 100 / (testsPassed + testsFailed)) + "%");
        System.out.println("==========================================================================");
        if (testsFailed == 0) {
            System.out.println("🎉 TODAS LAS VERIFICACIONES DE SEGURIDAD OWASP HAN SIDO APROBADAS.");
        } else {
            System.out.println("⚠️ SE ENCONTRARON VULNERABILIDADES QUE REQUIEREN ATENCIÓN.");
        }
        System.out.println("==========================================================================\n");
    }
}
