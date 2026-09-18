package com.solidaria;

import com.solidaria.handlers.AuthHandler;
import com.solidaria.handlers.AdminHandler;
import com.solidaria.handlers.DonationHandler;
import com.solidaria.handlers.StatsHandler;
import com.solidaria.handlers.StaticFileHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class Main {

    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        }

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

            // Rutas API de Autenticación con JWT
            AuthHandler authHandler = new AuthHandler();
            server.createContext("/api/auth/register", authHandler);
            server.createContext("/api/auth/login", authHandler);
            server.createContext("/api/auth/me", authHandler);

            // Rutas API de Donaciones
            DonationHandler donationHandler = new DonationHandler();
            server.createContext("/api/donations/my-donations", donationHandler);
            server.createContext("/api/donations", donationHandler);

            // Estadísticas
            server.createContext("/api/donations/stats", new StatsHandler());

            // Rutas de Superusuario (Admin)
            AdminHandler adminHandler = new AdminHandler();
            server.createContext("/api/admin/pending-users", adminHandler);
            server.createContext("/api/admin/users/status", adminHandler);
            server.createContext("/api/admin/audit-logs", adminHandler);
            server.createContext("/api/admin/users", adminHandler);
            server.createContext("/api/admin/donations", adminHandler);
            server.createContext("/api/admin", adminHandler);

            // Archivos Estáticos (HTML, CSS, JS) y Ventana de Donaciones
            StaticFileHandler staticHandler = new StaticFileHandler();
            server.createContext("/static/", staticHandler);
            server.createContext("/", staticHandler);

            // Pool de hilos para concurrencia eficiente
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();

            System.out.println("==========================================================");
            System.out.println("🚀 Servidor Java Solidaria iniciado exitosamente");
            System.out.println("📍 Puerto: " + port);
            System.out.println("🌐 URL Aplicación: http://localhost:" + port);
            System.out.println("🔑 Autenticación: JWT (RFC 7519, HMAC-SHA256)");
            System.out.println("👤 Usuario Demo: demo@donaciones.org / demo1234");
            System.out.println("==========================================================");

        } catch (IOException e) {
            System.err.println("Error al iniciar el servidor en el puerto " + port + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}

