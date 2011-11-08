package multipjava;

import java.applet.Applet;
import java.awt.Graphics;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import lljvm.runtime.Context;
import lljvm.runtime.System;
import lljvm.runtime.DefaultContext;
import lljvm.runtime.AppletContext;
import lljvm.runtime.ExecThread;
import lljvm.runtime.Function;
import lljvm.runtime.IO;
import lljvm.runtime.Memory;
import lljvm.runtime.System.Exit;

public class AppletRunner extends Applet{
    static final int FLAG_MAINSTREAM=1;
    static final int FLAG_MAINGRAPHICS=2;
    static final int FLAG_MAINMASK=FLAG_MAINSTREAM|FLAG_MAINGRAPHICS;

    static final String[] paramNames={"class","cp","workdir","splash","flags","fontsize"};

    String[] args=new String[]{""};

    String classPath;
    String mainClass;
    String workDir;
    String splash;

    int flags;

    URL codeBase;
    
    JavaFrame paintListener=null;
    
    Context context;
    PG pg;
    System system;
    Function function;
    IO io;
    Memory memory;

    public String hexHash(){
        return "AppletRunner "+Integer.toHexString(hashCode());
    }
    
    @Override
    public String[][] getParameterInfo(){
        String pinfo[][] = {
            {paramNames[0], "", "main class name"},
//            {paramNames[1], "", "class path for C compiled classes"},
            {paramNames[2], "", "C application working directory"},
            {paramNames[3], "", "load time text"},
            {paramNames[4], "0-2", "flags"},
            {paramNames[5], "1-100", "console font size"}
        };
        
        return pinfo;
    }

    @Override
    public void paint( Graphics g ) {
        if (paintListener!=null){
          paintListener.paint(g);
        }
    }

    @Override
    public void update(Graphics g){
        paint(g);
    }
    public static String[] splitArgs(String args){
        ArrayList v=new ArrayList();
        
        args=args.trim();
        
        int len=args.length();
        
        for (int a=0 ; a<len ; a++){
            char c=' ';

            for ( ; a<len ; a++){
                c=args.charAt(a);
                
                if (c!=' '){
                    break;
                }
            }

            if (c=='\"'){
                int b=a+1;
                for ( ; b<len && args.charAt(b)!='\"' ; b++){
                }                

                v.add(args.substring(a+1, b));
                
                a=b+1;
            }else{
                int b=a;
                for (; b<len && args.charAt(b)!=' ' ; b++){
                }
                
                v.add(args.substring(a, b));
                
                a=b;
            }
        }
        
        String[] quotes=new String[v.size()];
        quotes=(String[]) v.toArray(quotes);
        
        return quotes;
    }

    protected Method commonInit(){  
        pg=context.getModule(PG.class);
        system=context.getModule(System.class);
        function=context.getModule(Function.class);
        io=context.getModule(IO.class);
        memory=context.getModule(Memory.class);
        
        if ((flags&FLAG_MAINMASK)==FLAG_MAINSTREAM){
            pg.mainStream=new JavaAwtStream(pg.streams,this);
        }else if ((flags&FLAG_MAINMASK)==FLAG_MAINGRAPHICS){
            pg.mainContainer=this;
            pg.mainStream=new JavaAwtStream(pg.streams);
        }else{
            pg.mainStream=new JavaAwtStream(pg.streams);            
        }

        if (splash!=null){
            pg.mainStream.line(splash);
        }
        pg.mainStream.interupt(JavaStream.si_none);

        if (!PG.jvok()){
            pg.mainStream.println("You need at least Java "+PG.minimumJavaVersion+" (current version is "+PG.jvs()+")");            
            return null;
        }
        
        system.setThrowExit(true);    // call exception on exit()

        if (classPath==null) classPath=".";
        if (mainClass==null) mainClass="Main";
        if (StreamContainer.fontSize==0) StreamContainer.fontSize=11;
    
        try{
            int callid=function.getFunctionPointer(mainClass,"main(II)I");

            return function.getMethod(callid);
        }
        catch(IllegalArgumentException e){
            e.printStackTrace();
            pg.mainStream.println("Unable to find C function 'int main(int argc,const char* argv)' nor Java method 'public static int main(int,int)' in class '"+mainClass+"'");
        }
        
        return null;
    }
    void start(Method main){
        if (main!=null){
            AppletThread thread=new AppletThread(context,this,main,args);

            thread.start();    
        }
    }

    @Override
    public void start() {
     //   System.out.println(hexHash()+" start()");
                
    	Method main=commonInit();

        start(main);

    //    System.out.println(hexHash()+" started");
    }
    
    @Override
    public void init(){		// applet html embeded start
     //   System.out.println(hexHash()+" init()");
        
	mainClass=getParameter(paramNames[0]);
        workDir=getParameter(paramNames[2]);
        splash=getParameter(paramNames[3]);
        
        if (getParameter(paramNames[4])!=null){
            flags=Integer.parseInt(getParameter(paramNames[4]));
        }
        if (getParameter(paramNames[5])!=null){
            StreamContainer.fontSize=Integer.parseInt(getParameter(paramNames[5]));
        }
        
        String argsString=getParameter("args");
        if (argsString!=null){
            args=splitArgs("PGJavaApplet "+argsString);
        }

	codeBase=getCodeBase();

        // setup applet file system
        URL workDirUrl=codeBase;
        
        if (workDir!=null){
            try {
                workDirUrl=new URL(codeBase,workDir);
            } catch (MalformedURLException e) {
                e.printStackTrace();
                pg.mainStream.line("Unable to find workdir URL : "+workDir);
                
                return;
            }
        }
        
        context=new AppletContext(workDirUrl);

        //function.setClassLoaderAppletMode(true);

      //  System.out.println(hexHash()+" initialized");
    }
    
    @Override
    public void destroy(){
        context.close();
    }

    @Override
    public void stop() {
       // System.out.println(hexHash()+" stop()");
        
        pg.stopApplet();
        
      //  System.out.println(hexHash()+" stopped");
    }

        
    public void mainInit(String[] args){
	for (int a=0 ; a<args.length ; a++){
	    for (int b=0 ; b<paramNames.length ; b++){
		if (args[a].equalsIgnoreCase("-"+paramNames[b])){
		    args[a++]=null;

		    switch(b){
			case 0:
			    mainClass=args[a];
			break;
			case 1:
			    classPath=args[a];
			break;
                        case 2:
                            workDir=args[a];
                        break;
                        case 3:
                            splash=args[a];
                        break;
                        case 4:
                            flags=Integer.parseInt(args[a]);
                        break;
                        case 5:
                            StreamContainer.fontSize=Integer.parseInt(args[a]);
                        break;
		    }

		    args[a]=null;
		    break;
		}
	    }
	}

	int filteredArgs=0;

	for (int a=0 ; a<args.length ; a++){
	    if (args[a]!=null){
		filteredArgs++;
	    }
	}

	this.args=new String[filteredArgs+1];

	filteredArgs=0;
        
        this.args[filteredArgs++]="PGJavaApp";
                
	for (int a=0 ; a<args.length ; a++){
	    if (args[a]!=null){
		this.args[filteredArgs++]=args[a];
	    }
	}

       context=new DefaultContext();

   /*     try{
	    String cpString[]=classPath.split(",");
	    URL cpUrl[]=new URL[cpString.length];

	    for (int a=0 ; a<cpString.length ; a++){
		if (codeBase!=null){
		    cpUrl[a]=new URL(codeBase+cpString[a]);
		}else{
		    cpUrl[a]=new File(cpString[a]).toURI().toURL();
		}
	    }
            
            ReflectionUtils.setClassLoader(new URLClassLoader(cpUrl));
        }
	catch(MalformedURLException e){
	    e.printStackTrace();
	}*/
                
	start(commonInit());
    }

    void setListner(JavaFrame pl) {
        paintListener=pl;
    }
}
class AppletThread extends ExecThread{
    final AppletRunner app;
    Method main;
    String[] args;

    AppletThread(Context context,AppletRunner notify,Method main,String[] args){
        super(context,"Applet "+Integer.toHexString(notify.hashCode())+" ExecThread");
        this.app=notify;
	this.main=main;
	this.args=args;
    }

    @Override
    public void run(){
        final Object[] params = new Object[2];
        
        app.memory.createStackFrame();
        
	params[0]=args.length;
        params[1]=app.memory.storeStack(args);
        
        JavaStream stream=app.pg.mainStream;
        app.pg.setRunningApplet(app);

        Object instance=app.context.getModule(main.getDeclaringClass());
        
	try{
            Object o=main.invoke(instance, params);
            app.memory.destroyStackFrame();
            
            stream.println("Program finished ("+o+")");
	}
        catch(java.lang.reflect.InvocationTargetException e){
            Throwable t=e.getCause();
          
            if (t instanceof Exit){
                Exit ex=(Exit)t;
                
                stream.println("Program exited ("+ex.status+").");
            }else{
                stream.println("Runtime error in thread "+Thread.currentThread()+" : "+t);
                stream.printStackTrace(t);
                t.printStackTrace();
            }
        }
	catch(Throwable e){
	    stream.println("Runtime exception.");
	    e.printStackTrace();
	}
        finally{
   //         stream.println(notifier.hexHash()+" thread finished");
      //      System.err.println(notifier.hexHash()+" thread finished");
            app.pg.setRunningApplet(null);
       //     stream.println(notifier.hexHash()+" thread finished!");
        }
    }
}
