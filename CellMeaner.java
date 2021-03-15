import java.util.*;
import java.util.concurrent.*;

public class CellMeaner extends RecursiveTask<HashMap<Integer,Integer>> {

// startCorner should be the coordinate of this CellMeaner at the next lowest dimension.
// In other words, CellMeaner starts at {startCoordinate},0 with the trailing zero being the position in currentLevel, which will be incremented.
    int[] startCorner;
    int currentLevel;
// Represents an estimate of the work implicit in every point of the current dimension. In other words, if a point on this dimension were to be the startCorner of
// a CellMeaner, how much work would be done by that CellMeaner and the threads it forks off?
    int workOneDimensionUp;

    public CellMeaner (int [] startAtCoord, int level) {
        startCorner = startAtCoord;
// remember that DownSampler.depth is a count, so starts at one, while dimension is an index, so starts at zero.
        currentLevel = level;
        SetWorkOneLevelAbove();
    }

    @Override
    protected HashMap<Integer,Integer> compute () {    
        HashMap<Integer,Integer> tally = new HashMap<Integer,Integer>();
// This logic allows the program to downsample to resolutions that would otherwise render some dimensions zero locations long:
        int extent = DownSampler.dimensions[currentLevel];
        int limitedExtent = DownSampler.samplingFactor;
        if (limitedExtent > extent) {
            limitedExtent = extent;
        }
// If we're not yet at the highest dimension, we'll create child CellMeaners to survey them for us (or use their compute() methods with the current thread)...     
        if (currentLevel + 1 < DownSampler.depth) {  
            for (int i = 0; i < limitedExtent; ++i) {
                int[] childCoordinate = Arrays.copyOf(startCorner, startCorner.length);
                childCoordinate[currentLevel] += i;
                CellMeaner childMeaner = new CellMeaner(childCoordinate, currentLevel + 1);
                HashMap<Integer,Integer> sliceResults = new HashMap<Integer, Integer>();
                if (workOneDimensionUp > 1024 && i != limitedExtent - 1 && DownSampler.staySynchronous == false) {
                    // System.out.println("Deploying CellMeaner to " + Arrays.toString(childCoordinate) + " at depth " + (dimension + 1));
                    Future <HashMap<Integer,Integer>> floatFromChild = ForkJoinPool.commonPool().submit(childMeaner);
                    DownSampler.MeanerThreadsStarted += 1;
// If this is the wrong way to handle exceptions, I appologize; I just tried stuff until the warnings went away. Also I'm new to lamdas.
                    try {                        
                        sliceResults = floatFromChild.get();
                    }
                    catch (InterruptedException e) {}
                    catch (ExecutionException e) {}
                }
                else {
                    // System.out.println("Doing it myself: " + Arrays.toString(childCoordinate) + " at depth " + (dimension + 1));
                    sliceResults = childMeaner.compute();                    
                }
                incorporateResults(sliceResults, tally);
            }
        }
// ...otherwise, this is an instance that has to do some counting!
        else {
            for (int i = 0; i < limitedExtent; ++i) {
                int [] finalCoordinate = Arrays.copyOf(startCorner, startCorner.length);
                finalCoordinate[currentLevel] += i;
                int intHere = DownSampler.ReferTo(DownSampler.input, finalCoordinate);
                if (tally.containsKey(intHere)){
                    tally.put(intHere, tally.get(intHere) + 1);
                }
                else {
                    tally.put(intHere, 1);
                }
            }
        }
        return tally;
    }

    void SetWorkOneLevelAbove () {
// If we're not at the highest dimension, we'll refer to DownSampler's table of location-counts.
        if (currentLevel + 1 < DownSampler.depth) {
            workOneDimensionUp = DownSampler.locationsByTheSlice[currentLevel + 1];
        }
// Otherwise, it's going to be the samplingFactor or the extent of this dimension, whichever is higher.
        else {
            int extent = DownSampler.dimensions[currentLevel];
            int limitedExtent = DownSampler.samplingFactor;
            if (limitedExtent > extent) {
                limitedExtent = extent;
            } 
            workOneDimensionUp = limitedExtent;
        }
    }

    void incorporateResults (HashMap<Integer,Integer> input, HashMap<Integer,Integer> output) {
        for (Map.Entry<Integer, Integer> pair: input.entrySet()) {
            int key = pair.getKey();
            int value = pair.getValue();
            if (output.containsKey(key)) {
                output.put(key, value + output.get(key));
            } 
            else {
                output.put(key, value);
            }
        }
    }

}
