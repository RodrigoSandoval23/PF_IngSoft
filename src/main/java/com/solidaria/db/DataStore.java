package com.solidaria.db;

import com.solidaria.auth.PasswordUtil;
import com.solidaria.model.Donation;
import com.solidaria.model.User;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class DataStore {

    private static final DataStore INSTANCE = new DataStore();
    private static final String DB_URL = "jdbc:sqlite:donations.db";

    private boolean useSqlite = false;

    // Respaldo en memoria (fallback)
    private final Map<Integer, User> memoryUsersById = new ConcurrentHashMap<>();
    private final Map<String, User> memoryUsersByEmail = new ConcurrentHashMap<>();
    private final List<Donation> memoryDonations = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger memoryUserIdSeq = new AtomicInteger(1);
    private final AtomicInteger memoryDonationIdSeq = new AtomicInteger(1);

    private DataStore() {
        initDatabase();
    }

    public static DataStore getInstance() {
        return INSTANCE;
    }

    private synchronized void initDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL;");
                stmt.execute("PRAGMA busy_timeout=5000;");
                useSqlite = true;
                createTables(conn);
                seedSuperUserAndDemoData(conn);
                System.out.println("💾 Base de datos SQLite conectada e inicializada: donations.db");
            }
        } catch (Throwable t) {
            System.err.println("⚠️ No se pudo inicializar SQLite JDBC (" + t.getMessage() + "). Usando almacenamiento en memoria.");
            useSqlite = false;
            seedInMemory();
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    private void createTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Tabla de usuarios
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT NOT NULL, " +
                    "email TEXT UNIQUE NOT NULL, " +
                    "password_hash TEXT NOT NULL, " +
                    "role TEXT NOT NULL DEFAULT 'donor', " +
                    "created_at TEXT NOT NULL" +
                    ");");

            // Tabla de donaciones
            stmt.execute("CREATE TABLE IF NOT EXISTS donations (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "user_id INTEGER, " +
                    "donor_name TEXT NOT NULL, " +
                    "donor_email TEXT NOT NULL, " +
                    "amount REAL NOT NULL, " +
                    "cause TEXT NOT NULL, " +
                    "payment_method TEXT NOT NULL, " +
                    "message TEXT, " +
                    "rfc TEXT DEFAULT '', " +
                    "created_at TEXT NOT NULL, " +
                    "FOREIGN KEY (user_id) REFERENCES users(id)" +
                    ");");

            // Migración idempotente por si la tabla ya existía
            try {
                stmt.execute("ALTER TABLE donations ADD COLUMN rfc TEXT DEFAULT '';");
            } catch (Exception ignored) {}
        }
    }

    private void seedSuperUserAndDemoData(Connection conn) throws SQLException {
        // 1. Sembrar Superusuario (admin@donaciones.org)
        if (getUserByEmail("admin@donaciones.org") == null) {
            String adminHash = PasswordUtil.hashPassword("admin1234");
            String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users (name, email, password_hash, role, created_at) VALUES (?, ?, ?, ?, ?)")) {
                ps.setString(1, "Super Administrador");
                ps.setString(2, "admin@donaciones.org");
                ps.setString(3, adminHash);
                ps.setString(4, "admin");
                ps.setString(5, now);
                ps.executeUpdate();
                System.out.println("👑 Superusuario creado en BD: admin@donaciones.org (Rol: admin)");
            }
        }

        // 2. Sembrar Usuario Donante Demo (demo@donaciones.org)
        if (getUserByEmail("demo@donaciones.org") == null) {
            String demoHash = PasswordUtil.hashPassword("demo1234");
            String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users (name, email, password_hash, role, created_at) VALUES (?, ?, ?, ?, ?)")) {
                ps.setString(1, "Donante Solidario");
                ps.setString(2, "demo@donaciones.org");
                ps.setString(3, demoHash);
                ps.setString(4, "donor");
                ps.setString(5, now);
                ps.executeUpdate();
            }
        }

        // 3. Sembrar Donaciones iniciales si está vacía
        boolean hasDonations = false;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM donations")) {
            if (rs.next() && rs.getInt(1) > 0) {
                hasDonations = true;
            }
        }

        if (!hasDonations) {
            String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO donations (user_id, donor_name, donor_email, amount, cause, payment_method, message, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setInt(1, 2);
                ps.setString(2, "Donante Solidario");
                ps.setString(3, "demo@donaciones.org");
                ps.setDouble(4, 50.0);
                ps.setString(5, "Educación para Niños");
                ps.setString(6, "tarjeta");
                ps.setString(7, "¡Apoyo a la educación!");
                ps.setString(8, now);
                ps.executeUpdate();

                ps.setInt(1, 2);
                ps.setString(2, "Donante Solidario");
                ps.setString(3, "demo@donaciones.org");
                ps.setDouble(4, 25.0);
                ps.setString(5, "Refugio Animal");
                ps.setString(6, "paypal");
                ps.setString(7, "Para alimentación de rescatados.");
                ps.setString(8, now);
                ps.executeUpdate();
            }
        }
    }

    private void seedInMemory() {
        // Superusuario en memoria
        String adminHash = PasswordUtil.hashPassword("admin1234");
        User admin = new User(memoryUserIdSeq.getAndIncrement(), "Super Administrador", "admin@donaciones.org", adminHash, "admin");
        memoryUsersById.put(admin.getId(), admin);
        memoryUsersByEmail.put(admin.getEmail().toLowerCase(), admin);

        // Donante demo en memoria
        String demoHash = PasswordUtil.hashPassword("demo1234");
        User demo = new User(memoryUserIdSeq.getAndIncrement(), "Donante Solidario", "demo@donaciones.org", demoHash, "donor");
        memoryUsersById.put(demo.getId(), demo);
        memoryUsersByEmail.put(demo.getEmail().toLowerCase(), demo);

        addDonation(demo.getId(), demo.getName(), demo.getEmail(), 50.0, "Educación para Niños", "tarjeta", "¡Apoyo!");
        addDonation(demo.getId(), demo.getName(), demo.getEmail(), 25.0, "Refugio Animal", "paypal", "Para rescatados.");
    }

    public synchronized User registerUser(String name, String email, String password) {
        return registerUser(name, email, password, "donor");
    }

    public synchronized User registerUser(String name, String email, String password, String role) {
        String normalizedEmail = email.trim().toLowerCase();
        if (getUserByEmail(normalizedEmail) != null) {
            return null; // Ya registrado
        }

        String hash = PasswordUtil.hashPassword(password);
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        if (useSqlite) {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO users (name, email, password_hash, role, created_at) VALUES (?, ?, ?, ?, ?)",
                         Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name.trim());
                ps.setString(2, normalizedEmail);
                ps.setString(3, hash);
                ps.setString(4, role != null ? role : "donor");
                ps.setString(5, now);
                ps.executeUpdate();

                try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int id = generatedKeys.getInt(1);
                        return new User(id, name.trim(), normalizedEmail, hash, role, now);
                    }
                }
            } catch (SQLException e) {
                System.err.println("Error al insertar usuario en SQLite: " + e.getMessage());
            }
        }

        // Fallback memoria
        int newId = memoryUserIdSeq.getAndIncrement();
        User user = new User(newId, name.trim(), normalizedEmail, hash, role, now);
        memoryUsersById.put(newId, user);
        memoryUsersByEmail.put(normalizedEmail, user);
        return user;
    }

    public User getUserByEmail(String email) {
        if (email == null) return null;
        String normalized = email.trim().toLowerCase();

        if (useSqlite) {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE LOWER(email) = ?")) {
                ps.setString(1, normalized);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new User(
                                rs.getInt("id"),
                                rs.getString("name"),
                                rs.getString("email"),
                                rs.getString("password_hash"),
                                rs.getString("role"),
                                rs.getString("created_at")
                        );
                    }
                }
            } catch (SQLException e) {
                System.err.println("Error al buscar usuario por email en SQLite: " + e.getMessage());
            }
        }

        return memoryUsersByEmail.get(normalized);
    }

    public User getUserById(int id) {
        if (useSqlite) {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE id = ?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new User(
                                rs.getInt("id"),
                                rs.getString("name"),
                                rs.getString("email"),
                                rs.getString("password_hash"),
                                rs.getString("role"),
                                rs.getString("created_at")
                        );
                    }
                }
            } catch (SQLException e) {
                System.err.println("Error al buscar usuario por ID en SQLite: " + e.getMessage());
            }
        }

        return memoryUsersById.get(id);
    }

    public List<User> getAllUsers() {
        List<User> list = new ArrayList<>();
        if (useSqlite) {
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM users ORDER BY id ASC")) {
                while (rs.next()) {
                    list.add(new User(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("email"),
                            rs.getString("password_hash"),
                            rs.getString("role"),
                            rs.getString("created_at")
                    ));
                }
                return list;
            } catch (SQLException e) {
                System.err.println("Error al obtener todos los usuarios de SQLite: " + e.getMessage());
            }
        }

        return new ArrayList<>(memoryUsersById.values());
    }

    public synchronized Donation addDonation(Integer userId, String donorName, String donorEmail, double amount, String cause, String paymentMethod, String message) {
        return addDonation(userId, donorName, donorEmail, amount, cause, paymentMethod, message, "");
    }

    public synchronized Donation addDonation(Integer userId, String donorName, String donorEmail, double amount, String cause, String paymentMethod, String message, String rfc) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String cleanRfc = rfc != null ? rfc.trim().toUpperCase() : "";

        if (useSqlite) {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO donations (user_id, donor_name, donor_email, amount, cause, payment_method, message, rfc, created_at) " +
                                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                         Statement.RETURN_GENERATED_KEYS)) {
                if (userId != null) {
                    ps.setInt(1, userId);
                } else {
                    ps.setNull(1, Types.INTEGER);
                }
                ps.setString(2, donorName);
                ps.setString(3, donorEmail);
                ps.setDouble(4, amount);
                ps.setString(5, cause);
                ps.setString(6, paymentMethod);
                ps.setString(7, message != null ? message : "");
                ps.setString(8, cleanRfc);
                ps.setString(9, now);
                ps.executeUpdate();

                try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int id = generatedKeys.getInt(1);
                        return new Donation(id, userId, donorName, donorEmail, amount, cause, paymentMethod, message, cleanRfc, now);
                    }
                }
            } catch (SQLException e) {
                System.err.println("Error al insertar donación en SQLite: " + e.getMessage());
            }
        }

        int id = memoryDonationIdSeq.getAndIncrement();
        Donation donation = new Donation(id, userId, donorName, donorEmail, amount, cause, paymentMethod, message, cleanRfc, now);
        memoryDonations.add(donation);
        return donation;
    }

    public List<Donation> getDonationsByUserId(int userId) {
        List<Donation> list = new ArrayList<>();
        if (useSqlite) {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM donations WHERE user_id = ? ORDER BY id DESC")) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String rfc = "";
                        try { rfc = rs.getString("rfc"); } catch (Exception ignored) {}
                        list.add(new Donation(
                                rs.getInt("id"),
                                rs.getInt("user_id"),
                                rs.getString("donor_name"),
                                rs.getString("donor_email"),
                                rs.getDouble("amount"),
                                rs.getString("cause"),
                                rs.getString("payment_method"),
                                rs.getString("message"),
                                rfc != null ? rfc : "",
                                rs.getString("created_at")
                        ));
                    }
                    return list;
                }
            } catch (SQLException e) {
                System.err.println("Error al obtener donaciones de usuario en SQLite: " + e.getMessage());
            }
        }

        synchronized (memoryDonations) {
            return memoryDonations.stream()
                    .filter(d -> d.getUserId() != null && d.getUserId() == userId)
                    .sorted((a, b) -> Integer.compare(b.getId(), a.getId()))
                    .collect(Collectors.toList());
        }
    }

    public List<Donation> getAllDonations() {
        List<Donation> list = new ArrayList<>();
        if (useSqlite) {
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM donations ORDER BY id DESC")) {
                while (rs.next()) {
                    String rfc = "";
                    try { rfc = rs.getString("rfc"); } catch (Exception ignored) {}
                    list.add(new Donation(
                            rs.getInt("id"),
                            rs.getInt("user_id"),
                            rs.getString("donor_name"),
                            rs.getString("donor_email"),
                            rs.getDouble("amount"),
                            rs.getString("cause"),
                            rs.getString("payment_method"),
                            rs.getString("message"),
                            rfc != null ? rfc : "",
                            rs.getString("created_at")
                    ));
                }
                return list;
            } catch (SQLException e) {
                System.err.println("Error al obtener todas las donaciones de SQLite: " + e.getMessage());
            }
        }

        synchronized (memoryDonations) {
            return new ArrayList<>(memoryDonations);
        }
    }

    public Map<String, Object> getStats() {
        double total = 0.0;
        int count = 0;
        double goal = 5000.0;

        if (useSqlite) {
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*), COALESCE(SUM(amount), 0) FROM donations")) {
                if (rs.next()) {
                    count = rs.getInt(1);
                    total = rs.getDouble(2);
                }
            } catch (SQLException e) {
                System.err.println("Error al obtener estadísticas de SQLite: " + e.getMessage());
            }
        } else {
            synchronized (memoryDonations) {
                total = memoryDonations.stream().mapToDouble(Donation::getAmount).sum();
                count = memoryDonations.size();
            }
        }

        double progress = Math.min(Math.round((total / goal) * 1000.0) / 10.0, 100.0);
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_raised", total);
        stats.put("goal", goal);
        stats.put("donations_count", count);
        stats.put("progress_percentage", progress);
        return stats;
    }

    public boolean isUsingSqlite() {
        return useSqlite;
    }
}
