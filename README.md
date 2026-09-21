# Sistema de Donaciones con Autenticación JWT

**Proyecto de Ingeniería de Software - Actividad 5**

Implementación completa en **Java 11**, **HTML5** y **CSS3** de un sistema de inicio de sesión seguro con tokens **JWT (JSON Web Tokens)**, gestión visual del **ícono y estado de sesión** del donante, y una **ventana interactiva de donaciones**.

---

## Tecnologías Utilizadas

- **Backend**: Java 11 (`com.sun.net.httpserver.HttpServer`).
- **Base de Datos**: **SQLite 3** (`donations.db`) con modo concurrente WAL y tablas `users` y `donations`.
- **Autenticación**: JSON Web Tokens (**JWT**, RFC 7519) firmados con algoritmo **HMAC-SHA256** (`javax.crypto.Mac`).
- **Criptografía de Contraseñas**: **PBKDF2 con HmacSHA256** y sal criptográfica aleatoria (`SecureRandom`).
- **Superusuario (Admin)**: Cuenta de administración (`admin@donaciones.org`) con acceso exclusivo para consultar todos los usuarios y donaciones en la base de datos.
- **Frontend**: **HTML5 semántico**, **CSS3 moderno** (diseño responsivo, glassmorphism) y **JavaScript vanilla** para el manejo de sesiones y peticiones HTTP.

---

## Características Principales

1. **Gestión e Ícono de Sesión (Navbar)**:
   - **No autenticado**: Muestra botón con ícono para iniciar sesión o registrarse.
   - **Autenticado con JWT**: Transforma el botón en un avatar interactivo con iniciales, punto indicador de estado activo, nombre del usuario y etiqueta `JWT Activo`.
   - **Menú Desplegable**:
     - Nombre y correo del donante.
     - **Mis Donaciones**: Consulta del historial personal en la API protegida por JWT.
     - **Inspeccionar Token JWT**: Visor en tiempo real que desglosa el Header y Payload del token en formato JSON.
     - **Cerrar Sesión**: Limpieza del token de `localStorage` y actualización instantánea de la interfaz.

2. **Ventana de Donaciones**:
   - Selección de causa benéfica activa (Educación, Refugio Animal, Reforestación, Comedores).
   - Montos predefinidos ($10, $25, $50, $100) y campo para monto personalizado.
   - Vinculación automática con la cuenta del usuario logueado vía JWT.
   - Pasarela de pago simulada (Tarjeta, PayPal, Transferencia).
   - Generación de comprobante/recibo digital con folio único de donación.
   - Barra de progreso hacia la meta comunitaria ($5,000 USD).

---

## Estructura del Proyecto

```
IngDeSoft Act5/
├── pom.xml                               # Configuración de Maven para presentación académica
├── compile.sh                            # Script para compilar el código Java con javac
├── run.sh                                # Script para ejecutar el servidor Java en http://localhost:8080
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── solidaria/
│   │   │           ├── Main.java         # Servidor HTTP y configuración de rutas
│   │   │           ├── auth/
│   │   │           │   ├── JwtUtil.java  # Emisión, firma HMAC-SHA256 y verificación de JWT
│   │   │           │   └── PasswordUtil.java # Cifrado PBKDF2 y salting de contraseñas
│   │   │           ├── db/
│   │   │           │   └── DataStore.java# Persistencia concurrente de usuarios y donaciones
│   │   │           ├── handlers/
│   │   │           │   ├── AuthHandler.java       # Rutas /api/auth/*
│   │   │           │   ├── DonationHandler.java   # Rutas /api/donations/*
│   │   │           │   ├── StatsHandler.java      # Ruta /api/donations/stats
│   │   │           │   ├── StaticFileHandler.java # Servidor de archivos HTML/CSS/JS
│   │   │           │   └── HttpHelper.java        # CORS, utilidades HTTP y validación de Bearer
│   │   │           ├── model/
│   │   │           │   ├── User.java              # Entidad de usuario
│   │   │           │   └── Donation.java          # Entidad de donación
│   │   │           └── util/
│   │   │               └── JsonUtil.java          # Serializador y parser JSON ligero
│   │   └── resources/
│   │       └── static/
│   │           ├── index.html            # Interfaz de usuario y ventana de donaciones
│   │           ├── styles.css            # Hoja de estilos moderna y responsiva
│   │           └── app.js                # Lógica del cliente y consumo de API con JWT
│   └── test/
│       └── java/
│           └── com/
│               └── solidaria/
│                   └── TestSystem.java   # Suite de pruebas unitarias automáticas
└── static/                               # Archivos estáticos de acceso directo
    ├── index.html
    ├── styles.css
    └── app.js
```

---

## Instrucciones de Compilación y Ejecución

### 1. Compilar el proyecto
```bash
./compile.sh
```

### 2. Ejecutar las pruebas unitarias
```bash
javac -d bin $(find src/main/java src/test/java -name "*.java") && java -cp bin com.solidaria.TestSystem
```

### 3. Iniciar el servidor
```bash
./run.sh
```
*El servidor arrancará por defecto en `http://localhost:8080`.*

### 4. Abrir en el navegador
Ingresa a: **[http://localhost:8080](http://localhost:8080)**

---

## Credenciales Demo para Pruebas

El sistema cuenta con un usuario inicial pre-configurado para agilizar la evaluación:
- **Correo**: `demo@donaciones.org`
- **Contraseña**: `demo1234`
*(En la ventana modal de inicio de sesión existe un botón para autocompletar automáticamente estos datos).*

## Análisis Estático de Código con SonarQube
El proyecto está configurado para realizar análisis estático de calidad de código, detección de vulnerabilidades de seguridad y Code Smells mediante **SonarQube** en Docker.

## Prerrequisitos
1. Docker Desktop activo y en ejecución.
2. Servidor de SonarQube disponible en la red (ej. http://192.168.1.73:9000).
3. Token de autenticación de SonarQube generado.

## Pasos para realizar el análisis
## Paso 1: Compilar el proyecto
SonarQube requiere los archivos binarios compilados (.class) para analizar el código Java en profundidad:

**Bash**
```bash
./compile.sh
```
## Paso 2: Ejecutar el escáner con Docker
Ejecuta el siguiente comando en la raíz del proyecto para iniciar la revisión mediante la imagen oficial de sonarsource/sonar-scanner-cli:

Bash
```bash
docker run --rm \
  -v "${PWD}:/usr/src" \
  -e SONAR_SCANNER_OPTS="-Dsonar.projectKey=PF_IngSoft -Dsonar.sources=. -Dsonar.java.binaries=. -Dsonar.host.url=[http://192.168.1.73:9000](http://192.168.1.73:9000) -Dsonar.login=sqp_9894833601aa407de0cb41233cfd34eeff548315" \
  sonarsource/sonar-scanner-cli
```
**Explicación de parámetros:**
- --rm: Elimina el contenedor al finalizar el escaneo.
- -v "${PWD}:/usr/src": Mapea el proyecto local hacia el contenedor.
- -Dsonar.projectKey=PF_IngSoft: Clave del proyecto dentro de SonarQube.
- -Dsonar.sources=.: Analiza la fuente dentro del directorio actual.
- -Dsonar.java.binaries=.: Analiza las clases Java compiladas.
- -Dsonar.host.url: Dirección del servidor de SonarQube.
- -Dsonar.login: Token de acceso seguro.

## Paso 3: Consultar reporte
Cuando finalice la ejecución (EXECUTION SUCCESS), puedes ver las métricas de calidad ingresando al panel Web:
👉 
```bash
http://192.168.1.73:9000/dashboard?id=PF_IngSoft
```

## Credenciales Demo para Pruebas
El sistema cuenta con un usuario inicial pre-configurado para agilizar la evaluación:

**Correo:** demo@donaciones.org

**Contraseña:** demo1234
(En la ventana modal de inicio de sesión existe un botón para autocompletar automáticamente estos datos).


---

## Credenciales Demo para Pruebas

El sistema cuenta con un usuario inicial pre-configurado para agilizar la evaluación:
- **Correo**: `demo@donaciones.org`
- **Contraseña**: `demo1234`
*(En la ventana modal de inicio de sesión existe un botón para autocompletar automáticamente estos datos).*

## Análisis Estático de Código con SonarQube
El proyecto está configurado para realizar análisis estático de calidad de código, detección de vulnerabilidades de seguridad y Code Smells mediante **SonarQube** en Docker.

## Prerrequisitos
1. Docker Desktop activo y en ejecución.
2. Servidor de SonarQube disponible en la red (ej. http://192.168.1.73:9000).
3. Token de autenticación de SonarQube generado.

## Pasos para realizar el análisis
## Paso 1: Compilar el proyecto
SonarQube requiere los archivos binarios compilados (.class) para analizar el código Java en profundidad:

**Bash**
```bash
./compile.sh
```
## Paso 2: Ejecutar el escáner con Docker
Ejecuta el siguiente comando en la raíz del proyecto para iniciar la revisión mediante la imagen oficial de sonarsource/sonar-scanner-cli:

Bash
```bash
docker run --rm \
  -v "${PWD}:/usr/src" \
  -e SONAR_SCANNER_OPTS="-Dsonar.projectKey=PF_IngSoft -Dsonar.sources=. -Dsonar.java.binaries=. -Dsonar.host.url=[http://192.168.1.73:9000](http://192.168.1.73:9000) -Dsonar.login=sqp_9894833601aa407de0cb41233cfd34eeff548315" \
  sonarsource/sonar-scanner-cli
```
**Explicación de parámetros:**
- --rm: Elimina el contenedor al finalizar el escaneo.
- -v "${PWD}:/usr/src": Mapea el proyecto local hacia el contenedor.
- -Dsonar.projectKey=PF_IngSoft: Clave del proyecto dentro de SonarQube.
- -Dsonar.sources=.: Analiza la fuente dentro del directorio actual.
- -Dsonar.java.binaries=.: Analiza las clases Java compiladas.
- -Dsonar.host.url: Dirección del servidor de SonarQube.
- -Dsonar.login: Token de acceso seguro.

## Paso 3: Consultar reporte
Cuando finalice la ejecución (EXECUTION SUCCESS), puedes ver las métricas de calidad ingresando al panel Web:
👉 
```bash
http://192.168.1.73:9000/dashboard?id=PF_IngSoft
```

## Credenciales Demo para Pruebas
El sistema cuenta con un usuario inicial pre-configurado para agilizar la evaluación:

**Correo:** demo@donaciones.org

**Contraseña:** demo1234
(En la ventana modal de inicio de sesión existe un botón para autocompletar automáticamente estos datos).
