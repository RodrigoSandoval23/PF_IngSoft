package com.solidaria.auth;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RateLimiter - Protección en memoria contra ataques de fuerza bruta (OWASP A07).
 * Monitorea intentos de autenticación fallidos por IP o identificador.
 */
public class RateLimiter {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_SECONDS = 10 * 60; // 10 minutos

    private static class AttemptRecord {
        int count;
        long firstAttemptTimestamp;
        long lockedUntilTimestamp;

        AttemptRecord(long now) {
            this.count = 1;
            this.firstAttemptTimestamp = now;
            this.lockedUntilTimestamp = 0;
        }
    }

    private final Map<String, AttemptRecord> attempts = new ConcurrentHashMap<>();

    private static final RateLimiter INSTANCE = new RateLimiter();

    public static RateLimiter getInstance() {
        return INSTANCE;
    }

    private RateLimiter() {}

    /**
     * Verifica si una llave (IP o usuario) se encuentra temporalmente bloqueada.
     */
    public synchronized boolean isBlocked(String key) {
        if (key == null) return false;
        AttemptRecord record = attempts.get(key);
        if (record == null) return false;

        long now = Instant.now().getEpochSecond();
        if (record.lockedUntilTimestamp > now) {
            return true;
        }

        // Si expiró el bloqueo, limpiar registro
        if (record.lockedUntilTimestamp > 0 && record.lockedUntilTimestamp <= now) {
            attempts.remove(key);
            return false;
        }

        // Si la ventana de tiempo expiró (más de 10 minutos desde el primer intento fallido)
        if (now - record.firstAttemptTimestamp > LOCKOUT_DURATION_SECONDS) {
            attempts.remove(key);
            return false;
        }

        return false;
    }

    /**
     * Registra un intento fallido y bloquea si se supera el umbral máximo.
     */
    public synchronized void recordFailedAttempt(String key) {
        if (key == null) return;
        long now = Instant.now().getEpochSecond();
        AttemptRecord record = attempts.get(key);

        if (record == null || (now - record.firstAttemptTimestamp > LOCKOUT_DURATION_SECONDS && record.lockedUntilTimestamp == 0)) {
            attempts.put(key, new AttemptRecord(now));
        } else {
            record.count++;
            if (record.count >= MAX_FAILED_ATTEMPTS) {
                record.lockedUntilTimestamp = now + LOCKOUT_DURATION_SECONDS;
            }
        }
    }

    /**
     * Limpia los intentos fallidos al tener un login exitoso.
     */
    public synchronized void resetAttempts(String key) {
        if (key != null) {
            attempts.remove(key);
        }
    }

    /**
     * Retorna los segundos restantes de bloqueo.
     */
    public synchronized long getRemainingLockoutSeconds(String key) {
        if (key == null) return 0;
        AttemptRecord record = attempts.get(key);
        if (record == null) return 0;

        long now = Instant.now().getEpochSecond();
        if (record.lockedUntilTimestamp > now) {
            return record.lockedUntilTimestamp - now;
        }
        return 0;
    }
}

