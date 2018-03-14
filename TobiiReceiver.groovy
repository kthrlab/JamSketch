import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

class TobiiReceiver extends Thread implements MotionController {
  TargetMover tm;
  Socket cl_socket;
  
  TobiiReceiver() {
  }

  void setTarget(TargetMover tm) {
    this.tm = tm
  }

  void init() {
    println("Waiting Tobii EyeTracker Sender...")
    String separator = System.getProperty("line.separator");
    ServerSocket  sv_socket = new ServerSocket(10001);
    cl_socket = sv_socket.accept();
    System.out.println("accept.");
    sv_socket.close();
  }

  void run() {
    if (tm ==  null)
      throw new IllegalStateException("setTarget() must be called")
    def input = new BufferedReader(new InputStreamReader(cl_socket.getInputStream()))
    def line
    while((line = input.readLine()) != null){  // ・・・(2)
    	System.out.println("line = " + line);
	def match = (line =~ /Gaze Data: \(([0-9\.]+), ([0-9\.]+)\).*/)
	if (match.size() > 0) {
	  println match[0][1]
	  println match[0][2]
	  tm.setTarget(match[0][1] as double, match[0][2] as double)
	}
//        System.currentThread().sleep(10) 
    }
  }    
}
