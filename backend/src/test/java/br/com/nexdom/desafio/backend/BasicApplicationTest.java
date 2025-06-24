package br.com.nexdom.desafio.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
public class BasicApplicationTest {

    @Test
    public void contextLoads() {
        // This test will pass if the application context loads successfully
    }
}