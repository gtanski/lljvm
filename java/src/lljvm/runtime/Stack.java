/*
 *  Runtime per thread stack
 */

package lljvm.runtime;

/**
 *
 * @author Grzegorz Tanski
 */
public class Stack{
    Memory memory;
    Threads threads;
    ExecThread thread;
    int threadShift;

    private int stackBottom;
    /** Current frame pointer */
    private int framePointer;
    /** Current stack pointer */
    private int stackPointer;
    /** Current number of frames on the stack */
    private int stackDepth = 0;

    public class OutOfStackSpace extends IllegalArgumentException {
        public OutOfStackSpace() {
            super("LLJVM cannot allocate stack for thread. All "+memory.MAX_THREADS+" threads are running.");
        }
    }
    public class StackOverflow extends IllegalArgumentException {
        public StackOverflow() {
            super("LLJVM stack overflow "+Thread.currentThread());
        }
    }
    
    Stack(Context context){
	memory=context.getModule(Memory.class);
        threads=context.getModule(Threads.class);

	threadShift=threads.allocThreadIndex();

	if (threadShift<0){ // first attempt tries to wait a second for killed threads to release
	    final int waitTime=1000;
	    // java.lang.System.out.println("LLJVM Have problem with allocating stack for new thread. Waiting "+waitTime+" miliseconds.");

	    try{
		Thread.sleep(waitTime);
	    }
	    catch(Exception e){
	    }

            threadShift=threads.allocThreadIndex();
	}

	if (threadShift<0){
	    throw new OutOfStackSpace();
	}
        stackBottom=memory.stackBottom()+(threadShift)*Memory.PER_THREAD_STACK;
        framePointer=memory.stackBottom()+(threadShift+1)*Memory.PER_THREAD_STACK;
        stackPointer=framePointer;
        
        memory.createStack(threadShift);

        //java.lang.System.out.println("LLJVM Stack init t="+threadShift+" "+" "+Integer.toHexString(framePointer)+" "+Thread.currentThread());
    }
    
    public void setExecThread(ExecThread e){
        thread=e;
    }
    public void killExecThread(){
        if (thread!=null){
            thread.interrupt();
        }
    }
    void free(){
        memory.freeStack(threadShift);
        threads.freeThreadIndex(threadShift);
    }
        
    /**
     * Create a new stack frame, storing the current frame pointer.
     */
    public void createStackFrame() {
        final int prevFramePointer = framePointer;

       // java.lang.System.out.println("=== Create stack frame FP="+framePointer+" depth="+stackDepth);

        framePointer = stackPointer;
        storeStack(prevFramePointer);
        stackDepth++;

        //java.lang.System.out.println("*** Create stack frame FP="+framePointer+" depth="+stackDepth);
    }

    /**
     * Destroy the current stack frame, restoring the previous frame pointer.
     */
    public void destroyStackFrame() {
        stackPointer = framePointer;

      //  if (stackPointer==0){
      //      java.lang.System.out.println("*** Destroy stack frame FP="+framePointer+" depth="+stackDepth);
      //  }

        framePointer = memory.load_i32(stackPointer - Memory.ALIGNMENT);
        stackDepth--;

      //  java.lang.System.out.println("=== Destroy stack frame FP="+framePointer+" depth="+stackDepth);
    }

    /**
     * Destroy the top n stack frames.
     *
     * @param n  the number of stack frames to destroy
     */
    public void destroyStackFrames(int n) {
        for(int i = 0; i < n; i++)
            destroyStackFrame();
    }

    /**
     * Return the number of stack frames currently on the stack.
     *
     * @return  the number of stack frames currently on the stack
     */
    public int getStackDepth() {
        return stackDepth;
    }

        /**
     * Allocate a block of the given size within the stack.
     *
     * @param size  the size of the block to allocate
     * @return      a pointer to the allocated block
     */
    public int allocateStack(int size) {
        stackPointer = Memory.alignOffsetDown(stackPointer - size, Memory.ALIGNMENT);

        if (stackPointer<stackBottom){
            throw new StackOverflow();
        }
        //java.lang.System.out.println("+++ Alloc stack size="+size+" SP="+stackPointer+" spmod16:"+(stackPointer%16));

        return stackPointer;
    }

    public int freeStack(int size) {
        stackPointer = Memory.alignOffsetUp(stackPointer + size, Memory.ALIGNMENT);

        //java.lang.System.out.println("+++ Free stack size="+size+" SP="+stackPointer);

        return stackPointer;
    }

    /**
     * Allocate one byte within the stack.
     *
     * @return  a pointer to the allocated byte
     */
    public int allocateStack() {
        return allocateStack(1);
    }

    /**
     * Store a boolean value in the stack, returning a pointer to the value.
     *
     * @param value  the value to be stored
     * @return       a pointer to the value
     */
    public int storeStack(boolean value) {
        final int addr = allocateStack(1);
        memory.store(addr, value);
        return addr;
    }

    /**
     * Store a byte in the stack, returning a pointer to the value.
     *
     * @param value  the value to be stored
     * @return       a pointer to the value
     */
    public int storeStack(byte value) {
        final int addr = allocateStack(1);
        memory.store(addr, value);
        return addr;
    }

    /**
     * Store a 16-bit integer in the stack, returning a pointer to the value.
     *
     * @param value  the value to be stored
     * @return       a pointer to the value
     */
    public int storeStack(short value) {
        final int addr = allocateStack(2);
        memory.store(addr, value);
        return addr;
    }

    /**
     * Store a 32-bit integer in the stack, returning a pointer to the value.
     *
     * @param value  the value to be stored
     * @return       a pointer to the value
     */
    public int storeStack(int value) {
        final int addr = allocateStack(4);
        memory.store(addr, value);
        return addr;
    }

    /**
     * Store a 64-bit integer in the stack, returning a pointer to the value.
     *
     * @param value  the value to be stored
     * @return       a pointer to the value
     */
    public int storeStack(long value) {
        final int addr = allocateStack(8);
        memory.store(addr, value);
        return addr;
    }

    /**
     * Store a single precision floating point number in the stack,
     * returning a pointer to the value.
     *
     * @param value  the value to be stored
     * @return       a pointer to the value
     */
    public int storeStack(float value) {
        final int addr = allocateStack(4);
        memory.store(addr, value);
        return addr;
    }

    /**
     * Store a double precision floating point number in the stack,
     * returning a pointer to the value.
     *
     * @param value  the value to be stored
     * @return       a pointer to the value
     */
    public int storeStack(double value) {
        final int addr = allocateStack(8);
        memory.store(addr, value);
        return addr;
    }

    /**
     * Store an array of bytes in the stack, returning a pointer to the bytes.
     *
     * @param bytes  the bytes to be stored
     * @return       a pointer to the bytes
     */
    public int storeStack(byte[] bytes) {
        final int addr = allocateStack(bytes.length);
        memory.store(addr, bytes);
        return addr;
    }

    /**
     * Store an array of strings in the stack, terminated by a null pointer.
     *
     * @param strings  the array of strings to be stored
     * @return         a pointer to the array
     */
    public int storeStack(String[] strings) {
        final int addr = allocateStack(strings.length * 4 + 4);

        //java.lang.System.out.println("store"+addr);

        for(int i = 0; i < strings.length; i++)
            memory.store(addr + i * 4, storeStack(strings[i]));

        memory.store(addr + strings.length * 4, memory.NULL);
        return addr;
    }

    /**
     * Store a string in the stack, returning a pointer to the string.
     *
     * @param string  the string to be stored
     * @return        a pointer to the string
     */
    public int storeStack(String string) {
        final byte[] bytes = string.getBytes();
        final int addr = allocateStack(bytes.length+1);
        memory.store(addr, bytes);
        memory.store(addr + bytes.length, (byte) 0);
        return addr;
    }
}
