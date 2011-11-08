/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package multipjava;

/**
 *
 * @author Grzegorz
 */

import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.*;
import lljvm.runtime.*;
import lljvm.util.ObjectHandler;

public class JavaFrame extends IndexedObject{
    // window messages
    static final int REPAINT=0;
    static final int CLOSE=1;
    static final int TIMER=2;
    static final int RESIZE=3;
    static final int KEY=4;
    static final int MOUSE=5;
    static final int RAWMOUSE=6;

    // window flags
    static final int RESIZABLE=1;
    static final int BORDERLESS=2;
    static final int BUFFERED=4;
    static final int NOCLOSE=8;
    static final int MAIN=16;
    
    public Memory memory;
    public PG pg;

    protected class FrameListener extends WindowAdapter implements ComponentListener{
        JavaFrame frame;

        public FrameListener(JavaFrame f){
            frame=f;
        }

        @Override
        public void windowClosing(WindowEvent event) {
            frame.callCppListner(CLOSE);
        }

        public void componentResized(ComponentEvent e){
            Dimension d=frame.getSize();
            frame.callCppListner(RESIZE,d.width,d.height);
        }

        public void componentMoved(ComponentEvent e) {
        }

        public void componentShown(ComponentEvent e) {
        }

        public void componentHidden(ComponentEvent e) {
        }
    }
    protected class AWTFrame extends Frame{
        JavaFrame frame;

        public AWTFrame(JavaFrame f){
            frame=f;
        }

        @Override
        public void paint(Graphics g){
            Insets i=getInsets();

            JavaFrameRaster jfr=new JavaFrameRaster(pg.rasters,frame.memory,i.left,i.top,g);

            frame.callCppListner(REPAINT,jfr.id());

            jfr.destroy();
        }

        @Override
        public void update(Graphics g){
            paint(g);
        }
    }
    
    protected int cppThis;
    protected Method cppListner;
    protected Object cppInstance;   // instance (module) of cppListner
    protected AWTFrame frame;
    protected Container container;  // frame or applet
    protected boolean opened;

    JavaFrame(ObjectHandler<Object> myhandler,Context context,Method listnerFunction,int cppPointer){
	super(myhandler);
        
        memory=context.getModule(Memory.class);
        pg=context.getModule(PG.class);
                
        cppThis=cppPointer;
        cppListner=listnerFunction;
        cppInstance=context.getModule(listnerFunction.getDeclaringClass());

	opened=false;
    }
    
/*    public static void freeAll(){
        Iterator<Object> i=handler.oSet().iterator();
        
        while(i.hasNext()){
            ((JavaFrame)i.next()).close();
        }
        
        handler.freeAll();
        
        JavaFrameRaster.freeAll();
        JavaEventLoop.freeAll();
    }
    public static void killAll(){
        Iterator<Object> i=handler.oSet().iterator();
        
        while(i.hasNext()){
            ((JavaFrame)i.next()).callCppListner(CLOSE);
        }
    }*/

    synchronized boolean isOpened(){
	return opened;
    }

    public JavaFrame id(int framei){
        return (JavaFrame)handler.hash(framei);
    }

    int callCppListner(int eventType){
        return callCppListner(eventType,0);
    }
    int callCppListner(int eventType,int p1){
        return callCppListner(eventType,p1,0);
    }
    int callCppListner(int eventType,int p1,int p2){
        return callCppListner(eventType,p1,p2,0,0,0);
    }
    synchronized int callCppListner(int eventType,int p1,int p2,int p3,int p4,int p5){
	if (!opened){
	    return 0;
	}

        final int args=6;

        Object[] params = new Object[args+1];
        params[0]=cppThis;
        params[1]=eventType;
        params[2]=p1;
        params[3]=p2;
        params[4]=p3;
        params[5]=p4;
        params[6]=p5;

        {
            try{
                return (Integer)cppListner.invoke(cppInstance,params);
            }
            catch(Exception e){
                java.lang.System.out.println("callCppListner failed : "+e);
                java.lang.System.out.println("               event  : "+eventType+" cpp:"+cppThis+" p:"+p1+" "+p2+" "+p3+" "+p4+" "+p5);
                java.lang.System.out.println("               target : "+cppListner);
                java.lang.System.out.println("               thread : "+Thread.currentThread());
                e.printStackTrace(java.lang.System.out);
                java.lang.System.exit(-1);
            }
        }

	return 0;
    }

    // interface
    synchronized public void open(int flags,AppletRunner mainContainer){
        if ((flags&MAIN)!=0 && mainContainer!=null){
            mainContainer.setListner(this);
            container=mainContainer;
        }else{
            frame=new AWTFrame(this);
            FrameListener listener=new FrameListener(this);
            frame.addWindowListener(listener);
            frame.addComponentListener(listener);

            frame.setResizable((flags&RESIZABLE)!=0);

            frame.setVisible(true);
            container=frame;
        }
        
    	opened=true;
    }
    synchronized public void close(){
    	opened=false;

        if (frame!=null){
            frame.setVisible(false);
            frame=null;
        }
    }
    synchronized public void setLocation(int x,int y){
        if (frame!=null){
            frame.setLocation(x, y);
        }
    }
    synchronized public void setTitle(String title){
        if (frame!=null){
            frame.setTitle(title);
        }
    }
    synchronized public Dimension getSize(){
        Insets i=frame.getInsets();
        Dimension d=frame.getSize();
        d.width+=i.left+i.right;
        d.height+=i.top+i.bottom;

        return d;
    }
    synchronized public void setSize(int x,int y){
        Insets i=container.getInsets();

        container.setSize(x+i.left+i.right,y+i.top+i.bottom);
    }
    synchronized public void repaint(int x,int y,int w,int h){
        if (this.isOpened()){
            if (x>0){
                container.repaint(x,y,w,h);
            }else{
                container.repaint();
            }
        }
    }
    synchronized public void paint(Graphics g){ // external paint (applet call)
        JavaFrameRaster jfr=new JavaFrameRaster(pg.rasters,memory,0,0,g);

        callCppListner(REPAINT,jfr.id());

        jfr.destroy();
    }
}
