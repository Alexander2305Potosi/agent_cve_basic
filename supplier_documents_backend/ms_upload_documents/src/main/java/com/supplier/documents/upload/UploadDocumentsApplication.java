package com.supplier.documents.upload;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.supplier.documents.upload")
public class UploadDocumentsApplication {

    public static void main(String[] args) {
        SpringApplication.run(UploadDocumentsApplication.class, args);
    }
}