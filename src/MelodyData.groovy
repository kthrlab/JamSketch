import jp.crestmuse.cmx.inference.*
import jp.crestmuse.cmx.filewrappers.*
import static jp.crestmuse.cmx.misc.ChordSymbol2.*
				     //import static Config.*
import static JamSketch.CFG

class MelodyData {
  def curve1 // y coord per pixel
  def curve2 // note number per beat
  def RHYTHMS = [[1, 0, 0, 0, 0, 0], [1, 0, 0, 1, 0, 0],
		 [1, 0, 0, 1, 0, 1], [1, 0, 1, 1, 0, 1],
		 [1, 0, 1, 1, 1, 1], [1, 1, 1, 1, 1, 1]];
  // The size of each list has to be DIVISION
  def scc
  def target_part
  def model
  def expgen
  def width
  def pianoroll

  MelodyData(filename, width, cmxcontrol, pianoroll) {
    this.width = width
    this.pianoroll = pianoroll
    curve1 = [null] * width
    scc = cmxcontrol.readSMFAsSCC(filename)
    scc.repeat(CFG.INITIAL_BLANK_MEASURES * CFG.BEATS_PER_MEASURE *
	       scc.division,
	       (CFG.INITIAL_BLANK_MEASURES + CFG.NUM_OF_MEASURES) *
	       CFG.BEATS_PER_MEASURE * scc.division, CFG.REPEAT_TIMES - 1)
    target_part = scc.getFirstPartWithChannel(1)
    model = new MelodyModel(cmxcontrol, new SCCGenerator())
    if (CFG.EXPRESSION) {
      expgen = new ExpressionGenerator()
      expgen.start(scc.getFirstPartWithChannel(1),
     		 getFullChordProgression(), CFG.BEATS_PER_MEASURE)
    }
  }

  def resetCurve() {
    curve1 = [null] * width
    updateCurve('all')
  }
    
  def updateCurve(measure) {
    def curve3 = curve1.subList(100, curve1.size()).collect {
      it == null ? null : pianoroll.y2notenum(it)
    }
    curve2 = shortenCurve(curve3)
    setDataToMusicRepresentation(curve2, model.mr)
    if (measure == 'all') {
      (0..<CFG.NUM_OF_MEASURES).each {
	model.updateMusicRepresentation(it)
      }
    } else {
      model.updateMusicRepresentation(measure as int)
    }
  }

  def setDataToMusicRepresentation(curve, mr) {
    def rhythm = decideRhythm(curve2, 0.25)
    (0..<(CFG.NUM_OF_MEASURES * CFG.DIVISION)).each { i ->
      def e = mr.getMusicElement("curve", i / CFG.DIVISION as int,
				 i % CFG.DIVISION as int)
      e.suspendUpdate()
      if (curve[i] == null) {
	e.setRest(true)
      } else if (rhythm[i] == 0) {
	e.setRest(false)
	e.setTiedFromPrevious(true)
      } else {
	e.setRest(false)
	e.setTiedFromPrevious(false)
	e.setEvidence(curve[i])
      }
    }
  }

  def shortenCurve(curve) {
    def newcurve = []
    (0..<(CFG.NUM_OF_MEASURES * CFG.DIVISION)).each { i ->
      int from = i / (CFG.NUM_OF_MEASURES * CFG.DIVISION) * curve.size()
      int thru = (i+1) / (CFG.NUM_OF_MEASURES * CFG.DIVISION) * curve.size()
      def curve1 = curve[from..<thru].findAll{it != null}
      if (curve1.size() > 0)
	newcurve.add(curve1.sum() / curve1.size())
        else
	newcurve.add(null)
    }
    newcurve
  }
    
  def decideRhythm(curve, th) {
    def r1 = [1]
    (0..<(curve.size()-1)).each { i ->
      if (curve[i+1] == null) {
	r1.add(0)
      } else if (curve[i] == null && curve[i+1] != null) {
	r1.add(1)
      } else if (Math.abs(curve[i+1] - curve[i]) >= th) {
	r1.add(1)
      } else {
	r1.add(0)
      }
    }
    def r2 = []
    (0..<(CFG.NUM_OF_MEASURES*2)).each { i ->
      def r1sub = r1[(CFG.DIVISION/2*i as int)..<(CFG.DIVISION/2*(i+1) as int)]
      def diffs = []
      diffs = RHYTHMS.collect { r ->
	abs(sub(r, r1sub)).sum()
      }
      def index = argmin(diffs)
      r2.add(RHYTHMS[index])
    }
    r2.sum()
  }

  def sub(x, y) {
    def z = []
    x.indices.each{ i ->
      z.add(x[i] - y[i])
    }
    z
  }
    
  def abs(x) {
    x.collect{ Math.abs(it) }
  }
    
  def argmin(x) {
    def minvalue = x[0]
    def argmin = 0
    x.indices.each { i ->
      if (x[i] < minvalue) {
	minvalue = x[i]
	argmin = i
      }
    }
    argmin
  }

  def getFullChordProgression() {
    [NON_CHORD] * CFG.INITIAL_BLANK_MEASURES + CFG.chordprog * CFG.REPEAT_TIMES
  }   

  class SCCGenerator implements MusicCalculator {
    void updated(int measure, int tick, String layer,
		 MusicRepresentation mr) {
      def sccdiv = scc.getDivision()
      def firstMeasure = pianoroll.getDataModel().getFirstMeasure()
      def e = mr.getMusicElement(layer, measure, tick)
      if (!e.rest() && !e.tiedFromPrevious()) {
	int notenum = getNoteNum(e.getMostLikely(),
				 curve2[measure * CFG.DIVISION + tick])
	int duration = e.duration() * sccdiv /
	(CFG.DIVISION / CFG.BEATS_PER_MEASURE)
	int onset = ((firstMeasure + measure) * CFG.DIVISION + tick) * sccdiv /
	(CFG.DIVISION / CFG.BEATS_PER_MEASURE)
	if (onset > pianoroll.getTickPosition()) {
	  def oldnotes =
	    SCCUtils.getNotesBetween(target_part, onset,
				     onset+duration, sccdiv, true, true)
	    //data.target_part.getNotesBetween2(onset, onset+duration)
	  target_part.remove(oldnotes)
	  target_part.addNoteElement(onset, onset+duration, notenum,
					  100, 100)
	}
      }

      if (CFG.EXPRESSION) {
	def fromTick = (firstMeasure + measure) * CFG.BEATS_PER_MEASURE *
	  CFG.DIVISION
	def thruTick = fromTick + CFG.BEATS_PER_MEASURE * CFG.DIVISION
	expgen.execute(fromTick, thruTick, CFG.DIVISION)
      }
    }

    def getNoteNum(notename, neighbor) {
      def best = 0
      for (int i in 0..11) {
	def notenum = i * 12 + notename
	if (Math.abs(notenum - neighbor) < Math.abs(best - neighbor))
	  best = notenum
      }
      best
    }
  }
}
