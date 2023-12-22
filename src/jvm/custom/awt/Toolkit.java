package jvm.custom.awt;

import annotations.Alias;

import java.awt.datatransfer.Clipboard;

import static jvm.LWJGLxGLFW.getWindowHeight;
import static jvm.LWJGLxGLFW.getWindowWidth;

@SuppressWarnings("unused")
public class Toolkit {
	public static final Toolkit INSTANCE = new Toolkit();
	public static Dimension screenSize = new Dimension(getWindowWidth(), getWindowHeight());

	@Alias(names = "jvm_custom_awt_Toolkit_getScreenSize_Ljava_awt_Dimension")
	public Dimension getScreenSize() {
		return screenSize;
	}

	public Clipboard getSystemClipboard() {
		return null;
	}

	@Alias(names = "jvm_custom_awt_Toolkit_getDefaultToolkit_Ljava_awt_Toolkit")
	public static Toolkit getDefaultToolkit() {
		return Toolkit.INSTANCE;
	}

}
