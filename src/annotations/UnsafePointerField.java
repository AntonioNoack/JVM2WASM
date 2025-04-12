package annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Won't be garbage collected.
 * */
@Retention(RetentionPolicy.RUNTIME)
public @interface UnsafePointerField {
}
