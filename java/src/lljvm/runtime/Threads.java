/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package lljvm.runtime;

import java.util.Iterator;
import java.util.concurrent.Semaphore;
import lljvm.util.ObjectHandler;

/**
 *
 * @author Grzegorz Tanski
 */
public class Threads implements Module{
    Context context;
    Memory memory;

    protected ObjectHandler<Stack> handler=new ObjectHandler<Stack>();
    protected ObjectHandler<Semaphore> semaphores=new ObjectHandler<Semaphore>();

    protected Stack mtStack=null;

    private boolean stackUsage[];

    @Override
    public void initialize(Context context) {
        this.context=context;
        memory=context.getModule(Memory.class);

	if (memory.MAX_THREADS<=1){
	    mtStack=new Stack(context);
	}else{
            stackUsage=new boolean[Memory.MAX_THREADS];
        }
    }

    @Override
    public void destroy(Context context) {
    }

    public Stack getCurrentThreadStack(){
        if (memory.MAX_THREADS>1){
            Thread ct=Thread.currentThread();

  	    if (ct instanceof ExecThread){
                return ((ExecThread)ct).stack;
            }else
	    {
                int id=ct.hashCode();

                Stack stack=handler.hash(id);

                if (stack==null){
                    //java.lang.System.out.println("LLJVM new external thread :"+ct+" (hash:"+id+")");

		    stack=new Stack(context);

                    handler.replace(id,stack);
                }

                return stack;
            }
        }else{
	    return mtStack;
	}
    }

    public void freeAll(){
        Iterator<Stack> ss=handler.oSet().iterator();
        
        while(ss.hasNext()){
            Stack s=ss.next();
            
            s.killExecThread();
        }
        
        handler.freeAll();
        semaphores.freeAll();
    }
    private String stackUsage(){	// debug tool
	String res="";

        for (int a=0 ; a<Memory.MAX_THREADS ; a++){
	    if (stackUsage[a]){
		res+="*";
	    }else{
		res+=".";
	    }
	}

	return res;
    }
    protected synchronized int allocThreadIndex(){
	int index=-1;

	for (int a=0 ; a<Memory.MAX_THREADS ; a++){
	    if (!stackUsage[a]){
		index=a;
		stackUsage[a]=true;

		break;
	    }
	}

	return index;
    }
   
    public synchronized void freeThreadIndex(int threadShift){
        stackUsage[threadShift]=false;
    }

    // C interface

    public int cthreadsJava_start_thread(int function,int argument){
        //java.lang.System.out.println("New thread started "+function+" "+argument);
        
        ExecThread ct=new ExecThread(context,function,argument);
        ct.start();

        return ct.ok()?1:0;
    }
    public int cthreadsJava_number_of_CPUs(){
        return (Memory.MAX_THREADS>1)?Runtime.getRuntime().availableProcessors():1;
    }
    public int cthreadsJava_max_threads(){
        return (Memory.MAX_THREADS>4)?(Memory.MAX_THREADS-4):1;	// minus AWT refresh thread + some other Java system threads
    }

    public int cthreadsJava_get_thread_id(){
        return Thread.currentThread().hashCode();
    }

    public int cthreadsJava_create_sem(){
        int id=semaphores.alloc(new Semaphore(0));

        if (id==0){
       //     java.lang.System.out.println("hmm");
        }

        //java.lang.System.out.println("Create sem "+id+" "+Thread.currentThread());

        return id;
    }
    public void cthreadsJava_setup_sem(int sem,int initial,int maximum){
       // java.lang.System.out.println("Setup sem "+sem+" "+initial+" "+maximum);

        semaphores.replace(sem, new Semaphore(initial));
    }
    public void cthreadsJava_alloc_sem(int sem,int n){
        if (sem==0){
       //     java.lang.System.out.println("Alloc sem "+sem+" "+n+" "+Thread.currentThread());
        }
        
        try{
            semaphores.hash(sem).acquire(n);
        }
        catch(InterruptedException e){
        }
    }
    public byte cthreadsJava_tryalloc_sem(int sem){
        if (semaphores.hash(sem).tryAcquire()){
            return 1;
        }else{
            return 0;
        }
    }
    public void cthreadsJava_free_sem(int sem,int n){
        if (sem==0){
        //    java.lang.System.out.println("Free sem "+sem+" "+n+" t:"+Thread.currentThread());
        }

        semaphores.hash(sem).release(n);
    }
    public void cthreadsJava_destroy_sem(int sem){
      // java.lang.System.out.println("Destroy sem "+sem+" "+Thread.currentThread());

	semaphores.free(sem);
	semaphores.resetIdCounter(100);
    //   java.lang.System.out.println("Destroyed sem "+sem+" "+Thread.currentThread());
    }
}
