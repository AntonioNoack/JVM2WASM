package engine;

import kotlin.Unit;
import kotlin.jvm.functions.Function2;
import me.anno.io.files.FileReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.*;
import java.net.URI;
import java.util.Collections;
import java.util.List;

public class VirtualFileRef extends FileReference {

	public VirtualFileRef(String absolutePath) {
		super(absolutePath);
	}

	@Override
	public boolean getExists() {
		return JavaIO.files.get(getAbsolutePath()) != null;
	}

	@Override
	public boolean isDirectory() {
		JavaIO.FileInfo fi = JavaIO.files.get(getAbsolutePath());
		return fi != null && fi.children != null;
	}

	@Override
	public long getLastAccessed() {
		return 0;
	}

	@Override
	public long getLastModified() {
		JavaIO.FileInfo fi = JavaIO.files.get(getAbsolutePath());
		return fi != null ? fi.lastModified : 0;
	}

	@Nullable
	@Override
	public List<FileReference> listChildren() {
		JavaIO.FileInfo fi = JavaIO.files.get(getAbsolutePath());
		if (fi == null || fi.children == null) {
			return Collections.emptyList();
		}
		return fi.children;
	}

	@Override
	public boolean delete() {
		return JavaIO.files.remove(getAbsolutePath()) != null;
	}

	@NotNull
	@Override
	public FileReference getChild(@NotNull String s) {
		return new VirtualFileRef(getAbsolutePath() + '/' + s);
	}

	private FileReference parent;

	@Nullable
	@Override
	public FileReference getParent() {
		String path = getAbsolutePath();
		int lix = path.lastIndexOf('/');
		if (lix < 0) return null;
		if (parent == null) parent = new VirtualFileRef(path.substring(0, lix));
		return parent;
	}

	@Override
	public void inputStream(long l, @NotNull Function2<? super InputStream, ? super Exception, Unit> callback) {
		String path = getAbsolutePath();
		JavaIO.FileInfo fi = JavaIO.files.get(path);
		if (fi != null) {
			if (fi.children != null) callback.invoke(null, new IOException("Cannot read directory"));
			else {
				byte[] bytes = fi.content;
				if (bytes == null) bytes = fi.content2.getBytes();
				callback.invoke(new ByteArrayInputStream(bytes), null);
			}
		} else callback.invoke(null, new FileNotFoundException(path));
	}

	@NotNull
	@Override
	public InputStream inputStreamSync() throws IOException {
		String path = getAbsolutePath();
		JavaIO.FileInfo fi = JavaIO.files.get(path);
		if (fi != null) {
			if (fi.children != null) {
				throw new IOException("Cannot read directory");
			} else {
				byte[] bytes = fi.content;
				if (bytes == null) bytes = fi.content2.getBytes();
				return new ByteArrayInputStream(bytes);
			}
		} else throw new FileNotFoundException(path);
	}

	@NotNull
	@Override
	public String readTextSync() throws IOException {
		String path = getAbsolutePath();
		JavaIO.FileInfo fi = JavaIO.files.get(path);
		if (fi != null) {
			if (fi.children != null) {
				throw new IOException("Cannot read directory");
			} else {
				String str = fi.content2;
				if (str != null) return str;
				byte[] bytes = fi.content;
				if (bytes != null) str = new String(bytes);
				fi.content2 = str;
				return str;
			}
		} else throw new FileNotFoundException(path);
	}

	@Override
	public void readText(@NotNull Function2<? super String, ? super Exception, Unit> callback) {
		String path = getAbsolutePath();
		JavaIO.FileInfo fi = JavaIO.files.get(path);
		if (fi != null) {
			if (fi.children != null) {
				callback.invoke(null, new IOException("Cannot read directory"));
			} else {
				String str = fi.content2;
				if (str == null) {
					str = new String(fi.content);
					fi.content2 = str;
				}
				callback.invoke(str, null);
			}
		} else callback.invoke(null, new FileNotFoundException(path));
	}

	@NotNull
	@Override
	public byte[] readBytesSync() throws IOException {
		String path = getAbsolutePath();
		JavaIO.FileInfo fi = JavaIO.files.get(path);
		if (fi != null) {
			if (fi.children != null) {
				throw new IOException("Cannot read directory");
			} else {
				byte[] bytes = fi.content;
				if (bytes == null) {
					bytes = fi.content2.getBytes();
					fi.content = bytes;
				}
				return bytes;
			}
		} else throw new FileNotFoundException(path);
	}

	@Override
	public void readBytes(@NotNull Function2<? super byte[], ? super Exception, Unit> callback) {
		String path = getAbsolutePath();
		JavaIO.FileInfo fi = JavaIO.files.get(path);
		if (fi != null) {
			if (fi.children != null) {
				callback.invoke(null, new IOException("Cannot read directory"));
			} else {
				byte[] bytes = fi.content;
				if (bytes == null) {
					bytes = fi.content2.getBytes();
					fi.content = bytes;
				}
				callback.invoke(bytes, null);
			}
		} else callback.invoke(null, new FileNotFoundException(path));
	}

	@Override
	public long length() throws FileNotFoundException {
		String path = getAbsolutePath();
		JavaIO.FileInfo fi = JavaIO.files.remove(path);
		if (fi == null) throw new FileNotFoundException(path);
		return fi.length();
	}

	@Override
	public boolean mkdirs() {
		JavaIO.files.put(getAbsolutePath(), new JavaIO.FileInfo(true));
		return true;
	}

	@NotNull
	@Override
	public OutputStream outputStream(boolean append) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream() {
			@Override
			public void close() throws IOException {
				super.close();

			}
		};
		if (append) {
			try {
				byte[] bytes = readBytesSync();
				bos.write(bytes);
			} catch (Exception ignored) {

			}
		}
		return bos;
	}

	@Override
	public void writeText(@NotNull String text) {
		JavaIO.files.put(getAbsolutePath(), new JavaIO.FileInfo(text, System.currentTimeMillis()));
	}

	@Override
	public void writeBytes(@NotNull byte[] bytes) {
		JavaIO.files.put(getAbsolutePath(), new JavaIO.FileInfo(bytes, System.currentTimeMillis()));
	}

	@Override
	public boolean renameTo(@NotNull FileReference dst) {
		JavaIO.FileInfo fi = JavaIO.files.remove(getAbsolutePath());
		if (fi != null) {
			JavaIO.files.put(dst.getAbsolutePath(), fi);
			return true;
		} else return false;
	}

	@NotNull
	@Override
	public URI toUri() {
		throw new NotImplementedException();
		// return null;
	}
}
