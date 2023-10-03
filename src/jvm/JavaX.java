package jvm;

import annotations.Alias;
import annotations.JavaScript;
import annotations.NoThrow;
import org.apache.logging.log4j.LoggerImpl;

public class JavaX {

    @NoThrow
    @JavaScript(code = "var d = new Date();var h=d.getHours(),m=d.getMinutes(),s=d.getSeconds();return fill(arg0, (h<10?'0'+h:h)+':'+(m<10?'0'+m:m)+':'+(s<10?'0'+s:s))")
    public static native int fillDate(char[] chr);

    @Alias(names = "org_apache_logging_log4j_LoggerImpl_getTimeStamp_Ljava_lang_String")
    public static String LoggerImpl_getTimeStamp(LoggerImpl impl) {
        char[] builder = FillBuffer.getBuffer();
        int length = fillDate(builder);
        return new String(builder, 0, length);
    }

    @Alias(names = "javax_vecmath_VecMathI18N_getString_Ljava_lang_StringLjava_lang_String")
    public static String javax_vecmath_VecMathI18N_getString_Ljava_lang_StringLjava_lang_String(String key) {
        // uses resource bundle to map error messages; not worth it in my opinion
        return key;
    }

}
