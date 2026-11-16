package hello;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "eureka.client.enabled=false")
class ConfigServiceApplicationTests {

	@LocalServerPort
	int port;

	@Test
	void servesSharedConfigurationFromBundledRepo() {
		String body = RestClient.create("http://localhost:" + port)
				.get().uri("/game-engine/default").retrieve().body(String.class);
		assertThat(body).contains("quizcadia.kafka.bootstrap-servers");
	}
}
