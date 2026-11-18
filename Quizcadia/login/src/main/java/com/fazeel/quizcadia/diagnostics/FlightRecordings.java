package com.fazeel.quizcadia.diagnostics;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.time.Duration;
import java.util.Optional;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import jdk.jfr.Configuration;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;

/**
 * {@code GET /actuator/jfr}: a Java Flight Recorder recording of this service, to open in JDK Mission Control or
 * IntelliJ (CPU hot spots, allocations, locks, GC pauses, slow I/O, exceptions).
 *
 * <ul>
 * <li>Without parameters: the last 30 minutes of the always-on recording that every image starts with
 * ({@code -XX:StartFlightRecording=name=continuous}, low overhead, see the Dockerfiles).</li>
 * <li>{@code ?seconds=60}: records the next 1 to 300 seconds with the more detailed "profile" settings, for
 * reproducing something on purpose.</li>
 * </ul>
 *
 * Like {@code /actuator/heapdump} and {@code /actuator/threaddump}, only reachable from inside the service's own
 * container ({@link Diagnostics}); {@code deploy/profile.sh} fetches it there.
 *
 * <p>A copy of quizcadia-security's, which the login service does not use.
 */
@Component
@Endpoint(id = "jfr")
public class FlightRecordings {

	static final String CONTINUOUS = "continuous";
	static final int MAX_SECONDS = 300;

	@ReadOperation(produces = "application/octet-stream")
	public Resource recording(@Nullable Integer seconds) throws IOException, ParseException, InterruptedException {
		Path file = Files.createTempFile("quizcadia-", ".jfr");
		if (seconds != null) {
			recordFor(Duration.ofSeconds(Math.clamp(seconds, 1, MAX_SECONDS)), file);
		}
		else {
			Optional<Recording> continuous = continuous();
			if (continuous.isEmpty()) {
				Files.deleteIfExists(file);
				return null; // 404: started without -XX:StartFlightRecording (e.g. from the IDE); use ?seconds=
			}
			continuous.get().dump(file);
		}
		return new DeletedAfterReading(file);
	}

	static Optional<Recording> continuous() {
		if (!FlightRecorder.isAvailable()) {
			return Optional.empty();
		}
		return FlightRecorder.getFlightRecorder().getRecordings().stream()
				.filter(r -> CONTINUOUS.equals(r.getName())).findFirst();
	}

	private static void recordFor(Duration length, Path file) throws IOException, ParseException, InterruptedException {
		try (Recording r = new Recording(Configuration.getConfiguration("profile"))) {
			r.setName("on-demand");
			r.setToDisk(true);
			r.start();
			Thread.sleep(length);
			r.stop();
			r.dump(file);
		}
	}

	/** The recording file, removed once it has been sent. */
	static final class DeletedAfterReading extends FileSystemResource {

		DeletedAfterReading(Path file) {
			super(file);
		}

		@Override
		public InputStream getInputStream() throws IOException {
			return new FilterInputStream(super.getInputStream()) {
				@Override
				public void close() throws IOException {
					try {
						super.close();
					}
					finally {
						Files.deleteIfExists(getFile().toPath());
					}
				}
			};
		}
	}
}
