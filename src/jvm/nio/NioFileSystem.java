package jvm.nio;

import kotlin.NotImplementedError;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static java.util.Collections.emptySet;

public class NioFileSystem extends FileSystem {

    private final FileSystemProvider provider;

    public NioFileSystem(FileSystemProvider provider) {
        this.provider = provider;
    }

    @Override
    public FileSystemProvider provider() {
        return provider;
    }

    @Override
    public void close() throws IOException {

    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public String getSeparator() {
        return "/";
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        throw new NotImplementedError();
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        throw new NotImplementedError();
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return emptySet();
    }

    @NotNull
    @Override
    public Path getPath(@NotNull String s, @NotNull String... strings) {
        if (strings.length > 0) {
            int length = s.length() + strings.length;
            for (String t : strings) {
                length += t.length();
            }
            StringBuilder builder = new StringBuilder(length);
            builder.append(s);
            for (String t : strings) {
                builder.append('/');
                builder.append(t);
            }
            s = builder.toString();
        }
        try {
            return new NioPath(new URI("file", null, null, 0, s, null, null), this);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public PathMatcher getPathMatcher(String s) {
        return null;
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        return new UserPrincipalLookupService() {
            @Override
            public UserPrincipal lookupPrincipalByName(String s) throws IOException {
                return null;
            }

            @Override
            public GroupPrincipal lookupPrincipalByGroupName(String s) throws IOException {
                return null;
            }
        };
    }

    @Override
    public WatchService newWatchService() throws IOException {
        return new WatchService() {
            @Override
            public void close() throws IOException {

            }

            @Override
            public WatchKey poll() {
                return null;
            }

            @Override
            public WatchKey poll(long l, TimeUnit timeUnit) throws InterruptedException {
                return null;
            }

            @Override
            public WatchKey take() throws InterruptedException {
                return null;
            }
        };
    }
}
