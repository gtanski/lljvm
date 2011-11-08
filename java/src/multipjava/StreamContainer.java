/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package multipjava;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;

/**
 *
 * @author Grzegorz
 */
public class StreamContainer extends Container implements ActionListener,KeyListener{
    public static int fontSize=11;
    
    TextField inputLine;
    Label promptLine;
    TextArea output;
    Container parent;
    
    int historyCursor;  // up down arrows cursor
    ArrayList history;  // previous lines entered

    int lastAppendCursor=0;
    String lastLine;	    // last line entered by user

    StreamContainer(){
        history=new ArrayList();
    }

    public void build(Container parent){
	this.parent=parent;

	parent.setLayout(new BorderLayout());

	Font font=new Font("MonoSpaced", Font.PLAIN, fontSize);

	inputLine=new TextField();
	inputLine.setFont(font);
	inputLine.addActionListener(this);
        inputLine.addKeyListener(this);

	promptLine=new Label();
	promptLine.setFont(font);
	promptLine.setAlignment(Label.RIGHT);

	Container bottomLine=new Container();
	bottomLine.setLayout(new BorderLayout());
	bottomLine.add(promptLine,BorderLayout.WEST);
	bottomLine.add(inputLine,BorderLayout.CENTER);

	output=new TextArea();
	output.setFont(font);
	output.setEditable(false);

	parent.add(bottomLine,BorderLayout.PAGE_END);
	parent.add(output,BorderLayout.CENTER);

	setInputActive(false);
    }

    void setInputActive(boolean v){
	inputLine.setEnabled(v);
//	inputLine.setVisible(v);
	promptLine.setEnabled(v);
//	promptLine.setVisible(v);
        inputLine.setText(""); 
    }
    void setPrompt(String v){
	promptLine.setText(v);
    }

    void setLastAppend(){
	lastAppendCursor=output.getText().length();
    }
    void appendText(String v){
        setLastAppend();
	output.append(v);
    }
    void clearLastAppend(){
	output.replaceRange("", lastAppendCursor, output.getText().length());
    }

    public void line(String line){
	clearLastAppend();
	appendText(line);
 //       System.out.println("!Line "+line+" "+Thread.currentThread());
    }
    public void nextLine(){
	appendText("\n");
	setLastAppend();
    }
    public synchronized String readLine(String prompt){
       // System.out.println("ReadNextLine"+" "+Thread.currentThread());

	lastLine=null;
        historyCursor=0;
        
	setInputActive(true);
	setPrompt(prompt);

	parent.validate();
        
        inputLine.requestFocusInWindow();

        try{
	    do{
		wait();
	    }while(lastLine==null);
	}
	catch(Exception e){
	}
        
      //  System.out.println("Readline :"+lastLine+" "+Thread.currentThread());
        
        if (lastLine==null){    // exception was raised
            lastLine="";
        }

	setInputActive(false);
        
        boolean hadd=true;
        
        if (lastLine.equals("")){
            hadd=false;
        }
        if (history.size()>0){
            if (lastLine.equals(history.get(history.size()-1))){
                hadd=false;                
            }
        }
                
        if (hadd){
            history.add(lastLine);
        }
        
        line(prompt+lastLine);
        nextLine();

	return lastLine;
    }

    synchronized public void actionPerformed(ActionEvent e){
	lastLine=e.getActionCommand();
	notify();
    }
    synchronized public void interuptReadLine(){
        lastLine="";
      //  System.out.println("Interuptline :"+lastLine+" "+Thread.currentThread());
        notify();        
    }
    public void keyPressed(KeyEvent ke) {
    }

    public void keyReleased(KeyEvent ke) {
        int kc=ke.getKeyCode();
        
        if (kc==KeyEvent.VK_UP){   
            if (historyCursor<history.size()){
                historyCursor++;
                
                inputLine.setText((String)history.get(history.size()-historyCursor));
                inputLine.setCaretPosition(inputLine.getText().length()+1);
            }
        }else if (kc==KeyEvent.VK_DOWN){
            if (historyCursor>1){
                historyCursor--;
                
                inputLine.setText((String)history.get(history.size()-historyCursor));
                inputLine.setCaretPosition(inputLine.getText().length());
            }else{
                inputLine.selectAll();
                historyCursor=0;
            }
        }
    }

    public void keyTyped(KeyEvent ke) {
    }
}