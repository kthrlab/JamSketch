import java.util.concurrent.*

class ExpressionGenerator {
  def featext
  def pfmrend
  def executor
  def futures

  ExpressionGenerator() {
    featext = new FeatureExtractor()
    pfmrend = new PerformanceRenderer()
    executor = Executors.newScheduledThreadPool(4)
    futures = [:]
  }

  def start(part, chordprog, beatsPerMeasure) {
    featext.start(part, chordprog, beatsPerMeasure)
    pfmrend.start()
  }

  def synchronized execute(fromTick, thruTick, division) {
    def runnable = new MyRunnable(fromTick, thruTick, division)
    if (futures[[fromTick,thruTick]] != null)
      futures[[fromTick,thruTick]].cancel(false)
    def future = executor.schedule(runnable, 10, TimeUnit.MILLISECONDS)
    futures[[fromTick,thruTick]] = future
  }
  
  class MyRunnable implements Runnable {
    def fromTick, thruTick, division
    
    MyRunnable(fromTick, thruTick, division) {
      this.fromTick = fromTick
      this.thruTick = thruTick
      this.division = division
    }

    void run() {
      long t1 = System.nanoTime()
      //def fromTick = 0
      //def thruTick = BEATS_PER_MEASURE * DIVISION * NUM_OF_MEASURES
      def (notelist, features) =
	featext.extractFeatureMapSeq(fromTick, thruTick, division)
      if (features != null)
        pfmrend.applyModels(notelist, features, featext.ATTR_NOMIAL)
      System.err.println("Time: " + ((System.nanoTime() - t1) / 1000 / 1000) + "[ms]")
    }
  }
}
