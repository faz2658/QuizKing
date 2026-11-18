package com.fazeel.quizcadia.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;

import jdk.jfr.Recording;

class DiagnosticsTest {

	@Test
	void onlyLoopbackAddressesCountAsInsideTheContainer() {
		assertThat(Diagnostics.isLoopback("127.0.0.1")).isTrue();
		assertThat(Diagnostics.isLoopback("0:0:0:0:0:0:0:1")).isTrue();
		assertThat(Diagnostics.isLoopback("::1")).isTrue();
		assertThat(Diagnostics.isLoopback("172.18.0.7")).isFalse(); // another container
		assertThat(Diagnostics.isLoopback("10.0.0.1")).isFalse();
		assertThat(Diagnostics.isLoopback("localhost")).isFalse(); // names are never looked up
		assertThat(Diagnostics.isLoopback("")).isFalse();
		assertThat(Diagnostics.isLoopback(null)).isFalse();
	}

	@Test
	void hasNothingToGiveWithoutTheContinuousRecording() throws Exception {
		assertThat(FlightRecordings.continuous()).isEmpty();
		assertThat(new FlightRecordings().recording(null)).isNull();
	}

	@Test
	void dumpsTheContinuousRecordingAndRemovesTheFileOnceSent() throws Exception {
		try (Recording continuous = new Recording()) {
			continuous.setName(FlightRecordings.CONTINUOUS);
			continuous.start();
			Resource jfr = new FlightRecordings().recording(null);
			Path file = jfr.getFile().toPath();
			try (InputStream in = jfr.getInputStream()) {
				byte[] head = in.readNBytes(4);
				assertThat(new String(head)).isEqualTo("FLR\0"); // the JFR file format's magic
			}
			assertThat(Files.exists(file)).isFalse();
		}
	}

	@Test
	void recordsOnDemandForTheSecondsAskedFor() throws Exception {
		long start = System.nanoTime();
		Resource jfr = new FlightRecordings().recording(1);
		assertThat(System.nanoTime() - start).isGreaterThanOrEqualTo(1_000_000_000L);
		assertThat(jfr.contentLength()).isPositive();
		jfr.getInputStream().close();
	}
}
