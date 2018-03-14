public interface TargetMover {
    public int height();
    public int width();
    public void setTarget(double x, double y);
    public void sendEvent(int event);
    public static final int ONSET = 1;
    public static final int OFFSET = -1;
    public static final int NO_EVENT = 0;
}
