import java.io.*;

public interface MotionController {
    void setTarget(TargetMover tm);
    void init() throws IOException;
    void start();
}
