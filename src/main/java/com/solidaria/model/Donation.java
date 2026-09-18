package com.solidaria.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Donation {
    private int id;
    private Integer userId;
    private String donorName;
    private String donorEmail;
    private double amount;
    private String cause;
    private String paymentMethod;
    private String message;
    private String createdAt;

    public Donation(int id, Integer userId, String donorName, String donorEmail, double amount, String cause, String paymentMethod, String message) {
        this.id = id;
        this.userId = userId;
        this.donorName = donorName;
        this.donorEmail = donorEmail;
        this.amount = amount;
        this.cause = cause;
        this.paymentMethod = paymentMethod;
        this.message = message != null ? message : "";
        this.createdAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public int getId() { return id; }
    public Integer getUserId() { return userId; }
    public String getDonorName() { return donorName; }
    public String getDonorEmail() { return donorEmail; }
    public double getAmount() { return amount; }
    public String getCause() { return cause; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getMessage() { return message; }
    public String getCreatedAt() { return createdAt; }
}

