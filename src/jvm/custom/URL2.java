package jvm.custom;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;

import static jvm.JVMShared.unsafeCast;

@SuppressWarnings("unused")
public class URL2 {
    private final String path;

    public URL2(String path) {
        this.path = path;
    }

    public URL2(String a, String n0, String n1, int port, String path, String n2, String n3) {
        this.path = path;
    }

    @Override
    public String toString() {
        return path;
    }

    public URL toURL() {
        return unsafeCast(this);
    }

    public URI toURI() {
        return unsafeCast(this);
    }

    public InputStream openStream() {
        throw new RuntimeException("Please use async FileReferences for IO");
    }
}
