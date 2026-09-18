package com.solidaria.handlers;

import com.solidaria.auth.JwtUtil;
import com.solidaria.auth.PasswordUtil;
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

    private void handleRegister(HttpExchange exchange) throws IOException {
        String body = HttpHelper.readRequestBody(exchange);
        Map<String, String> data = JsonUtil.parseSimpleJson(body);

        String name = data.get("name");
        String email = data.get("email");
        String password = data.get("password");

        if (name == null || name.trim().length() < 2) {
            HttpHelper.sendError(exchange, 400, "El nombre debe tener al menos 2 caracteres.");
            return;
        }
        if (email == null || !email.contains("@")) {
            HttpHelper.sendError(exchange, 400, "Debe ingresar un correo electrónico válido.");
            return;
        }
        if (password == null || password.length() < 6) {
            HttpHelper.sendError(exchange, 400, "La contraseña debe tener al menos 6 caracteres.");
            return;
        }

        User newUser = dataStore.registerUser(name, email, password);
        if (newUser == null) {
            HttpHelper.sendError(exchange, 400, "El correo electrónico ya está registrado.");
            return;
        }

        // Generar Token JWT
        String token = JwtUtil.generateToken(newUser.getId(), newUser.getEmail(), newUser.getName(), newUser.getRole());

        Map<String, Object> resp = new HashMap<>();
        resp.put("access_token", token);
        resp.put("token_type", "bearer");

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", newUser.getId());
        userMap.put("name", newUser.getName());
        userMap.put("email", newUser.getEmail());
        userMap.put("role", newUser.getRole());
        userMap.put("created_at", newUser.getCreatedAt());
        resp.put("user", userMap);

        HttpHelper.sendJsonResponse(exchange, 201, resp);
    }

    private void handleLogin(HttpExchange exchange) throws IOException {
        String body = HttpHelper.readRequestBody(exchange);
        Map<String, String> data = JsonUtil.parseSimpleJson(body);

        String email = data.get("email");
        String password = data.get("password");

        if (email == null || password == null) {
            HttpHelper.sendError(exchange, 400, "Email y contraseña requeridos.");
            return;
        }

        User user = dataStore.getUserByEmail(email);
        if (user == null || !PasswordUtil.verifyPassword(password, user.getPasswordHash())) {
            HttpHelper.sendError(exchange, 401, "Credenciales incorrectas (correo o contraseña no válidos).");
            return;
        }

        // Generar Token JWT firmado
        String token = JwtUtil.generateToken(user.getId(), user.getEmail(), user.getName(), user.getRole());

        Map<String, Object> resp = new HashMap<>();
        resp.put("access_token", token);
        resp.put("token_type", "bearer");

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("name", user.getName());
        userMap.put("email", user.getEmail());
        userMap.put("role", user.getRole());
        userMap.put("created_at", user.getCreatedAt());
        resp.put("user", userMap);

        HttpHelper.sendJsonResponse(exchange, 200, resp);
    }

    private void handleMe(HttpExchange exchange) throws IOException {
        User user = HttpHelper.getAuthenticatedUser(exchange);
        if (user == null) {
            HttpHelper.sendError(exchange, 401, "Se requiere un token JWT válido para acceder a este recurso.");
            return;
        }

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("name", user.getName());
        userMap.put("email", user.getEmail());
        userMap.put("role", user.getRole());
        userMap.put("created_at", user.getCreatedAt());

        HttpHelper.sendJsonResponse(exchange, 200, userMap);
    }
}

