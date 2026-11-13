# ms-andesstay-notify

Microservicio de **mensajería y notificaciones** del proyecto AndesStay (DSY1107 – Desarrollo Cloud Native I).
Desarrollado en **Spring Boot 3.3.5 · Java 21 · RabbitMQ · PostgreSQL · autenticación con AWS Cognito**, preparado para ejecutarse localmente con **Docker Desktop**.

---

## Arquitectura Local (Docker Desktop)

```
Frontend Angular (:4200) / BFF (:8080)
      │
      ▼  (HTTP / Bearer Access Token de AWS Cognito)
Docker Desktop · Red interna 'notify-net'
┌────────────────────────────────────────────────────────────────────────┐
│                                                                        │
│   ms-andesstay-notify (:8082)                                          │
│   ├── Producer: POST /enviar ──────► RabbitMQ (:5672 / :15672)         │
│   │                                       │ exchange 'cmd.direct'      │
│   │                                       ▼                            │
│   └── Consumers (email, housekeeping, voucher) ──► PostgreSQL (:5432)  │
│                                                     (tabla: notificaciones)
└────────────────────────────────────────────────────────────────────────┘
```

- **Producer:** `POST /api/v1/notificaciones/enviar` valida la solicitud y publica un evento en el exchange `cmd.direct` con la routing key correspondiente (`email.send`, `housekeeping.ticket`, `voucher.gen`). Responde `202 Accepted` con el `eventId`.
- **Consumers:** Un listener asíncrono por canal (`q.cmd.email`, `q.cmd.housekeeping`, `q.cmd.voucher`) con ACK manual, control de idempotencia por `eventId`, reintentos (máx. 3) y Dead Letter Queue (**DLQ**: `q.cmd.*.dlq`) ante errores no recuperables.
- **Persistencia:** Guarda el historial de notificaciones enviadas y fallidas en PostgreSQL local para trazabilidad y auditoría.

---

## Ejecución con Docker Desktop

### 1. Prerrequisitos
- Tener instalado y en ejecución **Docker Desktop** (con soporte para Docker Compose v2).

### 2. Iniciar el Stack Completo
En la raíz del proyecto, ejecuta:

```bash
docker compose up -d --build
```

Docker Desktop levantará 3 contenedores en la red `notify-net`:
1. `andesstay-notify-db`: PostgreSQL 16 (puerto `5432`).
2. `andesstay-notify-rabbit`: RabbitMQ 3 con Management UI (puertos `5672` y `15672`).
3. `andesstay-notify-app`: Microservicio Spring Boot 3.3.5 (puerto `8082`).

### 3. Verificar el Estado y Logs
```bash
# Ver estado de los contenedores y sus healthchecks
docker compose ps

# Ver logs del microservicio
docker compose logs -f app

# Verificar que la aplicación esté saludable
curl http://localhost:8082/actuator/health
```

### 4. Paneles y Accesos Locales
| Servicio | URL Local | Credenciales por defecto |
|---|---|---|
| **API Notificaciones** | `http://localhost:8082/api/v1/notificaciones` | Según perfil (`dev`: libre / `prod`: Bearer Token) |
| **Health Check** | `http://localhost:8082/actuator/health` | Público (`{"status":"UP"}`) |
| **Panel RabbitMQ** | `http://localhost:15672` | `guest` / `guest` |
| **PostgreSQL** | `localhost:5432` (DB: `andesstay_notify`) | `postgres` / `postgres` |

### 5. Detener el Stack
```bash
docker compose down        # Detiene y elimina contenedores y red
docker compose down -v     # Detiene y borra también los volúmenes persistentes
```

---

## Perfiles de Ejecución (`SPRING_PROFILES_ACTIVE`)

El microservicio admite dos modos de operación en Docker Desktop:

1. **`dev` (por defecto en Docker Desktop):**
   - JWT deshabilitado en Spring Security (`permitAll`).
   - Ideal para desarrollo ágil y pruebas rápidas desde Postman, `test.http` o el frontend sin requerir inicio de sesión en Cognito.
   - Conexión automática a PostgreSQL y RabbitMQ locales en Docker.

2. **`prod` (Seguridad estricta con AWS Cognito):**
   - Actúa como **OAuth2 Resource Server**, exigiendo un access token JWT válido emitido por el User Pool de AWS Cognito.
   - Valida firma (JWKS vía internet), expiración, issuer (`https://cognito-idp.<region>.amazonaws.com/<userPoolId>`) y client ID (`client_id`).
   - Autoriza por roles basados en el claim `cognito:groups`.
   - Se activa copiando `.env.example` a `.env` y configurando `SPRING_PROFILES_ACTIVE=prod` junto con las credenciales del User Pool de Cognito.

---

## Seguridad (AWS Cognito)

Cuando se ejecuta con profile `prod`:
- **Validación del token:**
  - `client_id`: El validador `CognitoClientIdValidator` exige que el claim `client_id` coincida con el App Client ID configurado (rechazando ID tokens que carecen de este claim).
  - `cognito:groups`: El conversor `CognitoJwtAuthenticationConverter` extrae los grupos y los mapea a `ROLE_<GRUPO>` en mayúsculas (ej: `ROLE_ADMIN`, `ROLE_RECEPCIONISTA`, `ROLE_AUDITOR`).
- **Control de Acceso (RBAC):**
  - `GET /api/v1/notificaciones/**`: Requiere `ADMIN`, `RECEPCIONISTA` o `AUDITOR`.
  - `POST /api/v1/notificaciones/enviar`: Requiere `ADMIN` o `RECEPCIONISTA`.
  - `OPTIONS /**`, `/actuator/health`, `/actuator/info`: Públicos.
- **Respuestas de Error en JSON:**
  - `401 Unauthorized` (`RestAuthenticationEntryPoint`): Cuando el token no se envía o es inválido.
  - `403 Forbidden` (`RestAccessDeniedHandler`): Cuando el token es válido pero no cuenta con el rol necesario.

---

## Variables de Entorno (`.env`)

Copia `.env.example` a `.env` para personalizar la configuración local:

| Variable | Descripción | Valor por defecto (Docker Desktop) |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Perfil de Spring Boot (`dev` o `prod`). | `dev` |
| `SERVER_PORT` | Puerto expuesto en el host para el microservicio. | `8082` |
| `DB_HOST` | Host de PostgreSQL (nombre del servicio en Docker). | `db` |
| `DB_PORT` | Puerto de PostgreSQL. | `5432` |
| `DB_NAME` | Nombre de la base de datos. | `andesstay_notify` |
| `DB_USER` / `DB_PASS` | Credenciales de PostgreSQL. | `postgres` / `postgres` |
| `RABBIT_HOST` | Host de RabbitMQ (nombre del servicio en Docker). | `rabbitmq` |
| `RABBIT_PORT` | Puerto AMQP. | `5672` |
| `RABBIT_USER` / `RABBIT_PASS` | Credenciales de RabbitMQ. | `guest` / `guest` |
| `COGNITO_REGION` | Región AWS del User Pool (solo para `prod`). | `us-east-1` |
| `COGNITO_USER_POOL_ID` | ID del User Pool de Cognito (solo para `prod`). | *(vacío en dev)* |
| `COGNITO_APP_CLIENT_ID` | App Client ID de Cognito (solo para `prod`). | *(vacío en dev)* |
| `CORS_ALLOWED_ORIGINS` | Orígenes permitidos (Angular, BFF). | `http://localhost:3000,http://localhost:4200,http://localhost:8080,http://localhost:8081` |

---

## Puerto 8082 y Resolución de Conflictos

El microservicio utiliza por defecto el puerto **8082** (`server.port: ${SERVER_PORT:8082}`).

Si en tu entorno local el puerto 8082 ya está ocupado por otro servicio (como `ms-andesstay-reservations`), puedes mapear un puerto externo diferente en `.env`:

```env
SERVER_PORT=8083
```
Y levantar con `docker compose up -d`. El microservicio responderá en `http://localhost:8083`.

---

## Endpoints (`/api/v1/notificaciones`)

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/enviar` | **Producer:** encola una notificación en RabbitMQ (`202 Accepted`). |
| `GET` | `/` | Obtiene el historial completo de notificaciones. |
| `GET` | `/{id}` | Obtiene una notificación por ID. |
| `GET` | `/canal/{canal}` | Filtra por canal (`EMAIL`, `HOUSEKEEPING`, `VOUCHER`). |
| `GET` | `/estado/{estado}` | Filtra por estado (`PENDIENTE`, `ENVIADA`, `FALLIDA`). |
| `GET` | `/reserva/{reservationId}` | Obtiene notificaciones de una reserva. |
| `GET` | `/actuator/health` | Healthcheck del servicio (Público). |

Cuerpo de ejemplo para `POST /api/v1/notificaciones/enviar`:
```json
{
  "canal": "EMAIL",
  "reservationId": 10,
  "guestName": "Ana Soto",
  "guestEmail": "ana@example.com",
  "propertyName": "Cabaña Lago",
  "roomNumber": "C-3",
  "checkIn": "2026-11-02",
  "checkOut": "2026-11-05",
  "totalAmount": 90000,
  "currency": "CLP",
  "operatorEmail": "operador@andesstay.cl",
  "specialRequests": "Cama extra"
}
```

Pruebas listas disponibles en [`test.http`](test.http).

---

## Ejecución de Pruebas Unitarias

Para ejecutar la suite de pruebas automatizadas con Maven:

```bash
mvn clean test
```

Incluye validadores de `client_id`, convertidor de grupos de Cognito y pruebas de seguridad MockMvc sobre el controlador.
