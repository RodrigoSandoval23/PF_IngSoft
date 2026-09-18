package com.solidaria.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Entity {
    private int id;
    private int userId;
    private String rfc;
    private String legalName;
    private String entityType; // 'EMPRESA DONANTE' o 'ORGANIZACION_SOCIAL'
    private String createdAt;

    public Entity(int id, int userId, String rfc, String legalName, String entityType) {
        this.id = id;
        this.userId = userId;
        this.rfc = rfc;
        this.legalName = legalName;
        this.entityType = entityType;
        this.createdAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public Entity(int id, int userId, String rfc, String legalName, String entityType, String createdAt) {
        this.id = id;
        this.userId = userId;
        this.rfc = rfc;
        this.legalName = legalName;
        this.entityType = entityType;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public int getUserId() { return userId; }
    public String getRfc() { return rfc; }
    public String getLegalName() { return legalName; }
    public String getEntityType() { return entityType; }
    public String getCreatedAt() { return createdAt; }

    public void setRfc(String rfc) { this.rfc = rfc; }
    public void setLegalName(String legalName) { this.legalName = legalName; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
}

