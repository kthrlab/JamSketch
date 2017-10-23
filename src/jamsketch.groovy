import groovy.transform.*
import jp.crestmuse.cmx.filewrappers.*
import jp.crestmuse.cmx.processing.*
import jp.crestmuse.cmx.processing.gui.*
import groovy.json.*
import jp.crestmuse.cmx.misc.*
import groovy.json.*
import controlP5.*
import javax.swing.*
import javax.swing.filechooser.*

class JamSketch extends SimplePianoRoll implements TargetMover {


  MelodyData data
  boolean nowDrawing = false
  String username = ""
  static def CFG
  
  void setup() {
    super.setup()
    size(1200, 700)
    showMidiOutChooser()
    def p5ctrl = new ControlP5(this)
    p5ctrl.addButton("startMusic").
    setLabel("Start / Stop").setPosition(20, 645).setSize(120, 40)
    p5ctrl.addButton("resetMusic").
    setLabel("Reset").setPosition(160, 645).setSize(120, 40)
    p5ctrl.addButton("loadCurve").
    setLabel("Load").setPosition(300, 645).setSize(120, 40)


    if (CFG.MOTION_CONTROLLER != null) {
      def ctrl = Class.forName(CFG.MOTION_CONTROLLER).newInstance()
      ctrl.setTarget(this)
      ctrl.init()
      ctrl.start()
    }
    
/*
    if (TOBII) {
      def tobii = new TobiiReceiver(this)
      tobii.init()	
      tobii.start()
    }
    if (BLUETOOTH) {
      def rfcomm = new RfcommServer(this, height)
      rfcomm.connect()
      rfcomm.start()
    }
*/
    initData()

/*
if (EYE_TRACKER) {
      def eyetracker = new EyeTrackerFrame()
      eyetracker.showCameraChooser()
      eyetracker.start(this)
    }
    */

//    inputName()
    
}

  void initData() {
	def filename = getClass().getClassLoader().getResource(CFG.MIDFILENAME).toURI().getPath()
	data = new MelodyData(filename , width, this, this)
    println(data.getFullChordProgression())
    smfread(data.scc.getMIDISequence())
    def part = data.scc.getFirstPartWithChannel(1)
    setDataModel(
      part.getPianoRollDataModel(
	CFG.INITIAL_BLANK_MEASURES,
	CFG.INITIAL_BLANK_MEASURES + CFG.NUM_OF_MEASURES
      ))
  }
  
  void draw() {
    super.draw()
    stroke(0, 0, 255)
    strokeWeight(3)
    (0..<(data.curve1.size()-1)).each { i ->
      if (data.curve1[i] != null && data.curve1[i+1] != null)
	line(i, data.curve1[i] as int, i+1, data.curve1[i+1] as int)
    }
    if (getCurrentMeasure() == CFG.NUM_OF_MEASURES - 1) {
      makeLog("melody")
      if (CFG.MELODY_RESETING) {
	getDataModel().shiftMeasure(CFG.NUM_OF_MEASURES)
	data.resetCurve()
      }
    }
    if (isNowPlaying()) {
      def data = getDataModel()
      int m = getCurrentMeasure() + data.getFirstMeasure() -
              CFG.INITIAL_BLANK_MEASURES + 1
      int mtotal = data.getMeasureNum() * CFG.REPEAT_TIMES
      textSize(32)
      fill(0, 0, 0)
      text(m + " / " + mtotal, 460, 675)
    }
    if (CFG.FORCED_PROGRESS) {
      mouseX = beat2x(getCurrentMeasure()+1, getCurrentBeat());
    }
    if (!CFG.ON_DRAG_ONLY && isInside(mouseX, mouseY)) {
      updateCurve()
    }
    if (CFG.CURSOR_ENHANCED) {
      fill(255, 0, 0)
      ellipse(mouseX, mouseY, 10, 10)
    }
  }

  void updateCurve() {
      int m1 = x2measure(mouseX)
      int m0 = x2measure(pmouseX)
      if (0 <= m0) {  
        if (pmouseX < mouseX) {
       	  (pmouseX..mouseX).each { i ->
            data.curve1[i] = mouseY
	  }
	}
//	else if (pmouseX > mouseX) {
//	  (pmouseX..mouseX).each { i ->
//	    data.curve1[i] = null
//	  }
//	}
        if (m1 > m0) {
          data.updateCurve(m0 % CFG.NUM_OF_MEASURES)
	}
      }
  }
  
  void stop() {
    super.stop()
    featext.stop()
  }

  void startMusic() {
    if (isNowPlaying()) {
      stopMusic()
      makeLog("stop")
    } else {
      //      setTickPosition(0)
      //      getDataModel().setFirstMeasure(INITIAL_BLANK_MEASURES)
      playMusic()
      makeLog("play")
    }
  }

  void resetMusic() {
    if (isNowPlaying()) {
      stopMusic()
      makeLog("stop")
    }
    initData()
    setTickPosition(0)
    getDataModel().setFirstMeasure(CFG.INITIAL_BLANK_MEASURES)
    makeLog("reset")
  }

  void makeLog(action) {
    def logname = "output_" + (new Date()).toString().replace(" ", "_").replace(":", "-")
    if (action == "melody") {
      def midname = "${CFG.LOG_DIR}/${logname}_melody.mid"
      data.scc.toWrapper().toMIDIXML().writefileAsSMF(midname)
      println("saved as ${midname}")
      def sccname = "${CFG.LOG_DIR}/${logname}_melody.sccxml"
      data.scc.toWrapper().writefile(sccname)
      println("saved as ${sccname}")
      def jsonname = "${CFG.LOG_DIR}/${logname}_curve.json"
      saveStrings(jsonname, [JsonOutput.toJson(data.curve1)] as String[])
      println("saved as ${jsonname}")
      def pngname = "${CFG.LOG_DIR}/${logname}_screenshot.png"
      save(pngname)
      println("saved as ${pngname}")
    } else if (action == "name") {
      def txtname = "${CFG.LOG_DIR}/${logname}_name.txt"
      saveStrings(txtname, [username] as String[])
    } else {
      def txtname = "${CFG.LOG_DIR}/${logname}_${action}.txt"
      saveStrings(txtname, [action] as String[])
      println("saved as ${txtname}")
    }
  }

  void mouseDragged() {
    //    if (ON_DRUG_ONLY)
      updateCurve()
  }
  
/*
  void mouseDragged() {
    if (FORCED_PROGRESS) {
      mouseX = beat2x(getCurrentMeasure()+1, getCurrentBeat());
    }
    if (inside(mouseX, mouseY)) {
      if (mouseButton == RIGHT || (keyPressed && keyCode == SHIFT)) {
	(pmouseX..mouseX).each { i ->
	  data.curve1[i] = null
	}
      } else if (mouseButton == LEFT) {
	(pmouseX..mouseX).each { i ->
	  data.curve1[i] = mouseY
	}
      }
      if (x2measure(pmouseX) < x2measure(mouseX)) {
	data.updateCurve(x2measure(pmouseX) % NUM_OF_MEASURES)
      }
    }
  }
*/

  void loadCurve() {
    def filter = new FileNameExtensionFilter(".json or .txt", "json", "txt")
    def chooser = new JFileChooser(currentDirectory: new File("."),
				   fileFilter: filter)
    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      if (chooser.selectedFile.name.endsWith(".json")) {
	data.curve1 = json.parseText(chooser.selectedFile.text)
      } else if (chooser.selectedFile.name.endsWith(".txt")) {
	println("Reading ${chooser.selectedFile.absolutePath}")
	def table = loadTable(chooser.selectedFile.absolutePath, "csv")
	data.curve1 = [null] * width
	int n = table.getRowCount()
	int m = data.curve1.size() - 100
	for (int i in 100..<(data.curve1.size()-1)) {
	  int from = (i-100) * n / m
	  int thru = ((i+1)-100) * n / m - 1
	  data.curve1[i] =
	    (from..thru).collect{notenum2y(table.getFloat(it, 0))}.sum() /
	    (from..thru).size()
	}
      }
    } else {
      println("File is not supported")
      return
    }
    data.updateCurve('all')
  }

  void inputName() {
    username = JOptionPane.showInputDialog(this, "Input your name") ?: username
    println("Set USERNAME to ${username}")
    makeLog("name")
  }
  
  void mousePressed() {
    nowDrawing = true
  }
  
  void mouseReleased() {
    nowDrawing = false
    if (isInside(mouseX, mouseY)) {
      println(x2measure(mouseX))
      println(CFG.NUM_OF_MEASURES)
      data.updateCurve(x2measure(mouseX) % CFG.NUM_OF_MEASURES)
    }
  }

  void keyReleased() {
    if (key == ' ') {
      if (isNowPlaying()) {
	stopMusic()
      } else {
	setTickPosition(0)
	getDataModel().setFirstMeasure(CFG.INITIAL_BLANK_MEASURES)
	playMusic()
      }
      //    } else if (key == 'l') {
      //      def json = new JsonSlurper()
      //      data.curve1 = json.parseText((new File("curve.json")).text)
      //      data.updateCurve()
      //      (0..<NUM_OF_MEASURES).each {
      //	model.updateMusicRepresentation(it)
      //      }
      //    } else if (key == 's') {
      //      def filename =
      //	"output_${(new Date()).toString().replace(" ", "_")}.mid"
      //	data.scc.toWrapper().toMIDIXML().writefileAsSMF(filename)
      //	println("saved as ${filename}.")
      //	data.scc.toWrapper().writefile("output.xml")
    } else if (key == 'b') {
      setNoteVisible(!isNoteVisible());
      println("Visible=${isVisible()}")
    } else if (key == 'u') {
      data.updateCurve('all')
    }
  }

//  double peyeX = 0, peyeY = 0
//  double meyeX = 0.5, meyeY = 0.5
//  int eyeCount = 1

  def eyeX = [] as LinkedList
  def eyeY = [] as LinkedList
  def n_eyesmooth = 10

  int height() {
    height
  }

  int width() {
    width
  }

  void sendEvent(int event) {
    if (event == TargetMover.ONSET) {
       mousePressed()
    } else if (event == TargetMover.OFFSET) {
       mouseReleased()
    }
  }

  void setTarget(double x, double y) {
    println("(${x}, ${y})")
/*      meyeX = (meyeX * eyeCount + x) / (eyeCount + 1)
      meyeY = (meyeY * eyeCount + y) / (eyeCount + 1)
    eyeCount++
    mouseX -= EYE_MOTION_SPEED * (x - meyeX)
    mouseY += EYE_MOTION_SPEED * (y - meyeY)
*/
   if (eyeX.size() > n_eyesmooth) {
     eyeX.removeAt(0)
     eyeY.removeAt(0)
   }
   eyeX.add(x)
   eyeY.add(y)
   def smoothX = eyeX.sum() / n_eyesmooth
   def smoothY = eyeY.sum() / n_eyesmooth

   smoothX = x
   smoothY = y

    if (smoothX < 0) smoothX = 0
    if (smoothX > width) smoothX = width
    if (smoothY < 0) smoothY = 0
    if (smoothY > height) smoothY = height


/*
   if (Math.abs(smoothX - peyeX) > 20) {
     if (smoothX > peyeX) {
       smoothX = peyeX + 20
     } else {
       smoothX = peyeX - 20
     }
   }
   if (Math.abs(smoothY - peyeY) > 20) {
     if (smoothY > peyeY) {
       smoothY = peyeY + 20
     } else {
       smoothY = peyeY - 20
     }
   }
   */
     mouseX = smoothX
     mouseY = smoothY
   
////    mouseX -= 0.5 * (x - peyeX) * width
////    mouseY += 0.5 * (y - peyeY) * height
//    peyeX = mouseX
//    peyeY = mouseY
  }
}
def uri = getClass().getClassLoader().getResource("config.txt").toURI()
JamSketch.CFG = evaluate(new File(uri))
JamSketch.start("JamSketch")
