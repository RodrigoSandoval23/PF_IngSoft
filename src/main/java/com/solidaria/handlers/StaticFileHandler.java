package com.solidaria.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;

public class StaticFileHandler implements HttpHandler {

    private final String[] searchDirs = new String[]{
            "src/main/resources/static",
            "static"
    };

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        HttpHelper.setCorsHeaders(exchange);
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        String path = exchange.getRequestURI().getPath();
        if (path == null || path.equals("/") || path.isEmpty()) {
            path = "index.html";
        } else if (path.startsWith("/static/")) {
            path = path.substring("/static/".length());
        } else if (path.startsWith("/")) {
            path = path.substring(1);
        }

        File targetFile = null;
        for (String dir : searchDirs) {
            File f = new File(dir, path);
            if (f.exists() && !f.isDirectory()) {
                targetFile = f;
                break;
            }
        }

        if (targetFile == null) {
            HttpHelper.sendError(exchange, 404, "Archivo no encontrado: " + path);
            return;
        }

        String contentType = getMimeType(targetFile.getName());
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, targetFile.length());

        try (FileInputStream fis = new FileInputStream(targetFile);
             OutputStream os = exchange.getResponseBody()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = fis.read(buffer)) != -1) {
                os.write(buffer, 0, count);
            }
        }
    }

    private String getMimeType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=utf-8";
        if (lower.endsWith(".css")) return "text/css; charset=utf-8";
        if (lower.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (lower.endsWith(".json")) return "application/json; charset=utf-8";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }
}

