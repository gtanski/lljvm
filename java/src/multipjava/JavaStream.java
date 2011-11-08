/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package multipjava;

import lljvm.util.ObjectHandler;

/**
 *
 * @author Grzegorz
 */
public abstract class JavaStream extends IndexedObject{
    static final int si_none=0;
    static final int si_interupt=1;
    static final int si_kill=2;
    
    int interupt_mark=0;

    JavaStream(ObjectHandler<Object> myhandler){
	super(myhandler);
    }

   /* public static void killAll(){
        Iterator<Object> i=myhandler.oSet().iterator();
        
        while(i.hasNext()){
            ((JavaStream)i.next()).interupt(si_kill);
        }
    }
    public static void freeAll(){
        myhandler.freeAll();
        mainStream=null;
    }*/
        
 /*   public static JavaStream id(int i){
        return (JavaStream)myhandler.hash(i);
    }*/
    
    public void close(){    // close frame etc.
    }
    public void interupt(int mode){
        interupt_mark=mode;
    }
    public boolean interupted(){
        //System.out.println("Interupted check"+this);
        if (interupt_mark==si_none){
            return false;
        }else if (interupt_mark==si_interupt){
            interupt_mark=si_none;
        }

        return true;
    }

    abstract void line(String line);
    abstract void nextLine();
    abstract String readLine(String prompt);
    
    void println(String line){
        line(line);
        nextLine();
    }
    void printStackTrace(Throwable t){  
        StackTraceElement[] ste=t.getStackTrace();
        
        for (int a=0 ; a<ste.length ; a++){
            println("    at "+ste[a].toString());
        }
    }    
}
