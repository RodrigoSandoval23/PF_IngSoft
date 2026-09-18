package com.solidaria;

import com.solidaria.auth.JwtUtil;
import com.solidaria.auth.PasswordUtil;
import com.solidaria.auth.RateLimiter;
import com.solidaria.db.DataStore;
import com.solidaria.model.AuditLog;
import com.solidaria.model.Donation;
import com.solidaria.model.User;

import java.util.List;
import java.util.Map;

public class TestSystem {

    public static void main(String[] args) {
        System.out.println("==========================================================================");
        System.out.println("🛡️  INICIANDO SUITE DE PRUEBAS DE SISTEMA Y CIBERSEGURIDAD");
        System.out.println("==========================================================================");

        // 1. Criptografía de Contraseñas
        System.out.println("\n=== 1. Probando Criptografía de Contraseñas (PBKDF2-HMAC-SHA256) ===");
        String password = "claveSuperSegura123";
        String hash = PasswordUtil.hashPassword(password);
        System.out.println("Hash generado: " + hash);

        boolean match = PasswordUtil.verifyPassword(password, hash);
        boolean badMatch = PasswordUtil.verifyPassword("claveIncorrecta", hash);
        if (!match || badMatch) {
            throw new RuntimeException("Fallo en verificación de contraseñas con PBKDF2");
        }
        System.out.println("✔ Hashing y verificación con PBKDF2: OK");

        // 2. Tokens JWT
        System.out.println("\n=== 2. Probando Tokens JWT (RFC 7519, HMAC-SHA256) ===");
        String token = JwtUtil.generateToken(10, "donante@test.com", "Donante de Prueba", "DONANTE");
        System.out.println("Token JWT generado: " + token);

        Map<String, String> claims = JwtUtil.validateToken(token);
        if (claims == null) {
            throw new RuntimeException("Fallo: Token JWT válido no pudo ser verificado.");
        }
        if (!"10".equals(claims.get("sub")) || !"donante@test.com".equals(claims.get("email"))) {
            throw new RuntimeException("Fallo: Los claims del token no coinciden.");
        }
        System.out.println("✔ Firma digital HMAC-SHA256 y claims JWT: OK");

        // Probar detección de manipulación
        String tamperedToken = token.substring(0, token.length() - 4) + "XXXX";
        if (JwtUtil.validateToken(tamperedToken) != null) {
            throw new RuntimeException("Fallo: Token manipulado fue aceptado.");
        }
        System.out.println("✔ Detección de token adulterado: OK");

        // 3. Registro de Entidades con RFC y Restricción de Tipo
        System.out.println("\n=== 3. Probando Registro de Entidades con RFC y Tipo Restrictivo ===");
        DataStore ds = DataStore.getInstance();
        long ts = System.currentTimeMillis();
        String testRfc = "RFC" + (ts % 1000000000) + "AB";
        String testEmail = "empresa_" + ts + "@donaciones.org";

        User registeredUser = ds.registerUserWithEntity(
                "Ing. Roberto Garza",
                testEmail,
                "empresaPass2026",
                "DONANTE",
                testRfc,
                "Corporativo Garza S.A. de C.V.",
                "EMPRESA DONANTE",
                "192.168.1.100"
        );

        if (registeredUser == null) {
            throw new RuntimeException("Fallo al registrar usuario con entidad y RFC.");
        }
        if (!testRfc.equals(registeredUser.getRfc()) || !"EMPRESA DONANTE".equals(registeredUser.getEntityType())) {
            throw new RuntimeException("Fallo: Los datos de la entidad no concuerdan con los registrados.");
        }
        System.out.println("✔ Registro de Entidad y RFC persistido en SQLite: OK (RFC: " + registeredUser.getRfc() + ", Tipo: " + registeredUser.getEntityType() + ")");

        // 4. Verificación de Estado PENDIENTE (HU03)
        System.out.println("\n=== 4. Probando Flujo de Estados de Cuenta (HU03) ===");
        if (!"PENDIENTE".equalsIgnoreCase(registeredUser.getStatus())) {
            throw new RuntimeException("Fallo: El estado inicial de un usuario nuevo debe ser 'PENDIENTE'. Obtenido: " + registeredUser.getStatus());
        }
        System.out.println("✔ Estado inicial PENDIENTE asignado correctamente: OK");

        // Admin aprueba la cuenta cambiándola a ACTIVO
        boolean statusUpdated = ds.updateUserStatus(registeredUser.getId(), "ACTIVO", 1, "127.0.0.1");
        if (!statusUpdated) {
            throw new RuntimeException("Fallo al actualizar estado de cuenta por administrador.");
        }

        User activeUser = ds.getUserById(registeredUser.getId());
        if (!"ACTIVO".equalsIgnoreCase(activeUser.getStatus())) {
            throw new RuntimeException("Fallo: La cuenta debió quedar en estado ACTIVO tras aprobación.");
        }
        System.out.println("✔ Aprobación de cuenta por Administrador (PENDIENTE -> ACTIVO): OK");

        // 5. Verificación de Trazabilidad y Auditoría (RNF02)
        System.out.println("\n=== 5. Probando Bitácora de Auditoría de Cuentas (RNF02) ===");
        List<AuditLog> logs = ds.getAuditLogs();
        if (logs.isEmpty()) {
            throw new RuntimeException("Fallo: La tabla auditoria_cuentas no contiene registros.");
        }

        boolean foundRegisterAudit = false;
        boolean foundStatusAudit = false;
        for (AuditLog l : logs) {
            if ("REGISTRO".equals(l.getAccion()) && testEmail.equals(l.getEmail())) {
                foundRegisterAudit = true;
            }
            if ("CAMBIO_ESTADO".equals(l.getAccion()) && testEmail.equals(l.getEmail())) {
                foundStatusAudit = true;
            }
        }

        if (!foundRegisterAudit || !foundStatusAudit) {
            throw new RuntimeException("Fallo: Los eventos de registro y cambio de estado no fueron auditados en auditoria_cuentas.");
        }
        System.out.println("✔ Trazabilidad verificada en auditoria_cuentas (REGISTRO y CAMBIO_ESTADO): OK");

        // 6. Protección contra Fuerza Bruta (OWASP A07)
        System.out.println("\n=== 6. Probando Protección contra Ataques de Fuerza Bruta (RateLimiter) ===");
        RateLimiter limiter = RateLimiter.getInstance();
        String attackIp = "192.0.2.77";

        limiter.resetAttempts(attackIp);
        if (limiter.isBlocked(attackIp)) {
            throw new RuntimeException("Fallo: IP limpia no debe estar bloqueada.");
        }

        // Simular 5 intentos fallidos
        for (int i = 1; i <= 5; i++) {
            limiter.recordFailedAttempt(attackIp);
        }

        if (!limiter.isBlocked(attackIp)) {
            throw new RuntimeException("Fallo: IP no fue bloqueada tras 5 intentos fallidos.");
        }
        long lockout = limiter.getRemainingLockoutSeconds(attackIp);
        if (lockout <= 0) {
            throw new RuntimeException("Fallo: Tiempo de espera de bloqueo inválido.");
        }
        System.out.println("✔ Bloqueo por Fuerza Bruta activado tras 5 intentos fallidos: OK (" + lockout + "s de espera)");

        limiter.resetAttempts(attackIp);
        if (limiter.isBlocked(attackIp)) {
            throw new RuntimeException("Fallo: Restablecimiento de intentos falló.");
        }
        System.out.println("✔ Restablecimiento de intentos de fuerza bruta tras éxito: OK");

        // 7. Donaciones y Persistencia con RFC
        System.out.println("\n=== 7. Probando Donaciones y Estadísticas ===");
        Donation don = ds.addDonation(activeUser.getId(), activeUser.getName(), activeUser.getEmail(), 250.0, "Educación para Niños", "tarjeta", "Donación Empresarial", activeUser.getRfc());
        if (don == null || !activeUser.getRfc().equals(don.getRfc())) {
            throw new RuntimeException("Fallo al persistir donación con RFC de la entidad.");
        }
        System.out.println("✔ Donación vinculada a entidad y RFC: OK (#DON-" + don.getId() + " - $" + don.getAmount() + " USD)");

        Map<String, Object> stats = ds.getStats();
        System.out.println("✔ Estadísticas calculadas: OK (" + stats + ")");

        System.out.println("\n==========================================================================");
        System.out.println("🎉 ¡TODAS LAS PRUEBAS DE CIBERSEGURIDAD Y SISTEMA PASARON SATISFACTORIAMENTE!");
        System.out.println("==========================================================================");
    }
}
