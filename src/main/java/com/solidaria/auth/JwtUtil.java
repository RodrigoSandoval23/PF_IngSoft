package com.solidaria.auth;

import com.solidaria.util.JsonUtil;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class JwtUtil {

    private static final String SECRET = "super_secreto_para_ingdesoft_actividad_5_jwt_seguro_2026_java";
    private static final String ALGORITHM = "HmacSHA256";
    private static final long EXPIRATION_SECONDS = 7200; // 2 horas

    /**
     * Genera un token JWT (RFC 7519) firmado con HMAC-SHA256.
     */
    public static String generateToken(int userId, String email, String name, String role) {
        try {
            long now = Instant.now().getEpochSecond();
            long exp = now + EXPIRATION_SECONDS;

            // 1. Header
            Map<String, Object> headerMap = new HashMap<>();
            headerMap.put("alg", "HS256");
            headerMap.put("typ", "JWT");
            String headerJson = JsonUtil.toJson(headerMap);
            String encodedHeader = base64UrlEncode(headerJson.getBytes(StandardCharsets.UTF_8));

            // 2. Payload
            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("sub", String.valueOf(userId));
            payloadMap.put("email", email);
            payloadMap.put("name", name);
            payloadMap.put("role", role);
            payloadMap.put("iat", now);
            payloadMap.put("exp", exp);
            String payloadJson = JsonUtil.toJson(payloadMap);
            String encodedPayload = base64UrlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));

            // 3. Firma HMAC-SHA256
            String dataToSign = encodedHeader + "." + encodedPayload;
            String signature = signHmacSha256(dataToSign, SECRET);

            return dataToSign + "." + signature;
        } catch (Exception e) {
            throw new RuntimeException("Error al generar token JWT", e);
        }
    }

    /**
     * Valida la firma y expiración del token JWT. Retorna los claims si es válido o null si es inválido.
     */
    public static Map<String, String> validateToken(String token) {
        if (token == null || token.trim().isEmpty()) return null;

        String[] parts = token.trim().split("\\.");
        if (parts.length != 3) return null;

        try {
            String dataToSign = parts[0] + "." + parts[1];
            String expectedSignature = signHmacSha256(dataToSign, SECRET);

            // Verificación segura contra timing attacks
            if (!MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    parts[2].getBytes(StandardCharsets.UTF_8))) {
                return null;
            }

            // Decodificar payload
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            String payloadJson = new String(payloadBytes, StandardCharsets.UTF_8);
            Map<String, String> claims = JsonUtil.parseSimpleJson(payloadJson);

            // Verificar tiempo de expiración
            if (claims.containsKey("exp")) {
                long exp = Long.parseLong(claims.get("exp"));
                long now = Instant.now().getEpochSecond();
                if (now > exp) {
                    return null; // Token expirado
                }
            }

            return claims;
        } catch (Exception e) {
            return null;
        }
    }

    private static String signHmacSha256(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance(ALGORITHM);
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
        mac.init(secretKey);
        byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return base64UrlEncode(hmacBytes);
    }

    private static String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

