package java.lang;

import annotations.Alias;
import jvm.FillBuffer;

import static jvm.JavaLang.fillD2S;

/**
 * Methods for AbstractStringBuilder, helps/fixes our super-resolution
 * */
@SuppressWarnings("ClassEscapesDefinedScope")
public class JavaLang2 {

    @Alias(names = "java_lang_AbstractStringBuilder_append_DLjava_lang_AbstractStringBuilder")
    public static AbstractStringBuilder AbstractStringBuilder_append_DLjava_lang_AbstractStringBuilder(AbstractStringBuilder builder, double v) {
        return StringBuilder_appendDouble(builder, v);
    }

    @Alias(names = "java_lang_AbstractStringStringBuilder_append_DLjava_lang_AbstractStringStringBuilder")
    public static AbstractStringBuilder StringBuilder_appendDouble(AbstractStringBuilder builder, double v) {
        char[] content = FillBuffer.getBuffer();
        int length = fillD2S(content, v);
        builder.append(content, 0, length);
        return builder;
    }
}
