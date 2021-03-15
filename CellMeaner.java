import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.RecursiveTask;

public class CellMeaner extends RecursiveTask<Float> {

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
    protected Float compute () {        
        Float average = 0f;
// This logic allows the program to downsample to resolutions that would otherwise render some dimensions zero locations long:
        int extent = DownSampler.dimensions[currentLevel];
        int limitedExtent = DownSampler.samplingFactor;
        if (limitedExtent > extent) {
            limitedExtent = extent;
        }
// If we're not yet at the highest dimension, we'll create child Cellmeaners to survey them for us...     
        if (currentLevel + 1 < DownSampler.depth) {  
            for (int i = 0; i < limitedExtent; ++i) {
                int[] childCoordinate = Arrays.copyOf(startCorner, startCorner.length);
                childCoordinate[currentLevel] += i;
                if (workOneDimensionUp > 1024 && i != limitedExtent - 1) {
                    // System.out.println("Deploying CellMeaner to " + Arrays.toString(childCoordinate) + " at depth " + (dimension + 1));
                    Future <Float> floatFromChild = ForkJoinPool.commonPool().submit(new CellMeaner(childCoordinate, currentLevel + 1));
                    DownSampler.MeanerThreadsStarted += 1;
// If this is the wrong way to handle exceptions, I appologize; I just tried stuff until the warnings went away.
                    try {                        
                        average += floatFromChild.get();
                    }
                    catch (InterruptedException e) {}
                    catch (ExecutionException e) {}
                }
                else {
                    // System.out.println("Doing it myself: " + Arrays.toString(childCoordinate) + " at depth " + (dimension + 1));
// compute() can't have any parameters, so if we want to keep working on this thread, we need to store the current state of the instance for when the recursion completes.
// I'm assuming this is preferable to creating a new CornerSeeker, but I could be wrong.
                    int comeBackToDepth = currentLevel;
                    int[] comeBackToCoordinate = startCorner;
                    currentLevel = currentLevel + 1;
                    startCorner = childCoordinate;
                    SetWorkOneLevelAbove();
                    average += compute();
                    currentLevel = comeBackToDepth;
                    startCorner = comeBackToCoordinate;
                    SetWorkOneLevelAbove();
                }
            }
        }
// ...otherwise, this is an instance that has to do some counting!
        else {
            for (int i = 0; i < limitedExtent; ++i) {
                int [] finalCoordinate = Arrays.copyOf(startCorner, startCorner.length);
                finalCoordinate[currentLevel] += i;
                average += DownSampler.ReferTo(DownSampler.input, finalCoordinate);
            }
        }
        average /= limitedExtent;
        return average;
    }

    void SetWorkOneLevelAbove () {
// If we're not at thi highest dimension, we'll refer to DownSampler's table of location-counts.
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

}
