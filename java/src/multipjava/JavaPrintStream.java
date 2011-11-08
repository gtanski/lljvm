/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package multipjava;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import lljvm.util.ObjectHandler;

/**
 *
 * @author Grzegorz
 */

public class JavaPrintStream extends JavaStream{
    PrintStream out;
    BufferedReader in;

    int lineLen=0;

    JavaPrintStream(ObjectHandler<Object> myhandler,InputStream in,PrintStream out){
        super(myhandler);
        
	this.in=new BufferedReader(new InputStreamReader(in));
	this.out=out;
    }

    @Override
    synchronized void line(String line){
	int len=line.length();
	out.print(line);

	for (int a=len ; a<lineLen ; a++){
	    out.print(" ");
	}

	out.print("\015");

    	lineLen=len;
    }

    @Override
    synchronized void nextLine() {
	out.println();
    }

    @Override
    synchronized String readLine(String prompt){
	out.print(prompt);

	try {
	    return in.readLine();
	}
	catch (IOException ex) {
	    return null;
	}
    }
}
