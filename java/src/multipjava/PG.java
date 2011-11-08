package multipjava;

import java.io.PrintStream;
import java.net.MalformedURLException;
import java.util.Iterator;
import lljvm.runtime.Context;
import lljvm.runtime.Function;
import lljvm.runtime.Memory;
import lljvm.runtime.Module;
import lljvm.util.ObjectHandler;

/**
 *
 * @author Grzegorz Tanski
 */

public class PG implements Module{
    public AppletRunner appletRunning;
    
    Context context;
    Memory memory;
    Function function;

    public ObjectHandler<Object> frames=new ObjectHandler<Object>();
    public ObjectHandler<Object> rasters=new ObjectHandler<Object>();
    public ObjectHandler<Object> streams=new ObjectHandler<Object>();
    public ObjectHandler<Object> eventLoops=new ObjectHandler<Object>();
    
    public JavaStream mainStream;
    public AppletRunner mainContainer;
    
    public void initialize(Context context) {
        this.context=context;
        memory=context.getModule(Memory.class);
        function=context.getModule(Function.class);
        
        mainStream=null;
        mainContainer=null;
    }

    public void destroy(Context context) {
    }
    
    public void stopStreams(){
        Iterator<Object> i=streams.oSet().iterator();
        
        while(i.hasNext()){
            JavaStream s=((JavaStream)i.next());
            s.interupt(JavaStream.si_kill);
            s.close();
        }
    }
    public void stopFrames(){
        Iterator<Object> i=frames.oSet().iterator();
        
        while(i.hasNext()){
            JavaFrame f=((JavaFrame)i.next());
            f.callCppListner(JavaFrame.CLOSE);
            f.close();
        }        
    }
    
    public void stop(){
        stopStreams();
        stopFrames();
    }
    public synchronized void setRunningApplet(AppletRunner applet){
        appletRunning=applet;
        notify();
    }
    public void displayThreadsList(PrintStream out){
        ThreadGroup rootGroup = Thread.currentThread( ).getThreadGroup( );

        Thread[] threads = new Thread[ rootGroup.activeCount() ];
        while ( rootGroup.enumerate( threads, true ) == threads.length ) {
            threads = new Thread[ threads.length * 2 ];
        }
        
        int cnt=rootGroup.enumerate( threads, true );
        
        for (int a=0 ; a<cnt ; a++){
            out.println("T:"+threads[a]);
        }        
    }
    public synchronized void stopApplet(){   // if param==null, stop any applet
       // displayThreadsList(System.out);

        AppletRunner appr=appletRunning;

        stop();

        try {
            while(appletRunning!=null){
                System.out.println(appletRunning.hexHash()+" waits for thread to stop "+Thread.currentThread());
                wait();
            }
        } catch (InterruptedException ex) {
            ex.printStackTrace(System.out);
        }

        if (appr!=null){
            System.out.println(appr.hexHash()+" thread is stopped");                
        }
    }
    
    public static String minimumJavaVersion="1.6.0";
    
    public static float jvi(String jvstring){
        return Float.parseFloat(jvstring.replace(".", "").replace("_",""));        
    }
    public static String jvs(){
        return System.getProperty("java.version");
    }
    public static float jvn(){
        return jvi(jvs());       
    }
    public static boolean jvok(){
        return jvn()>=jvi(minimumJavaVersion);
    }
    
    /////////////////////////////////////////////////
    
    public synchronized int pgJava_debug(int value,int stringptr){
        System.out.print("PGJavaDebug t:"+Thread.currentThread()+" v:"+value);
	if (stringptr!=0){
	    System.out.println(" s:"+memory.load_string_s0(stringptr));
	}else{
	    System.out.println();
	}
	
	return 0;
    }
    public synchronized void pgJava_debug(int value,String string){
	System.out.println("PGJavaIntDebug t:"+Thread.currentThread()+" v:"+value+" s:"+string);
    }

    JavaFrame frame(int id){
        return (JavaFrame)frames.hash(id);  
    }
    public int pgJava_createFrame(int listener,int cppThisPointer){
        JavaFrame frame=new JavaFrame(frames,context,function.getMethod(listener),cppThisPointer);
        return frame.id();
    }
    public void pgJava_deleteFrame(int id){
        frame(id).destroy();
    }
    public void pgJava_openFrame(int id,int flags){            
        ((JavaFrame)frames.hash(id)).open(flags,mainContainer);
    }
    public void pgJava_closeFrame(int id){
        ((JavaFrame)frames.hash(id)).close();
    }
    public void pgJava_setFrameSize(int id,int x,int y){
        ((JavaFrame)frames.hash(id)).setSize(x,y);
    }
    public void pgJava_setFramePosition(int id,int x,int y){
        ((JavaFrame)frames.hash(id)).setLocation(x,y);
    }
    public void pgJava_setFrameTitle(int id,int title){
        ((JavaFrame)frames.hash(id)).setTitle(memory.load_string_s0(title));
    }
    public void pgJava_frameRepaint(int id,int x,int y,int w,int h){
        ((JavaFrame)frames.hash(id)).repaint(x,y,w,h);
    }

    //////////////////////////////////////////////////////
    
    JavaFrameRaster raster(int id){
        return (JavaFrameRaster)rasters.hash(id);
    }
    public void pgJava_plots(int id,int n,int pntsPtr){
        raster(id).plots(n, pntsPtr);
    }
    public void pgJava_copyPixels(int id,int pixels,int modulo,int x0,int y0,int w,int h){
        raster(id).copyPixels(pixels,modulo,x0,y0,w,h);
    }

    //////////////////////////////////////////////////////

    JavaEventLoop eventLoop(int id){
        return (JavaEventLoop)eventLoops.hash(id);
    }
    public int pgJava_createEventLoop(){
        JavaEventLoop frame=new JavaEventLoop(eventLoops);
        return frame.id();
    }
    public void pgJava_deleteEventLoop(int id){
        eventLoop(id).destroy();
    }
    public void pgJava_runEventLoop(int id){
        eventLoop(id).loopRun();
    }
    public void pgJava_breakEventLoop(int id){       // msg thread
        eventLoop(id).loopBreak();
    }

    //////////////////////////////////////////////////////

    JavaStream stream(int id){
        return (JavaStream)streams.hash(id);
    }
    public int pgJava_getStream(int getMainStream){  // if getMainStream, returns main stream, otherwise, creates new
	JavaStream stream=null;

	if (getMainStream!=0){
	    if (mainStream==null){
		mainStream=new JavaPrintStream(streams,System.in,System.out);
	    }

	    stream=mainStream;
	}

	if (stream==null){
	    stream=new JavaAwtStream(streams);	// opens new frame
	}

	return stream.id();
    }
    public void pgJava_deleteStream(int id){
        JavaStream jid = stream(id);
        
        if (jid!=mainStream){
            jid.destroy();            
        }
    }
    public void pgJava_streamLine(int id,int strptr){
	stream(id).line(memory.load_string_s0(strptr));
    }
    public void pgJava_streamNextLine(int id){
	stream(id).nextLine();
    }
    public int pgJava_streamReadLine(int id,int promptAdr,int buffer,int bufsize){
        String prompt=memory.load_string_s0(promptAdr);
        
	String line=stream(id).readLine(prompt);

	if (line!=null){
	    if (memory.store(buffer, line, bufsize+1)!=memory.NULL){
		return 1;
	    }
	}

	return 0;
    }
    public int pgJava_streamInterupted(int id){
        return stream(id).interupted()?1:0;
    }

    //////////////////////////////////////////////////////

    public void MultJava_exit(int status){      // remove
    //    throw new Exit(status);
    }
    public  byte MultJava_wait(int miliSeconds){
        try{
            Thread.sleep(miliSeconds);
        }
        catch(Exception e){
            return 0;
        }
        
        return 1;
    }
    public int MultJava_clock(){
        return (int)System.currentTimeMillis();
    }
    public byte MultJava_openURL(int urlptr){
        String url = memory.load_string_s0(urlptr);

        if (appletRunning!=null){
            try {
                appletRunning.getAppletContext().showDocument(new java.net.URL(url),"_blank");
                
                return 1;
            } catch (MalformedURLException ex) {
            }
        }

        if( java.awt.Desktop.isDesktopSupported() ) {
            java.awt.Desktop desktop = java.awt.Desktop.getDesktop();

            if( desktop.isSupported( java.awt.Desktop.Action.BROWSE ) ) {
                try {
                    java.net.URI uri = new java.net.URI( url );
                    desktop.browse( uri );
                    return 1;
                }
                catch ( Exception e ) {
                    return 0;
                }
            }
        }
                
        return 0;
    }
}
