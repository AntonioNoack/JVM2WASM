package engine;

import me.anno.io.files.FileReference;
import me.anno.io.files.InvalidRef;
import me.anno.utils.async.Callback;
import org.jetbrains.annotations.NotNull;

import java.io.*;
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
	public long getCreationTime() {
		return 0L;
	}

	@Override
	public long getLastModified() {
		JavaIO.FileInfo fi = JavaIO.files.get(getAbsolutePath());
		return fi != null ? fi.lastModified : 0;
	}

	@NotNull
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
	public FileReference getChildImpl(@NotNull String s) {
		return new VirtualFileRef(getAbsolutePath() + '/' + s);
	}

	private FileReference parent;

	@NotNull
	@Override
	public FileReference getParent() {
		String path = getAbsolutePath();
		int lix = path.lastIndexOf('/');
		if (lix < 0) return InvalidRef.INSTANCE;
		if (parent == null) parent = new VirtualFileRef(path.substring(0, lix));
		return parent;
	}

	@Override
	public void inputStream(long lengthLimit, boolean close, @NotNull Callback<InputStream> callback) {
		String path = getAbsolutePath();
		JavaIO.FileInfo fi = JavaIO.files.get(path);
		if (fi != null) {
			if (fi.children != null) callback.err(new IOException("Cannot read directory"));
			else {
				byte[] bytes = fi.content;
				if (bytes == null) bytes = fi.content2.getBytes();
				callback.ok(new ByteArrayInputStream(bytes));
			}
		} else callback.err(new FileNotFoundException(path));
	}

	@NotNull
	@Override
	public InputStream inputStreamSync() {
		String path = getAbsolutePath();
		JavaIO.FileInfo fi = JavaIO.files.get(path);
		if (fi != null) {
			if (fi.children != null) {
				throw new RuntimeException("Cannot read directory");
			} else {
				byte[] bytes = fi.content;
				if (bytes == null) bytes = fi.content2.getBytes();
				return new ByteArrayInputStream(bytes);
			}
		} else throw new RuntimeException("Missing file " + path);
	}

	@NotNull
	@Override
	public String readTextSync() {
		String path = getAbsolutePath();
		JavaIO.FileInfo fi = JavaIO.files.get(path);
		if (fi != null) {
			if (fi.children != null) {
				throw new RuntimeException("Cannot read directory");
			} else {
				String str = fi.content2;
				if (str != null) return str;
				byte[] bytes = fi.content;
				if (bytes != null) str = new String(bytes);
				fi.content2 = str;
				return str;
			}
		} else throw new RuntimeException("Missing file " + path);
	}

	@Override
	public void readText(@NotNull Callback<String> callback) {
		String path = getAbsolutePath();
		JavaIO.FileInfo fi = JavaIO.files.get(path);
		if (fi != null) {
			if (fi.children != null) {
				callback.err(new IOException("Cannot read directory"));
			} else {
				String str = fi.content2;
				if (str == null) {
					str = new String(fi.content);
					fi.content2 = str;
				}
				callback.ok(str);
			}
		} else callback.err(new FileNotFoundException(path));
	}

	@NotNull
	@Override
	public byte[] readBytesSync() {
		String path = getAbsolutePath();
		JavaIO.FileInfo fi = JavaIO.files.get(path);
		if (fi != null) {
			if (fi.children != null) {
				throw new RuntimeException("Cannot read directory");
			} else {
				byte[] bytes = fi.content;
				if (bytes == null) {
					bytes = fi.content2.getBytes();
					fi.content = bytes;
				}
				return bytes;
			}
		} else throw new RuntimeException("Missing path " + path);
	}

	@Override
	public void readBytes(@NotNull Callback<byte[]> callback) {
		String path = getAbsolutePath();
		JavaIO.FileInfo fi = JavaIO.files.get(path);
		if (fi != null) {
			if (fi.children != null) {
				callback.err(new IOException("Cannot read directory"));
			} else {
				byte[] bytes = fi.content;
				if (bytes == null) {
					bytes = fi.content2.getBytes();
					fi.content = bytes;
				}
				callback.ok(bytes);
			}
		} else callback.err(new FileNotFoundException(path));
	}

	@Override
	public long length() {
		String path = getAbsolutePath();
		JavaIO.FileInfo fi = JavaIO.files.remove(path);
		if (fi == null) throw new RuntimeException("Missing path " + path);
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
	public void writeText(@NotNull String text, int offset, int length) {
		assert offset == 0;
		assert text.length() == length;
		JavaIO.files.put(getAbsolutePath(), new JavaIO.FileInfo(text, System.currentTimeMillis()));
	}

	@Override
	public void writeBytes(@NotNull byte[] bytes, int offset, int length) {
		assert offset == 0;
		assert bytes.length == length;
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
}
