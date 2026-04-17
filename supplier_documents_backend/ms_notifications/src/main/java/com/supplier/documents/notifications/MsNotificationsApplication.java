package com.supplier.documents.notifications;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Microservicio de Notificaciones
 * Responsable del envío de notificaciones por email, SMS y push
 */
@SpringBootApplication
public class MsNotificationsApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsNotificationsApplication.class, args);
    }
}
