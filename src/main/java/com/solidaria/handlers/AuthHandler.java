package com.solidaria.handlers;

import com.solidaria.auth.JwtUtil;
import com.solidaria.auth.PasswordUtil;
import com.solidaria.auth.RateLimiter;
import com.solidaria.auth.RbacFilter;
import com.solidaria.db.DataStore;
import com.solidaria.model.User;
import com.solidaria.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class AuthHandler implements HttpHandler {

    private final DataStore dataStore = DataStore.getInstance();
    private final RateLimiter rateLimiter = RateLimiter.getInstance();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpHelper.handlePreflight(exchange)) return;

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if ("/api/auth/register".equals(path) && "POST".equalsIgnoreCase(method)) {
            handleRegister(exchange);
        } else if ("/api/auth/login".equals(path) && "POST".equalsIgnoreCase(method)) {
            handleLogin(exchange);
        } else if ("/api/auth/me".equals(path) && "GET".equalsIgnoreCase(method)) {
            handleMe(exchange);
        } else {
            HttpHelper.sendError(exchange, 404, "Ruta no encontrada");
        }
    }

    private String getClientIp(HttpExchange exchange) {
        if (exchange.getRemoteAddress() != null && exchange.getRemoteAddress().getAddress() != null) {
            return exchange.getRemoteAddress().getAddress().getHostAddress();
        }
        return "127.0.0.1";
    }

    private void handleRegister(HttpExchange exchange) throws IOException {
        String clientIp = getClientIp(exchange);
        String body = HttpHelper.readRequestBody(exchange);
        Map<String, String> data = JsonUtil.parseSimpleJson(body);

        String name = data.get("name");
        String email = data.get("email");
        String password = data.get("password");
        String role = data.get("role");
        String rfc = data.get("rfc");
        String legalName = data.get("legal_name");
        String entityType = data.get("entity_type");

        // 1. Validaciones de Usuario
        if (name == null || name.trim().length() < 2) {
            HttpHelper.sendError(exchange, 400, "El nombre de representante debe tener al menos 2 caracteres.");
            return;
        }
        if (email == null || !email.contains("@")) {
            HttpHelper.sendError(exchange, 400, "Debe ingresar un correo electrónico válido.");
            return;
        }
        if (password == null || password.length() < 6) {
            HttpHelper.sendError(exchange, 400, "La contraseña debe tener al cliente un mínimo de 6 caracteres.");
            return;
        }

        // 2. Validación de RFC obligatorio (12 caracteres personas morales, 13 físicas)
        if (rfc == null || rfc.trim().length() < 12 || rfc.trim().length() > 13) {
            HttpHelper.sendError(exchange, 400, "El RFC es obligatorio y debe tener entre 12 y 13 caracteres alfanuméricos.");
            return;
        }
        String cleanRfc = rfc.trim().toUpperCase();

        // 3. Validación de Razón Social / Nombre Legal
        if (legalName == null || legalName.trim().length() < 2) {
            legalName = name.trim();
        }

        // 4. Validación de Tipo de Entidad: Estrictamente 'EMPRESA DONANTE' u 'ORGANIZACION_SOCIAL'
        String cleanEntityType = entityType != null ? entityType.trim().toUpperCase() : "";
        if (!"EMPRESA DONANTE".equals(cleanEntityType) && !"ORGANIZACION_SOCIAL".equals(cleanEntityType)) {
            HttpHelper.sendError(exchange, 400, "El tipo de entidad es inválido. Debe ser 'EMPRESA DONANTE' u 'ORGANIZACION_SOCIAL'.");
            return;
        }

        // 5. Restricción de Roles: El usuario puede solicitar DONANTE o BENEFICIARIO (no ADMIN)
        String requestedRole = "DONANTE";
        if (role != null && "BENEFICIARIO".equalsIgnoreCase(role.trim())) {
            requestedRole = "BENEFICIARIO";
        }

        // Sanitización anti-XSS
        String safeName = HttpHelper.sanitizeHtml(name.trim());
        String safeEmail = HttpHelper.sanitizeHtml(email.trim().toLowerCase());
        String safeLegalName = HttpHelper.sanitizeHtml(legalName.trim());

        User newUser = dataStore.registerUserWithEntity(safeName, safeEmail, password, requestedRole,
                cleanRfc, safeLegalName, cleanEntityType, clientIp);

        if (newUser == null) {
            HttpHelper.sendError(exchange, 400, "El correo electrónico o el RFC ya se encuentra registrado.");
            return;
        }

        // HU03: Las cuentas registradas inician en estado PENDIENTE y no tienen acceso hasta ser aprobadas
        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "PENDIENTE");
        resp.put("message", "Registro exitoso. Tu cuenta y entidad jurídica han sido creadas con estado 'PENDIENTE'. Un administrador revisará tu RFC y documentación para activarla.");
        resp.put("user", mapUser(newUser));

        HttpHelper.sendJsonResponse(exchange, 201, resp);
    }

    private void handleLogin(HttpExchange exchange) throws IOException {
        String clientIp = getClientIp(exchange);
        String body = HttpHelper.readRequestBody(exchange);
        Map<String, String> data = JsonUtil.parseSimpleJson(body);

        String email = data.get("email");
        String password = data.get("password");

        if (email == null || password == null) {
            HttpHelper.sendError(exchange, 400, "Email y contraseña requeridos.");
            return;
        }

        String normalizedEmail = email.trim().toLowerCase();
        String rateKey = clientIp + ":" + normalizedEmail;

        // Protección de Fuerza Bruta (Rate Limiting)
        if (rateLimiter.isBlocked(clientIp) || rateLimiter.isBlocked(rateKey)) {
            long remaining = Math.max(rateLimiter.getRemainingLockoutSeconds(clientIp), rateLimiter.getRemainingLockoutSeconds(rateKey));
            dataStore.logAuditEvent(null, normalizedEmail, "BLOQUEO_FUERZA_BRUTA",
                    "Bloqueo por exceder límite de intentos fallidos. Segundos de espera: " + remaining, clientIp);
            HttpHelper.sendError(exchange, 429,
                    "Demasiados intentos fallidos. Tu acceso ha sido bloqueado temporalmente por seguridad (OWASP A07). Espera " + remaining + " segundos.");
            return;
        }

        User user = dataStore.getUserByEmail(normalizedEmail);
        if (user == null || !PasswordUtil.verifyPassword(password, user.getPasswordHash())) {
            rateLimiter.recordFailedAttempt(clientIp);
            rateLimiter.recordFailedAttempt(rateKey);
            dataStore.logAuditEvent(user != null ? user.getId() : null, normalizedEmail, "LOGIN_FALLIDO",
                    "Contraseña o usuario incorrecto", clientIp);
            HttpHelper.sendError(exchange, 401, "Credenciales incorrectas (correo o contraseña no válidos).");
            return;
        }

        // Validación de Estado de Cuenta (HU03)
        String userStatus = user.getStatus();
        if ("PENDIENTE".equalsIgnoreCase(userStatus)) {
            dataStore.logAuditEvent(user.getId(), user.getEmail(), "LOGIN_RECHAZADO",
                    "Intento de inicio de sesión rechazado: Cuenta PENDIENTE de validación de RFC", clientIp);
            HttpHelper.sendError(exchange, 403,
                    "Tu cuenta se encuentra en estado 'PENDIENTE' de validación. Un Administrador debe auditar tu RFC y datos de entidad antes de otorgarte acceso al portal.");
            return;
        }

        if ("RECHAZADO".equalsIgnoreCase(userStatus)) {
            dataStore.logAuditEvent(user.getId(), user.getEmail(), "LOGIN_RECHAZADO",
                    "Intento de inicio de sesión rechazado: Cuenta RECHAZADA", clientIp);
            HttpHelper.sendError(exchange, 403,
                    "Tu solicitud de registro ha sido RECHAZADA durante la auditoría fiscal y legal.");
            return;
        }

        // Login Exitoso: Restablecer intentos de fuerza bruta y generar JWT
        rateLimiter.resetAttempts(clientIp);
        rateLimiter.resetAttempts(rateKey);
        dataStore.logAuditEvent(user.getId(), user.getEmail(), "LOGIN_EXITOSO",
                "Inicio de sesión exitoso con token JWT", clientIp);

        String token = JwtUtil.generateToken(user.getId(), user.getEmail(), user.getName(), user.getRole());

        Map<String, Object> resp = new HashMap<>();
        resp.put("access_token", token);
        resp.put("token_type", "bearer");
        resp.put("user", mapUser(user));

        HttpHelper.sendJsonResponse(exchange, 200, resp);
    }

    private void handleMe(HttpExchange exchange) throws IOException {
        // Validación con política Deny-by-Default: solo usuarios ACTIVO con roles válidos
        User user = RbacFilter.authenticateAndAuthorize(exchange, "ADMIN", "DONANTE", "BENEFICIARIO");
        if (user == null) {
            return; // RbacFilter ya envió el código 401 o 403 correspondiente
        }

        HttpHelper.sendJsonResponse(exchange, 200, mapUser(user));
    }

    private Map<String, Object> mapUser(User user) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("name", user.getName());
        userMap.put("email", user.getEmail());
        userMap.put("role", user.getRole());
        userMap.put("status", user.getStatus());
        userMap.put("rfc", user.getRfc() != null ? user.getRfc() : "");
        userMap.put("legal_name", user.getLegalName() != null ? user.getLegalName() : "");
        userMap.put("entity_type", user.getEntityType() != null ? user.getEntityType() : "");
        userMap.put("created_at", user.getCreatedAt());
        return userMap;
    }
}
