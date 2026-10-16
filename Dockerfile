# ═══════════════════════════════════════════════════════════════════════════════
# Dockerfile — ms-andesstay-notify
# Multi-stage build: Maven (compilación) → JRE ligero (ejecución)
# ═══════════════════════════════════════════════════════════════════════════════

# ── Stage 1: BUILD ────────────────────────────────────────────────────────────
# Se usa la imagen oficial de Maven con JDK 21 para compilar el proyecto.
# El resultado es un fat JAR en target/.
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# 1. Copiar solo los archivos de definición de dependencias primero.
#    Esto aprovecha la cache de Docker: mientras pom.xml no cambie,
#    las dependencias descargadas se reutilizan sin re-download.
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 2. Copiar el código fuente y compilar (sin tests para acelerar el build).
COPY src ./src
RUN mvn clean package -DskipTests -B

# ── Stage 2: RUN ──────────────────────────────────────────────────────────────
# Se usa una imagen JRE ligera (sin JDK, sin Maven) para minimizar el tamaño
# final de la imagen y reducir la superficie de ataque.
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# ── Seguridad: usuario no-root ────────────────────────────────────────────────
# Se crea un grupo y usuario dedicado (appuser) para ejecutar la aplicación.
# Esto sigue la buena práctica de no ejecutar procesos como root en producción.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copiar el JAR compilado desde el stage de build.
COPY --from=build /build/target/ms-andesstay-notify-0.0.1-SNAPSHOT.jar app.jar

# Asegurar que el usuario appuser sea dueño del directorio de trabajo.
RUN chown -R appuser:appgroup /app

# Cambiar al usuario no-root.
USER appuser

# ── Variables de entorno por defecto ──────────────────────────────────────────
# El profile 'prod' es seguro por defecto (JWT obligatorio).
# En desarrollo local usar SPRING_PROFILES_ACTIVE=dev.
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS=""

# Puerto expuesto por Spring Boot (configurado en application.yaml).
EXPOSE 8082

# ── Healthcheck integrado en Docker ──────────────────────────────────────────
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8082/actuator/health || exit 1

# ── Entrypoint ────────────────────────────────────────────────────────────────
# Se usa shell form para que JAVA_OPTS se expanda correctamente.
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
