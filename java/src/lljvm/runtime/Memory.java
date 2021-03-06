/*
* Copyright (c) 2009 David Roberts <d@vidr.cc>
*
* Permission is hereby granted, free of charge, to any person obtaining a copy
* of this software and associated documentation files (the "Software"), to deal
* in the Software without restriction, including without limitation the rights
* to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the Software is
* furnished to do so, subject to the following conditions:
*
* The above copyright notice and this permission notice shall be included in
* all copies or substantial portions of the Software.
*
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
* IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
* FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
* AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
* LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
* OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
* THE SOFTWARE.
*/

package lljvm.runtime;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import lljvm.util.ReflectionUtils;

/**
 * Virtual memory, with methods for storing/loading values to/from
 * specified addresses.
 * 
 * @author  David Roberts
 */
public final class Memory implements Module {
    private static final int PAGE_SHIFT = 22;
    private static final int PAGE_SIZE = 1<<PAGE_SHIFT;

    public static final int ALIGNMENT = 8; // 8-byte alignment
    public static final int MEM_SIZE = 2<<30; // GiB of virtual memory
    public static final int MAX_THREADS = 64;
    public static final int PER_THREAD_STACK=PAGE_SIZE;     // thread stack equals page_size, otherwise sometimes 'double' on page boundary problem appears
                        // it looks like LLVM code generator does not always align 'double' with 8 bytes on stack
                        // someone with more LLVM experience please check and fix
    public static final int STACK_SIZE = MAX_THREADS*PER_THREAD_STACK;
    private static final int DATA_SIZE = 16<<20; // 16 MiB Data+BSS
        
    private static final ByteOrder ENDIANNESS = java.nio.ByteOrder.nativeOrder(); //ByteOrder.LITTLE_ENDIAN;
    
    /*private static final int ALIGNMENT = 8; // 8-byte alignment
    private static final int MEM_SIZE = 1<<30; // 1 GiB of virtual memory
    private static final int DATA_SIZE = 1<<20; // 1 MiB Data+BSS
    private static final int STACK_SIZE = 1<<20; // 1 MiB stack
    
    // 64 KiB pages
    private static final int PAGE_SHIFT = 16;
    private static final int PAGE_SIZE = 1<<PAGE_SHIFT;
    
    private static final ByteOrder ENDIANNESS = ByteOrder.LITTLE_ENDIAN;*/
    
    Threads threads;
    
    /** Array of pages */
    private final ByteBuffer[] pages =
        new ByteBuffer[MEM_SIZE>>>PAGE_SHIFT];
    /** Current end of Data+BSS */
    private int dataEnd = 0;
    /** Current end of the heap */
    private int heapEnd = DATA_SIZE;
    /** Current frame pointer */
    private int framePointer = MEM_SIZE;
    /** Current stack pointer */
    private int stackPointer = framePointer;
    /** Current number of frames on the stack */
    private int stackDepth = 0;
    
    {
        final int DATA_BOTTOM = 0>>>PAGE_SHIFT;
        final int DATA_END = (DATA_SIZE - 1)>>>PAGE_SHIFT;
        for(int i = DATA_BOTTOM; i <= DATA_END; i++)
            pages[i] = createPage();
        
        //java.lang.System.out.println("Adress space - Data end: "+Integer.toHexString(DATA_SIZE)+" Stack bottom: "+Integer.toHexString(stackBottom())+" Mem end:"+Integer.toHexString(stackEnd()));
    }

    /** The null pointer */
    public final int NULL = allocateData();

    /** String conversion options */
    public static String charsetName="UTF-8";

    /**
     * Thrown if an application tries to access an invalid memory address, or
     * tries to write to a read-only location.
     */
    @SuppressWarnings("serial")
    public static class SegmentationFault extends IllegalArgumentException {
        public SegmentationFault(int addr) {
            super("Address = "+addr+" (0x"+Integer.toHexString(addr)+")");
        }
    }

    private Context context;
    
    /**
     * Prevent this class from being instantiated.
     */
    public Memory() {
    }
    
    @Override
    public void initialize(Context context) {
        this.context = context;
        this.threads=context.getModule(Threads.class);
    }

    @Override
    public void destroy(Context context) {
    }

    /**
     * Create a new page.
     * @return  the new page
     */
    private static ByteBuffer createPage() {
        return ByteBuffer.allocate(PAGE_SIZE).order(ENDIANNESS);
    }

    public int stackBottom(){
        return (MEM_SIZE - STACK_SIZE);
    }
    public int stackEnd(){
        return (MEM_SIZE - 1);
    }
    public void createStack(int index){ // creates stack pages
        final int STACK_BOTTOM = (stackBottom()+index*PER_THREAD_STACK)>>>PAGE_SHIFT;
        final int STACK_END = (stackBottom()+(index+1)*PER_THREAD_STACK-1)>>>PAGE_SHIFT;
        
        for(int i = STACK_BOTTOM; i <= STACK_END; i++)
            pages[i] = createPage();        
    }
    public void destroyStack(int index){
        final int STACK_BOTTOM = (stackBottom()+index*PER_THREAD_STACK)>>>PAGE_SHIFT;
        final int STACK_END = (stackBottom()+(index+1)*PER_THREAD_STACK-1)>>>PAGE_SHIFT;
        
        for(int i = STACK_BOTTOM; i <= STACK_END; i++)
            pages[i] = null;       
    }
    
    /**
     * Return the page of the given virtual memory address
     * 
     * @param addr  the virtual memory address
     * @return      the page of the given virtual memory address
     */
    private ByteBuffer getPage(int addr) {
        try {
            return pages[addr>>>PAGE_SHIFT];
        } catch(ArrayIndexOutOfBoundsException e) {
            throw new SegmentationFault(addr);
        }
    }

    /**
     * Return the offset within the page of the given virtual memory address
     * 
     * @param addr  the virtual memory address
     * @return      the offset of the given virtual memory address
     */
    private static int getOffset(int addr) {
        return addr & (PAGE_SIZE - 1);
    }
    
    /**
     * Returns the least address greater than offset which is a multiple of
     * align.
     * 
     * @param offset  the offset to align
     * @param align   the required alignment. Must be a power of two.
     * @return        the aligned offset
     */
    public static int alignOffsetUp(int offset, int align) {
        return ((offset-1) & ~(align-1)) + align;
    }

    /**
     * Returns the greatest address less than offset which is a multiple of
     * align.
     * 
     * @param offset  the offset to align
     * @param align   the required alignment. Must be a power of two.
     * @return        the aligned offset
     */
    public static int alignOffsetDown(int offset, int align) {
        return offset & ~(align-1);
    }

    /* Wrappers from static calls to Thread.stack */
    public Stack getCurrentThreadStack(){
        return threads.getCurrentThreadStack();
    }
    
    public void createStackFrame() {
         getCurrentThreadStack().createStackFrame();
    }
    public void destroyStackFrame() {
         getCurrentThreadStack().destroyStackFrame();
    }
    public void destroyStackFrames(int n) {
         getCurrentThreadStack().destroyStackFrames(n);
    }
    public int getStackDepth() {
        return getCurrentThreadStack().getStackDepth();
    }
    public int allocateStack(int arg) {
        return getCurrentThreadStack().allocateStack(arg);
    }
    public int freeStack(int arg) {
        return getCurrentThreadStack().freeStack(arg);
    }
    public int allocateStack(){
        return getCurrentThreadStack().allocateStack();
    }

    public int storeStack(boolean arg) {
        return getCurrentThreadStack().storeStack(arg);
    }
    public int storeStack(byte arg) {
        return getCurrentThreadStack().storeStack(arg);
    }
    public int storeStack(short arg) {
        return getCurrentThreadStack().storeStack(arg);
    }
    public int storeStack(int arg) {
        return getCurrentThreadStack().storeStack(arg);
    }
    public int storeStack(long arg) {
        return getCurrentThreadStack().storeStack(arg);
    }
    public int storeStack(float arg) {
        return getCurrentThreadStack().storeStack(arg);
    }
    public int storeStack(double arg) {
        return getCurrentThreadStack().storeStack(arg);
    }
    public int storeStack(byte[] arg) {
        return getCurrentThreadStack().storeStack(arg);
    }
    public int storeStack(String[] arg) {
        return getCurrentThreadStack().storeStack(arg);
    }
    public int storeStack(String arg) {
        return getCurrentThreadStack().storeStack(arg);
    }
    
    /**
     * Allocate a block of the given size within the data segment.
     * 
     * @param size  the size of the block to allocate
     * @return      a pointer to the allocated block
     */
    public int allocateData(int size) {
        final int addr = dataEnd;
        dataEnd = alignOffsetUp(dataEnd + size, ALIGNMENT);
        return addr;
    }
    
    /**
     * Allocate one byte within the data segment.
     * 
     * @return  a pointer to the allocated byte
     */
    public int allocateData() {
        return allocateData(1);
    }
    
    /**
     * Increase the size of the heap by the specified amount.
     * 
     * @param increment  the amount to increment the heap size
     * @return           a pointer to the previous end of the heap on success,
     *                   -1 on error
     */
    public int sbrk(int increment) {
        final int prevHeapEnd = heapEnd;
        if(heapEnd + increment > MEM_SIZE - STACK_SIZE
        || heapEnd + increment < DATA_SIZE) {
            Error error = context.getModule(Error.class);
            return error.errno(Error.ENOMEM);
        }
        heapEnd += increment;
        final int HEAP_BOTTOM = prevHeapEnd>>>PAGE_SHIFT;
        final int HEAP_END = (heapEnd - 1)>>>PAGE_SHIFT;
        
        for (int i = HEAP_BOTTOM; i <= HEAP_END; i++){
            if(pages[i] == null){
                pages[i] = createPage();
                
                //java.lang.System.out.println(i<<PAGE_SHIFT);
            }
        }
        // TODO: destroy pages if increment < 0
        return prevHeapEnd;
    }
    
    /**
     * Store a boolean value at the given address.
     * 
     * @param addr   the address at which to store the value
     * @param value  the value to be stored
     */
    public void store(int addr, boolean value) {
        try {
            getPage(addr).put(getOffset(addr), (byte) (value ? 1 : 0));
        } catch(NullPointerException e) {
            throw new SegmentationFault(addr);
        }
    }
    
    /**
     * Store a byte at the given address.
     * 
     * @param addr   the address at which to store the value
     * @param value  the value to be stored
     */
    public void store(int addr, byte value) {
        try {
            getPage(addr).put(getOffset(addr), value);
        } catch(NullPointerException e) {
            throw new SegmentationFault(addr);
        }
    }
    
    /**
     * Store a 16-bit integer at the given address.
     * 
     * @param addr   the address at which to store the value
     * @param value  the value to be stored
     */
    public void store(int addr, short value) {
        try {
            getPage(addr).putShort(getOffset(addr), value);
        } catch(NullPointerException e) {
            throw new SegmentationFault(addr);
        }
    }
    
    /**
     * Store a 32-bit integer at the given address.
     * 
     * @param addr   the address at which to store the value
     * @param value  the value to be stored
     */
    public void store(int addr, int value) {
        try {
            getPage(addr).putInt(getOffset(addr), value);
        } catch(NullPointerException e) {
            throw new SegmentationFault(addr);
        }
    }
    
    /**
     * Store a 64-bit integer at the given address.
     * 
     * @param addr   the address at which to store the value
     * @param value  the value to be stored
     */
    public void store(int addr, long value) {
        try {
            getPage(addr).putLong(getOffset(addr), value);
        } catch(NullPointerException e) {
            throw new SegmentationFault(addr);
        }
    }
    
    /**
     * Store a single precision floating point number at the given address.
     * 
     * @param addr   the address at which to store the value
     * @param value  the value to be stored
     */
    public void store(int addr, float value) {
        try {
            getPage(addr).putFloat(getOffset(addr), value);
        } catch(NullPointerException e) {
            throw new SegmentationFault(addr);
        }
    }
    
    /**
     * Store a double precision floating point number at the given address.
     * 
     * @param addr   the address at which to store the value
     * @param value  the value to be stored
     */
    public void store(int addr, double value) {
        try {
            getPage(addr).putDouble(getOffset(addr), value);
        } catch(NullPointerException e) {
            throw new SegmentationFault(addr);
        }
    }
    
    /**
     * Store an array of bytes at the given address.
     * 
     * @param addr   the address at which to store the bytes
     * @param bytes  the bytes to be stored
     */
    public void store(int addr, byte[] bytes) {
        // TODO: make more efficient by using put(byte[])
        for(int i = 0; i < bytes.length; i++)
            store(addr + i, bytes[i]);
    }
    
    /**
     * Store a string at the given address.
     * 
     * @param addr    the address at which to store the string
     * @param string  the string to be stored
     */
    public void store(int addr, String string) {
        final byte[] bytes;
	try {
	    bytes = string.getBytes(charsetName);
	} catch (UnsupportedEncodingException ex) {
	    store(addr,0);
	    return;
	}
        store(addr, bytes);
        store(addr + bytes.length, (byte) 0);
    }
    
    /**
     * Store a string at the given address, unless the string would occupy more
     * than size bytes (including the null terminator).
     * 
     * @param addr    the address at which to store the string
     * @param string  the string to be stored
     * @param size    the maximum size of the string
     * @return        addr on success, NULL on error
     */
    public int store(int addr, String string, int size) {
        final byte[] bytes;
	try {
	    bytes = string.getBytes(charsetName);
	} catch (UnsupportedEncodingException ex) {

            return NULL;
	}
        if(bytes.length + 1 > size) {
            
            return NULL;
        }
        
        store(addr, bytes);
        store(addr + bytes.length, (byte) 0);
        return addr;
    }
    
    /**
     * Store a boolean value in the data segment, returning a pointer to the
     * value.
     * 
     * @param value  the value to be stored
     * @return       a pointer to the value
     */
    public int storeData(boolean value) {
        final int addr = allocateData(1);
        store(addr, value);
        return addr;
    }
    
    /**
     * Store a byte in the data segment, returning a pointer to the value.
     * 
     * @param value  the value to be stored
     * @return       a pointer to the value
     */
    public int storeData(byte value) {
        final int addr = allocateData(1);
        store(addr, value);
        return addr;
    }
    
    /**
     * Store a 16-bit integer in the data segment, returning a pointer to the
     * value.
     * 
     * @param value  the value to be stored
     * @return       a pointer to the value
     */
    public int storeData(short value) {
        final int addr = allocateData(2);
        store(addr, value);
        return addr;
    }
    
    /**
     * Store a 32-bit integer in the data segment, returning a pointer to the
     * value.
     * 
     * @param value  the value to be stored
     * @return       a pointer to the value
     */
    public int storeData(int value) {
        final int addr = allocateData(4);
        store(addr, value);
        return addr;
    }
    
    /**
     * Store a 64-bit integer in the data segment, returning a pointer to the
     * value.
     * 
     * @param value  the value to be stored
     * @return       a pointer to the value
     */
    public int storeData(long value) {
        final int addr = allocateData(8);
        store(addr, value);
        return addr;
    }
    
    /**
     * Store a single precision floating point number in the data segment,
     * returning a pointer to the value.
     * 
     * @param value  the value to be stored
     * @return       a pointer to the value
     */
    public int storeData(float value) {
        final int addr = allocateData(4);
        store(addr, value);
        return addr;
    }
    
    /**
     * Store a double precision floating point number in the data segment,
     * returning a pointer to the value.
     * 
     * @param value  the value to be stored
     * @return       a pointer to the value
     */
    public int storeData(double value) {
        final int addr = allocateData(8);
        store(addr, value);
        return addr;
    }
    
    /**
     * Store an array of bytes in the data segment, returning a pointer to the
     * bytes.
     * 
     * @param bytes  the bytes to be stored
     * @return       a pointer to the bytes
     */
    public int storeData(byte[] bytes) {
        final int addr = allocateData(bytes.length);
        store(addr, bytes);
        return addr;
    }
    
    /**
     * Store a string in the data segment, returning a pointer to the string.
     * 
     * @param string  the string to be stored
     * @return        a pointer to the string
     */
    public int storeData(String string) {
        byte[] bytes={};
	try {
	    bytes = string.getBytes(charsetName);
	} catch (UnsupportedEncodingException ex) {
	}
        final int addr = allocateData(bytes.length+1);
        store(addr, bytes);
        store(addr + bytes.length, (byte) 0);
        return addr;
    }
    
    /**
     * Load a boolean value from the given address.
     * 
     * @param addr  the address from which to load the value
     * @return      the value at the given address
     */
    public boolean load_i1(int addr) {
        try {
            return getPage(addr).get(getOffset(addr)) != 0;
        } catch(NullPointerException e) {
            throw new SegmentationFault(addr);
        }
    }
    
    /**
     * Load a byte from the given address.
     * 
     * @param addr  the address from which to load the value
     * @return      the value at the given address
     */
    public byte load_i8(int addr) {
        try {
            return getPage(addr).get(getOffset(addr));
        } catch(NullPointerException e) {
            throw new SegmentationFault(addr);
        }
    }
    
    /**
     * Load a 16-bit integer from the given address.
     * 
     * @param addr  the address from which to load the value
     * @return      the value at the given address
     */
    public short load_i16(int addr) {
        try {
            return getPage(addr).getShort(getOffset(addr));
        } catch(NullPointerException e) {
            throw new SegmentationFault(addr);
        }
    }
    
    /**
     * Load a 32-bit integer from the given address.
     * 
     * @param addr  the address from which to load the value
     * @return      the value at the given address
     */
    public int load_i32(int addr) {
        try {
            return getPage(addr).getInt(getOffset(addr));
        } catch(NullPointerException e) {
            throw new SegmentationFault(addr);
        }
    }
    
    /**
     * Load a 64-bit integer from the given address.
     * 
     * @param addr  the address from which to load the value
     * @return      the value at the given address
     */
    public long load_i64(int addr) {
        try {
            return getPage(addr).getLong(getOffset(addr));
        } catch(NullPointerException e) {
            throw new SegmentationFault(addr);
        }
    }
    
    /**
     * Load a single precision floating point number from the given address.
     * 
     * @param addr  the address from which to load the value
     * @return      the value at the given address
     */
    public float load_f32(int addr) {
        try {
            return getPage(addr).getFloat(getOffset(addr));
        } catch(NullPointerException e) {
            throw new SegmentationFault(addr);
        }
    }
    
    /**
     * Load a double precision floating point number from the given address.
     * 
     * @param addr  the address from which to load the value
     * @return      the value at the given address
     */
    public double load_f64(int addr) {
        try {
            return getPage(addr).getDouble(getOffset(addr));
        } catch(NullPointerException e) {
            throw new SegmentationFault(addr);
        }
    }
    
    /**
     * Load a string from the given address.
     * 
     * @param addr  the address from which to load the string
     * @return      the string at the given address
     */
    public String load_string(int addr) {
        byte[] bytes = new byte[16];
        int i = 0;
        
	while((bytes[i++] = load_i8(addr++)) != 0){
            if(i >= bytes.length) bytes = Arrays.copyOf(bytes, i*2);
	}

	try {
	    return new String(Arrays.copyOf(bytes, i), charsetName);
	} catch (UnsupportedEncodingException ex) {
	}

	return "";
    }

    // same as above, but skip trailing zero (correct)
    
    public String load_string_s0(int addr){
        int i=0;
        
        while(load_i8(addr+i)!=0){
            i++;
        }
        
        byte[] bytes=new byte[i];
        
        for (int j=0 ; j<i ; j++){
            bytes[j]=load_i8(addr+j);
        }
        
	try {
	    return new String(bytes, charsetName);
	} catch (UnsupportedEncodingException ex) {
	}

	return "";        
    }
    
    /**
     * Load a value of the given type from the given address.
     * 
     * @param addr  the address from which to load the value
     * @param type  the type of value to load. Must be a primitive type other
     *              than char.
     * @return      the value at the given address
     */
    public Object load(int addr, Class<?> type) {
        if(type == boolean.class) return load_i1(addr);
        if(type == byte.class)    return load_i8(addr);
        if(type == short.class)   return load_i16(addr);
        if(type == int.class)     return load_i32(addr);
        if(type == long.class)    return load_i64(addr);
        if(type == float.class)   return load_f32(addr);
        if(type == double.class)  return load_f64(addr);
        throw new IllegalArgumentException("Unrecognised type");
    }
    
    public void getPages(PageConsumer scanner, int addr, int len) {
        while(len>0) {
            ByteBuffer page = getPage(addr).duplicate();
            int pageOff = getOffset(addr);
            int chunkLen = java.lang.Math.min(PAGE_SIZE - pageOff, len);
            page.position(pageOff);
            page.limit(pageOff+chunkLen);
            if (!scanner.next(page))
                return;
            len -= chunkLen;
            addr += chunkLen;
        }
    }
    
    
    /**
     * Store a boolean value at the given address, inserting any required
     * padding before the value, returning the first address following the
     * value.
     * 
     * @param addr   the address at which to store the value
     * @param value  the value to be stored
     * @return       the first address following the value
     */
    public int pack(int addr, boolean value) {
        addr = alignOffsetUp(addr, 1);
        store(addr, value);
        return addr + 1;
    }
    
    /**
     * Store a byte at the given address, inserting any required padding before
     * the value, returning the first address following the value.
     * 
     * @param addr   the address at which to store the value
     * @param value  the value to be stored
     * @return       the first address following the value
     */
    public int pack(int addr, byte value) {
        addr = alignOffsetUp(addr, 1);
        store(addr, value);
        return addr + 1;
    }
    
    /**
     * Store a 16-bit integer at the given address, inserting any required
     * padding before the value, returning the first address following the
     * value.
     * 
     * @param addr   the address at which to store the value
     * @param value  the value to be stored
     * @return       the first address following the value
     */
    public int pack(int addr, short value) {
        addr = alignOffsetUp(addr, 2);
        store(addr, value);
        return addr + 2;
    }
    
    /**
     * Store a 32-bit integer at the given address, inserting any required
     * padding before the value, returning the first address following the
     * value.
     * 
     * @param addr   the address at which to store the value
     * @param value  the value to be stored
     * @return       the first address following the value
     */
    public int pack(int addr, int value) {
        addr = alignOffsetUp(addr, 4);
        store(addr, value);
        return addr + 4;
    }
    
    /**
     * Store a 64-bit integer at the given address, inserting any required
     * padding before the value, returning the first address following the
     * value.
     * 
     * @param addr   the address at which to store the value
     * @param value  the value to be stored
     * @return       the first address following the value
     */
    public int pack(int addr, long value) {
        addr = alignOffsetUp(addr, 8);
        store(addr, value);
        return addr + 8;
    }
    
    /**
     * Store a single precision floating point number at the given address,
     * inserting any required padding before the value, returning the first
     * address following the value.
     * 
     * @param addr   the address at which to store the value
     * @param value  the value to be stored
     * @return       the first address following the value
     */
    public int pack(int addr, float value) {
        addr = alignOffsetUp(addr, 4);
        store(addr, value);
        return addr + 4;
    }
    
    /**
     * Store a double precision floating point number at the given address,
     * inserting any required padding before the value, returning the first
     * address following the value.
     * 
     * @param addr   the address at which to store the value
     * @param value  the value to be stored
     * @return       the first address following the value
     */
    public int pack(int addr, double value) {
        addr = alignOffsetUp(addr, 8);
        store(addr, value);
        return addr + 8;
    }
    
    /**
     * Store a string at the given address, returning the first address
     * following the null terminator.
     * 
     * @param addr    the address at which to store the string
     * @param string  the string to be stored
     * @return        the first address following the null terminator
     */
    public int pack(int addr, String string) {
        byte[] bytes={};
	try {
	    bytes = string.getBytes(charsetName);
	} catch (UnsupportedEncodingException ex) {
	}
        store(addr, bytes);
        store(addr + bytes.length, (byte) 0);
        return addr + bytes.length + 1;
    }
    
    /**
     * Store an array of chars at the given address, treating it as an array of
     * bytes i.e. each char is cast to a byte before being stored.
     * 
     * @param addr   the address at which to store the array
     * @param chars  the array of chars
     * @return       the first address following the stored array
     */
    public int pack(int addr, char[] chars) {
        for(int i = 0; i < chars.length; i++)
            store(addr + i, (byte) chars[i]);
        return addr + chars.length;
    }
    
    /**
     * Unpack a naturally-aligned value of the given size from the given
     * address. The given address is updated to point to the first address
     * following the value.
     * 
     * @param addrp  a pointer to the address
     * @param size   the size of the value in bytes. Must be a power of 2.
     * @return       the address of the first naturally-aligned value of the
     *               given size following the given address
     */
    public int unpack(int addrp, int size) {
        int addr = load_i32(addrp);
        addr = alignOffsetUp(addr, size);
        store(addrp, addr + size);
        return addr;
    }
    
    /**
     * Unpack a packed list of values from the given address, according to
     * the given list of types.
     * 
     * @param addr   the address from which to load the values
     * @param types  the array of types. All elements must be primitive types
     *               other than char.
     * @return       an array of unpacked values
     */
    public int unpack(int addr, Class<?>[] types, Object[] values) {
        int saddr=addr;

        for(int i = 0; i < types.length; i++) {
            final Class<?> type = types[i];
            final int size = ReflectionUtils.sizeOf(type);

            addr = alignOffsetUp(addr, size);
            values[i] = load(addr, type);
            addr += size;
        }

        return addr-saddr;
    }
    /*public Object[] unpack(int addr, Class<?>[] types) {
        Object[] values = new Object[types.length];
        for(int i = 0; i < types.length; i++) {
            final Class<?> type = types[i];
            final int size = ReflectionUtils.sizeOf(type);
            addr = alignOffsetUp(addr, size);
            values[i] = load(addr, type);
            addr += size;
        }
        return values;
    }*/
    
    /**
     * Copy len bytes from memory area src to memory area dest. The memory
     * areas should not overlap.
     * 
     * @param dest   the destination memory area
     * @param src    the source memory area
     * @param len    the number of bytes to copy
     * @param align  the alignment of the source and destination pointers,
     *               unless align is equal to 0 or 1
     */
    public void memcpy(int dest, int src, int len, int align) {
        // TODO: make more efficient by using put(ByteBuffer)
        for(int i = 0; i < len; i++)
            store(dest + i, load_i8(src + i));
    }
    
    /**
     * Copy len bytes from memory area src to memory area dest. The memory
     * areas should not overlap.
     * 
     * @param dest   the destination memory area
     * @param src    the source memory area
     * @param len    the number of bytes to copy
     * @param align  the alignment of the source and destination pointers,
     *               unless align is equal to 0 or 1
     */
    public void memcpy(int dest, int src, long len, int align) {
        memcpy(dest, src, (int) len, align);
    }
    
    /**
     * Copy len bytes from memory area src to memory area dest. The memory
     * areas may overlap.
     * 
     * @param dest   the destination memory area
     * @param src    the source memory area
     * @param len    the number of bytes to copy
     * @param align  the alignment of the source and destination pointers,
     *               unless align is equal to 0 or 1
     */
    public void memmove(int dest, int src, int len, int align) {
        // TODO: make more efficient by using put(ByteBuffer)
        if(dest < src)
            for(int i = 0; i < len; i++)
                store(dest + i, load_i8(src + i));
        else
            for(int i = len - 1; i >= 0; i--)
                store(dest + i, load_i8(src + i));
    }
    
    /**
     * Copy len bytes from memory area src to memory area dest. The memory
     * areas may overlap.
     * 
     * @param dest   the destination memory area
     * @param src    the source memory area
     * @param len    the number of bytes to copy
     * @param align  the alignment of the source and destination pointers,
     *               unless align is equal to 0 or 1
     */
    public void memmove(int dest, int src, long len, int align) {
        memmove(dest, src, (int) len, align);
    }
    
    /**
     * Fill the first len bytes of memory area dest with the constant byte val.
     * 
     * @param dest   the destination memory area
     * @param val    the constant byte fill value
     * @param len    the number of bytes to set
     * @param align  the alignment of the source and destination pointers,
     *               unless align is equal to 0 or 1
     */
    public void memset(int dest, byte val, int len, int align) {
        // TODO: make more efficient by setting larger blocks at a time
        for(int i = dest; i < dest + len; i++)
            store(i, val);
    }
    
    /**
     * Fill the first len bytes of memory area dest with the constant byte val.
     * 
     * @param dest   the destination memory area
     * @param val    the constant byte fill value
     * @param len    the number of bytes to set
     * @param align  the alignment of the source and destination pointers,
     *               unless align is equal to 0 or 1
     */
    public void memset(int dest, byte val, long len, int align) {
        memset(dest, val, (int) len, align);
    }
    
    /**
     * Fill the first len bytes of memory area dest with 0.
     * 
     * @param dest  the destination memory area
     * @param len   the number of bytes to set
     * @return      the address of the first byte following the block
     */
    public int zero(int dest, int len) {
        memset(dest, (byte) 0, len, 1);
        return dest + len;
    }
    
    public interface PageConsumer {
        boolean next(ByteBuffer buf);
    }
}
