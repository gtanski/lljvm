/*
 * Basic LLJVM multithreading tools
 */

package lljvm.runtime;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.concurrent.Semaphore;
import lljvm.util.ObjectHandler;

/**
 *
 * @author Grzegorz Tanski
 */

public class ExecThread extends Thread{
    private Context context;
    private Method function;
    public Stack stack;
    int argument;

/*    protected static ObjectHandler<Stack> handler=new ObjectHandler<Stack>();
    protected static ObjectHandler<Semaphore> semaphores=new ObjectHandler<Semaphore>();

    protected static Stack mtStack=null;

    {
	if (Memory.MAX_THREADS<=1){
	    mtStack=new Stack();
	}
    }*/

    // non-statics
    
    protected ExecThread(Context context,String name){
        super(name);
        this.context=context;
        this.stack=new Stack(context);
    }
    public ExecThread(Context context,int fn,int argument){
        this.context=context;
        
        Function fnm=context.getModule(Function.class);
        
        this.function=fnm.getMethod(fn);
        this.stack=new Stack(context);
	this.argument=argument;
        
        this.setName("ExecThread "+stack.threadShift+" fn:"+Integer.toHexString(fn)+" arg:"+Integer.toHexString(argument));
    }

    public boolean ok(){
        return function!=null;
    }

    @Override
    public void run(){
	Object[] params=new Object[1];
	params[0]=argument;

        //java.lang.System.out.println("ExecThread "+this+" started");
        Object instance=context.getModule(function.getDeclaringClass());

        try{
            Object invoke = function.invoke(instance,params);

        //    java.lang.System.out.println("ExecThread "+this+" ended");
        } catch(ThreadDeath e){
         //   java.lang.System.out.println("ExecThread "+this+" killed");
        } catch(Exception e){
            java.lang.System.err.println("ExecThread "+this+" caused exception:");
            e.printStackTrace(java.lang.System.err);
            function=null;
        }finally{
            stack.free();
        }
    }
}