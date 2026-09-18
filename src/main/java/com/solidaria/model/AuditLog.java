package com.solidaria.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class AuditLog {
    private int id;
    private Integer userId;
    private String email;
    private String accion; // 'REGISTRO', 'LOGIN_EXITOSO', 'LOGIN_FALLIDO', 'BLOQUEO_FUERZA_BRUTA', 'CAMBIO_ESTADO', etc.
    private String detalles;
    private String ipAddress;
    private String fecha;

    public AuditLog(int id, Integer userId, String email, String accion, String detalles, String ipAddress) {
        this.id = id;
        this.userId = userId;
        this.email = email;
        this.accion = accion;
        this.detalles = detalles;
        this.ipAddress = ipAddress;
        this.fecha = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public AuditLog(int id, Integer userId, String email, String accion, String detalles, String ipAddress, String fecha) {
        this.id = id;
        this.userId = userId;
        this.email = email;
        this.accion = accion;
        this.detalles = detalles;
        this.ipAddress = ipAddress;
        this.fecha = fecha;
    }

    public int getId() { return id; }
    public Integer getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getAccion() { return accion; }
    public String getDetalles() { return detalles; }
    public String getIpAddress() { return ipAddress; }
    public String getFecha() { return fecha; }
}

