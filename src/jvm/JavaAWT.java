package jvm;

import annotations.Alias;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.Map;

public class JavaAWT {

	@Alias(names = "java_awt_Desktop_getDesktop_Ljava_awt_Desktop")
	public static Desktop getDesktop() {
		throw new UnsupportedOperationException("Desktop API is not supported on the current platform");
	}

	@Alias(names = "java_awt_Desktop_isDesktopSupported_Z")
	public static boolean isDesktopSupported() {
		return false;
	}

	@Alias(names = "java_awt_Robot_waitForIdle_V")
	public static void robotWaitForIdle(Object robot) {
		// idk ...
	}

	@Alias(names = "java_awt_GraphicsEnvironment_createGE_Ljava_awt_GraphicsEnvironment")
	public static GraphicsEnvironment GraphicsEnvironment_createGE() {
		return new GraphicsEnvironment() {
			@Override
			public GraphicsDevice[] getScreenDevices() throws HeadlessException {
				return new GraphicsDevice[0];
			}

			@Override
			public GraphicsDevice getDefaultScreenDevice() throws HeadlessException {
				return null;
			}

			@Override
			public Graphics2D createGraphics(BufferedImage bufferedImage) {
				return null;
			}

			@Override
			public Font[] getAllFonts() {
				return new Font[0];
			}

			@Override
			public String[] getAvailableFontFamilyNames() {
				return new String[0];
			}

			@Override
			public String[] getAvailableFontFamilyNames(Locale locale) {
				return new String[0];
			}
		};
	}

	@Alias(names = "java_awt_Robot_init_Ljava_awt_GraphicsDeviceV")
	public static void Robot_init(Robot robot, GraphicsDevice device) {
	}

	@Alias(names = "java_awt_Font_deriveFont_IFLjava_awt_Font")
	public static Font java_awt_Font_deriveFont_IFLjava_awt_Font(Font self, int flags, float size) {
		return new Font(self.getFontName(), flags, (int) size);
	}

	@Alias(names = "java_awt_Font_getFontName_Ljava_util_LocaleLjava_lang_String")
	public static String java_awt_Font_getFontName_Ljava_util_LocaleLjava_lang_String(Font font, Locale locale) {
		return font.getName();
	}

	@Alias(names = "java_awt_Font_hashCode_I")
	public static int java_awt_Font_hashCode_I(Font font) {
		return System.identityHashCode(font);
	}

	@Alias(names = "static_java_awt_RenderingHints_V")
	public static void static_java_awt_RenderingHints_V() {
		// just setting all key constants (with sun values); hopefully they can be null
	}

	@Alias(names = "java_awt_font_TextLayout_fastInit_ACLjava_awt_FontLjava_util_MapLjava_awt_font_FontRenderContextV")
	public static void java_awt_font_TextLayout_fastInit_ACLjava_awt_FontLjava_util_MapLjava_awt_font_FontRenderContextV(
			TextLayout self, char[] chars, Font font, Map map, FontRenderContext ctx) {
		// lots of stuff, that we probably don't need ^^
	}

	@Alias(names = "new_java_awt_font_TextLayout_Ljava_lang_StringLjava_awt_FontLjava_awt_font_FontRenderContextV")
	public static void new_java_awt_font_TextLayout_Ljava_lang_StringLjava_awt_FontLjava_awt_font_FontRenderContextV(
			TextLayout self, String text, Font font, FontRenderContext ctx) {

	}

	// font size = ascent + descent
	// todo how can we get the font? constructor... where do we save it?
	@Alias(names = "java_awt_font_TextLayout_getAscent_F")
	public static float java_awt_font_TextLayout_getAscent_F(TextLayout layout) {
		return 10f;
	}

	@Alias(names = "java_awt_font_TextLayout_getDescent_F")
	public static float java_awt_font_TextLayout_getDescent_F(TextLayout layout) {
		return 10f;
	}

	@Alias(names = "java_awt_font_TextLayout_getOutline_Ljava_awt_geom_AffineTransformLjava_awt_Shape")
	public static Shape TextLayout_getOutline(TextLayout layout, AffineTransform transform) {
		// todo generate shape like in Android
		GeneralPath shape = new GeneralPath();
		shape.moveTo(0f, 0f);
		shape.lineTo(0f, 1f);
		shape.lineTo(1f, 1f);
		shape.lineTo(1f, 0f);
		shape.closePath();
		return shape;
	}

	@Alias(names = "static_java_awt_datatransfer_DataFlavor_V")
	public static void static_java_awt_datatransfer_DataFlavor_V() {
	}

}
