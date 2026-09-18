package com.solidaria.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class User {
    private int id;
    private String name;
    private String email;
    private String passwordHash;
    private String role; // 'DONANTE', 'BENEFICIARIO', 'ADMIN'
    private String status; // 'PENDIENTE', 'ACTIVO', 'RECHAZADO'
    private String createdAt;

    // Datos de entidad opcionales (cargados mediante JOIN o asignados en registro)
    private String rfc;
    private String legalName;
    private String entityType; // 'EMPRESA DONANTE' o 'ORGANIZACION_SOCIAL'

    public User(int id, String name, String email, String passwordHash, String role) {
        this(id, name, email, passwordHash, role, "PENDIENTE",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }

    public User(int id, String name, String email, String passwordHash, String role, String status, String createdAt) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = normalizeRole(role);
        this.status = (status != null && !status.trim().isEmpty()) ? status.trim().toUpperCase() : "PENDIENTE";
        this.createdAt = (createdAt != null) ? createdAt : LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static String normalizeRole(String r) {
        if (r == null) return "DONANTE";
        String upper = r.trim().toUpperCase();
        if (upper.equals("ADMIN") || upper.equals("SUPERUSER")) return "ADMIN";
        if (upper.equals("BENEFICIARIO")) return "BENEFICIARIO";
        return "DONANTE";
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
    public String getStatus() { return status; }
    public String getCreatedAt() { return createdAt; }

    public String getRfc() { return rfc; }
    public String getLegalName() { return legalName; }
    public String getEntityType() { return entityType; }

    public void setName(String name) { this.name = name; }
    public void setEmail(String email) { this.email = email; }
    public void setRole(String role) { this.role = normalizeRole(role); }
    public void setStatus(String status) { this.status = status; }

    public void setEntityInfo(String rfc, String legalName, String entityType) {
        this.rfc = rfc;
        this.legalName = legalName;
        this.entityType = entityType;
    }
}
