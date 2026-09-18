package com.solidaria.handlers;

import com.solidaria.db.DataStore;
import com.solidaria.model.Donation;
import com.solidaria.model.User;
import com.solidaria.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DonationHandler implements HttpHandler {

    private final DataStore dataStore = DataStore.getInstance();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpHelper.handlePreflight(exchange)) return;

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if ("/api/donations".equals(path) && "POST".equalsIgnoreCase(method)) {
            handleCreateDonation(exchange);
        } else if ("/api/donations/my-donations".equals(path) && "GET".equalsIgnoreCase(method)) {
            handleGetMyDonations(exchange);
        } else {
            HttpHelper.sendError(exchange, 404, "Ruta no encontrada");
        }
    }

    private void handleCreateDonation(HttpExchange exchange) throws IOException {
        String body = HttpHelper.readRequestBody(exchange);
        Map<String, String> data = JsonUtil.parseSimpleJson(body);

        String amountStr = data.get("amount");
        String cause = data.get("cause");
        String paymentMethod = data.get("payment_method");
        String message = data.get("message");

        if (amountStr == null || cause == null) {
            HttpHelper.sendError(exchange, 400, "Monto y causa requeridos.");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
            if (amount <= 0) {
                HttpHelper.sendError(exchange, 400, "El monto debe ser mayor a 0.");
                return;
            }
        } catch (NumberFormatException e) {
            HttpHelper.sendError(exchange, 400, "Monto numérico no válido.");
            return;
        }

        // Si el usuario envió un token JWT válido, se vincula automáticamente
        User authUser = HttpHelper.getAuthenticatedUser(exchange);
        Integer userId = authUser != null ? authUser.getId() : null;
        String donorName = authUser != null ? authUser.getName() : "Donante Anónimo";
        String donorEmail = authUser != null ? authUser.getEmail() : "anonimo@donaciones.org";

        String rfc = data.get("rfc");

        Donation donation = dataStore.addDonation(
                userId,
                donorName,
                donorEmail,
                amount,
                HttpHelper.sanitizeHtml(cause),
                paymentMethod != null ? paymentMethod : "tarjeta",
                HttpHelper.sanitizeHtml(message),
                HttpHelper.sanitizeHtml(rfc)
        );

        Map<String, Object> resp = mapDonation(donation);
        HttpHelper.sendJsonResponse(exchange, 201, resp);
    }

    private void handleGetMyDonations(HttpExchange exchange) throws IOException {
        User authUser = HttpHelper.getAuthenticatedUser(exchange);
        if (authUser == null) {
            HttpHelper.sendError(exchange, 401, "Se requiere iniciar sesión con JWT para ver su historial.");
            return;
        }

        List<Donation> userDonations = dataStore.getDonationsByUserId(authUser.getId());
        List<Map<String, Object>> listMaps = new ArrayList<>();
        for (Donation d : userDonations) {
            listMaps.add(mapDonation(d));
        }

        HttpHelper.setCorsHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        String json = JsonUtil.listToJson(listMaps);
        byte[] bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (java.io.OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private Map<String, Object> mapDonation(Donation d) {
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
        return map;
    }
}

