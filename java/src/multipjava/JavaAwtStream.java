/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package multipjava;

import java.awt.Container;
import java.awt.Frame;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import lljvm.util.ObjectHandler;

/**
 *
 * @author Grzegorz
 */
public class JavaAwtStream extends JavaStream implements WindowListener{
    protected StreamContainer container;
    protected Frame frame;	// if is null, stream is embeded in external object

    JavaAwtStream(ObjectHandler<Object> myhandler,Container parent){   // embeded in existing object (main stream)
	super(myhandler);
        container=new StreamContainer();
	container.build(parent);
    }
    JavaAwtStream(ObjectHandler<Object> myhandler){		// create new frame for itself
        super(myhandler);
    	frame=new Frame();
	frame.setLocation(100,100);
	frame.setSize(600,300);
        frame.addWindowListener(this);

    	frame.setVisible(true);

    	container=new StreamContainer();
	container.build(frame);
    }

    public StreamContainer getContainer(){
	return container;
    }
    public Frame getFrame(){
	return frame;
    }
    public boolean embeded(){
	return frame!=null;
    }

    @Override
    public void close(){
	if (frame!=null){
	    frame.setVisible(false);
	}        
    }

    @Override
    public void interupt(int mode){
        //System.out.println("Stream "+this.hashCode()+" imode:"+mode+" "+Thread.currentThread());
        super.interupt(mode);
        
        if (mode!=si_none){
            container.interuptReadLine();
        }
    }
        
    @Override
    public void destroy(){
        close();

	super.destroy();
    }

    synchronized void line(String line){
	container.line(line);
    }
    synchronized void nextLine(){
	container.nextLine();
    }
    synchronized String readLine(String prompt){
        if (interupt_mark==si_none){
            return container.readLine(prompt);
        }
        
        return "";
    }

    public void windowClosing(WindowEvent event) {
        interupt(si_kill);
    }

    public void windowOpened(WindowEvent e) {
    }

    public void windowClosed(WindowEvent e) {
    }

    public void windowIconified(WindowEvent e) {
    }

    public void windowDeiconified(WindowEvent e) {
    }

    public void windowActivated(WindowEvent e) {
    }

    public void windowDeactivated(WindowEvent e) {
    }
}
