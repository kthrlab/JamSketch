class MyGACalc extends GACalculator<Integer,Double> {

  MyGACalc(tree, model) {
    this.tree = tree
    this.model = model
  }
   
    
    @CompileStatic
    StoppingCondition getStoppingCondition() {
      //new FixedGenerationCount(5)
      new FixedElapsedTime(500, TimeUnit.MILLISECONDS);
    }
    
    @CompileStatic
    void populationUpdated(Population p) {
      println(p.getFittestChromosome().getFitness());
    }
    
    @CompileStatic
    List<Integer> createInitial(int size) {
      //createInitialRandom(size)
      createInitialFromTree(size)
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
	chooseNoteFromTree(model.melodytree, seq, size)
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
      double entrdiff = -(entr-model.entropy_mean) * (entr-model.entropy_mean)
      double chord = calcLogChordBeatUnigramLikelihood(s, e)
      3 * mse + 2 * lik + delta + 3 * chord + 10 * entrdiff
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
	lik += Math.log(model.bigram[s[i]][s[i+1]])
      }
      lik
    }

    @CompileStatic
    double calcLogDeltaTransLikelihood(List<Integer> s) {
      double lik = 0.0
      int length = s.size() - 2
      for (int i = 0; i < length; i++) {
	lik += Math.log(model.delta_bigram[s[i+1]-s[i]][s[i+2]-s[i+1]])
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
	lik += Math.log(model.chord_beat_dur_unigram[key][s[i]] + 0.001)
      }
      lik
    }

    @CompileStatic
    double calcEntropy(List<Integer> s, int maxvalue) {
      int[] freq = [0] * maxvalue
      int sum = 0
      for (int i = 0; i < s.size(); i++) {
	freq[s[i]] += 1
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


