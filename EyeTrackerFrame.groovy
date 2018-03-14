import java.awt.*
import javax.swing.*
import com.googlecode.javacv.*
import com.googlecode.javacv.cpp.opencv_objdetect
import com.googlecode.javacv.cpp.videoInputLib.videoInput
import com.googlecode.javacpp.Loader

class EyeTrackerFrame extends JFrame implements MotionController {
  private TargetMover tm = null
  
  EyeTrackerFrame() {
    super("Eye Tracker")
    getContentPane().setLayout(new BorderLayout())
    Loader.load(opencv_objdetect.class)
  }

  void setTarget(TargetMover tm) {
    this.tm = tm
  }

  void start() {
    if (tm == null)
      throw new IllegalStateException("setTarget() must be called")
    def eyePanel = new EyePanel(tm)
    getContentPane().add(eyePanel, BorderLayout.CENTER)
    setResizable(false)
    pack()
    setVisible(true)
  }

  void init() {
    showCameraChooser()
  }

  def showCameraChooser() {
    int n = videoInput.listDevices()
    def devices = []
    for (int i in 0..<n) {
      devices.add(videoInput.getDeviceName(i))
    }
    def selected = JOptionPane.showInputDialog(this,
      "Select Camera.", "Select Camera...",
      JOptionPane.PLAIN_MESSAGE, null, devices as String[], null)
    EyePanel.CAMERA_ID = devices.indexOf(selected)
  }
}
