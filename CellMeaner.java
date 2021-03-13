import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public class CellMeaner extends RecursiveTask<Float> {

    DataBank origin;
    int[] startCorner;
    int dimension;

    public CellMeaner (DataBank driver, int [] startAtCoord, int level) {
        origin = driver;
        startCorner = startAtCoord;
// remember that origin.depth is a count, so starts at one, while dimension is an index, so starts at zero.
        dimension = level;
    }

    @Override
    protected Float compute () {
        Float average = 0f;
        int extent = origin.dimensions[dimension];
        int limitedExtent = origin.samplingFactor;
        if (limitedExtent > extent) {
            limitedExtent = extent;
        }        
        if (dimension + 1 < origin.depth) {  
            for (int i = 0; i < limitedExtent; ++i) {
                int[] childCoordinate = Arrays.copyOf(startCorner, startCorner.length);
                childCoordinate[dimension] += i;
                // System.out.println("Deploying CellMeaner to " + Arrays.toString(childCoordinate) + " at depth " + (dimension + 1));
                average += ForkJoinPool.commonPool().invoke(new CellMeaner(origin, childCoordinate, dimension + 1));
            }
        }
        else {
            for (int i = 0; i < limitedExtent; ++i) {
                int [] finalCoordinate = Arrays.copyOf(startCorner, startCorner.length);
                finalCoordinate[dimension] += i;
                average += origin.ReferTo(downSampler.input, finalCoordinate, -1);
            }
        }
        average /= limitedExtent;
        return average;
    }

}
