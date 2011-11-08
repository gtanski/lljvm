/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package multipjava;

import lljvm.util.ObjectHandler;

/**
 *
 * @author Grzegorz
 */
public class IndexedObject{
    protected ObjectHandler<Object> handler;
    protected int index;

    IndexedObject(ObjectHandler<Object> handler){
	this.handler=handler;
        register();
    }

    final void register(){
        index=handler.alloc(this);
    }
    void destroy(){
        handler.free(index);
    }
    public int id(){
        return index;
    }
}
