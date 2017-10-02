import groovy.transform.*
import jp.crestmuse.cmx.inference.*
import jp.crestmuse.cmx.inference.models.*
import org.apache.commons.math3.genetics.*
import java.util.concurrent.*
import groovy.json.*
import jp.crestmuse.cmx.misc.*
//import static Config.*
import static JamSketch.CFG

class MelodyModel {

  class MelodyTree {
    int note,freq
    List<MelodyTree> next = []
    }
  
  def mr
  double[][] bigram
  double[][] delta_bigram
  Map<String,List<Double>> chord_beat_dur_unigram
  double entropy_mean
  MelodyTree melodytree
  
  int GA_TIME, GA_POPUL_SIZE
  String GA_INIT
  int BEATS_PER_MEASURE, DIVISION
  List<ChordSymbol2> chordprog
  
  MelodyModel(cmxcontrol, sccgenerator) {
    mr = cmxcontrol.createMusicRepresentation(CFG.NUM_OF_MEASURES, CFG.DIVISION)
    mr.addMusicLayerCont("curve")
    mr.addMusicLayer("melody", (0..11) as int[])
    def json = new JsonSlurper()
	def uri = ClassLoader.getSystemResource(CFG.MODEL_FILE).toURI()
    def model = json.parseText((new File(uri)).text)
    bigram = model.bigram
    delta_bigram = model.delta_bigram
    chord_beat_dur_unigram = model.chord_beat_dur_unigram
    entropy_mean = model.entropy.mean
    melodytree = makeMelodyTree(model.melodytree)
    def hmm = new HMMContWithGAImpl(new MyGACalc(),
				    (int)(CFG.GA_POPUL_SIZE / 2),
				    CFG.GA_POPUL_SIZE, (double)0.2,
				    new UniformCrossover(0.5), (double)0.8,
				    new BinaryMutation(), (double)0.2,
				    new TournamentSelection(10))
    def calc = new MostSimpleHMMContCalculator("curve", "melody", hmm, mr)
    calc.setCalcLength(CFG.CALC_LENGTH * CFG.DIVISION)
    calc.enableSeparateThread(true)
    mr.addMusicCalculator("curve", calc)
    mr.addMusicCalculator("melody", sccgenerator)

    GA_TIME = CFG.GA_TIME
    GA_POPUL_SIZE = CFG.GA_POPUL_SIZE
    GA_INIT = CFG.GA_INIT
    BEATS_PER_MEASURE = CFG.BEATS_PER_MEASURE
    DIVISION = CFG.DIVISION
    chordprog = CFG.chordprog
    
  }

  def makeMelodyTree(map) {
    if (map != null) {
      def node = new MelodyTree(note: map.note, freq: map.freq)
      map.next.each {
	node.next.add(makeMelodyTree(it))
      }
      node
    } else {
      null
    }
  }

  def updateMusicRepresentation(measure) {
    println measure
    def e = mr.getMusicElement("curve", measure + 1, -1)
    e.resumeUpdate()
    mr.reflectTies("curve", "melody")
    mr.reflectRests("curve", "melody")
  }

  class MyGACalc extends GACalculator<Integer,Double> {
    
    @CompileStatic
    StoppingCondition getStoppingCondition() {
      //new FixedGenerationCount(5)
      new FixedElapsedTime(GA_TIME, TimeUnit.MILLISECONDS);
    }
    
    @CompileStatic
    void populationUpdated(Population p, int gen, List<MusicElement> e) {
      Chromosome c = p.getFittestChromosome()
      println("Population,${e[0].measure()},${e[0].tick()},${gen}," +
      	      "${c.getFitness()},\"${c}\"")
      //      println(p.getFittestChromosome().getFitness());
    }
    
    @CompileStatic
    List<Integer> createInitial(int size) {
      if (GA_INIT.equalsIgnoreCase("random"))
	createInitialRandom(size)
      else if (GA_INIT.equalsIgnoreCase("tree"))
        createInitialFromTree(size)
      else
	throw new IllegalArgumentException("GA_INIT \"${GA_INIT}\" is not supported")
    }
    
    @CompileStatic
    List<Integer> createInitialRandom(int size) {
      List<Integer> seq = []
      for (int i in 0..<size) {
	seq.add((12 * Math.random()) as int)
      }
      seq
    }

    @CompileStatic
    List<Integer> createInitialFromTree(int size) {
      List<Integer> seq= []
      while (seq.size() < size) {
	chooseNoteFromTree(melodytree, seq, size)
      }
      seq
    }

    @CompileStatic
    void chooseNoteFromTree(MelodyTree tree, List<Integer> seq, int size) {
      if (seq.size() < size && tree != null && tree.next.size() > 0) {
	int rand = (Math.random() * tree.next.size()) as int
	seq.add(tree.next[rand].note)
	chooseNoteFromTree(tree.next[rand], seq, size)
      }
    }
    
    @CompileStatic
    double calcFitness(List<Integer> s, List<Double> o, List<MusicElement> e) {
      double mse = -calcRMSE(s, o)
      double lik = calcLogTransLikelihood(s)
      double delta = calcLogDeltaTransLikelihood(s)
      double entr = calcEntropy(s, 12)
      double entrdiff = -(entr-entropy_mean) * (entr-entropy_mean)
      double chord = calcLogChordBeatUnigramLikelihood(s, e)
      3 * mse + 2 * lik + 1 * delta + 3 * chord + 20 * entrdiff
      //      3 * mse + 2 * lik + delta + 3 * chord + 10 * entrdiff
    }

    @CompileStatic
    double calcRMSE(List<Integer> s, List<Double> o) {
      double e = 0
      int length = s.size()
      for (int i = 0; i < length; i++) {
	double e1 = (o[i] % 12) - (s[i] % 12)
	e += e1 * e1;
      }
      Math.sqrt(e)
    }

    @CompileStatic
    double calcLogTransLikelihood(List<Integer> s) {
      double lik = 0.0
      int length = s.size() - 1;
      for (int i = 0; i < length; i++) {
	lik += Math.log(bigram[s[i].intValue()][s[i+1].intValue()])
      }
      lik
    }

    @CompileStatic
    double calcLogDeltaTransLikelihood(List<Integer> s) {
      double lik = 0.0
      int length = s.size() - 2
      for (int i = 0; i < length; i++) {
	lik += Math.log(delta_bigram[s[i+1]-s[i]][s[i+2]-s[i+1]])
      }
      lik
    }

    @CompileStatic
    double calcLogChordBeatUnigramLikelihood(List<Integer> s,
					     List<MusicElement> e) {
      double lik = 0.0
      int length = s.size() - 1
      for (int i = 0; i < length; i++) {
	String chord = chordprog[i/DIVISION as int].toString()
	int div4 = DIVISION / BEATS_PER_MEASURE as int
	String beat = (e[i].tick() == 0 ? "head" :
		       (e[i].tick() % div4 == 0 ? "on" : "off"))
	String dur = (e[i].duration() >= div4 ? "long" : "short")
	String key = chord + "_" + beat + "_" + dur
	lik += Math.log(chord_beat_dur_unigram[key][s[i]] + 0.001)
      }
      lik
    }

    @CompileStatic
    double calcEntropy(List<Integer> s, int maxvalue) {
      int[] freq = [0] * maxvalue
      int sum = 0
      for (int i = 0; i < s.size(); i++) {
	freq[s[i].intValue()] += 1
        sum++
	  }
      double entropy = 0.0
      for (int i = 0; i < maxvalue; i++) {
	if (freq[i] > 0) {
	  double p = (double)freq[i] / sum
	  entropy += -Math.log(p) * p / Math.log(2)
	}
      }
      entropy
    }
  }

}


