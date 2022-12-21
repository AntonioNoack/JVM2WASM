package jvm.nio;

import org.jetbrains.annotations.NotNull;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.Iterator;

public class NioPath implements Path {
    private final URI uri;
    private final FileSystem fs;

    public NioPath(URI uri, FileSystem fs) {
        this.uri = uri;
        this.fs = fs;
    }

    @NotNull
    @Override
    public FileSystem getFileSystem() {
        return fs;
    }

    @Override
    public boolean isAbsolute() {
        return true;
    }

    @Override
    public Path getRoot() {
        return null;
    }

    @Override
    public Path getFileName() {
        return null;
    }

    @Override
    public Path getParent() {
        return null;
    }

    @Override
    public int getNameCount() {
        return 0;
    }

    @NotNull
    @Override
    public Path getName(int i) {
        return this;
    }

    @NotNull
    @Override
    public Path subpath(int i, int i1) {
        throw new NotImplementedException();
        // return null;
    }

    @Override
    public boolean startsWith(@NotNull Path path) {
        return startsWith(path.toString());
    }

    @Override
    public boolean startsWith(@NotNull String s) {
        return false;
    }

    @Override
    public boolean endsWith(@NotNull Path path) {
        return endsWith(path.toString());
    }

    @Override
    public boolean endsWith(@NotNull String s) {
        return false;
    }

    @NotNull
    @Override
    public Path normalize() {
        return this;
    }

    @NotNull
    @Override
    public Path resolve(@NotNull Path path) {
        return null;
    }

    @NotNull
    @Override
    public Path resolve(@NotNull String s) {
        return null;
    }

    @NotNull
    @Override
    public Path resolveSibling(@NotNull Path path) {
        return null;
    }

    @NotNull
    @Override
    public Path resolveSibling(@NotNull String s) {
        return null;
    }

    @NotNull
    @Override
    public Path relativize(@NotNull Path path) {
        return null;
    }

    @NotNull
    @Override
    public URI toUri() {
        return uri;
    }

    @NotNull
    @Override
    public Path toAbsolutePath() {
        return this;
    }

    @NotNull
    @Override
    public Path toRealPath(@NotNull LinkOption... linkOptions) throws IOException {
        return this;
    }

    @NotNull
    @Override
    public File toFile() {
        return new File(uri);
    }

    @NotNull
    @Override
    public WatchKey register(@NotNull WatchService watchService, @NotNull WatchEvent.Kind<?>[] kinds, WatchEvent.Modifier... modifiers) throws IOException {
        return null;
    }

    @NotNull
    @Override
    public WatchKey register(@NotNull WatchService watchService, @NotNull WatchEvent.Kind<?>... kinds) throws IOException {
        return null;
    }

    @NotNull
    @Override
    public Iterator<Path> iterator() {
        return null;
    }

    @Override
    public int compareTo(@NotNull Path path) {
        return toString().compareTo(path.toString());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof NioPath && ((NioPath) o).uri.equals(uri);
    }

    @Override
    public int hashCode() {
        return uri.hashCode();
    }

    @NotNull
    @Override
    public String toString() {
        return uri.toString();
    }
}
