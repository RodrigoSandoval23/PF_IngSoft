package com.solidaria.db;

import com.solidaria.auth.PasswordUtil;
import com.solidaria.model.Donation;
import com.solidaria.model.User;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class DataStore {

    private static final DataStore INSTANCE = new DataStore();

    private final Map<Integer, User> usersById = new ConcurrentHashMap<>();
    private final Map<String, User> usersByEmail = new ConcurrentHashMap<>();
    private final List<Donation> donations = Collections.synchronizedList(new ArrayList<>());

    private final AtomicInteger userIdSeq = new AtomicInteger(1);
    private final AtomicInteger donationIdSeq = new AtomicInteger(1);

    private DataStore() {
        seedInitialData();
    }

    public static DataStore getInstance() {
        return INSTANCE;
    }

    private void seedInitialData() {
        // Usuario demo
        String demoHash = PasswordUtil.hashPassword("demo1234");
        User demoUser = new User(
                userIdSeq.getAndIncrement(),
                "Donante Solidario",
                "demo@donaciones.org",
                demoHash,
                "donor"
        );
        usersById.put(demoUser.getId(), demoUser);
        usersByEmail.put(demoUser.getEmail().toLowerCase(), demoUser);

        // Donaciones demo
        addDonation(
                demoUser.getId(),
                demoUser.getName(),
                demoUser.getEmail(),
                50.0,
                "Educación para Niños",
                "tarjeta",
                "¡Mucho éxito con la labor escolar!"
        );
        addDonation(
                demoUser.getId(),
                demoUser.getName(),
                demoUser.getEmail(),
                25.0,
                "Refugio Animal",
                "paypal",
                "Para alimento y atención de los perritos."
        );
    }

    public synchronized User registerUser(String name, String email, String password) {
        String normalizedEmail = email.trim().toLowerCase();
        if (usersByEmail.containsKey(normalizedEmail)) {
            return null; // Ya existe
        }

        String hash = PasswordUtil.hashPassword(password);
        int newId = userIdSeq.getAndIncrement();
        User user = new User(newId, name.trim(), normalizedEmail, hash, "donor");

        usersById.put(newId, user);
        usersByEmail.put(normalizedEmail, user);
        return user;
    }

    public User getUserByEmail(String email) {
        if (email == null) return null;
        return usersByEmail.get(email.trim().toLowerCase());
    }

    public User getUserById(int id) {
        return usersById.get(id);
    }

    public synchronized Donation addDonation(Integer userId, String donorName, String donorEmail, double amount, String cause, String paymentMethod, String message) {
        int id = donationIdSeq.getAndIncrement();
        Donation donation = new Donation(id, userId, donorName, donorEmail, amount, cause, paymentMethod, message);
        donations.add(donation);
        return donation;
    }

    public List<Donation> getDonationsByUserId(int userId) {
        synchronized (donations) {
            return donations.stream()
                    .filter(d -> d.getUserId() != null && d.getUserId() == userId)
                    .sorted((a, b) -> Integer.compare(b.getId(), a.getId()))
                    .collect(Collectors.toList());
        }
    }

    public List<Donation> getAllDonations() {
        synchronized (donations) {
            return new ArrayList<>(donations);
        }
    }

    public Map<String, Object> getStats() {
        synchronized (donations) {
            double total = donations.stream().mapToDouble(Donation::getAmount).sum();
            int count = donations.size();
            double goal = 5000.0;
            double progress = Math.min(Math.round((total / goal) * 1000.0) / 10.0, 100.0);

            Map<String, Object> stats = new HashMap<>();
            stats.put("total_raised", total);
            stats.put("goal", goal);
            stats.put("donations_count", count);
            stats.put("progress_percentage", progress);
            return stats;
        }
    }
}

