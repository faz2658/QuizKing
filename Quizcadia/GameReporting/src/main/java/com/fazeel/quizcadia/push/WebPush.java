package com.fazeel.quizcadia.push;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Sends Web Push messages with nothing but the JDK: the payload is encrypted for the browser as RFC 8291 says
 * ({@code aes128gcm}: ECDH on P-256, HKDF-SHA256, AES-128-GCM) and the request is signed with our VAPID key
 * (RFC 8292: an ES256 JWT). Keys are the usual base64url strings: a 65-byte uncompressed public point and a 32-byte
 * private scalar.
 */
public class WebPush implements PushSender {

	/** The push service's answer: {@code gone} means the subscription no longer exists (404/410) and can be dropped. */
	public record Result(int status, boolean ok, boolean gone) {
	}

	private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder B64D = Base64.getUrlDecoder();
	private static final int RECORD_SIZE = 4096;
	private static final ECParameterSpec P256 = p256();

	private final String publicKey;
	private final ECPrivateKey privateKey;
	private final String subject;
	private final HttpClient http;
	private final SecureRandom random = new SecureRandom();

	/** @param subject who to contact about our pushes, a {@code mailto:} or {@code https:} URL (required by VAPID) */
	public WebPush(String publicKey, String privateKey, String subject, HttpClient http) {
		this.publicKey = publicKey;
		this.privateKey = privateKey(B64D.decode(privateKey));
		this.subject = subject;
		this.http = http;
	}

	public String publicKey() {
		return publicKey;
	}

	/** Encrypts {@code payload} for the subscription and posts it; the message is kept up to {@code ttl}. */
	@Override
	public Result send(String endpoint, String p256dh, String auth, String payload, Duration ttl)
			throws java.io.IOException, InterruptedException {
		byte[] salt = new byte[16];
		random.nextBytes(salt);
		byte[] body = encrypt(payload.getBytes(StandardCharsets.UTF_8), B64D.decode(p256dh), B64D.decode(auth), salt,
				ephemeral());
		URI uri = URI.create(endpoint);
		HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
				.header("TTL", String.valueOf(ttl.toSeconds())).header("Urgency", "normal")
				.header("Content-Encoding", "aes128gcm").header("Content-Type", "application/octet-stream")
				.header("Authorization", "vapid t=" + vapidJwt(uri.getScheme() + "://" + uri.getAuthority(),
						Instant.now().plus(Duration.ofHours(12))) + ", k=" + publicKey)
				.POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
		int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
		return new Result(status, status >= 200 && status < 300, status == 404 || status == 410);
	}

	/**
	 * RFC 8291 encryption, with the salt and our one-time key pair given so it can be checked against the RFC's
	 * example. The body is the RFC 8188 header (salt, record size, our public key) and one encrypted record.
	 */
	static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret, byte[] salt, KeyPair as) {
		try {
			byte[] asPublic = raw((ECPublicKey) as.getPublic());
			KeyAgreement ka = KeyAgreement.getInstance("ECDH");
			ka.init(as.getPrivate());
			ka.doPhase(publicKey(uaPublic), true);
			byte[] ecdhSecret = ka.generateSecret();

			byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic);
			byte[] ikm = hkdf(authSecret, ecdhSecret, keyInfo, 32);
			byte[] cek = hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
			byte[] nonce = hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

			Cipher gcm = Cipher.getInstance("AES/GCM/NoPadding");
			gcm.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
			byte[] record = gcm.doFinal(concat(plaintext, new byte[] { 2 })); // 2: the last (and only) record

			ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + asPublic.length);
			header.put(salt).putInt(RECORD_SIZE).put((byte) asPublic.length).put(asPublic);
			return concat(header.array(), record);
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("Could not encrypt the push message", e);
		}
	}

	/** The VAPID token: an ES256 JWT for the push service's origin, signed with our private key. */
	String vapidJwt(String audience, Instant expires) {
		String header = B64.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
		String claims = B64.encodeToString(("{\"aud\":\"" + audience + "\",\"exp\":" + expires.getEpochSecond()
				+ ",\"sub\":\"" + subject + "\"}").getBytes(StandardCharsets.UTF_8));
		try {
			Signature es256 = Signature.getInstance("SHA256withECDSAinP1363Format"); // raw r||s, as JWS wants
			es256.initSign(privateKey);
			es256.update((header + "." + claims).getBytes(StandardCharsets.US_ASCII));
			return header + "." + claims + "." + B64.encodeToString(es256.sign());
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("Could not sign the VAPID token", e);
		}
	}

	/** HKDF-SHA256 (RFC 5869) for outputs of at most 32 bytes: one expand round. */
	static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws GeneralSecurityException {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(salt, "HmacSHA256"));
		byte[] prk = mac.doFinal(ikm);
		mac.init(new SecretKeySpec(prk, "HmacSHA256"));
		return Arrays.copyOf(mac.doFinal(concat(info, new byte[] { 1 })), length);
	}

	private KeyPair ephemeral() {
		try {
			KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
			g.initialize(new ECGenParameterSpec("secp256r1"), random);
			return g.generateKeyPair();
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	/** A P-256 key pair from its raw form (65-byte public point, 32-byte private scalar). */
	static KeyPair keyPair(byte[] publicRaw, byte[] privateRaw) {
		return new KeyPair(publicKey(publicRaw), privateKey(privateRaw));
	}

	static ECPublicKey publicKey(byte[] raw) {
		if (raw.length != 65 || raw[0] != 4) {
			throw new IllegalArgumentException("Not an uncompressed P-256 public key");
		}
		try {
			ECPoint w = new ECPoint(new BigInteger(1, Arrays.copyOfRange(raw, 1, 33)), new BigInteger(1, Arrays.copyOfRange(raw, 33, 65)));
			return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(w, P256));
		}
		catch (GeneralSecurityException e) {
			throw new IllegalArgumentException("Not a valid P-256 public key", e);
		}
	}

	static ECPrivateKey privateKey(byte[] raw) {
		try {
			return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new BigInteger(1, raw), P256));
		}
		catch (GeneralSecurityException e) {
			throw new IllegalArgumentException("Not a valid P-256 private key", e);
		}
	}

	/** The 65-byte uncompressed form of a public key. */
	static byte[] raw(ECPublicKey key) {
		byte[] out = new byte[65];
		out[0] = 4;
		fill(key.getW().getAffineX(), out, 1);
		fill(key.getW().getAffineY(), out, 33);
		return out;
	}

	private static void fill(BigInteger n, byte[] out, int at) {
		byte[] b = n.toByteArray(); // may have a leading 0 or be shorter than 32 bytes
		int len = Math.min(b.length, 32);
		System.arraycopy(b, b.length - len, out, at + 32 - len, len);
	}

	private static byte[] concat(byte[]... parts) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (byte[] p : parts) {
			out.writeBytes(p);
		}
		return out.toByteArray();
	}

	private static ECParameterSpec p256() {
		try {
			AlgorithmParameters p = AlgorithmParameters.getInstance("EC");
			p.init(new ECGenParameterSpec("secp256r1"));
			return p.getParameterSpec(ECParameterSpec.class);
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}
}
