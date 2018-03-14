     
// EyePanel.java
// Andrew Davison, July 2013, ad@fivedots.psu.ac.th
// Chonmaphat Roonnapak, February 2013, cocasmile.chon@gmail.com

/* This panel repeatedly snaps a picture and draw it onto
   the panel.  A eye is highlighted with a yellow rectangle, which is updated
   as the eye moves. Eye detection is done using a Haar classifier 
   written with JavaCV.

   The eye image is passed to a ColorRectDector object to find the pupil/iris based on
   its color. The detected pupil/iris is outlined with a red rectangle, which is
   updated as the pupil moves.
   
   The locations of several bounded boxes for the eye and pupil/iris are averaged 
   (using AverageRect objects), so that slight movements in both can be 
   'smoothed out' by using
   an average of the rectangles. As new rectangles are added to the
   objects, old values are removed.

   The current position of the pupil/iris center relative to the eye rectangle 
   is used to move a target image in a separate target window.
*/


import java.awt.*;
import javax.swing.*;
import java.awt.image.*;
import java.io.*;
import javax.imageio.*;
import java.util.*;

import com.googlecode.javacv.*;
import com.googlecode.javacv.cpp.*;
import com.googlecode.javacv.cpp.videoInputLib.*;

import static com.googlecode.javacv.cpp.opencv_core.*;
import static com.googlecode.javacv.cpp.opencv_imgproc.*;
import static com.googlecode.javacv.cpp.opencv_objdetect.*;
import static com.googlecode.javacv.cpp.opencv_highgui.*;
import static com.googlecode.javacv.cpp.avutil.*;   // for grabber/recorder constants



public class EyePanel extends JPanel implements Runnable
{
  // dimensions of this panel == dimensions of webcam image
  private static final int WIDTH = 640;  
  private static final int HEIGHT = 480;

  private static final int DELAY = 100; // time (ms) between redraws of the panel

  public static int CAMERA_ID = 0;

  private static final int CAM_IM_SCALE = 4;   // scaling applied to webcam image

  // Haar cascade definition used for eye detection
  private static final String EYE_CASCADE_FNM = "eye.xml";
  /* originally called haarcascade_frontalface_alt2.xml
     in C:\OpenCV2.2\data\haarcascades\ and at http://alereimondo.no-ip.org/OpenCV/34
  */

  private static final double EYE_SCALE = 0.65;   
     // for reducing the bounded box around the detected eye

  private static final double PUPIL_SCALE = 3;   
    // for increasing pupil movement relative to eye center


  private IplImage snapIm = null;
  private volatile boolean isRunning;
  private volatile boolean isFinished;

  // JavaCV variables
  private CvMemStorage storage;
  private CanvasFrame debugCanvas;

  // eye detection
  private CvHaarClassifierCascade eyeClassifier;
  private AverageRect eyeAvgRect;    // average bounded box for eye

  // pupil/iris detection
  private ColorRectDetector pupilDetector;
  private AverageRect pupilAvgRect;  // average bounded box for pupil/iris

  private TargetMover targetFrame;   
    // the window whose target is moved by pupil/iris movement



  public EyePanel(TargetMover tm)
  {
    targetFrame = tm;
    setBackground(Color.white);

    initDetector();

    eyeAvgRect = new AverageRect();
    pupilAvgRect = new AverageRect();
    pupilDetector = new ColorRectDetector("blackHSV.txt");

    new Thread(this).start(); // start updating the panel's image
  } // end of EyePanel()



  public Dimension getPreferredSize()
  // dimensions of this panel == dimensions of webcam image
  {   return new Dimension(WIDTH, HEIGHT); }



  private void initDetector()
  {
    // instantiate a classifier cascade for eye detection
    eyeClassifier = new CvHaarClassifierCascade(cvLoad(EYE_CASCADE_FNM));
    if (eyeClassifier.isNull()) {
      System.out.println("\nCould not load the classifier file: " + EYE_CASCADE_FNM);
      System.exit(1);
    }

    storage = CvMemStorage.create(); // create storage used during object detection

    // debugCanvas = new CanvasFrame("Debugging Canvas");
        // useful for showing JavaCV IplImage objects, to check on image processing
  }  // end of initDetector()



  public void run() 
  /* display the current webcam image every DELAY ms
     Each image is processed to find an eye and its pupil
  */
  {
    FrameGrabber grabber = initGrabber(CAMERA_ID);
    if (grabber == null)
      return;

    long duration;
    isRunning = true;
    isFinished = false;

    while (isRunning) {
      long startTime = System.currentTimeMillis();
      
      try {
      snapIm = picGrab(grabber, CAMERA_ID); 

      IplImage eyeIm = trackEye(snapIm);   // find the eye
      if (eyeIm != null)
        trackPupil(eyeIm);    // find the pupil/iris
      repaint();
      } catch (RuntimeException e) {
	  System.err.println("The following exception has been thrown. Continuing processing. ");
	  System.err.println(e.toString());
      }
      
      duration = System.currentTimeMillis() - startTime;
      if (duration < DELAY) {
        try {
          Thread.sleep(DELAY - duration); // wait until DELAY time has passed
        }
        catch (Exception ex) {}
      }
    }
    closeGrabber(grabber, CAMERA_ID);
    System.out.println("Execution End");
    isFinished = true;
  }  // end of run()



  private FrameGrabber initGrabber(int ID)
  {
    FrameGrabber grabber = null;
    System.out.println("Initializing grabber for " + videoInput.getDeviceName(ID) + " ...");
    try {
      grabber = FrameGrabber.createDefault(ID);
      grabber.setFormat("dshow");       // using DirectShow
      grabber.setImageWidth(WIDTH);     // default is too small: 320x240
      grabber.setImageHeight(HEIGHT);
      grabber.start();
    }
    catch(Exception e) 
    {  System.out.println("Could not start grabber");  
       System.out.println(e);
       System.exit(1);
    }
    return grabber;
  }  // end of initGrabber()



  private IplImage picGrab(FrameGrabber grabber, int ID)
  {
    IplImage im = null;
    try {
      im = grabber.grab();  // take a snap
    }
    catch(Exception e) 
    {  System.out.println("Problem grabbing image for camera " + ID);  }
    return im;
  }  // end of picGrab()



  private void closeGrabber(FrameGrabber grabber, int ID)
  {
    try {
      grabber.stop();
      grabber.release();
    }
    catch(Exception e) 
    {  System.out.println("Problem stopping grabbing for camera " + ID);  }
  }  // end of closeGrabber()


  public void closeDown()
  /* Terminate run() and wait for it to finish.
     This stops the application from exiting until everything
     has finished. */
  { 
    isRunning = false;
    while (!isFinished) {
      try {
        Thread.sleep(DELAY);
      } 
      catch (Exception ex) {}
    }
  } // end of closeDown()



  // ------------------------- eye tracking ----------------------------

  private IplImage trackEye(IplImage im) 
  /* Find the eye in the current image, store its coordinates as a Rectangle,
     and return the cropped part of the image containing the eye (or null).
  */
  { IplImage eyeIm = null;

    // long startTime = System.currentTimeMillis();
    CvRect cvEyeRect = findEye(im, eyeClassifier);
    eyeAvgRect.add( scaleRectangle(cvEyeRect) );   // add to other rectangles
    CvRect avRect = eyeAvgRect.get();    // get average
    if (avRect != null) {
      eyeIm = IplImage.create(avRect.width(), avRect.height(), IPL_DEPTH_8U, 3);
      cvSetImageROI(im, avRect);
      cvCopy(im, eyeIm);
      cvResetImageROI(im);
      // System.out.println(" eye detection time: " + 
      //               (System.currentTimeMillis() - startTime) + "ms");
    }
    return eyeIm;
  }  // end of trackEye()




  private CvRect findEye(IplImage im, CvHaarClassifierCascade classifier) 
  /* Return the bounded rectangle around an eye, found using a
     Haar detector.
  */
  {
    IplImage cvImg = scaleGray(im);
    CvSeq eyeSeq = cvHaarDetectObjects(cvImg, classifier, storage, 1.1, 1,
                              CV_HAAR_DO_ROUGH_SEARCH | CV_HAAR_FIND_BIGGEST_OBJECT);
      // speed things up by searching for only a single, largest eye subimage

    int total = eyeSeq.total();
    if (total == 0) {
      // System.out.println("  No eye found");
      return null;
    }
    else if (total > 1) // this case should not happen, but included for safety
      System.out.println("Multiple eyes detected (" + total + "); using the first");

    CvRect rect = new CvRect(cvGetSeqElem(eyeSeq, 0));
    cvClearMemStorage(storage);
    return rect;
  }  // end of findEye()




  private IplImage scaleGray(IplImage img)
  /* Scale the image and convert it to grayscale. Scaling makes
     the image smaller and so faster to process, and Haar detection
     requires a grayscale image as input
  */
  {
    // convert to grayscale
    IplImage grayImg = cvCreateImage(cvGetSize(img), IPL_DEPTH_8U, 1);
    cvCvtColor(img, grayImg, CV_BGR2GRAY);  

    // scale the grayscale (to speed up face detection)
    IplImage smallImg = IplImage.create(grayImg.width()/CAM_IM_SCALE, 
                                        grayImg.height()/CAM_IM_SCALE, IPL_DEPTH_8U, 1);
    cvResize(grayImg, smallImg, CV_INTER_LINEAR);

    // equalize the small grayscale
	cvEqualizeHist(smallImg, smallImg);
    return smallImg;
  }  // end of scaleGray()



  private CvRect scaleRectangle(CvRect rect) 
  /* Two scalings are performed. The first
     scales the rectangle back to the original webcam image size.
     The second scaling reduces the size of the bounded box, 
     which tends to be much larger the eye.
  */
  {
    if (rect == null)
      return null;

    int x = rect.x() * CAM_IM_SCALE;    // scale back to original size
    int y = rect.y() * CAM_IM_SCALE;
    int w = rect.width() * CAM_IM_SCALE;
    int h = rect.height() * CAM_IM_SCALE;

    // reduce the bounded box size
    int wScaled = (int)Math.round(w * EYE_SCALE);
    int hScaled = (int)Math.round(h * EYE_SCALE);
    int xScaled = x + (w - wScaled)/2;
    int yScaled = y + (h - hScaled)/2;

    return new CvRect(xScaled, yScaled, wScaled, hScaled); 
  }  // end of scaleRectangle()




  // ------------------------- pupil/iris tracking ----------------------------


  private void trackPupil(IplImage eyeIm) 
  /* find the bounded box for the pupil/iris in the current eye image
     Convert the pupil coordinate into a position relative to the eye
     rectangle, and use it to move the target in the target window.
  */
  {
    // long startTime = System.currentTimeMillis();

    pupilAvgRect.add( pupilDetector.findRect(eyeIm) );
       /* find pupil/iris rectangle, and add to average rectangles object;
          its coordinates are relative to the eye image not the
          webcam image */

    // get average eye and pupil rectangles
    CvRect eyeRect = eyeAvgRect.get();
    CvRect pupilRect = pupilAvgRect.get();
    if (pupilRect != null) {
      // calculate distance of pupil from the center of the eye rectangle
      int xDist = (pupilRect.x() + pupilRect.width()/2) - eyeRect.width()/2;    
      int yDist = pupilRect.y() + pupilRect.height()/2 - eyeRect.height()/2;

      /* scale distances, and convert to percentage positions inside the 
         eye rectangle (the values may be greater than 1 due to the scaling) 
      */
      double xInEye = ((double)eyeRect.width()/2 + xDist*PUPIL_SCALE)/eyeRect.width();   
      double yInEye = ((double)eyeRect.height()/2 + yDist*PUPIL_SCALE)/eyeRect.height();

      // move the target using the pupil's relative position inside the eye rectangle
      targetFrame.setTarget(xInEye, yInEye);
    }
  }  // end of trackPupil()



  // -------------------------------- painting -------------------------------


  public void paintComponent(Graphics g) 
  /* Draw the webcam image, and two rectangles around the detected eye
     and pupil. */
  {
    Graphics2D g2 = (Graphics2D) g;
    super.paintComponent(g);

    if (snapIm == null)
      g2.drawString("Initializing webcam, please wait...", 20, HEIGHT/2);
    else {
      g2.drawImage(snapIm.getBufferedImage(), 0, 0, this);
      CvRect eyeRect = eyeAvgRect.get();
      if (eyeRect != null) {
        drawEyeRect(g2, eyeRect);
        drawPupilRect(g2, eyeRect);
      }
    }
  } // end of paintComponent()



  private void drawEyeRect(Graphics2D g2, CvRect eyeRect) 
  // draw a yellow outline rectangle around the eye
  {
    // draw a thick yellow rectangle
    g2.setColor(Color.YELLOW);
    g2.setStroke(new BasicStroke(6));
    g2.drawRect(eyeRect.x(), eyeRect.y(), eyeRect.width(), eyeRect.height());
  }  // end of drawEyeRect()



  private void drawPupilRect(Graphics2D g2, CvRect eyeRect) 
  // draw red outline rectangle around the pupil/iris
  {
    CvRect pupilRect = pupilAvgRect.get();
    if (pupilRect == null)
      return;

    pupilRect.x(pupilRect.x() + eyeRect.x());     // convert pupil to screen coords
    pupilRect.y(pupilRect.y() + eyeRect.y());

    g2.setPaint(Color.RED);
    g2.setStroke(new BasicStroke(4));
    g2.drawRect(pupilRect.x(), pupilRect.y(), pupilRect.width(), pupilRect.height());
  }  // end of drawPupilRect()


} // end of EyePanel class

