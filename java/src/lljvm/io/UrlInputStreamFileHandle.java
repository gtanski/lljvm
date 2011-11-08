/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package lljvm.io;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URL;
import lljvm.runtime.Context;
import lljvm.runtime.IO;

/**
 *
 * @author Grzegorz
 */
public class UrlInputStreamFileHandle extends InputStreamFileHandle{
    URL url;
    int fileLength;
    
    int nextPos;    // position for read
    int currentPos; // current stream position
    
    public UrlInputStreamFileHandle(Context context, URL url) throws IOException {
        super(context,new BufferedInputStream(url.openStream()));
        
        this.url=url;
        
        nextPos=0;
        
        fileLength=-1;  // unknown
        
        System.out.println("UrlInputStream url: "+url+" open");
        
        reset();
    }
    
    private int length() throws IOException{
        if (fileLength<0){
            fileLength=currentPos+skip(0x7fffffff);
            currentPos=fileLength;
            System.out.println("UrlInputStream url: "+url+" len:"+fileLength);
        }
        
        return fileLength;
    }

    private void reset() throws IOException{
        if (inputStream!=null){
            inputStream.close();        
        }
        
        inputStream=url.openStream();        
    }
    private int skip(int s) throws IOException{
        int skipped=0;
        
        try{
            for (int a=0 ; a<s ; a++){
                if (inputStream.read()==-1){
                    break;
                }
                skipped++;
            }
        } catch(IOException e){
            
        }
        
        return skipped;
    }

    @Override
    protected int read() throws IOException {
        if (currentPos!=nextPos){
            if (nextPos>currentPos){
                skip(nextPos-currentPos);
            }else{
                reset();
                skip(nextPos);
            }
            currentPos=nextPos;
        }
        
        int readv=super.read();
        
        if (readv>=0){
            currentPos++;
            nextPos++;
        }
        
        return readv;
    }

    @Override
    public int seek(int offset, int whence) {  
//        System.out.println("UrlInputStream off: "+offset+" mode: "+whence+" cur: "+nextPos);
        
        try {
            switch(whence) {
                case IO.SEEK_SET:   //0
                    nextPos=offset;
                break;
                case IO.SEEK_CUR:   //1
                    nextPos+=offset;
                break;
                case IO.SEEK_END:   //2
                    nextPos=length()+offset;
                break;
                default:
                    return error.errno(lljvm.runtime.Error.EINVAL);
            }
        
//            System.out.println("set:"+nextPos);
        } catch (IOException ex) {
            return error.errno(lljvm.runtime.Error.EINVAL);
        }

  //      System.out.println("ok p:"+seekPos);

        return nextPos;
    }    
}
