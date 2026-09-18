package com.solidaria.db;

import com.solidaria.auth.PasswordUtil;
import com.solidaria.model.AuditLog;
import com.solidaria.model.Donation;
import com.solidaria.model.Entity;
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
    private final Map<Integer, Entity> memoryEntitiesByUserId = new ConcurrentHashMap<>();
    private final List<Donation> memoryDonations = Collections.synchronizedList(new ArrayList<>());
    private final List<AuditLog> memoryAuditLogs = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger memoryUserIdSeq = new AtomicInteger(1);
    private final AtomicInteger memoryEntityIdSeq = new AtomicInteger(1);
    private final AtomicInteger memoryDonationIdSeq = new AtomicInteger(1);
    private final AtomicInteger memoryAuditIdSeq = new AtomicInteger(1);

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
            // 1. Tabla de usuarios con rol y estado (PENDIENTE, ACTIVO, RECHAZADO)
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT NOT NULL, " +
                    "email TEXT UNIQUE NOT NULL, " +
                    "password_hash TEXT NOT NULL, " +
                    "role TEXT NOT NULL DEFAULT 'DONANTE', " +
                    "status TEXT NOT NULL DEFAULT 'PENDIENTE', " +
                    "created_at TEXT NOT NULL" +
                    ");");

            // Migración idempotente de columna status
            try {
                stmt.execute("ALTER TABLE users ADD COLUMN status TEXT NOT NULL DEFAULT 'PENDIENTE';");
            } catch (Exception ignored) {}

            // 2. Tabla de entidades asociadas (RFC, Razón Social, Tipo de Entidad)
            stmt.execute("CREATE TABLE IF NOT EXISTS entidades (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "user_id INTEGER NOT NULL UNIQUE, " +
                    "rfc TEXT NOT NULL UNIQUE, " +
                    "legal_name TEXT NOT NULL, " +
                    "entity_type TEXT NOT NULL CHECK(entity_type IN ('EMPRESA DONANTE', 'ORGANIZACION_SOCIAL')), " +
                    "created_at TEXT NOT NULL, " +
                    "FOREIGN KEY (user_id) REFERENCES users(id)" +
                    ");");

            // 3. Tabla de donaciones
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

            try {
                stmt.execute("ALTER TABLE donations ADD COLUMN rfc TEXT DEFAULT '';");
            } catch (Exception ignored) {}

            // 4. Tabla de trazabilidad y auditoría de cuentas (RNF02)
            stmt.execute("CREATE TABLE IF NOT EXISTS auditoria_cuentas (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "user_id INTEGER, " +
                    "email TEXT, " +
                    "accion TEXT NOT NULL, " +
                    "detalles TEXT, " +
                    "ip_address TEXT, " +
                    "fecha TEXT NOT NULL, " +
                    "FOREIGN KEY (user_id) REFERENCES users(id)" +
                    ");");
        }
    }

    private void seedSuperUserAndDemoData(Connection conn) throws SQLException {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // 1. Sembrar Superusuario (admin@donaciones.org) con rol ADMIN y estado ACTIVO
        User adminUser = getUserByEmail("admin@donaciones.org");
        if (adminUser == null) {
            String adminHash = PasswordUtil.hashPassword("admin1234");
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users (name, email, password_hash, role, status, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, "Super Administrador");
                ps.setString(2, "admin@donaciones.org");
                ps.setString(3, adminHash);
                ps.setString(4, "ADMIN");
                ps.setString(5, "ACTIVO");
                ps.setString(6, now);
                ps.executeUpdate();
                System.out.println("👑 Superusuario creado en BD: admin@donaciones.org (Rol: ADMIN, Estado: ACTIVO)");

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        int adminId = keys.getInt(1);
                        logAuditEvent(adminId, "admin@donaciones.org", "SISTEMA", "Inicialización de cuenta Superusuario", "127.0.0.1");
                    }
                }
            }
        } else {
            // Asegurar que el admin tenga estado ACTIVO y rol ADMIN
            try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET status = 'ACTIVO', role = 'ADMIN' WHERE id = ?")) {
                ps.setInt(1, adminUser.getId());
                ps.executeUpdate();
            }
        }

        // 2. Sembrar Usuario Donante Demo (demo@donaciones.org) con rol DONANTE y estado ACTIVO
        User demoUser = getUserByEmail("demo@donaciones.org");
        if (demoUser == null) {
            String demoHash = PasswordUtil.hashPassword("demo1234");
            int demoId = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users (name, email, password_hash, role, status, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, "Fundación Demo Solidaria");
                ps.setString(2, "demo@donaciones.org");
                ps.setString(3, demoHash);
                ps.setString(4, "DONANTE");
                ps.setString(5, "ACTIVO");
                ps.setString(6, now);
                ps.executeUpdate();

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        demoId = keys.getInt(1);
                    }
                }
            }

            if (demoId > 0) {
                // Registrar entidad para el donante demo
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO entidades (user_id, rfc, legal_name, entity_type, created_at) VALUES (?, ?, ?, ?, ?)")) {
                    ps.setInt(1, demoId);
                    ps.setString(2, "FDS850101XYZ");
                    ps.setString(3, "Fundación Demo Solidaria A.C.");
                    ps.setString(4, "ORGANIZACION_SOCIAL");
                    ps.setString(5, now);
                    ps.executeUpdate();
                }
                logAuditEvent(demoId, "demo@donaciones.org", "SISTEMA", "Inicialización de cuenta Demo Donante", "127.0.0.1");
            }
        } else {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET status = 'ACTIVO', role = 'DONANTE' WHERE id = ?")) {
                ps.setInt(1, demoUser.getId());
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
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO donations (user_id, donor_name, donor_email, amount, cause, payment_method, message, rfc, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setInt(1, 2);
                ps.setString(2, "Fundación Demo Solidaria");
                ps.setString(3, "demo@donaciones.org");
                ps.setDouble(4, 50.0);
                ps.setString(5, "Educación para Niños");
                ps.setString(6, "tarjeta");
                ps.setString(7, "¡Apoyo a la educación!");
                ps.setString(8, "FDS850101XYZ");
                ps.setString(9, now);
                ps.executeUpdate();

                ps.setInt(1, 2);
                ps.setString(2, "Fundación Demo Solidaria");
                ps.setString(3, "demo@donaciones.org");
                ps.setDouble(4, 25.0);
                ps.setString(5, "Refugio Animal");
                ps.setString(6, "paypal");
                ps.setString(7, "Para alimentación de rescatados.");
                ps.setString(8, "FDS850101XYZ");
                ps.setString(9, now);
                ps.executeUpdate();
            }
        }
    }

    private void seedInMemory() {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // Superusuario en memoria
        String adminHash = PasswordUtil.hashPassword("admin1234");
        User admin = new User(memoryUserIdSeq.getAndIncrement(), "Super Administrador", "admin@donaciones.org", adminHash, "ADMIN", "ACTIVO", now);
        memoryUsersById.put(admin.getId(), admin);
        memoryUsersByEmail.put(admin.getEmail().toLowerCase(), admin);

        // Donante demo en memoria
        String demoHash = PasswordUtil.hashPassword("demo1234");
        User demo = new User(memoryUserIdSeq.getAndIncrement(), "Fundación Demo Solidaria", "demo@donaciones.org", demoHash, "DONANTE", "ACTIVO", now);
        demo.setEntityInfo("FDS850101XYZ", "Fundación Demo Solidaria A.C.", "ORGANIZACION_SOCIAL");
        memoryUsersById.put(demo.getId(), demo);
        memoryUsersByEmail.put(demo.getEmail().toLowerCase(), demo);

        Entity demoEntity = new Entity(memoryEntityIdSeq.getAndIncrement(), demo.getId(), "FDS850101XYZ", "Fundación Demo Solidaria A.C.", "ORGANIZACION_SOCIAL", now);
        memoryEntitiesByUserId.put(demo.getId(), demoEntity);

        addDonation(demo.getId(), demo.getName(), demo.getEmail(), 50.0, "Educación para Niños", "tarjeta", "¡Apoyo!", "FDS850101XYZ");
        addDonation(demo.getId(), demo.getName(), demo.getEmail(), 25.0, "Refugio Animal", "paypal", "Para rescatados.", "FDS850101XYZ");
    }

    /* ==========================================================================
       REGISTRO DE USUARIOS Y ENTIDADES (CON RFC Y TIPO RESTRICTIVO)
       ========================================================================== */

    public synchronized User registerUser(String name, String email, String password) {
        return registerUser(name, email, password, "DONANTE");
    }

    public synchronized User registerUser(String name, String email, String password, String role) {
        return registerUserWithEntity(name, email, password, role, "XAXX010101000", name + " Org", "ORGANIZACION_SOCIAL", "127.0.0.1");
    }

    public synchronized User registerUserWithEntity(String name, String email, String password, String role,
                                                    String rfc, String legalName, String entityType, String clientIp) {
        String normalizedEmail = email.trim().toLowerCase();
        if (getUserByEmail(normalizedEmail) != null) {
            return null; // Email ya registrado
        }

        String cleanRfc = (rfc != null) ? rfc.trim().toUpperCase() : "";
        String cleanLegalName = (legalName != null) ? legalName.trim() : "";
        String cleanEntityType = (entityType != null) ? entityType.trim().toUpperCase() : "";

        // Validar que el tipo de entidad sea estrictamente 'EMPRESA DONANTE' u 'ORGANIZACION_SOCIAL'
        if (!"EMPRESA DONANTE".equals(cleanEntityType) && !"ORGANIZACION_SOCIAL".equals(cleanEntityType)) {
            cleanEntityType = "ORGANIZACION_SOCIAL";
        }

        String normalizedRole = "DONANTE";
        if (role != null) {
            String r = role.trim().toUpperCase();
            if (r.equals("BENEFICIARIO")) normalizedRole = "BENEFICIARIO";
            else if (r.equals("ADMIN")) normalizedRole = "ADMIN";
        }

        String hash = PasswordUtil.hashPassword(password);
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String initialStatus = "PENDIENTE"; // HU03: Todo nuevo registro inicia en estado PENDIENTE

        if (useSqlite) {
            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);
                try {
                    int userId;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO users (name, email, password_hash, role, status, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                            Statement.RETURN_GENERATED_KEYS)) {
                        ps.setString(1, name.trim());
                        ps.setString(2, normalizedEmail);
                        ps.setString(3, hash);
                        ps.setString(4, normalizedRole);
                        ps.setString(5, initialStatus);
                        ps.setString(6, now);
                        ps.executeUpdate();

                        try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                            if (generatedKeys.next()) {
                                userId = generatedKeys.getInt(1);
                            } else {
                                throw new SQLException("Fallo al obtener ID autoincremental de usuario.");
                            }
                        }
                    }

                    // Insertar Entidad
                    try (PreparedStatement psEntity = conn.prepareStatement(
                            "INSERT INTO entidades (user_id, rfc, legal_name, entity_type, created_at) VALUES (?, ?, ?, ?, ?)")) {
                        psEntity.setInt(1, userId);
                        psEntity.setString(2, cleanRfc);
                        psEntity.setString(3, cleanLegalName.isEmpty() ? name.trim() : cleanLegalName);
                        psEntity.setString(4, cleanEntityType);
                        psEntity.setString(5, now);
                        psEntity.executeUpdate();
                    }

                    // Registrar auditoría de cuenta
                    try (PreparedStatement psAudit = conn.prepareStatement(
                            "INSERT INTO auditoria_cuentas (user_id, email, accion, detalles, ip_address, fecha) VALUES (?, ?, ?, ?, ?, ?)")) {
                        psAudit.setInt(1, userId);
                        psAudit.setString(2, normalizedEmail);
                        psAudit.setString(3, "REGISTRO");
                        psAudit.setString(4, "Registro de usuario con entidad. RFC: " + cleanRfc + ", Tipo: " + cleanEntityType + ", Estado: " + initialStatus);
                        psAudit.setString(5, clientIp != null ? clientIp : "127.0.0.1");
                        psAudit.setString(6, now);
                        psAudit.executeUpdate();
                    }

                    conn.commit();

                    User user = new User(userId, name.trim(), normalizedEmail, hash, normalizedRole, initialStatus, now);
                    user.setEntityInfo(cleanRfc, cleanLegalName.isEmpty() ? name.trim() : cleanLegalName, cleanEntityType);
                    return user;
                } catch (SQLException ex) {
                    conn.rollback();
                    System.err.println("Error en transacción de registro con entidad: " + ex.getMessage());
                    return null;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                System.err.println("Error de conexión SQLite en registro: " + e.getMessage());
                return null;
            }
        }

        // Fallback Memoria
        int newId = memoryUserIdSeq.getAndIncrement();
        User user = new User(newId, name.trim(), normalizedEmail, hash, normalizedRole, initialStatus, now);
        user.setEntityInfo(cleanRfc, cleanLegalName.isEmpty() ? name.trim() : cleanLegalName, cleanEntityType);
        memoryUsersById.put(newId, user);
        memoryUsersByEmail.put(normalizedEmail, user);

        Entity entity = new Entity(memoryEntityIdSeq.getAndIncrement(), newId, cleanRfc, cleanLegalName, cleanEntityType, now);
        memoryEntitiesByUserId.put(newId, entity);

        logAuditEvent(newId, normalizedEmail, "REGISTRO",
                "Registro de usuario con entidad. RFC: " + cleanRfc + ", Estado: " + initialStatus, clientIp);

        return user;
    }

    /* ==========================================================================
       VALIDACIÓN Y CAMBIO DE ESTADO DE CUENTAS (HU03: PENDIENTE -> ACTIVO/RECHAZADO)
       ========================================================================== */

    public synchronized boolean updateUserStatus(int userId, String newStatus, Integer adminUserId, String adminIp) {
        if (newStatus == null) return false;
        String statusUpper = newStatus.trim().toUpperCase();
        if (!"ACTIVO".equals(statusUpper) && !"RECHAZADO".equals(statusUpper) && !"PENDIENTE".equals(statusUpper)) {
            return false;
        }

        User target = getUserById(userId);
        if (target == null) return false;

        String oldStatus = target.getStatus();
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        if (useSqlite) {
            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);
                try {
                    try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET status = ? WHERE id = ?")) {
                        ps.setString(1, statusUpper);
                        ps.setInt(2, userId);
                        ps.executeUpdate();
                    }

                    try (PreparedStatement psAudit = conn.prepareStatement(
                            "INSERT INTO auditoria_cuentas (user_id, email, accion, detalles, ip_address, fecha) VALUES (?, ?, ?, ?, ?, ?)")) {
                        psAudit.setInt(1, userId);
                        psAudit.setString(2, target.getEmail());
                        psAudit.setString(3, "CAMBIO_ESTADO");
                        psAudit.setString(4, "Estado modificado de '" + oldStatus + "' a '" + statusUpper + "' por Admin ID: " + (adminUserId != null ? adminUserId : "Sistema"));
                        psAudit.setString(5, adminIp != null ? adminIp : "127.0.0.1");
                        psAudit.setString(6, now);
                        psAudit.executeUpdate();
                    }

                    conn.commit();
                    return true;
                } catch (SQLException e) {
                    conn.rollback();
                    System.err.println("Error al actualizar estado en SQLite: " + e.getMessage());
                    return false;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                System.err.println("Error de conexión al cambiar estado: " + e.getMessage());
                return false;
            }
        }

        target.setStatus(statusUpper);
        logAuditEvent(userId, target.getEmail(), "CAMBIO_ESTADO",
                "Estado modificado de '" + oldStatus + "' a '" + statusUpper + "' por Admin ID: " + adminUserId, adminIp);
        return true;
    }

    /* ==========================================================================
       TRAZABILIDAD Y AUDITORÍA DE CUENTAS (RNF02)
       ========================================================================== */

    public synchronized void logAuditEvent(Integer userId, String email, String accion, String detalles, String ipAddress) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String cleanIp = (ipAddress != null && !ipAddress.trim().isEmpty()) ? ipAddress.trim() : "127.0.0.1";

        if (useSqlite) {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO auditoria_cuentas (user_id, email, accion, detalles, ip_address, fecha) VALUES (?, ?, ?, ?, ?, ?)")) {
                if (userId != null) {
                    ps.setInt(1, userId);
                } else {
                    ps.setNull(1, Types.INTEGER);
                }
                ps.setString(2, email != null ? email.trim() : "");
                ps.setString(3, accion != null ? accion.trim().toUpperCase() : "DESCONOCIDO");
                ps.setString(4, detalles != null ? detalles.trim() : "");
                ps.setString(5, cleanIp);
                ps.setString(6, now);
                ps.executeUpdate();
                return;
            } catch (SQLException e) {
                System.err.println("Error al registrar auditoría en SQLite: " + e.getMessage());
            }
        }

        int id = memoryAuditIdSeq.getAndIncrement();
        memoryAuditLogs.add(new AuditLog(id, userId, email, accion, detalles, cleanIp, now));
    }

    public List<AuditLog> getAuditLogs() {
        List<AuditLog> list = new ArrayList<>();
        if (useSqlite) {
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM auditoria_cuentas ORDER BY id DESC LIMIT 100")) {
                while (rs.next()) {
                    int userId = rs.getInt("user_id");
                    list.add(new AuditLog(
                            rs.getInt("id"),
                            rs.wasNull() ? null : userId,
                            rs.getString("email"),
                            rs.getString("accion"),
                            rs.getString("detalles"),
                            rs.getString("ip_address"),
                            rs.getString("fecha")
                    ));
                }
                return list;
            } catch (SQLException e) {
                System.err.println("Error al consultar auditoría en SQLite: " + e.getMessage());
            }
        }

        synchronized (memoryAuditLogs) {
            List<AuditLog> copy = new ArrayList<>(memoryAuditLogs);
            copy.sort((a, b) -> Integer.compare(b.getId(), a.getId()));
            return copy;
        }
    }

    /* ==========================================================================
       CONSULTAS DE USUARIOS CON DATOS DE ENTIDAD
       ========================================================================== */

    public User getUserByEmail(String email) {
        if (email == null) return null;
        String normalized = email.trim().toLowerCase();

        if (useSqlite) {
            String sql = "SELECT u.*, e.rfc, e.legal_name, e.entity_type " +
                    "FROM users u " +
                    "LEFT JOIN entidades e ON u.id = e.user_id " +
                    "WHERE LOWER(u.email) = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, normalized);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        User user = new User(
                                rs.getInt("id"),
                                rs.getString("name"),
                                rs.getString("email"),
                                rs.getString("password_hash"),
                                rs.getString("role"),
                                rs.getString("status"),
                                rs.getString("created_at")
                        );
                        user.setEntityInfo(rs.getString("rfc"), rs.getString("legal_name"), rs.getString("entity_type"));
                        return user;
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
            String sql = "SELECT u.*, e.rfc, e.legal_name, e.entity_type " +
                    "FROM users u " +
                    "LEFT JOIN entidades e ON u.id = e.user_id " +
                    "WHERE u.id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        User user = new User(
                                rs.getInt("id"),
                                rs.getString("name"),
                                rs.getString("email"),
                                rs.getString("password_hash"),
                                rs.getString("role"),
                                rs.getString("status"),
                                rs.getString("created_at")
                        );
                        user.setEntityInfo(rs.getString("rfc"), rs.getString("legal_name"), rs.getString("entity_type"));
                        return user;
                    }
                }
            } catch (SQLException e) {
                System.err.println("Error al buscar usuario por ID en SQLite: " + e.getMessage());
            }
        }

        return memoryUsersById.get(id);
    }

    public List<User> getPendingUsers() {
        List<User> list = new ArrayList<>();
        if (useSqlite) {
            String sql = "SELECT u.*, e.rfc, e.legal_name, e.entity_type " +
                    "FROM users u " +
                    "LEFT JOIN entidades e ON u.id = e.user_id " +
                    "WHERE UPPER(u.status) = 'PENDIENTE' " +
                    "ORDER BY u.id ASC";
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    User u = new User(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("email"),
                            rs.getString("password_hash"),
                            rs.getString("role"),
                            rs.getString("status"),
                            rs.getString("created_at")
                    );
                    u.setEntityInfo(rs.getString("rfc"), rs.getString("legal_name"), rs.getString("entity_type"));
                    list.add(u);
                }
                return list;
            } catch (SQLException e) {
                System.err.println("Error al consultar usuarios pendientes en SQLite: " + e.getMessage());
            }
        }

        return memoryUsersById.values().stream()
                .filter(u -> "PENDIENTE".equalsIgnoreCase(u.getStatus()))
                .collect(Collectors.toList());
    }

    public List<User> getAllUsers() {
        List<User> list = new ArrayList<>();
        if (useSqlite) {
            String sql = "SELECT u.*, e.rfc, e.legal_name, e.entity_type " +
                    "FROM users u " +
                    "LEFT JOIN entidades e ON u.id = e.user_id " +
                    "ORDER BY u.id ASC";
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    User u = new User(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("email"),
                            rs.getString("password_hash"),
                            rs.getString("role"),
                            rs.getString("status"),
                            rs.getString("created_at")
                    );
                    u.setEntityInfo(rs.getString("rfc"), rs.getString("legal_name"), rs.getString("entity_type"));
                    list.add(u);
                }
                return list;
            } catch (SQLException e) {
                System.err.println("Error al obtener todos los usuarios de SQLite: " + e.getMessage());
            }
        }

        return new ArrayList<>(memoryUsersById.values());
    }

    /* ==========================================================================
       GESTIÓN DE DONACIONES
       ========================================================================== */

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
                        logAuditEvent(userId, donorEmail, "DONACION_REALIZADA",
                                "Donación registrada por $" + amount + " para causa: " + cause + " (RFC: " + cleanRfc + ")", "127.0.0.1");
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
        logAuditEvent(userId, donorEmail, "DONACION_REALIZADA", "Donación $" + amount + " para " + cause, "127.0.0.1");
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
