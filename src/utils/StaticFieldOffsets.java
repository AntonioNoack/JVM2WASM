package utils;

import static jvm.JVM32.objectOverhead;
import static jvm.JVM32.ptrSize;
import static jvm.JVMShared.intSize;

// todo hardcode all fields that need hardcoding;
//  verify them, so everything is still secure
public class StaticFieldOffsets {

    public static final int OFFSET_ARRAY_LENGTH = objectOverhead;

    public static final int OFFSET_CLASS_INDEX = objectOverhead;
    public static final int OFFSET_CLASS_NAME = objectOverhead + intSize;
    public static final int OFFSET_CLASS_SIMPLE_NAME = objectOverhead + intSize + ptrSize;
    public static final int OFFSET_CLASS_FIELDS = objectOverhead + intSize + 2 * ptrSize;
    public static final int OFFSET_CLASS_METHODS = objectOverhead + intSize + 3 * ptrSize;
    public static final int OFFSET_CLASS_CONSTRUCTORS = objectOverhead + intSize + 4 * ptrSize;
    public static final int OFFSET_CLASS_ENUM_CONSTANTS = objectOverhead + intSize + 5 * ptrSize;
    public static final int OFFSET_CLASS_MODIFIERS = objectOverhead + intSize + 6 * ptrSize;

    public static final int OFFSET_STRING_HASH = objectOverhead;
    public static final int OFFSET_STRING_VALUE = objectOverhead + intSize;

    public static final int OFFSET_FIELD_SLOT = objectOverhead;
    public static final int OFFSET_FIELD_NAME = objectOverhead + intSize;
    public static final int OFFSET_FIELD_TYPE = objectOverhead + intSize + ptrSize;
    public static final int OFFSET_FIELD_CLAZZ = objectOverhead + intSize + 2 * ptrSize;
    public static final int OFFSET_FIELD_ANNOTATIONS = objectOverhead + intSize + 3 * ptrSize;
    public static final int OFFSET_FIELD_MODIFIERS = objectOverhead + intSize + 4 * ptrSize;

    public static final int OFFSET_METHOD_SLOT = objectOverhead;
    public static final int OFFSET_METHOD_NAME = objectOverhead + intSize;
    public static final int OFFSET_METHOD_RETURN_TYPE = objectOverhead + intSize + ptrSize;
    public static final int OFFSET_METHOD_PARAMETER_TYPES = objectOverhead + intSize + 2 * ptrSize;
    public static final int OFFSET_METHOD_CALL_SIGNATURE = objectOverhead + intSize + 3 * ptrSize;
    public static final int OFFSET_METHOD_DECLARING_CLASS = objectOverhead + intSize + 4 * ptrSize;
    public static final int OFFSET_METHOD_MODIFIERS = objectOverhead + intSize + 5 * ptrSize;

    public static final int OFFSET_CONSTRUCTOR_SLOT = objectOverhead;
    public static final int OFFSET_CONSTRUCTOR_PARAMETER_TYPES = objectOverhead + intSize ;
    public static final int OFFSET_CONSTRUCTOR_CALL_SIGNATURE = objectOverhead + intSize + ptrSize;
    public static final int OFFSET_CONSTRUCTOR_DECLARING_CLASS = objectOverhead + intSize + 2 * ptrSize;
    public static final int OFFSET_CONSTRUCTOR_MODIFIERS = objectOverhead + intSize + 3 * ptrSize;

    public static final int OFFSET_THROWABLE_MESSAGE = objectOverhead;
    public static final int OFFSET_THROWABLE_STACKTRACE = objectOverhead + ptrSize;

    public static final int OFFSET_STE_LINE = objectOverhead;
    public static final int OFFSET_STE_CLASS = objectOverhead + intSize;
    public static final int OFFSET_STE_METHOD = objectOverhead + intSize + ptrSize;
    public static final int OFFSET_STE_FILE = objectOverhead + intSize + ptrSize * 2;

}
