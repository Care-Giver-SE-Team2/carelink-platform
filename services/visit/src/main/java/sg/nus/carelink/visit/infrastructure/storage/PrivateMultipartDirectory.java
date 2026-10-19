package sg.nus.carelink.visit.infrastructure.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The servlet container spools evidence uploads to {@code spring.servlet.multipart.location} and
 * rejects every upload when that directory is missing. In the monolith core's credential storage
 * created it; visit runs on its own, so it makes its own at start-up.
 */
@Component
class PrivateMultipartDirectory {

	PrivateMultipartDirectory(@Value("${spring.servlet.multipart.location:}") String location) {
		if (location.isBlank()) {
			return;
		}
		try {
			Files.createDirectories(Path.of(location).toAbsolutePath().normalize());
		}
		catch (IOException ex) {
			throw new UncheckedIOException("The private multipart directory must be writable.", ex);
		}
	}

}
