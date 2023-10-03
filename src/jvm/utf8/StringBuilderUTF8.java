package jvm.utf8;

import annotations.Alias;

public class StringBuilderUTF8 {

    // todo StringBuilder should be extended as well :3

    // temporary, while content is still char[]
    @Alias(names = "java_lang_StringBuilder_append_Ljava_lang_StringLjava_lang_StringBuilder")
    private static StringBuilder java_lang_StringBuilder_append_Ljava_lang_StringLjava_lang_StringBuilder(StringBuilder self, String str) {
        byte[] bytes = str.getBytes();
        for (byte b : bytes) self.append((char) b);
        return self;
    }

}
