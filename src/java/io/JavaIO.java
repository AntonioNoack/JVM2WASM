package java.io;

import annotations.Alias;
import me.anno.io.files.FileFileRef;
import me.anno.io.files.FileReference;

import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashMap;

public class JavaIO {

	private static FileSystem fs;

	public static class FileInfo {
		public ArrayList<FileReference> children;
		public byte[] content;
		public String content2;
		public long lastModified;

		public FileInfo(boolean directory) {
			if (directory) this.children = new ArrayList<>();
		}

		public FileInfo(String content2, long lastModified) {
			this.content2 = content2;
			this.lastModified = lastModified;
		}

		public FileInfo(byte[] content, long lastModified) {
			this.content = content;
			this.lastModified = lastModified;
		}

		public int length() {
			if (content2 != null) return content2.length();
			if (content != null) return content.length;
			return 0;
		}
	}

	public static final HashMap<String, FileInfo> files = new HashMap<>();

	@Alias(names = "me_anno_io_files_FileReference_writeText_Ljava_lang_StringV")
	public static void FileReference_writeText_Ljava_lang_StringV(FileReference ref, String content) {
		FileInfo fi = new FileInfo(false);
		fi.content2 = content;
		files.put(ref.getAbsolutePath(), fi);
	}

	@Alias(names = "java_io_DefaultFileSystem_getFileSystem_Ljava_io_FileSystem")
	private static FileSystem DefaultFileSystem_getFileSystem() {
		if (fs == null) fs = new FileSystem() {

			// todo make this a proper linux file system :)

			@Override
			public char getSeparator() {
				return '/';
			}

			@Override
			public boolean isInvalid(File f) {
				return false;
			}

			@Override
			public char getPathSeparator() {
				return '/';
			}

			@Override
			public String normalize(String s) {
				// todo what does this do?
				return s;
			}

			@Override
			public int prefixLength(String s) {
				// https://stackoverflow.com/questions/39267785/what-is-a-pathname-strings-prefix-and-its-length-in-java
				return !s.isEmpty() && s.charAt(0) == '/' ? 1 : 0;
			}

			@Override
			public String resolve(String s, String s1) {
				if (s.endsWith("/")) return s + s1;
				else return s + "/" + s1;
			}

			@Override
			public String getDefaultParent() {
				// file is being executed in root folder -> true :)
				return "/";
			}

			@Override
			public String fromURIPath(String s) {
				return s;
			}

			@Override
			public boolean isAbsolute(File file) {
				// file is being executed in root folder -> true :)
				return true;
			}

			@Override
			public String resolve(File file) {
				// file is being executed in root folder -> same path :)
				return file.getPath();
			}

			@Override
			public String canonicalize(String s) {
				return s;
			}

			@Override
			public int getBooleanAttributes(File file) {
				FileInfo info = files.get(file.getPath());
				// log("Looking up", file.getPath());
				if (info != null) {
					return (info.children != null ? BA_DIRECTORY : BA_REGULAR) | BA_EXISTS;
				} else return 0;
			}

			@Override
			public boolean checkAccess(File file, int i) {
				return false;
			}

			@Override
			public boolean setPermission(File file, int i, boolean b, boolean b1) {
				return false;
			}

			@Override
			public long getLastModifiedTime(File file) {
				return 0;
			}

			@Override
			public long getLength(File file) {
				return 0;
			}

			@Override
			public boolean createFileExclusively(String s) {
				return false;
			}

			@Override
			public boolean delete(File file) {
				return false;
			}

			@Override
			public String[] list(File file) {
				return new String[0];
			}

			@Override
			public boolean createDirectory(File file) {
				return false;
			}

			@Override
			public boolean rename(File file, File file1) {
				return false;
			}

			@Override
			public boolean setLastModifiedTime(File file, long l) {
				return false;
			}

			@Override
			public boolean setReadOnly(File file) {
				return false;
			}

			@Override
			public File[] listRoots() {
				return new File[]{new File("/")};
			}

			@Override
			public long getSpace(File file, int i) {
				return 0;
			}

			@Override
			public int compare(File file, File file1) {
				return file.getName().compareTo(file1.getName());
			}

			@Override
			public int hashCode(File file) {
				return System.identityHashCode(file);
			}
		};
		return fs;
	}

	@Alias(names = "java_security_AccessController_doPrivileged_Ljava_security_PrivilegedActionLjava_lang_Object")
	public static <T> T AccessController_doPrivileged(PrivilegedAction<T> action) {
		return action.run();
	}

}
