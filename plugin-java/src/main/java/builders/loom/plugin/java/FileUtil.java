/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package builders.loom.plugin.java;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public final class FileUtil {

    private FileUtil() {
    }

    public static void deleteDirectoryRecursively(final Path parent, final boolean parentItself) {
        assertDirectory(parent);
        try {
            Files.walkFileTree(parent, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
                    throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(final Path dir, final IOException exc)
                    throws IOException {
                    if (parentItself || !dir.equals(parent)) {
                        Files.delete(dir);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void assertDirectory(final Path dir) {
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Directory '" + dir + " doesn't exist");
        }
    }

    public static void copy(final Path srcDir, final JarOutputStream os) throws IOException {
        if (Files.isDirectory(srcDir)) {
            Files.walkFileTree(srcDir, new CreateJarFileVisitor(srcDir, os));
        }
    }

    private static class CreateJarFileVisitor extends SimpleFileVisitor<Path> {

        private final Path sourceDir;
        private final JarOutputStream os;

        CreateJarFileVisitor(final Path sourceDir, final JarOutputStream os) {
            this.sourceDir = sourceDir;
            this.os = os;
        }

        @Override
        public FileVisitResult preVisitDirectory(final Path dir,
                                                 final BasicFileAttributes attrs)
            throws IOException {

            if (dir.equals(sourceDir)) {
                return FileVisitResult.CONTINUE;
            }

            final JarEntry entry = new JarEntry(sourceDir.relativize(dir).toString() + "/");
            entry.setTime(attrs.lastModifiedTime().toMillis());
            os.putNextEntry(entry);
            os.closeEntry();

            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(final Path file,
                                         final BasicFileAttributes attrs)
            throws IOException {

            final JarEntry entry = new JarEntry(sourceDir.relativize(file).toString());
            entry.setTime(attrs.lastModifiedTime().toMillis());
            os.putNextEntry(entry);
            Files.copy(file, os);
            os.closeEntry();
            return FileVisitResult.CONTINUE;
        }

    }
}
