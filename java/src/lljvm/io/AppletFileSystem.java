/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package lljvm.io;

import java.io.IOException;
import java.net.URL;
import lljvm.runtime.IO;
import lljvm.runtime.Error;

/**
 *
 * @author Grzegorz Tański
 */
public class AppletFileSystem extends NativeFileSystem {
    private URL urlBase;
    
    public AppletFileSystem(URL base){
        super(".");
        urlBase=base;
    }
    
    @Override
    public FileHandle open(String pathname, int flags) {
        FileHandle filehandle=null;
        
        try{
            if ((flags&3)==IO.O_RDONLY){
                filehandle=new UrlInputStreamFileHandle(context,new URL(urlBase,pathname));
            }
        }
        catch(IOException e){
        }
        
        if (filehandle==null){
            error.errno(Error.EACCES);      
        }
        
        return filehandle;
    }
}
