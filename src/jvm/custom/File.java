package jvm.custom;

import annotations.Alias;
import annotations.NoThrow;
import kotlin.NotImplementedError;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;

import static jvm.JVMShared.unsafeCast;

// 9.88 MB -> 9.82 MB -> not really worth it...
@SuppressWarnings("unused")
public class File {
    private final String path;

    @NoThrow
    public File(String path) {
        this.path = path;
    }

    public File(URI uri) {
        this.path = uri.toString();
    }

    public File(java.io.File parent, String child) {
        this.path = parent + "/" + child;
    }

    public File(String a, String n0, String n1, int port, String path, String n2, String n3) {
        this.path = path;
    }

    // todo check if it actually exists... cannot be done sync
    // todo -> bake in list of all existing things...
    @NoThrow
    public boolean exists() {
        return true;
    }

    @NoThrow
    public boolean isDirectory() {
        return false;
    }

    @NoThrow
    public long lastModified() {
        return 0L;
    }

    @NoThrow
    public long length() {
        return -1L;
    }

    @NoThrow
    public static java.io.File[] listRoots() {
        return roots;
    }

    @NoThrow
    public String getAbsolutePath() {
        return path;
    }

    @Override
    public String toString() {
        return path;
    }

    @NoThrow
    public Path toPath() {
        return unsafeCast(this);
    }

    @NoThrow
    public String getPath() {
        return path;
    }

    public java.io.File getParentFile() {
        throw new RuntimeException("Not implemented");
    }

    @NoThrow
    public java.io.File toFile() {
        return unsafeCast(this);
    }

    public void deleteOnExit() {
        // todo delete this, when we have persistent files
    }

    public boolean delete() {
        // todo delete this, when we have persistent files
        return false;
    }

    public boolean renameTo(java.io.File target) {
        // todo do this
        return false;
    }

    public URLConnection openConnection() {
        throw new RuntimeException("Not implemented");
    }

    public WatchKey register(WatchService service, WatchEvent.Kind[] kinds) {
        // idk, maybe later we support this :)
        return null;
    }

    public String getName() {
        throw new RuntimeException("Not implemented");
    }

    @NoThrow
    public static <V> BasicFileAttributes readAttributes(Path path, Class<V> clazz, LinkOption[] options) {
        return null;
    }

    public static <T> Path createTempDirectory(String sth, FileAttribute<T>[] attributes) {
        return null;
    }

    public static <T> java.io.File createTempFile(String sth, String sth2) {
        return null;
    }

    public boolean mkdirs() {
        return false;
    }

    public static URI create(String path) {
        return unsafeCast(new File(path));
    }

    public URL toURL() {
        return unsafeCast(this);
    }

    public URI toURI() {
        return unsafeCast(this);
    }

    public boolean isInvalid() {
        return false;
    }

    public boolean isAbsolute() {
        return true;
    }

    public InputStream openStream() {
        throw new RuntimeException("Please use async FileReferences for IO");
    }

    private static final java.io.File[] roots = {new java.io.File("/")};

    @Alias(names = "java_io_FileInputStream_open0_Ljava_lang_StringV")
    public static void java_io_FileInputStream_open0_Ljava_lang_StringV(FileInputStream self, String path) {
        throw new NotImplementedError("Why are we using files?");
    }
}
