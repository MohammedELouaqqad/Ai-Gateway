package com.backend.Ai_Gateway;

import com.backend.Ai_Gateway.config.TestJwtConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestJwtConfig.class)
class DemoApplicationTests {

	@Test
	void contextLoads() {
	}

}
