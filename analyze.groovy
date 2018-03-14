import controlP5.*

class JamSketchAnalyzer extends JamSketch {
  void setup() {
    size(1200, 700)
    def p5ctrl = new ControlP5(this)
    p5ctrl.addButton("loadCurve").
    setLabel("Load").setPosition(20, 645).setSize(120, 40)
    CFG.EXPRESSION = false
    //    Config.GA_TIME = 300000
    //    Config.GA_POPUL_SIZE = 200000
    //    Config.GA_INIT = "random"
    initData()
  }

  void keyReleased() {
    super.keyReleased()
    if (key == 's') {
      makeLog("melody")
    }
  }
}

JamSketchAnalyzer.start("JamSketchAnalyzer")

