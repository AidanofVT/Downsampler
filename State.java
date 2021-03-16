import java.util.Arrays;

// This class is just a light structure to allow CellModers and CornerSeekers to save their states so they can use their own compute() methods and then revert, rather than create
// a whole new instance of their class. 
public class State {

    public int[] startCoord;
    public int[] stopCoord;
    public int dimension;
    public boolean allowForking;

    public State (int [] startAt, int level) {
        startCoord = Arrays.copyOf(startAt, startAt.length);
        dimension = level;
    }

    public State (int [] startAt, int[] stopAt, int dimension, boolean allowChildThreads) {
        this(startAt, dimension);
        stopCoord = stopAt;
        allowForking = allowChildThreads;
    }
    
}
