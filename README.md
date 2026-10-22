# ms-andesstay-notify

Microservicio de **mensajería y notificaciones** del proyecto AndesStay (DSY1107 – Desarrollo Cloud Native I, Entrega 2).
Spring Boot 3.3 · Java 21 · RabbitMQ · PostgreSQL (RDS) · autenticación con Microsoft Entra ID (Azure) · API Gateway (AWS).

## Arquitectura

```
Angular (local) ──login──► Microsoft Entra ID (Azure) ──► access token (JWT)
      │
      └─ Bearer token ─► API Gateway (AWS, JWT authorizer Azure)
                              │
                              ▼
                   EC2 · Docker ── ms-andesstay-notify :8082
                                      │ POST /enviar = PRODUCER ──► RabbitMQ (EC2) ──┐
                                      │                                              │ cmd.direct
                                      │ CONSUMERS (email, housekeeping, voucher) ◄───┘
                                      └─► PostgreSQL en RDS (historial + idempotencia)
```

- **Producer:** `POST /api/v1/notificaciones/enviar` valida la solicitud y publica un evento en el exchange `cmd.direct`
  con la routing key del canal (`email.send`, `housekeeping.ticket`, `voucher.gen`). Responde `202 Accepted` con el `eventId`.
- **Consumers:** un listener por cola (`q.cmd.email`, `q.cmd.housekeeping`, `q.cmd.voucher`) con ACK manual,
  idempotencia por `eventId`, reintentos (máx. 3) y **DLQ** (`q.cmd.*.dlq`) para mensajes que fallan definitivamente.
- **Seguridad:** resource server OAuth2; valida firma, expiración e issuer del JWT de Azure
  (`https://login.microsoftonline.com/<TENANT_ID>/v2.0`). Todos los endpoints exigen token salvo `/actuator/health`.
  En el profile `dev` (solo local) no se pide token.

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

Cuerpo de `POST /enviar`:

```json
{
  "canal": "EMAIL",
  "reservationId": 1,
  "guestName": "Nombre Apellido",
  "guestEmail": "huesped@correo.cl",
  "propertyName": "Cabaña Los Andes",
  "roomNumber": "C-1",
  "checkIn": "2026-11-01",
  "checkOut": "2026-11-04",
  "totalAmount": 90000,
  "currency": "CLP",
  "operatorEmail": "operador@andesstay.cl",
  "specialRequests": "Llegada tarde"
}
```

Ejemplos listos en [`test.http`](test.http).

## Variables de entorno

Copia `.env.example` a `.env` (**no se sube a Git**). En el profile `prod` no tienen valor por defecto: si falta una, la app no arranca.

| Variable | Descripción | Ejemplo |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` (por defecto) o `dev` (solo local, sin JWT). | `prod` |
| `JWT_ISSUER_URI` | Issuer de Azure (v2.0). | `https://login.microsoftonline.com/<TENANT_ID>/v2.0` |
| `DB_HOST` | Endpoint de la instancia RDS. | `andesstay-db.xxxx.us-east-1.rds.amazonaws.com` |
| `DB_PORT` | Puerto de PostgreSQL. | `5432` |
| `DB_NAME` | Base de datos creada en RDS. | `notifydb` |
| `DB_USER`, `DB_PASS` | Credenciales de RDS. | `postgres` / *(secreto)* |
| `RABBIT_HOST` | Host de RabbitMQ (nombre del servicio en el compose o IP de la EC2). | `rabbitmq` |
| `RABBIT_PORT` | Puerto AMQP (opcional). | `5672` |
| `RABBIT_USER`, `RABBIT_PASS` | Credenciales de RabbitMQ. | *(secreto)* |
| `CORS_ALLOWED_ORIGINS` | Origen del frontend. | `http://localhost:4200` |

> `notify` es palabra reservada en PostgreSQL, por eso la base se llama `notifydb`.

## Ejecución local

```bash
docker compose up -d --build     # Postgres + RabbitMQ + la app (profile dev) en :8082
# Panel de RabbitMQ: http://localhost:15672  (guest / guest)
```

O sin Docker para la app: levanta `db` y `rabbitmq` con compose y ejecuta
`SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run`.

Tests: `mvn test`.

## Despliegue en AWS (Learner Lab, `us-east-1`)

Recursos creados:

| Recurso | Configuración |
|---|---|
| **RDS** `andesstay-db` | PostgreSQL, `db.t3.micro`, 20 GiB, sin acceso público, base inicial `notifydb`. |
| **EC2** `andesstay-notify` | Amazon Linux 2023, `t3.small`, 20 GiB, con IP elástica. Corre RabbitMQ y notify con Docker. |
| **Security group de la EC2** | Entrada: 22 (SSH, IP del equipo), 8082 (API Gateway) y 15672 (panel RabbitMQ, IP del equipo). |
| **Security group de RDS** | Entrada: 5432 solo desde el security group de la EC2. |
| **API Gateway** `andesstay-api` | HTTP API con las rutas y el autorizador descritos abajo. |

Pasos en la EC2:

```bash
# 1. Docker, git y Compose
sudo dnf install -y docker git && sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user          # volver a conectarse por SSH después
# (Docker Compose v2 y buildx >= 0.17 como plugins en ~/.docker/cli-plugins)

# 2. Código y configuración
git clone https://github.com/<usuario>/ms-andesstay-notify.git && cd ms-andesstay-notify
cp .env.example .env && nano .env         # completar con los valores reales (ver tabla)

# 3. Levantar RabbitMQ + notify
docker compose -f docker-compose.aws.yml --env-file .env up -d --build
docker logs -f notify                     # esperar "Started NotifyApplication"
curl -i localhost:8082/actuator/health    # {"status":"UP"}
```

Panel de RabbitMQ: `http://<ip-elastica>:15672` (usuario y contraseña de `RABBIT_USER` / `RABBIT_PASS`).
Deben existir 6 colas: `q.cmd.email`, `q.cmd.housekeeping`, `q.cmd.voucher` y sus DLQ.

Los contenedores usan `restart: unless-stopped`: si el laboratorio se detiene y se vuelve a iniciar,
basta con arrancar la EC2 y la RDS y los servicios vuelven solos.

### API Gateway (HTTP API)

- **Integración:** HTTP `ANY http://<ip-elastica>:8082/{proxy}`.
- **Rutas:**
  - `ANY /{proxy+}` con el autorizador JWT `azure-jwt` (protege todas las peticiones reales).
  - `OPTIONS /{proxy+}` **sin autorizador**, con la misma integración. Es necesaria para que el *preflight* de CORS
    del navegador no reciba `401` (el navegador nunca envía el token en el preflight).
- **Autorizador JWT `azure-jwt`:** origen de identidad `$request.header.Authorization`; issuer
  `https://login.microsoftonline.com/<TENANT_ID>/v2.0`; audience = Client ID de la app `andesstay-backend`.
- **CORS:** origen `http://localhost:4200`; métodos `GET, POST, OPTIONS`; headers `authorization, content-type`.
- **Etapa:** `$default` con implementación automática.

Verificación:

```bash
# Sin token → 401 (lo responde el API Gateway)
curl -i https://<api-id>.execute-api.us-east-1.amazonaws.com/api/v1/notificaciones

# Preflight → 200 con access-control-allow-origin
curl -i -X OPTIONS https://<api-id>.execute-api.us-east-1.amazonaws.com/api/v1/notificaciones \
  -H "Origin: http://localhost:4200" -H "Access-Control-Request-Method: GET" \
  -H "Access-Control-Request-Headers: authorization"
```

### Problemas frecuentes

| Síntoma | Causa y solución |
|---|---|
| `compose build requires buildx 0.17.0 or later` | Instalar un buildx reciente en `~/.docker/cli-plugins/docker-buildx`. |
| El navegador no puede conectar y el preflight da 401 | Falta la ruta `OPTIONS /{proxy+}` sin autorizador. |
| `401` con sesión iniciada | El token no tiene `aud` = Client ID del backend o `iss` distinto del configurado. |
| La app no arranca en `prod` | Falta alguna variable de entorno (no hay valores por defecto). |

## Azure (Microsoft Entra ID)

- App `andesstay-backend` (API) con scope `access-all` y `requestedAccessTokenVersion = 2`.
- App `andesstay-frontend` (SPA) con redirect `http://localhost:4200`.
- Roles de aplicación: `CONSULTOR`, `AUDITOR` (asignados en la aplicación empresarial).
- Notify valida que el token sea de Azure y esté vigente; el control de acceso por rol se aplica en el frontend.

## Flujo de trabajo (GitHub Flow)

`main` siempre estable · ramas `feature/…`, `fix/…`, `docs/…` · commits tipo `feat:` / `fix:` / `docs:` · Pull Request a `main` con revisión de otro integrante.
