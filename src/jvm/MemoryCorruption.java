package jvm;

public class MemoryCorruption extends OutOfMemoryError {
    public MemoryCorruption(){
        super();
    }
    public MemoryCorruption(String msg){
        super(msg);
    }
}
