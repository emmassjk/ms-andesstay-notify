package com.andesstay.notify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.andesstay.notify.config.RabbitProperties;

/**
 * Punto de entrada del microservicio ms-andesstay-notify.
 * <p>
 * Responsabilidad: consumir mensajes de RabbitMQ publicados por ms-andesstay-reservations
 * y notificar al Operador (recepcionista) por los canales configurados:
 * email, housekeeping y voucher.
 */
@SpringBootApplication
@EnableConfigurationProperties(RabbitProperties.class)
public class NotifyApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotifyApplication.class, args);
    }
}
