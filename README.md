# Sistema de Donaciones con Autenticación JWT

**Proyecto de Ingeniería de Software — Sprint 1: Autenticación**

Implementación en **Java 11**, **HTML5** y **CSS3** de un sistema de autenticación seguro con tokens **JWT**, cifrado de contraseñas **PBKDF2-HmacSHA256** y base de datos **SQLite**.

---

## Tecnologías Utilizadas

| Capa | Tecnología |
|------|-----------|
| Backend | Java 11 — `com.sun.net.httpserver.HttpServer` |
| Base de Datos | SQLite 3 (`donations.db`) — modo WAL |
| Autenticación | JWT (RFC 7519) firmado con HMAC-SHA256 |
| Criptografía | PBKDF2-HmacSHA256 + `SecureRandom` |
| Frontend | HTML5, CSS3, JavaScript vanilla |

---

## Estructura del Proyecto

```
IngDeSoft Act5/
├── compile.sh
├── run.sh
├── run_unit_tests.sh
├── src/
│   ├── main/java/com/solidaria/
│   │   ├── Main.java
│   │   ├── auth/
│   │   │   ├── JwtUtil.java
│   │   │   ├── PasswordUtil.java
│   │   │   └── RateLimiter.java
│   │   ├── db/
│   │   │   └── DataStore.java
│   │   ├── handlers/
│   │   │   ├── AuthHandler.java
│   │   │   ├── DonationHandler.java
│   │   │   ├── StatsHandler.java
│   │   │   ├── StaticFileHandler.java
│   │   │   └── HttpHelper.java
│   │   ├── model/
│   │   │   ├── User.java
│   │   │   ├── Entity.java
│   │   │   ├── Donation.java
│   │   │   └── AuditLog.java
│   │   └── util/
│   │       └── JsonUtil.java
│   └── test/java/com/solidaria/
│       └── UnitTests.java
└── static/
    ├── index.html
    ├── styles.css
    └── app.js
```

---

## Compilación y Ejecución

### 1. Compilar el proyecto
```bash
./compile.sh
```

### 2. Ejecutar pruebas unitarias
```bash
./run_unit_tests.sh
```

### 3. Iniciar el servidor
```bash
./run.sh
```
El servidor arranca en **[http://localhost:8080](http://localhost:8080)**.

---

## Credenciales Demo para Pruebas

El sistema incluye un usuario pre-configurado para facilitar la evaluación:

| Campo | Valor |
|-------|-------|
| Correo | `demo@donaciones.org` |
| Contraseña | `demo1234` |

> En el formulario de inicio de sesión existe un botón **"Autocompletar credenciales demo"** que rellena estos campos automáticamente.

---

## Análisis Estático de Código con SonarQube

El proyecto está configurado para análisis de calidad de código, detección de vulnerabilidades y Code Smells mediante **SonarQube** ejecutado en Docker.

### Prerrequisitos

1. **Docker Desktop** activo y en ejecución.
2. Servidor **SonarQube** disponible en la red — `http://192.168.1.73:9000`.
3. **Token de autenticación** de SonarQube generado.

---

### Paso 1 — Compilar el proyecto

SonarQube requiere los binarios `.class` para analizar Java en profundidad:

```bash
./compile.sh
```

---

### Paso 2 — Ejecutar el escáner con Docker

```bash
docker run --rm \
  -v "${PWD}:/usr/src" \
  -e SONAR_SCANNER_OPTS="-Dsonar.projectKey=PF_IngSoft \
    -Dsonar.sources=. \
    -Dsonar.java.binaries=. \
    -Dsonar.host.url=http://192.168.1.73:9000 \
    -Dsonar.login=sqp_9894833601aa407de0cb41233cfd34eeff548315" \
  sonarsource/sonar-scanner-cli
```

**Explicación de parámetros:**

| Parámetro | Descripción |
|-----------|------------|
| `--rm` | Elimina el contenedor al finalizar el escaneo |
| `-v "${PWD}:/usr/src"` | Mapea el directorio del proyecto al contenedor |
| `sonar.projectKey` | Clave del proyecto en SonarQube (`PF_IngSoft`) |
| `sonar.sources` | Directorio raíz de código fuente a analizar |
| `sonar.java.binaries` | Ruta de los `.class` compilados |
| `sonar.host.url` | Dirección del servidor SonarQube |
| `sonar.login` | Token de acceso seguro |

---

### Paso 3 — Consultar el reporte

Cuando el escáner muestre `EXECUTION SUCCESS`, abre el dashboard:

**[http://192.168.1.73:9000/dashboard?id=PF_IngSoft](http://192.168.1.73:9000/dashboard?id=PF_IngSoft)**

---

## Pruebas Unitarias

Las pruebas están en [`src/test/java/com/solidaria/UnitTests.java`](src/test/java/com/solidaria/UnitTests.java) y cubren:

| Módulo | Casos de prueba |
|--------|----------------|
| `PasswordUtil` | Hash, verificación correcta, contraseña incorrecta |
| `JwtUtil` | Generación, verificación, token inválido |
| `RateLimiter` | Bloqueo tras intentos, reseteo |
| `JsonUtil` | Escape de caracteres, serialización, parseo |
| Modelos | `User`, `Entity`, `AuditLog` — constructores y campos |
| RBAC | Verificación de roles admin/donante |

Ejecutar con:
```bash
./run_unit_tests.sh
```
