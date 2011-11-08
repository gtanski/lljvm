package multipjava;

import java.applet.Applet;
import java.awt.Frame;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

/**
 *
 * @author Grzegorz
 */
class AppRunner extends Frame implements WindowListener{
    Applet applet;
    
    public AppRunner(Applet app){
	super("Applet");

        applet=app;
	setLocation(100,100);
	setSize(600,300);
	add(app);
    }
    
    public static void main(String[] args){
	AppletRunner applet=new AppletRunner();

	applet.mainInit(args);

	AppRunner app=new AppRunner(applet);
	app.addWindowListener(app);
	app.setVisible(true);
    }

    public void windowClosing(WindowEvent event) {
        applet.stop();
        applet.destroy();
        System.exit(0);
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