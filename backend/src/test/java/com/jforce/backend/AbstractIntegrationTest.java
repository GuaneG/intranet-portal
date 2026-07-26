package com.jforce.backend;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")           // application-test.properties devreye girsin (jwt.secret, ve db_password üstüne yazılsın diye)
@Testcontainers                   // JUnit 5 eklentisi: @Container yaşam döngüsünü yönetir
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection             //konteynerin url/user/pass'ini Spring datasource'una otomatik bağlar
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");
}