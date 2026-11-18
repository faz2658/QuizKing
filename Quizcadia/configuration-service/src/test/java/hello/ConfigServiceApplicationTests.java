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

	@Test
	void servesEachProfilesSettingsOnTopOfTheSharedOnes() {
		RestClient http = RestClient.create("http://localhost:" + port);
		String prod = http.get().uri("/game-engine/prod").retrieve().body(String.class);
		assertThat(prod).contains("application-prod.yml", "org.apache.kafka", "quizcadia.kafka.bootstrap-servers");
		assertThat(http.get().uri("/login/local").retrieve().body(String.class)).contains("application-local.yml");
		assertThat(http.get().uri("/login/docker").retrieve().body(String.class)).contains("application-docker.yml")
				.doesNotContain("application-prod.yml");
	}
}
