package com.jobtracker;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "jwt.secret=TestSecretKeyThatIsAtLeast256BitsLongForTestingPurposesOnly",
        "jwt.access-token-expiration-ms=900000",
        "jwt.refresh-token-expiration-ms=604800000",
        "cors.allowed-origins=http://localhost:3000"
})
class JobTrackerApplicationTests {

    @Test
    void contextLoads() {
    }
}
