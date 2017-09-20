@GrabConfig(systemClassLoader=true)
@Grab("org.xerial:sqlite-jdbc:3.7.2")
import groovy.sql.*
import jp.crestmuse.cmx.misc.*
import jp.crestmuse.cmx.math.*
import static jp.crestmuse.cmx.misc.NoteSymbol.*
import static jp.crestmuse.cmx.misc.ChordSymbol.Mode.*
import groovy.json.*

def dbfile = new File("./wjazzd.db")
def url = "jdbc:sqlite:${dbfile.name}"
def driver = "org.sqlite.JDBC"
def gsql = Sql.newInstance(url, driver)

def solo_info = [:]
def first_id = [:]
def melody = [:]
def chords = [:]

def model = [:]

gsql.eachRow("SELECT melid,key FROM solo_info") {
  try {
    if (it.key.contains("blues")) {
      solo_info[it.melid] = [melid: it.melid, key: it.key]
    }
  } catch (IllegalArgumentException e) {
    // do nothing
  }
}

gsql.eachRow("SELECT eventid,melid FROM melody") {
  if (!first_id.containsKey(it.melid))
    first_id[it.melid] = it.eventid
}

gsql.eachRow("SELECT melid,type,start,end,value FROM sections") {
  if (!chords.containsKey(it.melid)) {
    chords[it.melid] = []
  }
  if (it.type == "CHORD" && it.value != "NC") {
    chords[it.melid].add([melid: it.melid, start: it.start, end: it.end,
			  chord: ChordSymbol2.parse(it.value)])
  }
}

gsql.eachRow("SELECT eventid,melid,pitch,duration,bar,beat,tatum,beatdur FROM melody") {
  if (solo_info.containsKey(it.melid)) {
    if (!melody.containsKey(it.melid)) {
      melody[it.melid] = []
    }
    def eventid = it.eventid - first_id[it.melid]
    def chord = null
    chords[it.melid].find { c ->
      if (c.start <= eventid && eventid <= c.end) {
	def beat = (it.tatum == 1 ?
	  (it.beat == 1 ? "head" : "on") : "off")
	def duration = it.duration / it.beatdur
	def duration01 = duration > 0.9 ? "long" : "short"
	melody[it.melid].add([pitch: it.pitch as int, chord: c.chord, 
			      bar: it.bar, beat: beat, dur: duration01])
	return true
      } else {
	return false
      }
    }
  }
}

solo_info.each { id, info ->
  keynum = KeySymbol.parse(info.key - "-blues").root().number()
  melody[id] = melody[id].collect{
    [pitch: (it.pitch - keynum) % 12,
     chord: it.chord.transpose(-keynum, false),
     bar: it.bar, beat: it.beat, dur: it.dur]
  }
}

/********* Bigram ***/

model.bigram = []
12.times { model.bigram.add([0] * 12) }
melody.each {id, notes ->
  for (int i in 0..<(notes.size()-1)) {
    model.bigram[notes[i].pitch][notes[i+1].pitch]++
  }
}
model.bigram.eachWithIndex { a, i ->
  if (a.sum() > 0)
    model.bigram[i] = a.collect{it / a.sum()}
}

/********* Delta Bigram ***/

model.delta_bigram = []
24.times { model.delta_bigram.add([0] * 24) }
melody.each {id, notes ->
  for (int i in 0..<(notes.size()-2)) {
    int d1 = notes[i+1].pitch - notes[i].pitch
    int d2 = notes[i+2].pitch - notes[i+1].pitch
    model.delta_bigram[d1][d2]++
      }
}
model.delta_bigram.eachWithIndex { a, i ->
  if (a.sum() > 0)
    model.delta_bigram[i] = a.collect{it / a.sum()}
}
    

/********* Chordwise, beatwise, durationwise unigram ***/

model.chord_beat_dur_unigram = [:]
melody.each {melid, mel ->
  mel.each { note ->
    def strchord = note.chord.root().toString() +
      (note.chord.mode() == MIN ? "m" : "")
      //def key = strchord + "_" + note.dur
    def key = strchord + "_" + note.beat + "_" + note.dur
    if (!model.chord_beat_dur_unigram.containsKey(key)) {
      model.chord_beat_dur_unigram[key] = [0] * 12
    }
    model.chord_beat_dur_unigram[key][note.pitch % 12]++
  }
}
model.chord_beat_dur_unigram.each { key, values ->
  model.chord_beat_dur_unigram[key] = values.collect{ it / values.sum() }
}

/******* Entropies ****/

def entropies = []
melody.each { meid, mel ->
  def bars = mel.collect{it.bar}.toUnique().findAll{it % 2 == 0}
  bars.each { bar ->
    def seq = mel.findAll{it.bar==bar || it.bar==bar+1}.collect{it.pitch}
    entropies.add(calcEntropy(seq))
  }
}
def entropies2 = MathUtils.createDoubleArray(entropies)
model.entropy = [:]
model.entropy.mean = Operations.mean(entropies2)
model.entropy.var = Operations.var(entropies2)


def melodytree = initMelodyTree(-1)
melody.each { melid, mel ->
  def bars = mel.collect{it.bar}.toUnique().findAll{it % 2 == 0}
  bars.each { bar ->
    def seq = mel.findAll{bar <= it.bar && it.bar < bar+8}.collect{it.pitch}
    makeMelodyTree(melodytree, seq)
  }
}
pruneMelodyTree(melodytree, 1/12, 1)
model.melodytree = melodytree

println(JsonOutput.toJson(model))

/****** Below, functions ***/

/****** calculate entropies *****/ 

def calcEntropy(seq) {
  def freq = [:]
  def sum = 0
  seq.each {
    if (!freq.containsKey(it))
      freq[it] = 0
    freq[it] = freq[it] + 1
    sum++
  }
  double entropy = 0.0
  seq.toUnique().each {
    double p = freq[it] / sum
    entropy += -Math.log(p) * p / Math.log(2)
  }
  entropy
}

/****** make a melody tree *****/

def initMelodyTree(note) {
  [note: note, freq: 1, next: [null] * 12]
}

def makeMelodyTree(tree, seq) {
  if (seq.size() > 0) {
    if (tree.next[seq.head()] == null) {
      tree.next[seq.head()] = initMelodyTree(seq.head())
    } else {
      tree.next[seq.head()].freq++
	}
    makeMelodyTree(tree.next[seq.head()], seq.tail())
  }
}

def pruneMelodyTree(tree, ratio, minfreq) {
  if (tree != null) {
    def freqsum = tree.next.collect{it != null ? it.freq : 0}.sum()
    for (int i in 0..<tree.next.size()) {
      def n = tree.next[i]
      if (n != null) {
	if (n.freq <= minfreq || n.freq / freqsum < ratio) {
	  tree.next[i] = null
	} else {
	  pruneMelodyTree(n, ratio, minfreq)
	}
      }
    }
    tree.next.removeAll{it==null}
  }
}
  
