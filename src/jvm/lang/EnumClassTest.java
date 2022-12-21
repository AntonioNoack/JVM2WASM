package jvm.lang;

public enum EnumClassTest {

    ENUM_CLASS_TEST(0),
    CLASS_TEST(1),
    TEST(2);

    EnumClassTest(int mode) {
        this.mode = mode;
    }

    // todo if a class is enum, replace "return" in clinit with
    // todo - findClass
    // todo - getField
    // todo - set(clazz, enumConstants)

    // todo invoke static from JavaLangAccess -> todo make it findable with resolveInterface()
    // todo or call JavaLangAccess.values()...

    /**
  // access flags 0x4019
  public final static enum Ljvm/lang/EnumClassTest; ENUM_CLASS_TEST

  // access flags 0x4019
  public final static enum Ljvm/lang/EnumClassTest; CLASS_TEST

  // access flags 0x4019
  public final static enum Ljvm/lang/EnumClassTest; TEST

  // access flags 0x11
  public final I mode

  // access flags 0x101A
  private final static synthetic [Ljvm/lang/EnumClassTest; $VALUES

  // access flags 0x9
  public static values()[Ljvm/lang/EnumClassTest;
   L0
    LINENUMBER 3 L0
    GETSTATIC jvm/lang/EnumClassTest.$VALUES : [Ljvm/lang/EnumClassTest;
    INVOKEVIRTUAL [Ljvm/lang/EnumClassTest;.clone ()Ljava/lang/Object;
    CHECKCAST [Ljvm/lang/EnumClassTest;
    ARETURN
    MAXSTACK = 1
    MAXLOCALS = 0

  // access flags 0x9
  public static valueOf(Ljava/lang/String;)Ljvm/lang/EnumClassTest;
   L0
    LINENUMBER 3 L0
    LDC Ljvm/lang/EnumClassTest;.class
    ALOAD 0
    INVOKESTATIC java/lang/Enum.valueOf (Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;
    CHECKCAST jvm/lang/EnumClassTest
    ARETURN
   L1
    LOCALVARIABLE name Ljava/lang/String; L0 L1 0
    MAXSTACK = 2
    MAXLOCALS = 1

  // access flags 0x2
  // signature (I)V
  // declaration: void <init>(int)
  private <init>(Ljava/lang/String;II)V
   L0
    LINENUMBER 9 L0
    ALOAD 0
    ALOAD 1
    ILOAD 2
    INVOKESPECIAL java/lang/Enum.<init> (Ljava/lang/String;I)V
   L1
    LINENUMBER 10 L1
    ALOAD 0
    ILOAD 3
    PUTFIELD jvm/lang/EnumClassTest.mode : I
   L2
    LINENUMBER 11 L2
    RETURN
   L3
    LOCALVARIABLE this Ljvm/lang/EnumClassTest; L0 L3 0
    LOCALVARIABLE mode I L0 L3 3
    MAXSTACK = 3
    MAXLOCALS = 4

  // access flags 0x8
  static <clinit>()V
   L0
    LINENUMBER 5 L0
    NEW jvm/lang/EnumClassTest
    DUP
    LDC "ENUM_CLASS_TEST"
    ICONST_0
    ICONST_0
    INVOKESPECIAL jvm/lang/EnumClassTest.<init> (Ljava/lang/String;II)V
    PUTSTATIC jvm/lang/EnumClassTest.ENUM_CLASS_TEST : Ljvm/lang/EnumClassTest;
   L1
    LINENUMBER 6 L1
    NEW jvm/lang/EnumClassTest
    DUP
    LDC "CLASS_TEST"
    ICONST_1
    ICONST_1
    INVOKESPECIAL jvm/lang/EnumClassTest.<init> (Ljava/lang/String;II)V
    PUTSTATIC jvm/lang/EnumClassTest.CLASS_TEST : Ljvm/lang/EnumClassTest;
   L2
    LINENUMBER 7 L2
    NEW jvm/lang/EnumClassTest
    DUP
    LDC "TEST"
    ICONST_2
    ICONST_2
    INVOKESPECIAL jvm/lang/EnumClassTest.<init> (Ljava/lang/String;II)V
    PUTSTATIC jvm/lang/EnumClassTest.TEST : Ljvm/lang/EnumClassTest;
   L3
    LINENUMBER 3 L3
    ICONST_3
    ANEWARRAY jvm/lang/EnumClassTest
    DUP
    ICONST_0
    GETSTATIC jvm/lang/EnumClassTest.ENUM_CLASS_TEST : Ljvm/lang/EnumClassTest;
    AASTORE
    DUP
    ICONST_1
    GETSTATIC jvm/lang/EnumClassTest.CLASS_TEST : Ljvm/lang/EnumClassTest;
    AASTORE
    DUP
    ICONST_2
    GETSTATIC jvm/lang/EnumClassTest.TEST : Ljvm/lang/EnumClassTest;
    AASTORE
    PUTSTATIC jvm/lang/EnumClassTest.$VALUES : [Ljvm/lang/EnumClassTest;
    RETURN
    MAXSTACK = 5
    MAXLOCALS = 0
    * */

    public final int mode;
}
