# ms-andesstay-notify

Microservicio de **mensajería y notificaciones** del proyecto AndesStay (DSY1107 – Desarrollo Cloud Native I, Entrega 2).
Spring Boot 3.3 · Java 21 · RabbitMQ · PostgreSQL (RDS) · autenticación con Microsoft Entra ID (Azure).

## Arquitectura

```
Angular (local) ──login──► Microsoft Entra ID (Azure)  ──► access token (JWT)
      │
      └─ Bearer token ─► API Gateway ─► ms-andesstay-notify (EC2, Docker)
                                          │ POST /enviar  = PRODUCER ──► RabbitMQ (EC2) ──┐
                                          │                                               │ cmd.direct / cmd.topic
                                          │ CONSUMERS (email, housekeeping, voucher) ◄────┘
                                          └─► PostgreSQL en RDS (historial + idempotencia)
```

- **Producer:** `POST /api/v1/notificaciones/enviar` valida la solicitud y publica un evento en `cmd.direct`
  con la routing key del canal (`email.send`, `housekeeping.ticket`, `voucher.gen`).
- **Consumers:** un listener por cola (`q.cmd.email`, `q.cmd.housekeeping`, `q.cmd.voucher`) con ACK manual,
  idempotencia por `eventId`, reintentos (máx. 3) y **DLQ** para mensajes que fallan definitivamente.
- **Seguridad:** resource server OAuth2; valida firma, expiración e issuer del JWT de Azure
  (`https://login.microsoftonline.com/<TENANT_ID>/v2.0`). En el profile `dev` no pide token.

## Endpoints (`/api/v1/notificaciones`)

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/enviar` | **Producer.** Encola una notificación (202 Accepted + `eventId`). |
| GET | `/` | Historial completo. |
| GET | `/{id}` | Una notificación. |
| GET | `/canal/{canal}` | Filtra por `EMAIL`, `HOUSEKEEPING` o `VOUCHER`. |
| GET | `/estado/{estado}` | Filtra por `PENDIENTE`, `ENVIADA` o `FALLIDA`. |
| GET | `/reserva/{reservationId}` | Notificaciones de una reserva. |
| GET | `/actuator/health` | Público. |

Ejemplos listos en [`test.http`](test.http).

## Variables de entorno

Copia `.env.example` a `.env` (no se sube a Git). En el profile `prod` no tienen valor por defecto: si falta una, la app no arranca.

| Variable | Descripción |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` (por defecto) o `dev` (solo local, sin JWT). |
| `JWT_ISSUER_URI` | `https://login.microsoftonline.com/<TENANT_ID>/v2.0` |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASS` | PostgreSQL (RDS). |
| `RABBIT_HOST`, `RABBIT_PORT`, `RABBIT_USER`, `RABBIT_PASS` | RabbitMQ (EC2). |
| `CORS_ALLOWED_ORIGINS` | Origen del frontend, ej. `http://localhost:4200`. |

## Ejecución local

```bash
docker compose up -d --build     # Postgres + RabbitMQ + la app (profile dev) en :8082
# Panel de RabbitMQ: http://localhost:15672  (guest / guest)
```

O sin Docker para la app: levanta `db` y `rabbitmq` con compose y ejecuta
`SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run`.

Tests: `mvn test`.

## Despliegue en AWS

1. **RDS:** instancia PostgreSQL con la base `andesstay_notify`; permite el puerto 5432 desde el security group de la EC2.
2. **EC2 de RabbitMQ:** `docker run -d --name rabbit -p 5672:5672 -p 15672:15672 -e RABBITMQ_DEFAULT_USER=<user> -e RABBITMQ_DEFAULT_PASS=<pass> rabbitmq:3-management`.
3. **EC2 de notify:** `docker build -t ms-andesstay-notify .` y
   `docker run -d --name notify -p 8082:8082 --env-file .env ms-andesstay-notify`.
4. **API Gateway:** ruta `/api/v1/notificaciones/{proxy+}` hacia `http://<ip-ec2>:8082`, con JWT authorizer de Azure
   (issuer `https://login.microsoftonline.com/<TENANT_ID>/v2.0`, audience = Client ID de `andesstay-backend`).

## Azure (Microsoft Entra ID)

- App `andesstay-backend` (API) con scope `access-all` y `requestedAccessTokenVersion = 2`.
- App `andesstay-frontend` (SPA) con redirect `http://localhost:4200`.
- Roles de aplicación: `CONSULTOR`, `AUDITOR`.

## Flujo de trabajo (GitHub Flow)

`main` siempre estable · ramas `feature/…`, `fix/…`, `docs/…` · commits tipo `feat:` / `fix:` / `docs:` · Pull Request a `main` con revisión de otro integrante.
