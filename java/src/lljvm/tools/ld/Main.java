/*
 * Copyright (c) 2009 David Roberts <d@vidr.cc>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package lljvm.tools.ld;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;

import lljvm.tools.LoggingEnvironment;

/**
 * Main class for executing the LLJVM linker.
 * 
 * @author David Roberts
 * @author Joshua Arnold
 */
public class Main {
    
    public static int doMain(String[] args, InputStream in, OutputStream out, PrintWriter err) {
        try {
            boolean stdMode=true;
            boolean verbose=false;
            int mode=1;
            String extension=".j";

            LinkerParameters params = new LinkerParameters();
            for (String arg : args) {
                if (arg.equals("-j")){          // Jasmin source code
                    mode=0;
                    stdMode=false;
                }else if (arg.equals("-c") || arg.equals("-l")){    // compiled Java class (library)
                    mode=1;
                    stdMode=false;
                }else if (arg.equals("-u")){    // unresolved target
                    mode=2;
                    stdMode=false;
                }else if (arg.startsWith("-e")){
                    extension=arg.substring(2);
                }else if (arg.startsWith("-v")){
                    verbose=true;
                }else if (arg.startsWith("-")){
                    System.out.println("Unknown option "+arg);
                    System.out.println("Syntax: lljvm.jar ld < input.j > output.j class1 class2 ~class3 ...");
                    System.out.println("      or");
                    System.out.println("Syntax: lljvm.jar ld -j input1.j input2.j ... -c class1 class2 -u class3 ... [-e.newextension] [-v]");
                    System.exit(1);
                }else{
                    switch(mode){
                        case 0:
                            params.addSource(new AsmStreamSource(arg,new FileInputStream(arg),new FileOutputStream(arg+extension)));
                        break;
                        case 1:
                            if (arg.startsWith("~")) {
                                params.setUnresolvedTarget(arg.substring(1));
                            } else {
                                params.addLibraryClass(arg);
                            }
                        break;
                        case 2:
                            params.setUnresolvedTarget(arg);
                        break;
                    }
                }
            }

            if (stdMode){
                params.addSource(new AsmStreamSource("<stdin>", in, out));
            }

            AsmLinker linker = new AsmLinker(params);
            linker.run();
        } catch (AsmLinkerException e) {
            err.println("Linker Error: " + e);
            e.printStackTrace(err);
            err.flush();
            return 1;            
        } catch (FileNotFoundException e){
            err.println("File not found: " + e);
            e.printStackTrace(err);
            err.flush();
            return 1;            
        }
        return 0;
    }
    


    public static void main(String[] args)  {
        args = LoggingEnvironment.setupLogging(args).getNonLoggingArgs(); 
        int res;
        try {
            res = doMain(args, System.in, System.out, new PrintWriter(System.err));
        } catch (Throwable t) {
            System.err.println("An unexpected error occurred: " + t);
            t.printStackTrace();
            res = 1;            
        }
        System.exit(res);        
    }
    
    
}
