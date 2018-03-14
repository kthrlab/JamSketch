
// AverageRect.java
// Andrew Davison, July 2013, ad@fivedots.psu.ac.th

/* Storage for up to MAX_ELEMS CvRect objects,
   which are returned as an average when get() is called.

   add() is a little non-standard. Attempting to add a null
   reference causes the first CvRect in the list to be
   deleted. The first object is also deleted when add() is
   called when the list is full.
   This allows the stored rectangles to change as add() is 
   called.
*/

import java.awt.*;
import java.io.*;
import java.util.*;

import com.googlecode.javacv.*;
import com.googlecode.javacv.cpp.*;

import static com.googlecode.javacv.cpp.opencv_core.*;


public class AverageRect
{
  private static final int MAX_ELEMS = 10;

  private ArrayList<CvRect> rects;


  public AverageRect()
  {  rects = new ArrayList<CvRect>();  }


  public synchronized void add(CvRect r)
  /* synchronized prevents add(0 from being called at 
     the same time as a get() */
  {
    if (r == null) {     // adding null means delete the oldest element
      if (rects.size() > 0)
        rects.remove(0);
     return;
    }
    
    if (rects.size() == MAX_ELEMS)
      rects.remove(0);
    rects.add(r);
  }  // end of add()



  public synchronized CvRect get()
  {
    if (rects.size() == 0)
      return null;

    // calculate average of all the rectangles
    int xTot, yTot, widthTot, heightTot;
    xTot = yTot = 0;
    widthTot = heightTot = 0;

    for(CvRect r : rects) {
      xTot += r.x();  yTot += r.y();
      widthTot += r.width();  
      heightTot += r.height();
    }

    int n = rects.size();
    return new CvRect( xTot/n, yTot/n, widthTot/n, heightTot/n);
  }  // end of get()

}  // end of AverageRect class
