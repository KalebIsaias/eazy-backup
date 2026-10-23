package io.github.kalebisaias.eazybackup.backup;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

final class ZipArchiver {
	/** Held open by the game while a world is loaded, it can't be read and is useless in a backup. */
	private static final String SESSION_LOCK = "session.lock";
	private static final int BUFFER_SIZE = 64 * 1024;
	private static final int READ_ATTEMPTS = 3;

	private ZipArchiver() {
	}

	/**
	 * Compresses {@code source} into {@code target}. Entries are placed inside a {@code rootName} folder,
	 * matching the layout of vanilla world backups.
	 */
	static void zip(Path source, String rootName, Path target, int level, ProgressListener progress) throws IOException {
		List<Path> files = new ArrayList<>();
		long totalBytes = 0L;
		try (var stream = Files.walk(source)) {
			for (Path path : (Iterable<Path>) stream::iterator) {
				if (Files.isRegularFile(path) && !path.getFileName().toString().equals(SESSION_LOCK)) {
					files.add(path);
					totalBytes += Files.size(path);
				}
			}
		}

		Path temp = target.resolveSibling(target.getFileName() + ".part");
		long doneBytes = 0L;
		byte[] buffer = new byte[BUFFER_SIZE];
		try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(temp), BUFFER_SIZE))) {
			zip.setLevel(level);
			for (Path file : files) {
				String entryName = rootName + "/" + source.relativize(file).toString().replace('\\', '/');
				doneBytes += writeEntry(zip, file, entryName, buffer);
				progress.update(totalBytes == 0L ? 1.0F : (float) doneBytes / totalBytes);
			}
		} catch (IOException | RuntimeException e) {
			Files.deleteIfExists(temp);
			throw e;
		}

		Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
		progress.update(1.0F);
	}

	private static long writeEntry(ZipOutputStream zip, Path file, String entryName, byte[] buffer) throws IOException {
		ZipEntry entry = new ZipEntry(entryName);
		entry.setLastModifiedTime(Files.getLastModifiedTime(file));
		// Small files such as level.dat may be replaced by the game while we read them, so retry a few times.
		byte[] content = null;
		IOException failure = null;
		long size = Files.size(file);
		if (size <= 4L * 1024L * 1024L) {
			for (int attempt = 0; attempt < READ_ATTEMPTS && content == null; attempt++) {
				try {
					content = Files.readAllBytes(file);
				} catch (IOException e) {
					failure = e;
					sleepQuietly(50L * (attempt + 1));
				}
			}

			if (content == null) {
				throw failure;
			}

			zip.putNextEntry(entry);
			zip.write(content);
			zip.closeEntry();
			return content.length;
		}

		zip.putNextEntry(entry);
		long written = 0L;
		try (InputStream input = Files.newInputStream(file)) {
			int read;
			while ((read = input.read(buffer)) != -1) {
				zip.write(buffer, 0, read);
				written += read;
			}
		}

		zip.closeEntry();
		return written;
	}

	/**
	 * Extracts {@code archive} into {@code target}. If every entry shares the same top level folder
	 * (as backups created by this mod or by vanilla do), that folder is stripped.
	 */
	static void unzip(Path archive, Path target, ProgressListener progress) throws IOException {
		Path normalizedTarget = target.toAbsolutePath().normalize();
		Files.createDirectories(normalizedTarget);
		try (ZipFile zip = new ZipFile(archive.toFile())) {
			String commonRoot = findCommonRoot(zip);
			long totalBytes = 0L;
			for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); ) {
				totalBytes += Math.max(0L, entries.nextElement().getSize());
			}

			long doneBytes = 0L;
			byte[] buffer = new byte[BUFFER_SIZE];
			for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); ) {
				ZipEntry entry = entries.nextElement();
				String name = entry.getName();
				if (commonRoot != null) {
					name = name.substring(commonRoot.length());
				}

				if (name.isEmpty()) {
					continue;
				}

				Path destination = normalizedTarget.resolve(name).normalize();
				if (!destination.startsWith(normalizedTarget)) {
					throw new IOException("Archive entry is outside of the target folder: " + entry.getName());
				}

				if (entry.isDirectory()) {
					Files.createDirectories(destination);
					continue;
				}

				Files.createDirectories(destination.getParent());
				try (InputStream input = zip.getInputStream(entry); OutputStream output = Files.newOutputStream(destination)) {
					int read;
					while ((read = input.read(buffer)) != -1) {
						output.write(buffer, 0, read);
						doneBytes += read;
					}
				}

				if (entry.getLastModifiedTime() != null) {
					Files.setLastModifiedTime(destination, entry.getLastModifiedTime());
				}

				progress.update(totalBytes == 0L ? 1.0F : Math.min(1.0F, (float) doneBytes / totalBytes));
			}
		}

		progress.update(1.0F);
	}

	private static String findCommonRoot(ZipFile zip) {
		String root = null;
		for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); ) {
			String name = entries.nextElement().getName();
			int slash = name.indexOf('/');
			if (slash <= 0) {
				return null;
			}

			String candidate = name.substring(0, slash + 1);
			if (root == null) {
				root = candidate;
			} else if (!root.equals(candidate)) {
				return null;
			}
		}

		return root;
	}

	static void deleteRecursively(Path path) throws IOException {
		if (!Files.exists(path)) {
			return;
		}

		Files.walkFileTree(path, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
				if (exception != null) {
					throw exception;
				}

				Files.delete(directory);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private static void sleepQuietly(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	interface ProgressListener {
		void update(float progress);
	}
}
