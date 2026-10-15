# Se construye primero el jar:  ./mvnw clean package -DskipTests   (o: mvn clean package -DskipTests)
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/ms-andesstay-notify-0.0.1-SNAPSHOT.jar app.jar

# Seguro por defecto: profile prod (JWT obligatorio). Las variables se pasan con --env-file.
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
