package builders.loom.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

// TODO
public class SkipChecksumUtils {

	public static String jvmVersion() {
		return "JVM " + Runtime.version().toString();
	}

	public static String file(final Path file) {
		try {
			return Hasher.bytesToHex(Hasher.hash(Files.readAllBytes(file)));
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
	}



	// TODO
	// config ?
	// config files (e.g. checkstyle.xml)
	// setup: (e.g. jdk version)
	// ENV
	// systemproperties


}
