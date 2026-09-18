package com.solidaria.handlers;

import com.solidaria.db.DataStore;
import com.solidaria.model.Donation;
import com.solidaria.model.User;
import com.solidaria.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminHandler implements HttpHandler {

    private final DataStore dataStore = DataStore.getInstance();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpHelper.handlePreflight(exchange)) return;

        User user = HttpHelper.getAuthenticatedUser(exchange);
        if (user == null) {
            HttpHelper.sendError(exchange, 401, "Se requiere autenticación JWT.");
            return;
        }

        if (!"admin".equalsIgnoreCase(user.getRole())) {
            HttpHelper.sendError(exchange, 403, "Acceso denegado: se requieren privilegios de Superusuario (admin).");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if ("/api/admin/users".equals(path) && "GET".equalsIgnoreCase(method)) {
            handleGetUsers(exchange);
        } else if ("/api/admin/donations".equals(path) && "GET".equalsIgnoreCase(method)) {
            handleGetDonations(exchange);
        } else {
            HttpHelper.sendError(exchange, 404, "Recurso de administración no encontrado.");
        }
    }

    private void handleGetUsers(HttpExchange exchange) throws IOException {
        List<User> users = dataStore.getAllUsers();
        List<Map<String, Object>> list = new ArrayList<>();
        for (User u : users) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", u.getId());
            map.put("name", u.getName());
            map.put("email", u.getEmail());
            map.put("role", u.getRole());
            map.put("created_at", u.getCreatedAt());
            list.add(map);
        }

        sendJsonList(exchange, list);
    }

    private void handleGetDonations(HttpExchange exchange) throws IOException {
        List<Donation> donations = dataStore.getAllDonations();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Donation d : donations) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", d.getId());
            map.put("user_id", d.getUserId());
            map.put("donor_name", d.getDonorName());
            map.put("donor_email", d.getDonorEmail());
            map.put("amount", d.getAmount());
            map.put("cause", d.getCause());
            map.put("payment_method", d.getPaymentMethod());
            map.put("message", d.getMessage());
            map.put("rfc", d.getRfc());
            map.put("created_at", d.getCreatedAt());
            list.add(map);
        }

        sendJsonList(exchange, list);
    }

    private void sendJsonList(HttpExchange exchange, List<Map<String, Object>> list) throws IOException {
        HttpHelper.setCorsHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        String json = JsonUtil.listToJson(list);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}

