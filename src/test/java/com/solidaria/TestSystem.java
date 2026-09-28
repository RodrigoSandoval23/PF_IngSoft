package com.solidaria;

// Importación de utilidades relacionadas con autenticación y seguridad.
import java.util.List;
import java.util.Map;

import com.solidaria.auth.JwtUtil;
import com.solidaria.auth.PasswordUtil;
import com.solidaria.auth.RateLimiter;
import com.solidaria.db.DataStore;
import com.solidaria.model.AuditLog;
import com.solidaria.model.Donation;
import com.solidaria.model.User;

/**
 * Clase encargada de ejecutar pruebas generales del sistema.
 * Las pruebas verifican aspectos funcionales y de ciberseguridad como:
 * - Protección de contraseñas.
 * - Generación y validación de tokens JWT.
 * - Registro de usuarios y entidades.
 * - Flujo de estados de las cuentas.
 * - Auditoría de operaciones.
 * - Protección contra ataques de fuerza bruta.
 * - Registro y persistencia de donaciones.
 */
public class TestSystem {

    /**
     * Método principal desde el cual se ejecuta toda la suite de pruebas.
     * Si alguna prueba falla, se lanza una RuntimeException indicando
     * el problema encontrado. Si ninguna falla, se muestra un mensaje
     * confirmando que todas las pruebas fueron satisfactorias.
     */
    public static void main(String[] args) {

        // Encabezado de inicio de las pruebas.
        System.out.println("==========================================================================");
        System.out.println("   INICIANDO SUITE DE PRUEBAS DE SISTEMA Y CIBERSEGURIDAD");
        System.out.println("==========================================================================");


        /*
         * ================================================================
         * PRUEBA 1: CRIPTOGRAFÍA DE CONTRASEÑAS
         * ================================================================
         * Se comprueba que las contraseñas puedan almacenarse de forma
         * segura mediante PBKDF2-HMAC-SHA256.
         * La prueba verifica dos situaciones:
         * 1. Que una contraseña correcta coincida con su hash.
         * 2. Que una contraseña incorrecta sea rechazada.
         */

        System.out.println("\n=== 1. Probando Criptografía de Contraseñas (PBKDF2-HMAC-SHA256) ===");

        // Contraseña utilizada únicamente para la prueba.
        String password = "claveSuperSegura123";

        // Genera un hash seguro a partir de la contraseña.
        String hash = PasswordUtil.hashPassword(password);

        // Se muestra el resultado para comprobar que la contraseña
        // no se almacena directamente en texto plano.
        System.out.println("Hash generado: " + hash);

        // Comprueba que la contraseña original sea reconocida correctamente.
        boolean match = PasswordUtil.verifyPassword(password, hash);

        // Comprueba que una contraseña incorrecta sea rechazada.
        boolean badMatch = PasswordUtil.verifyPassword(
                "claveIncorrecta",
                hash
        );

        // Si la contraseña correcta no coincide o una incorrecta es aceptada,
        // significa que existe un problema en el mecanismo de protección.
        if (!match || badMatch) {
            throw new RuntimeException(
                    "Fallo en verificación de contraseñas con PBKDF2"
            );
        }

        System.out.println("✔ Hashing y verificación con PBKDF2: OK");


        /*
         * ================================================================
         * PRUEBA 2: TOKENS JWT
         * ================================================================
         * Se verifica el funcionamiento de los JSON Web Tokens (JWT).
         * La prueba comprueba:
         * - Generación de un token.
         * - Validación de su firma.
         * - Recuperación correcta de los claims.
         * - Rechazo de un token modificado.
         */

        System.out.println("\n=== 2. Probando Tokens JWT (RFC 7519, HMAC-SHA256) ===");

        /*
         * Genera un JWT que representa una sesión autenticada.
         * Los datos enviados son:
         * - ID del usuario.
         * - Correo electrónico.
         * - Nombre.
         * - Rol.
         */
        String token = JwtUtil.generateToken(
                10,
                "donante@test.com",
                "Donante de Prueba",
                "DONANTE"
        );

        System.out.println("Token JWT generado: " + token);

        /*
         * Valida la firma del token y obtiene la información
         * almacenada dentro de sus claims.
         */
        Map<String, String> claims = JwtUtil.validateToken(token);

        // Si devuelve null, el token no pudo ser validado.
        if (claims == null) {
            throw new RuntimeException(
                    "Fallo: Token JWT válido no pudo ser verificado."
            );
        }

        /*
         * Verifica que la información almacenada dentro del JWT
         * corresponda con la información utilizada al generarlo.
         * "sub" corresponde al identificador del usuario.
         */
        if (!"10".equals(claims.get("sub"))
                || !"donante@test.com".equals(claims.get("email"))) {

            throw new RuntimeException(
                    "Fallo: Los claims del token no coinciden."
            );
        }

        System.out.println(
                "✔ Firma digital HMAC-SHA256 y claims JWT: OK"
        );


        /*
         * Prueba de manipulación del token.
         * Se modifican los últimos caracteres del JWT para simular
         * que un atacante intenta alterar su contenido o firma.
         */
        String tamperedToken =
                token.substring(0, token.length() - 4) + "XXXX";

        /*
         * Un token modificado debe ser rechazado por la validación.
         * Si el sistema lo acepta, existiría un problema grave de seguridad.
         */
        if (JwtUtil.validateToken(tamperedToken) != null) {
            throw new RuntimeException(
                    "Fallo: Token manipulado fue aceptado."
            );
        }

        System.out.println(
                "✔ Detección de token adulterado: OK"
        );


        /*
         * ================================================================
         * PRUEBA 3: REGISTRO DE ENTIDADES
         * ================================================================
         * Se verifica que el sistema permita registrar un usuario
         * perteneciente a una entidad y almacenar correctamente:
         * - Nombre.
         * - Correo.
         * - Contraseña.
         * - Rol.
         * - RFC.
         * - Nombre de la entidad.
         * - Tipo de entidad.
         * - Dirección IP desde donde se realizó el registro.
         */

        System.out.println(
                "\n=== 3. Probando Registro de Entidades con RFC y Tipo Restrictivo ==="
        );

        // Obtiene la instancia única del almacén de datos.
        DataStore ds = DataStore.getInstance();

        /*
         * Obtiene la fecha y hora actual en milisegundos.
         * Se utiliza para generar datos únicos y evitar conflictos
         * con registros realizados en ejecuciones anteriores.
         */
        long ts = System.currentTimeMillis();

        // Genera un RFC de prueba dinámico.
        String testRfc =
                "RFC" + (ts % 1000000000) + "AB";

        // Genera un correo único para esta ejecución.
        String testEmail =
                "empresa_" + ts + "@donaciones.org";

        // Registra al usuario junto con los datos de su entidad.
         
        User registeredUser = ds.registerUserWithEntity(
                "Ing. Roberto Garza",
                testEmail,
                "empresaPass2026",
                "DONANTE",
                testRfc,
                "Corporativo Garza S.A. de C.V.",
                "EMPRESA DONANTE",
                "192.168.1.100"
        );

        // Comprueba que el registro haya sido realizado.
        if (registeredUser == null) {
            throw new RuntimeException(
                    "Fallo al registrar usuario con entidad y RFC."
            );
        }

        /*
         * Comprueba que el RFC y el tipo de entidad recuperados
         * coincidan con los datos enviados durante el registro.
         */
        if (!testRfc.equals(registeredUser.getRfc())
                || !"EMPRESA DONANTE".equals(
                        registeredUser.getEntityType())) {

            throw new RuntimeException(
                    "Fallo: Los datos de la entidad no concuerdan con los registrados."
            );
        }

        System.out.println(
                "✔ Registro de Entidad y RFC persistido en SQLite: OK "
                        + "(RFC: "
                        + registeredUser.getRfc()
                        + ", Tipo: "
                        + registeredUser.getEntityType()
                        + ")"
        );


        /*
         * ================================================================
         * PRUEBA 4: FLUJO DE ESTADOS DE CUENTA - HU03
         * ================================================================
         * Según la HU03, una cuenta recién registrada debe comenzar
         * con el estado PENDIENTE.
         * Posteriormente, un administrador puede aprobarla y cambiarla
         * al estado ACTIVO.
         */

        System.out.println(
                "\n=== 4. Probando Flujo de Estados de Cuenta (HU03) ==="
        );

        // Comprueba el estado inicial asignado a la cuenta.
        if (!"PENDIENTE".equalsIgnoreCase(
                registeredUser.getStatus())) {

            throw new RuntimeException(
                    "Fallo: El estado inicial de un usuario nuevo debe ser "
                            + "'PENDIENTE'. Obtenido: "
                            + registeredUser.getStatus()
            );
        }

        System.out.println(
                "✔ Estado inicial PENDIENTE asignado correctamente: OK"
        );


        /*
         * Simula la aprobación de la cuenta por un administrador.
         * Se envían:
         * - ID del usuario.
         * - Nuevo estado.
         * - ID del administrador.
         * - IP desde donde se realizó la operación.
         */
        boolean statusUpdated = ds.updateUserStatus(
                registeredUser.getId(),
                "ACTIVO",
                1,
                "127.0.0.1"
        );

        // Comprueba que la actualización haya sido realizada.
        if (!statusUpdated) {
            throw new RuntimeException(
                    "Fallo al actualizar estado de cuenta por administrador."
            );
        }

        /*
         * Recupera nuevamente al usuario desde la base de datos
         * para comprobar que el cambio haya sido persistido.
         */
        User activeUser =
                ds.getUserById(registeredUser.getId());

        // Verifica que la cuenta tenga ahora el estado ACTIVO.
        if (!"ACTIVO".equalsIgnoreCase(activeUser.getStatus())) {

            throw new RuntimeException(
                    "Fallo: La cuenta debió quedar en estado ACTIVO "
                            + "tras aprobación."
            );
        }

        System.out.println(
                "✔ Aprobación de cuenta por Administrador "
                        + "(PENDIENTE -> ACTIVO): OK"
        );


        /*
         * ================================================================
         * PRUEBA 5: TRAZABILIDAD Y AUDITORÍA - RNF02
         * ================================================================
         * Se verifica que las acciones importantes realizadas sobre
         * las cuentas queden almacenadas en la bitácora de auditoría.
         *
         * En este caso deben existir registros correspondientes a:
         * - REGISTRO.
         * - CAMBIO_ESTADO.
         */

        System.out.println(
                "\n=== 5. Probando Bitácora de Auditoría de Cuentas (RNF02) ==="
        );

        // Recupera todos los registros de auditoría.
        List<AuditLog> logs = ds.getAuditLogs();

        // La bitácora no debe estar vacía después de las operaciones anteriores.
        if (logs.isEmpty()) {
            throw new RuntimeException(
                    "Fallo: La tabla auditoria_cuentas no contiene registros."
            );
        }

        /*
         * Variables utilizadas para indicar si fueron encontrados
         * los eventos esperados.
         */
        boolean foundRegisterAudit = false;
        boolean foundStatusAudit = false;

        // Recorre cada registro almacenado en la bitácora.
        for (AuditLog l : logs) {

            /*
             * Busca el evento correspondiente al registro inicial
             * del usuario de prueba.
             */
            if ("REGISTRO".equals(l.getAccion())
                    && testEmail.equals(l.getEmail())) {

                foundRegisterAudit = true;
            }

            /*
             * Busca el evento correspondiente al cambio de estado
             * de PENDIENTE a ACTIVO.
             */
            if ("CAMBIO_ESTADO".equals(l.getAccion())
                    && testEmail.equals(l.getEmail())) {

                foundStatusAudit = true;
            }
        }

        /*
         * Ambos eventos deben existir para garantizar
         * la trazabilidad de las operaciones.
         */
        if (!foundRegisterAudit || !foundStatusAudit) {
            throw new RuntimeException(
                    "Fallo: Los eventos de registro y cambio de estado "
                            + "no fueron auditados en auditoria_cuentas."
            );
        }

        System.out.println(
                "✔ Trazabilidad verificada en auditoria_cuentas "
                        + "(REGISTRO y CAMBIO_ESTADO): OK"
        );


        /*
         * ================================================================
         * PRUEBA 6: PROTECCIÓN CONTRA FUERZA BRUTA - OWASP A07
         * ================================================================
         * Se verifica el mecanismo RateLimiter encargado de limitar
         * los intentos fallidos de inicio de sesión.
         * La prueba simula cinco intentos incorrectos provenientes
         * de la misma dirección IP.
         */

        System.out.println(
                "\n=== 6. Probando Protección contra Ataques de Fuerza Bruta (RateLimiter) ==="
        );

        // Obtiene la instancia del controlador de intentos.
        RateLimiter limiter = RateLimiter.getInstance();

        // Dirección IP utilizada únicamente para simular el ataque.
        String attackIp = "192.0.2.77";

        /*
         * Limpia posibles intentos registrados previamente
         * para comenzar la prueba desde cero.
         */
        limiter.resetAttempts(attackIp);

        // Una IP sin intentos fallidos no debe estar bloqueada.
        if (limiter.isBlocked(attackIp)) {
            throw new RuntimeException(
                    "Fallo: IP limpia no debe estar bloqueada."
            );
        }


        /*
         * Simula cinco intentos fallidos consecutivos.
         * Esto representa un posible intento de ataque
         * de fuerza bruta contra el sistema.
         */
        for (int i = 1; i <= 5; i++) {
            limiter.recordFailedAttempt(attackIp);
        }

        /*
         * Después de alcanzar el límite establecido,
         * la dirección IP debe quedar bloqueada.
         */
        if (!limiter.isBlocked(attackIp)) {
            throw new RuntimeException(
                    "Fallo: IP no fue bloqueada tras 5 intentos fallidos."
            );
        }

        /*
         * Obtiene el tiempo restante durante el cual
         * la dirección IP permanecerá bloqueada.
         */
        long lockout =
                limiter.getRemainingLockoutSeconds(attackIp);

        // El tiempo de bloqueo debe ser mayor que cero.
        if (lockout <= 0) {
            throw new RuntimeException(
                    "Fallo: Tiempo de espera de bloqueo inválido."
            );
        }

        System.out.println(
                "✔ Bloqueo por Fuerza Bruta activado tras "
                        + "5 intentos fallidos: OK ("
                        + lockout
                        + "s de espera)"
        );


        /*
         * Simula que posteriormente se permite restablecer
         * los intentos asociados con la dirección IP.
         */
        limiter.resetAttempts(attackIp);

        /*
         * Después del restablecimiento, la IP debe volver
         * a estar disponible.
         */
        if (limiter.isBlocked(attackIp)) {
            throw new RuntimeException(
                    "Fallo: Restablecimiento de intentos falló."
            );
        }

        System.out.println(
                "✔ Restablecimiento de intentos de fuerza bruta "
                        + "tras éxito: OK"
        );


        /*
         * ================================================================
         * PRUEBA 7: DONACIONES Y PERSISTENCIA
         * ================================================================
         * Se verifica que un usuario activo pueda realizar
         * una donación y que esta quede asociada correctamente
         * con la entidad y su RFC.
         */

        System.out.println(
                "\n=== 7. Probando Donaciones y Estadísticas ==="
        );

        /*
         * Registra una nueva donación utilizando el usuario
         * que previamente fue aprobado.
         *
         * Información almacenada:
         * - ID del usuario.
         * - Nombre.
         * - Correo.
         * - Monto.
         * - Causa.
         * - Método de pago.
         * - Descripción.
         * - RFC.
         */
        Donation don = ds.addDonation(
                activeUser.getId(),
                activeUser.getName(),
                activeUser.getEmail(),
                250.0,
                "Educación para Niños",
                "tarjeta",
                "Donación Empresarial",
                activeUser.getRfc()
        );

        /*
         * Se comprueba que:
         * 1. La donación haya sido creada.
         * 2. El RFC almacenado coincida con el de la entidad donante.
         */
        if (don == null
                || !activeUser.getRfc().equals(don.getRfc())) {

            throw new RuntimeException(
                    "Fallo al persistir donación con RFC de la entidad."
            );
        }

        System.out.println(
                "✔ Donación vinculada a entidad y RFC: OK "
                        + "(#DON-"
                        + don.getId()
                        + " - $"
                        + don.getAmount()
                        + " USD)"
        );


        /*
         * Obtiene las estadísticas generales del sistema
         * después de registrar la donación.
         */
        Map<String, Object> stats = ds.getStats();

        System.out.println(
                "✔ Estadísticas calculadas: OK (" + stats + ")"
        );


        /*
         * ================================================================
         * RESULTADO FINAL
         * ================================================================
         * Si la ejecución llegó hasta este punto significa que ninguna
         * de las validaciones anteriores lanzó una excepción.
         * Por lo tanto, todas las pruebas fueron completadas correctamente.
         */
        System.out.println("\n==========================================================================");
        System.out.println(
                "🎉 ¡TODAS LAS PRUEBAS DE CIBERSEGURIDAD Y SISTEMA "
                        + "PASARON SATISFACTORIAMENTE!"
        );
        System.out.println("==========================================================================");
    }
}