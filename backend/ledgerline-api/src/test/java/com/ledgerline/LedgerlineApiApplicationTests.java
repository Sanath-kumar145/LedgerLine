package com.ledgerline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Full Application Context & Repository Integration Boot Test")
class LedgerlineApiApplicationTests {

    @Test
    void contextLoads() {
    }

}
