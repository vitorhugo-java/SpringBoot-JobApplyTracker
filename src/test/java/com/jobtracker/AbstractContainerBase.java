package com.jobtracker;

import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

public abstract class AbstractContainerBase {

    @Container
    protected static final MariaDBContainer<?> MARIADB =
            new MariaDBContainer<>(DockerImageName.parse("mariadb:11.2"))
                    .withDatabaseName("jobtracker_test")
                    .withUsername("test")
                    .withPassword("test");

    static {
        MARIADB.start();
        System.setProperty("spring.datasource.url", MARIADB.getJdbcUrl());
        System.setProperty("spring.datasource.username", MARIADB.getUsername());
        System.setProperty("spring.datasource.password", MARIADB.getPassword());
        System.setProperty("spring.datasource.driver-class-name", "org.mariadb.jdbc.Driver");
    }
}
