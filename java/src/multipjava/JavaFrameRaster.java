/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package multipjava;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import lljvm.runtime.Memory;
import lljvm.util.ObjectHandler;

/**
 *
 * @author Grzegorz
 */
public class JavaFrameRaster extends IndexedObject implements ImageObserver{
    int shiftX;
    int shiftY;

    Graphics gfx;
    Memory memory;

    JavaFrameRaster(ObjectHandler<Object> myhandler,Memory memory,int sx,int sy,Graphics gfx){
	super(myhandler);
        shiftX=sx;
        shiftY=sy;

        this.gfx=gfx;
        this.memory=memory;
    }
    
  /*  public static void freeAll(){
        handler.freeAll();
    }*/

    public JavaFrameRaster id(int rasti){
        return (JavaFrameRaster)handler.hash(rasti);
    }

    public void plots(int n,int pntsPtr){
        for (int a=0 ; a<n ; a++){
            int x=memory.load_i32(pntsPtr)+shiftX;
            int y=memory.load_i32(pntsPtr+4)+shiftY;
            int color=memory.load_i32(pntsPtr+8);

            gfx.setColor(new Color(color));
            gfx.fillRect(x,y,1,1);

            pntsPtr+=12;
        }
    }
    public void copyPixels(int pixels,int pixelsModulo,int x0,int y0,int w,int h){
        BufferedImage bimg=new BufferedImage(w,h,BufferedImage.TYPE_INT_BGR);
	
	pixelsModulo-=w;
        pixelsModulo*=4;

        for (int y=0 ; y<h ; y++){
            for (int x=0 ; x<w ; x++){
                int c=memory.load_i32(pixels);
                pixels+=4;
                bimg.setRGB(x,y,c);
                
            }
            pixels+=pixelsModulo;
	}
        
        gfx.drawImage(bimg, x0+shiftX, y0+shiftY, this);
    }

    public boolean imageUpdate(Image image, int i, int i1, int i2, int i3, int i4) {
        return true;
    }
}
