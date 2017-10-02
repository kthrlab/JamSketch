import weka.core.*
import groovy.transform.*
//import static Config.*
import static JamSketch.CFG

class PerformanceRenderer {
  def model = [:]

  def ATTR_NAMES =
    [dur: "duration_rat", energy: "energy_rat", onset: "onset_dev"]

  def start() {
    CFG.MODEL_FILENAMES.each {k, v ->
      def filename = ClassLoader.getSystemResource(v).toURI().getPath()
      def stream = new ObjectInputStream(new FileInputStream(filename))
      model[k] = stream.readObject()
      stream.close()
    }
  }
     
  def applyModels(notes, features, attrsNomial) {
    FastVector attrs = new FastVector()
    features[0].keySet().eachWithIndex { key, index ->
      def attr
      if (attrsNomial.containsKey(key)) {
	FastVector v = new FastVector()
	attrsNomial[key].each { v.addElement(it) }
	attr = new Attribute(key, v, index)
      } else {
	attr = new Attribute(key, index)
      }
      attrs.addElement(attr)
    }
    attrs.addElement(new Attribute(ATTR_NAMES.energy, attrs.size()))
    Instances insts = new Instances("PerformAction", attrs, features.size())
    insts.setClassIndex(attrs.size()-1)

    notes.eachWithIndex{ note, index ->
      def instance = new Instance(features[index].values().size() + 1)
      instance.setDataset(insts)
      features[index].eachWithIndex {key, value, i ->
	if (value instanceof Number) {
 	  instance.setValue(i, value as double)
	} else if (value instanceof String) {
	  instance.setValue(i, value)
	} else {
	  throw new IllegalStateException(key + ": " + value)
	}
      }
      instance.setMissing(attrs.size()-1)

      def dev = [:]
      ["energy", "dur", "onset"].each { pa ->
	dev[pa] = model[pa].classifyInstance(instance)
      }
      println dev
      note.setVelocity((int)(limit(dev.energy, CFG.LIMITS.energy) * 64))
      note.setOnset((long)(note.getAttributeLong("OriginalOnset") +
			   limit(dev.onset, CFG.LIMITS.onset) *
			   note.ticksPerBeat()))
      note.setOffset((long)(note.getAttributeLong("OriginalDuration") *
			    limit(dev.dur, CFG.LIMITS.dur) + note.onset()))
      if (index+1 < notes.size() && note.offset() >= notes[index+1].onset())
	note.setOffset(notes[index+1].onset()-1)
    }
  }

  @CompileStatic
  double limit(double value, List<Double> bounds) {
    if (value > bounds[1]) {
      value = bounds[1].doubleValue()
    }
    if (value < bounds[0]) {
      value = bounds[0].doubleValue()
    }
    value
  }
}
