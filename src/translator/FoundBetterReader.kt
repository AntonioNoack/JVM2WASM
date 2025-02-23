package translator

import org.objectweb.asm.ClassReader

class FoundBetterReader(val reader: ClassReader) : Throwable()