package com.solidaria.handlers;

import com.solidaria.db.DataStore;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.Map;

public class StatsHandler implements HttpHandler {

    private final DataStore dataStore = DataStore.getInstance();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpHelper.handlePreflight(exchange)) return;

        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            Map<String, Object> stats = dataStore.getStats();
            HttpHelper.sendJsonResponse(exchange, 200, stats);
        } else {
            HttpHelper.sendError(exchange, 405, "Método no permitido");
        }
    }
}

