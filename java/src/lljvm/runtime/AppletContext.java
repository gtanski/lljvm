/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package lljvm.runtime;

import java.net.URL;
import lljvm.io.AppletFileSystem;
import lljvm.io.FileSystem;

/**
 *
 * @author Grzegorz Tański
 */
public class AppletContext extends DefaultContext  {
    URL base;
    public AppletContext(URL base){
        this.base=base;
    }
    
    @Override
    protected <T> T createModule(Class<T> clazz) {
        if (FileSystem.class.equals(clazz))
            return clazz.cast(new AppletFileSystem(base));

        return super.createModule(clazz);
    }    
}
