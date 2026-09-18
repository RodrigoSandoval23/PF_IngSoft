package com.solidaria;

import com.solidaria.auth.JwtUtil;
import com.solidaria.auth.PasswordUtil;
import com.solidaria.db.DataStore;
import com.solidaria.model.Donation;
import com.solidaria.model.User;

import java.util.List;
import java.util.Map;

public class TestSystem {

    public static void main(String[] args) {
        System.out.println("=== 1. Probando Criptografía de Contraseñas (PBKDF2) ===");
        String password = "claveSuperSegura123";
        String hash = PasswordUtil.hashPassword(password);
        System.out.println("Hash generado: " + hash);

        boolean match = PasswordUtil.verifyPassword(password, hash);
        boolean badMatch = PasswordUtil.verifyPassword("claveIncorrecta", hash);
        if (!match || badMatch) {
            throw new RuntimeException("Fallo en verificación de contraseñas con PBKDF2");
        }
        System.out.println("✔ Hashing y verificación con PBKDF2: OK");

        System.out.println("\n=== 2. Probando Tokens JWT (RFC 7519, HMAC-SHA256) ===");
        String token = JwtUtil.generateToken(10, "donante@test.com", "Donante de Prueba", "donor");
        System.out.println("Token JWT generado: " + token);

        Map<String, String> claims = JwtUtil.validateToken(token);
        if (claims == null) {
            throw new RuntimeException("Fallo: Token JWT válido no pudo ser verificado.");
        }
        if (!"10".equals(claims.get("sub")) || !"donante@test.com".equals(claims.get("email"))) {
            throw new RuntimeException("Fallo: Los claims del token no coinciden.");
        }
        System.out.println("✔ Firma digital HMAC-SHA256 y claims JWT: OK");

        // Probar token alterado / inválido
        String tamperedToken = token.substring(0, token.length() - 4) + "XXXX";
        if (JwtUtil.validateToken(tamperedToken) != null) {
            throw new RuntimeException("Fallo: Token manipulado fue aceptado.");
        }
        System.out.println("✔ Detección de token adulterado: OK");

        System.out.println("\n=== 3. Probando DataStore de Usuarios y Donaciones ===");
        DataStore ds = DataStore.getInstance();
        User demo = ds.getUserByEmail("demo@donaciones.org");
        if (demo == null || !PasswordUtil.verifyPassword("demo1234", demo.getPasswordHash())) {
            throw new RuntimeException("Fallo: Usuario demo no disponible.");
        }
        System.out.println("✔ Usuario demo pre-cargado: OK (" + demo.getEmail() + ")");

        // Registrar nuevo usuario
        User newUser = ds.registerUser("Maria Lopez", "maria@donaciones.org", "maria2026");
        if (newUser == null) {
            throw new RuntimeException("Fallo al registrar nuevo usuario.");
        }
        System.out.println("✔ Registro de nuevo donante: OK (" + newUser.getEmail() + ")");

        // Registrar donación
        Donation don = ds.addDonation(newUser.getId(), newUser.getName(), newUser.getEmail(), 100.0, "Refugio Animal", "tarjeta", "Apoyo");
        List<Donation> userDons = ds.getDonationsByUserId(newUser.getId());
        if (userDons.size() != 1 || userDons.get(0).getAmount() != 100.0) {
            throw new RuntimeException("Fallo al vincular donación con usuario.");
        }
        System.out.println("✔ Donación vinculada a usuario JWT: OK (#" + don.getId() + " - $" + don.getAmount() + ")");

        Map<String, Object> stats = ds.getStats();
        System.out.println("✔ Estadísticas calculadas: OK (" + stats + ")");

        System.out.println("\n🎉 ¡TODAS LAS PRUEBAS EN JAVA PASARON SATISFACTORIAMENTE!");
    }
}

