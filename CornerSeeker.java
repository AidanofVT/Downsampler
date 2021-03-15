import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.ExecutionException;
import java.lang.System.Logger.Level;
import java.util.Arrays;
import java.util.HashMap;


public class CornerSeeker extends RecursiveAction {
// startCoordinate should be the coordinate of this CornerSeeker at the next lowest dimension.
// In other words, CornerSeeker starts at {startCoordinate},0 with the trailing zero being the position in currentLevel, which will be incremented.
    int[] startCoordinate;
    int[] stopCoordinate;
    int currentLevel;
// Represents an estimate of the work implicit in every corner of the current dimension. In other words, if a point on this dimension were to be the startCoordinate of
// a CornerSeeker, how much work would be done by that CornerSeeker and the threads it forks off?
    int workOneDimensionUp;
    boolean allowSubDivideThisLevel = false;
    int forkWorthWhileStandard = 32;

    public CornerSeeker (int [] startAtCoord, int level, boolean isSibling) {
        startCoordinate = Arrays.copyOf(startAtCoord, startAtCoord.length);
        stopCoordinate = Arrays.copyOf(startCoordinate, startCoordinate.length);
        stopCoordinate[level] = DownSampler.dimensions[level];
// remember that DownSampler.depth is a count, so starts at one, while dimension is an index, so starts at zero.
        currentLevel = level;
        allowSubDivideThisLevel = !isSibling;
        EstimateWorkAbove();
    }

    public CornerSeeker (int [] startAtCoord, int level, boolean isSibling, int[] endAtCoord) {
        startCoordinate = Arrays.copyOf(startAtCoord, startAtCoord.length);;
        stopCoordinate = endAtCoord;
// remember that DownSampler.depth is a count, so starts at one, while dimension is an index, so starts at zero.
        currentLevel = level;
        allowSubDivideThisLevel = !isSibling;
        EstimateWorkAbove();
    }

    @Override
    protected void compute () {
        if (allowSubDivideThisLevel == true) {
            divideAndConquer();
        }
        if (currentLevel < DownSampler.depth - 1) {
            int extent = DownSampler.dimensions[currentLevel];
            for (int i = 0; i < extent; i += DownSampler.samplingFactor) {
                int [] childCoordinate = Arrays.copyOf(startCoordinate, startCoordinate.length);
                childCoordinate[currentLevel] = i;
                CornerSeeker childSeeker = new CornerSeeker (childCoordinate, currentLevel + 1, false);
// This is the decision to fork or do some higher-dimension work on this thread:            
                if (workOneDimensionUp > forkWorthWhileStandard && i < extent - DownSampler.samplingFactor && DownSampler.staySynchronous == false) {                    
                    // System.out.println("Deploying Solver to " + Arrays.toString(childCoordinate) + " at depth " + (dimension + 1));
                    ForkJoinPool.commonPool().execute(childSeeker);
                    DownSampler.SeekerThreadsStarted += 1;
                }
                else {
                    // System.out.println("Doing it myself: " + Arrays.toString(childCoordinate) + " at depth " + (dimension + 1));
                    childSeeker.compute();
                }       
            }
        }
        else {
// If the program gets here that means that parentCoordinate has all the digitsof a full coordinate, so it is truly the corner of a cell, and CellMeaner activity should start from here.
            // String pCoordEncoded = Arrays.toString(parentCoordinate);
            // System.out.println("Deploying CellMeaner to " + pCoordEncoded + " at depth " + 0);
            for (int i = startCoordinate[currentLevel]; i < stopCoordinate[currentLevel]; i += DownSampler.samplingFactor){
                HashMap<Integer,Integer> returned = new HashMap<Integer,Integer>();
                int [] targetCornerCoord = Arrays.copyOf(startCoordinate, startCoordinate.length);
                targetCornerCoord[currentLevel] = i;
                CellMeaner meaner = new CellMeaner(targetCornerCoord, 0);
                returned = meaner.compute();
                Integer[] keys = returned.keySet().toArray(new Integer[returned.size()]);
                int mostPopulous = 1;
                int topPopularity = 0;
                for (int j = 0; j < keys.length; ++j) {
                    int numInQuestion = keys[j];
                    int occurances = returned.get(numInQuestion);
                    if (occurances > topPopularity) {
                        mostPopulous = numInQuestion;
                        topPopularity = occurances;
                    }
                }
                int [] scaledCoordinate = Arrays.copyOf(targetCornerCoord, targetCornerCoord.length);
                for (int j = 0; j < scaledCoordinate.length; ++j) {
                    scaledCoordinate[j] /= DownSampler.samplingFactor;
                }
                // System.out.println("Refering to " + scaledCoordinate[0]);
                DownSampler.ReferTo(DownSampler.output, scaledCoordinate, mostPopulous);
            }
        }
    }

    void divideAndConquer () {
        if (DownSampler.staySynchronous == false) {
            int extent = DownSampler.dimensions[currentLevel];
            allowSubDivideThisLevel = false;
            int cores = ForkJoinPool.getCommonPoolParallelism();
            int evenShare = extent / cores;
// This looks odd, but it's purpose is to limit the number of cores used when the extent of the dimension is less than (cores * forkWorthwhileStandard)
            if (evenShare < forkWorthWhileStandard) {
                cores = (extent - (extent % forkWorthWhileStandard)) / forkWorthWhileStandard;
            }
            int responsibility = evenShare - (evenShare % DownSampler.samplingFactor);
            for (int i = 0; i < cores - 1; ++i) {
                int[] sisterStartCoord = Arrays.copyOf(startCoordinate, startCoordinate.length);
                sisterStartCoord[currentLevel] = i * responsibility;
                int[] sisterStopCoord = Arrays.copyOf(startCoordinate, startCoordinate.length);
                sisterStopCoord[currentLevel] = (i + 1) * responsibility;
                ForkJoinPool.commonPool().execute(new CornerSeeker(sisterStartCoord, currentLevel, true, sisterStopCoord));
                ++DownSampler.SeekerThreadsStarted;
            }
            startCoordinate[currentLevel] = responsibility * cores;
        }
    }

    void EstimateWorkAbove () {
// A noteable flaw here is that this function does not estimate whether CellMeaner work would be done by forked threads or not, which matters for deciding whether to fork.
        if (currentLevel < DownSampler.depth - 1) {
            workOneDimensionUp = DownSampler.cornersByTheSlice[currentLevel + 1];
        }
        else {
            workOneDimensionUp = 1;
        }
    }
    
}
