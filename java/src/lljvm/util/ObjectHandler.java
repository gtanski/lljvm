/*
 * Misc object 2 way hashing
 */

package lljvm.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.*;

/**
 *
 * @author Grzegorz Tanski
 */

public class ObjectHandler<T> {
    private int id=0;
    private int resetCounter=0;

    private Map<Integer, T> ido = new HashMap<Integer, T>();
    private Map<T, Integer> oid = new HashMap<T, Integer>();

    private ReadWriteLock rwl=new java.util.concurrent.locks.ReentrantReadWriteLock();

    private int _hash(T o){
        return oid.get(o);
    }
    private T _hash(int id){
        return ido.get(id);
    }
    private void _add(int id,T o){
        oid.put(o,id);
        ido.put(id,o);
    }
    private void add(int id,T o){
        try{
            rwl.writeLock().lock();
            _add(id,o);
        }finally{
            rwl.writeLock().unlock();
        }
    }

    // interface
    public int alloc(T o){
        try{
            rwl.writeLock().lock();
            id++;
            while(_hash(id)!=null){
                id++;
                if (id==0) id++;  // int loop protection
            }

            _add(id,o);

            return id;
        }finally{
            rwl.writeLock().unlock();
        }
    }

    public void replace(int id,T new_o){
        free(id);
        add(id,new_o);
    }

    public int hash(T o){
        try{
            rwl.readLock().lock();
            return _hash(o);
        }finally{
            rwl.readLock().unlock();
        }
    }
    public T hash(int id){
        try{
            rwl.readLock().lock();
            return _hash(id);
        }finally{
            rwl.readLock().unlock();
        }
    }

    public void resetIdCounter(){
	//java.lang.System.out.println("resetIdCounter NOW "+this);

        try{
            rwl.readLock().lock();
            id=0;
        }finally{
            rwl.readLock().unlock();
        }
    }
    public void resetIdCounter(int every){
	synchronized(this){
	//    java.lang.System.out.println("resetIdCounter "+resetCounter+"/"+every+" "+this);

	    if (resetCounter>0){
		resetCounter--;

		return;
	    }else{
		resetCounter=every;
	    }
	}

	resetIdCounter();

	return;
    }

    public void free(T o,int id){
         try{
            rwl.writeLock().lock();
            oid.remove(o);
            ido.remove(id);

            this.id=id-1;   // reuse freed ids
        }finally{
            rwl.writeLock().unlock();
        }
    }
    public void free(T o){
       free(o,_hash(o));
    }
    public void free(int id){
       free(_hash(id),id);
    }
    
    public Set<Integer> idSet(){
        return ido.keySet();
    }
    public Set<T> oSet(){
        return oid.keySet();
    }
    
    public void freeAll(){
         try{
            rwl.writeLock().lock();
            oid.clear();
            ido.clear();
        }finally{
            rwl.writeLock().unlock();
        }        
        resetIdCounter();
    }
}
