import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.ExecutionException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;
import java.lang.Math;

public class CornerSeeker extends RecursiveAction {
// parentCoordinate should be the coordinate of this CornerSeeker at the next lowest dimension.
// In other words, CornerSeeker starts at {parentCoordinate},0 with the trailing zero being the position in currentLevel, which will be incremented.
    int[] parentCoordinate;
    int currentLevel;
// Represents an estimate of the work implicit in every corner of the current dimension. In other words, if a point on this dimension were to be the parentCoordinate of
// a CornerSeeker, how much work would be done by that CornerSeeker and the threads it forks off?
    int workOneDimensionUp;

    public CornerSeeker (int [] startAtCoord, int level) {
        parentCoordinate = startAtCoord;
// remember that DownSampler.depth is a count, so starts at one, while dimension is an index, so starts at zero.
        currentLevel = level;
        EstimateWorkAbove();
    }

    @Override
    protected void compute () {
// Since currentLevel is zero-based, failing this IF statement means that currentLevel has EXCEEDED the topmost index of DownSampler.dimensions.
        if (currentLevel < DownSampler.depth) {
            int extent = DownSampler.dimensions[currentLevel];
            for (int i = 0; i < extent; i += DownSampler.samplingFactor) {
                int [] childCoordinate = Arrays.copyOf(parentCoordinate, parentCoordinate.length);
                childCoordinate[currentLevel] = i;
// This is the decision to fork or do some higher-dimension work on this thread:            
                if (workOneDimensionUp > 32 && i < extent - DownSampler.samplingFactor && DownSampler.staySynchronous == false) {                    
                    // System.out.println("Deploying Solver to " + Arrays.toString(childCoordinate) + " at depth " + (dimension + 1));
                    ForkJoinPool.commonPool().execute(new CornerSeeker (childCoordinate, currentLevel + 1));
                    DownSampler.SeekerThreadsStarted += 1;
                }
                else {
                    // System.out.println("Doing it myself: " + Arrays.toString(childCoordinate) + " at depth " + (dimension + 1));
// compute() can't have any parameters, so if we want to keep working on this thread, we need to store the current state of the instance for when the recursion completes.
// I'm assuming this is preferable to creating a new CornerSeeker, but I could be wrong.
                    int comeBackToDepth = currentLevel;
                    int[] comeBackToCoordinate = parentCoordinate;
                    currentLevel = currentLevel + 1;
                    parentCoordinate = childCoordinate;
                    EstimateWorkAbove();
                    compute();
                    currentLevel = comeBackToDepth;
                    parentCoordinate = comeBackToCoordinate;
                    EstimateWorkAbove();
                }       
            }
        }
        else {
// If the program gets here that means that parentCoordinate has all the digitsof a full coordinate, so it is truly the corner of a cell, and CellMeaner activity should start from here.
            // String pCoordEncoded = Arrays.toString(parentCoordinate);
            // System.out.println("Deploying CellMeaner to " + pCoordEncoded + " at depth " + 0);
            HashMap<Integer,Integer> returned = new HashMap<Integer,Integer>();
            CellMeaner meaner = new CellMeaner(parentCoordinate, 0);
// Another fork-or-not-to-fork decision. If this is the wrong way to handle exceptions, I appologize; I just tried stuff until the warnings went away.
            if (DownSampler.locationsByTheSlice[0] > 256 && DownSampler.staySynchronous == false){
                try {
                    Future <HashMap<Integer,Integer>> resultingMap = ForkJoinPool.commonPool().submit(meaner);
                    DownSampler.MeanerThreadsStarted += 1;
                    returned = resultingMap.get();
                    // System.out.println(pCoordEncoded + " is a corner and it's average value is " + result);
                }
                catch (InterruptedException e) {}
                catch (ExecutionException e) {}
            }
            else {
                returned = meaner.compute();
            }
            Integer[] keys = returned.keySet().toArray(new Integer[returned.size()]);
            int mostPopulous = 1;
            int topPopularity = 0;
            for (int i = 0; i < keys.length; ++i) {
                int numInQuestion = keys[i];
                int occurances = returned.get(numInQuestion);
                if (occurances > topPopularity) {
                    mostPopulous = numInQuestion;
                    topPopularity = occurances;
                }
            }
            int [] scaledCoordinate = Arrays.copyOf(parentCoordinate, parentCoordinate.length);
            for (int i = 0; i < scaledCoordinate.length; ++i) {
                scaledCoordinate[i] /= DownSampler.samplingFactor;
            }
            DownSampler.ReferTo(DownSampler.output, scaledCoordinate, mostPopulous);
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
