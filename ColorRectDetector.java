
// ColorRectDetector.java
// Andrew Davison, ad@fivedots.coe.psu.ac.th, July 2013

/* This detector uses JavaCV (OpenCV) to find the largest bounded box
   for a specified HSV colour in an image using OpenCV contour detection.

   The HSV information is supplied in a filename when the constructor
   is called.

   The image is supplied by a call to findRect() which returns the
   box as a Rectangle object.
*/

import java.io.*;
import java.awt.*;
import java.awt.image.*;

import com.googlecode.javacv.*;
import com.googlecode.javacv.cpp.*;
import com.googlecode.javacpp.Loader;

import static com.googlecode.javacv.cpp.opencv_core.*;
import static com.googlecode.javacv.cpp.opencv_imgproc.*;



public class ColorRectDetector
{
  private static final float SMALLEST_BOX =  600.0f;    // was 100.0f;
            // ignore detected boxes smaller than SMALLEST_BOX pixels

  // HSV ranges defining the colour
  private int hueLower, hueUpper, satLower, satUpper, briLower, briUpper;

  // OpenCV elements
  private CvMemStorage storage;
  private CanvasFrame debugCanvas;



  public ColorRectDetector(String hsvFnm)
  {
    storage = CvMemStorage.create();
    // debugCanvas = new CanvasFrame("Debugging Canvas");
        // useful for showing JavaCV IplImage objects, to check on image processing

    readHSVRanges(hsvFnm);
  }  // end of ColorRectDetector()



  private void readHSVRanges(String hsvFnm) 
  // read three lines for the lower/upper HSV ranges
  {
    try {
      BufferedReader in = new BufferedReader(new FileReader(hsvFnm));
      String line = in.readLine();   // get hue range
      String[] toks = line.split("\\s+");    // split based on white space
      hueLower = Integer.parseInt(toks[1]);
      hueUpper = Integer.parseInt(toks[2]);

      line = in.readLine();   // get saturation range
      toks = line.split("\\s+");
      satLower = Integer.parseInt(toks[1]);
      satUpper = Integer.parseInt(toks[2]);

      line = in.readLine();   // get brightness range
      toks = line.split("\\s+");
      briLower = Integer.parseInt(toks[1]);
      briUpper = Integer.parseInt(toks[2]);

      in.close();
      System.out.println("Read HSV ranges from " + hsvFnm);
    }
    catch (Exception e)
    {  System.out.println("Could not read HSV ranges from " + hsvFnm);
       System.exit(1);
    }
  }  // end of readHSVRanges()




  public CvRect findRect(IplImage im)
 /* Convert the image into an HSV version. Calculate a threshold
    image using the HSV color ranges. Find the largest bounded box 
    in the threshold image, and return it as a CvRect
 */
  {
    int imWidth = im.width();
    int imHeight = im.height();
    IplImage hsvImg = IplImage.create(imWidth, imHeight, 8, 3);     // for the HSV image
    IplImage imgThreshed = IplImage.create(imWidth, imHeight, 8, 1);   // threshold image

    // convert to HSV
    cvCvtColor(im, hsvImg, CV_BGR2HSV);
    // debugCanvas.showImage(hsvImg);     // useful for debugging

    // threshold image using supplied HSV settings
    cvInRangeS(hsvImg, cvScalar(hueLower, satLower, briLower, 0),
                       cvScalar(hueUpper, satUpper, briUpper, 0), imgThreshed);

    cvMorphologyEx(imgThreshed, imgThreshed, null, null, CV_MOP_OPEN, 1);
        // do erosion followed by dilation on image to remove specks of white & retain size

    CvBox2D maxBox = findBiggestBox(imgThreshed);

    // store OpenCV box details in a Rectangle
    if (maxBox != null) {
      int xC = (int)Math.round( maxBox.center().x());
      int yC = (int)Math.round( maxBox.center().y());
      int width = (int)Math.round(maxBox.size().width());
      int height = (int)Math.round(maxBox.size().height());
      return new CvRect(xC-width/2, yC-height/2, width, height);
    }
    else 
      return null;
  }  // end of findRect()



  private CvBox2D findBiggestBox(IplImage imgThreshed)
  // return the bounding box for the largest contour in the threshold image
  {
    CvSeq bigContour = null;

    // generate all the contours in the threshold image as a list
    CvSeq contours = new CvSeq(null);
    cvFindContours(imgThreshed, storage, contours, Loader.sizeof(CvContour.class),
                                                CV_RETR_LIST, CV_CHAIN_APPROX_SIMPLE);

    // find the largest OpenCV box in the list of contours
    float maxArea = SMALLEST_BOX;
    CvBox2D maxBox = null;
    while (contours != null && !contours.isNull()) {
      if (contours.elem_size() > 0) {
        CvBox2D box = cvMinAreaRect2(contours, storage);
        if (box != null) {
          CvSize2D32f size = box.size();
          float area = size.width() * size.height();
          if (area > maxArea) {
            maxArea = area;
            maxBox = box;
            bigContour = contours;
          }
        }
      }
      contours = contours.h_next();
    }
    return maxBox;
  }  // end of findBiggestBox()


}  // end of ColorRectDetector class
