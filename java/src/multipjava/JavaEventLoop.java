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
public class JavaEventLoop extends IndexedObject{
    boolean broken;

    Object sync;

    JavaEventLoop(ObjectHandler<Object> myhandler){
	super(myhandler);
        broken=false;
        sync=new Object();
    }
    
  /*  public static void freeAll(){
        myhandler.freeAll();
    }*/
    
    synchronized public void loopRun(){
        try{
            while(!broken){
                wait();
            }
        }
        catch(Exception e){
            System.out.println("wait exception : "+e);
            System.exit(0);
        }

        broken=false;   // recharge
    }
    synchronized public void loopBreak(){
        broken=true;

        notify();
    }

/*    public static JavaEventLoop id(int id){
        return (JavaEventLoop)handler.hash(id);
    }*/
}
