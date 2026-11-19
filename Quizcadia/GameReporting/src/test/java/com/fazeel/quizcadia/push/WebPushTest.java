package com.fazeel.quizcadia.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class WebPushTest {

	static byte[] b64(String s) {
		return Base64.getUrlDecoder().decode(s);
	}

	/** The worked example of RFC 8291, appendix A: same inputs, so exactly the same bytes. */
	@Test
	void encryptsExactlyLikeTheRfcExample() {
		byte[] plaintext = "When I grow up, I want to be a watermelon".getBytes(StandardCharsets.UTF_8);
		byte[] uaPublic = b64("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");
		byte[] auth = b64("BTBZMqHH6r4Tts7J_aSIgg");
		byte[] salt = b64("DGv6ra1nlYgDCS1FRnbzlw");
		var as = WebPush.keyPair(b64("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8"),
				b64("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw"));

		byte[] body = WebPush.encrypt(plaintext, uaPublic, auth, salt, as);

		assertThat(Base64.getUrlEncoder().withoutPadding().encodeToString(body)).isEqualTo(
				"DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN");
	}

	@Test
	void signsAVapidTokenThePushServiceCanCheck() throws Exception {
		String pub = "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
		WebPush push = new WebPush(pub, "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw", "mailto:ops@quizcadia.test", null);

		String jwt = push.vapidJwt("https://fcm.googleapis.com", Instant.ofEpochSecond(1_800_000_000L));

		String[] parts = jwt.split("\\.");
		assertThat(new String(b64(parts[0]), StandardCharsets.UTF_8)).isEqualTo("{\"typ\":\"JWT\",\"alg\":\"ES256\"}");
		assertThat(new String(b64(parts[1]), StandardCharsets.UTF_8))
				.isEqualTo("{\"aud\":\"https://fcm.googleapis.com\",\"exp\":1800000000,\"sub\":\"mailto:ops@quizcadia.test\"}");
		Signature es256 = Signature.getInstance("SHA256withECDSAinP1363Format");
		es256.initVerify(WebPush.publicKey(b64(pub)));
		es256.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
		assertThat(b64(parts[2])).hasSize(64);
		assertThat(es256.verify(b64(parts[2]))).isTrue();
	}
}
