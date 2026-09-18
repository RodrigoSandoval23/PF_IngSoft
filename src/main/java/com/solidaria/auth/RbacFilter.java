package com.solidaria.auth;

import com.solidaria.db.DataStore;
import com.solidaria.model.User;
import com.solidaria.handlers.HttpHelper;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * RbacFilter - Control de Acceso Basado en Roles con política Deny-by-Default (OWASP A01).
 * Valida la firma del token JWT, vigencia de sesión, estado de la cuenta (ACTIVO)
 * y autorización explícita por rol (ADMIN, DONANTE, BENEFICIARIO).
 */
public class RbacFilter {

    /**
     * Valida la autenticación JWT y autoriza contra una lista explícita de roles permitidos.
     * Denegación por defecto: si allowedRoles es nulo o vacío, o el rol no coincide, se deniega.
     * Además, si el usuario está en estado 'PENDIENTE' o 'RECHAZADO', se deniega con 403 Forbidden.
     *
     * @param exchange Petición HTTP entrante
     * @param allowedRoles Conjunto de roles con permiso (ej. "ADMIN", "DONANTE", "BENEFICIARIO")
     * @return El usuario autenticado y autorizado, o null si la petición fue rechazada.
     */
    public static User authenticateAndAuthorize(HttpExchange exchange, String... allowedRoles) throws IOException {
        Set<String> rolesSet = new HashSet<>();
        if (allowedRoles != null) {
            for (String r : allowedRoles) {
                if (r != null) rolesSet.add(r.trim().toUpperCase());
            }
        }
        return authenticateAndAuthorize(exchange, rolesSet);
    }

    public static User authenticateAndAuthorize(HttpExchange exchange, Set<String> allowedRoles) throws IOException {
        // 1. Extraer cabecera Authorization: Bearer <token>
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            HttpHelper.sendError(exchange, 401, "No autenticado. Se requiere un token JWT válido (RFC 7519).");
            return null;
        }

        String token = authHeader.substring(7).trim();
        Map<String, String> claims = JwtUtil.validateToken(token);
        if (claims == null) {
            HttpHelper.sendError(exchange, 401, "Token JWT inválido, expirado o con firma apócrifa.");
            return null;
        }

        // 2. Obtener usuario desde la base de datos para verificar estado en tiempo real
        String sub = claims.get("sub");
        if (sub == null) {
            HttpHelper.sendError(exchange, 401, "Claims del token incompletos (sub no encontrado).");
            return null;
        }

        int userId;
        try {
            userId = Integer.parseInt(sub);
        } catch (NumberFormatException e) {
            HttpHelper.sendError(exchange, 401, "Identificador de usuario inválido en token.");
            return null;
        }

        User user = DataStore.getInstance().getUserById(userId);
        if (user == null) {
            HttpHelper.sendError(exchange, 401, "La cuenta asociada al token ya no existe en el sistema.");
            return null;
        }

        // 3. Validación de Estado de Cuenta (HU03)
        String status = user.getStatus();
        if ("PENDIENTE".equalsIgnoreCase(status)) {
            HttpHelper.sendError(exchange, 403,
                    "Acceso denegado: Tu cuenta está en estado 'PENDIENTE' de validación por un Administrador. Tus credenciales no pueden acceder a recursos protegidos.");
            return null;
        }

        if ("RECHAZADO".equalsIgnoreCase(status)) {
            HttpHelper.sendError(exchange, 403,
                    "Acceso denegado: Tu registro fue 'RECHAZADO' durante el proceso de verificación fiscal y legal.");
            return null;
        }

        if (!"ACTIVO".equalsIgnoreCase(status)) {
            HttpHelper.sendError(exchange, 403,
                    "Acceso denegado: La cuenta no se encuentra en estado 'ACTIVO' (Estado: " + status + ").");
            return null;
        }

        // 4. Denegación por Defecto (Deny-by-Default): Solo roles explícitamente permitidos
        if (allowedRoles == null || allowedRoles.isEmpty()) {
            HttpHelper.sendError(exchange, 403, "Acceso denegado: No existen permisos configurados para este recurso.");
            return null;
        }

        String userRole = user.getRole().toUpperCase();
        if (!allowedRoles.contains(userRole)) {
            HttpHelper.sendError(exchange, 403,
                    "Acceso denegado: El rol '" + userRole + "' no cuenta con permisos para acceder a esta función.");
            return null;
        }

        return user;
    }
}
