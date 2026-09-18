package com.solidaria.handlers;

import com.solidaria.auth.RbacFilter;
import com.solidaria.db.DataStore;
import com.solidaria.model.AuditLog;
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

        // Control de acceso RBAC con política Deny-by-Default (Solo rol ADMIN y cuenta ACTIVO)
        User adminUser = RbacFilter.authenticateAndAuthorize(exchange, "ADMIN");
        if (adminUser == null) {
            return; // RbacFilter ya envió el código 401 o 403 correspondiente
        }

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if ("/api/admin/pending-users".equals(path) && "GET".equalsIgnoreCase(method)) {
            handleGetPendingUsers(exchange);
        } else if ("/api/admin/users/status".equals(path) && "POST".equalsIgnoreCase(method)) {
            handleUpdateUserStatus(exchange, adminUser);
        } else if ("/api/admin/audit-logs".equals(path) && "GET".equalsIgnoreCase(method)) {
            handleGetAuditLogs(exchange);
        } else if ("/api/admin/users".equals(path) && "GET".equalsIgnoreCase(method)) {
            handleGetUsers(exchange);
        } else if ("/api/admin/donations".equals(path) && "GET".equalsIgnoreCase(method)) {
            handleGetDonations(exchange);
        } else {
            HttpHelper.sendError(exchange, 404, "Recurso de administración no encontrado.");
        }
    }

    private String getClientIp(HttpExchange exchange) {
        if (exchange.getRemoteAddress() != null && exchange.getRemoteAddress().getAddress() != null) {
            return exchange.getRemoteAddress().getAddress().getHostAddress();
        }
        return "127.0.0.1";
    }

    private void handleGetPendingUsers(HttpExchange exchange) throws IOException {
        List<User> pending = dataStore.getPendingUsers();
        List<Map<String, Object>> list = new ArrayList<>();
        for (User u : pending) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", u.getId());
            map.put("name", u.getName());
            map.put("email", u.getEmail());
            map.put("role", u.getRole());
            map.put("status", u.getStatus());
            map.put("rfc", u.getRfc() != null ? u.getRfc() : "");
            map.put("legal_name", u.getLegalName() != null ? u.getLegalName() : "");
            map.put("entity_type", u.getEntityType() != null ? u.getEntityType() : "");
            map.put("created_at", u.getCreatedAt());
            list.add(map);
        }

        sendJsonList(exchange, list);
    }

    private void handleUpdateUserStatus(HttpExchange exchange, User adminUser) throws IOException {
        String body = HttpHelper.readRequestBody(exchange);
        Map<String, String> data = JsonUtil.parseSimpleJson(body);

        String userIdStr = data.get("user_id");
        String newStatus = data.get("status");

        if (userIdStr == null || newStatus == null) {
            HttpHelper.sendError(exchange, 400, "Parámetros 'user_id' y 'status' son requeridos.");
            return;
        }

        int targetUserId;
        try {
            targetUserId = Integer.parseInt(userIdStr);
        } catch (NumberFormatException e) {
            HttpHelper.sendError(exchange, 400, "El 'user_id' debe ser un número entero.");
            return;
        }

        String upperStatus = newStatus.trim().toUpperCase();
        if (!"ACTIVO".equals(upperStatus) && !"RECHAZADO".equals(upperStatus) && !"PENDIENTE".equals(upperStatus)) {
            HttpHelper.sendError(exchange, 400, "El estado debe ser 'ACTIVO', 'RECHAZADO' o 'PENDIENTE'.");
            return;
        }

        String adminIp = getClientIp(exchange);
        boolean success = dataStore.updateUserStatus(targetUserId, upperStatus, adminUser.getId(), adminIp);

        if (!success) {
            HttpHelper.sendError(exchange, 404, "Usuario no encontrado o error al actualizar estado.");
            return;
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Estado de la cuenta actualizado exitosamente a '" + upperStatus + "'.");
        resp.put("user_id", targetUserId);
        resp.put("new_status", upperStatus);

        HttpHelper.sendJsonResponse(exchange, 200, resp);
    }

    private void handleGetAuditLogs(HttpExchange exchange) throws IOException {
        List<AuditLog> logs = dataStore.getAuditLogs();
        List<Map<String, Object>> list = new ArrayList<>();
        for (AuditLog l : logs) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", l.getId());
            map.put("user_id", l.getUserId() != null ? l.getUserId() : 0);
            map.put("email", l.getEmail() != null ? l.getEmail() : "");
            map.put("accion", l.getAccion());
            map.put("detalles", l.getDetalles() != null ? l.getDetalles() : "");
            map.put("ip_address", l.getIpAddress() != null ? l.getIpAddress() : "");
            map.put("fecha", l.getFecha());
            list.add(map);
        }

        sendJsonList(exchange, list);
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
            map.put("status", u.getStatus());
            map.put("rfc", u.getRfc() != null ? u.getRfc() : "");
            map.put("legal_name", u.getLegalName() != null ? u.getLegalName() : "");
            map.put("entity_type", u.getEntityType() != null ? u.getEntityType() : "");
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
