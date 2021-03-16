import java.util.concurrent.*;
import java.util.Arrays;
import java.util.HashMap;


public class CornerSeeker extends RecursiveAction {
// startCoordinate should be the coordinate of this CornerSeeker at the next lowest dimension.
// In other words, CornerSeeker starts at {startCoordinate},0 with the trailing zero being the position in currentLevel, which will be incremented.
    int[] startCoordinate;
// stopCoordinate is the last coordinate in this dimension unless divideAndConquer() determines that this dimension should be split, in
// which case stopCoordinate is the end of this CornerSeeker's share of the work.
    int[] stopCoordinate;
// remember that DownSampler.depth is a count, so starts at one, while currentLevel is an index, so starts at zero.
    int currentLevel;
// Represents an estimate of corners are implicit in every corner of the current dimension. In other words, if a point on this dimension were to be the startCoordinate of
// another CornerSeeker, how much work would be done by that CornerSeeker and the threads it forks off?
    int workOneDimensionUp;
    boolean allowSubDivideThisLevel = false;
    int worthwhileForkStandard = 32;

    public CornerSeeker (int [] startAtCoord, int level, boolean isSibling) {
        startCoordinate = Arrays.copyOf(startAtCoord, startAtCoord.length);
        stopCoordinate = Arrays.copyOf(startCoordinate, startCoordinate.length);
        stopCoordinate[level] = DownSampler.dimensions[level];
        currentLevel = level;
        allowSubDivideThisLevel = !isSibling;
        EstimateWorkAbove();
    }

    public CornerSeeker (int [] startAtCoord, int level, boolean isSibling, int[] endAtCoord) {
        startCoordinate = Arrays.copyOf(startAtCoord, startAtCoord.length);;
        stopCoordinate = endAtCoord;
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
// This is the decision to fork or to do some higher-dimension work on this thread:            
                if (workOneDimensionUp > worthwhileForkStandard && i < extent - DownSampler.samplingFactor && DownSampler.staySynchronous == false) {                    
                    // System.out.println("Deploying Solver to " + Arrays.toString(childCoordinate) + " at depth " + (dimension + 1));
                    CornerSeeker childSeeker = new CornerSeeker (childCoordinate, currentLevel + 1, false);
                    ForkJoinPool.commonPool().execute(childSeeker);
                    DownSampler.SeekerThreadsStarted += 1;
                }
                else {
                    // System.out.println("Doing it myself: " + Arrays.toString(childCoordinate) + " at depth " + (dimension + 1));
                    State backUp = new State(startCoordinate, stopCoordinate, currentLevel, allowSubDivideThisLevel);
                    startCoordinate = childCoordinate;
                    currentLevel = currentLevel + 1;
                    stopCoordinate = Arrays.copyOf(startCoordinate, startCoordinate.length);
                    stopCoordinate[currentLevel] = DownSampler.dimensions[currentLevel];
                    allowSubDivideThisLevel = true;
                    compute();
                    revertToState(backUp);
                }       
            }
        }
        else {
// If the program gets here that means that parentCoordinate has all the digits of a full coordinate, so it is truly the corner of a cell, and CellModer activity should start from here.
            // System.out.println("Deploying CellModer to " + Arrays.toString(startCoordinate) + " at depth " + 0);
            for (int i = startCoordinate[currentLevel]; i < stopCoordinate[currentLevel]; i += DownSampler.samplingFactor){
                HashMap<Integer,Integer> tally = new HashMap<Integer,Integer>();
                int [] targetCornerCoord = Arrays.copyOf(startCoordinate, startCoordinate.length);
                targetCornerCoord[currentLevel] = i;
                CellModer moder = new CellModer(targetCornerCoord, 0);
                tally = moder.compute();
                Integer[] keys = tally.keySet().toArray(new Integer[tally.size()]);
                int mostPopulous = 1;
                int topPopularity = 0;
                for (int j = 0; j < keys.length; ++j) {
                    int numInQuestion = keys[j];
                    int occurances = tally.get(numInQuestion);
                    if (occurances > topPopularity) {
                        mostPopulous = numInQuestion;
                        topPopularity = occurances;
                    }
                }
                int [] scaledCoordinate = Arrays.copyOf(targetCornerCoord, targetCornerCoord.length);
                for (int j = 0; j < scaledCoordinate.length; ++j) {
                    scaledCoordinate[j] /= DownSampler.samplingFactor;
                }
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
// This looks odd, but its purpose is to limit the number of threads started when the extent of the dimension is less than (cores * worthwhileForkStandard)
            if (evenShare < worthwhileForkStandard) {
                cores = (extent - (extent % worthwhileForkStandard)) / worthwhileForkStandard;
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
// A noteable flaw here is that this function does not guess whether forked threads would do any CellModer work.
        if (currentLevel < DownSampler.depth - 1) {
            workOneDimensionUp = DownSampler.cornersByTheSlice[currentLevel + 1];
        }
        else {
            workOneDimensionUp = 1;
        }
    }

    void revertToState (State desiredState) {
        startCoordinate = desiredState.startCoord;
        stopCoordinate = desiredState.stopCoord;
        currentLevel = desiredState.dimension;
        allowSubDivideThisLevel = desiredState.allowForking;
    }
    
}
