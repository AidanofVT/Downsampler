import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.Arrays;
import java.lang.Math;

public class Solver extends RecursiveAction {
    DataBank origin;
    int[] parentCoordinate;
    int dimension;

    public Solver (DataBank driver, int [] startAtCoord, int level) {
        origin = driver;
        parentCoordinate = startAtCoord;
// remember that origin.depth is a count, so starts at one, while dimension is an index, so starts at zero.
        dimension = level;
    }

    @Override
    protected void compute () {
        if (dimension < origin.depth) {
            int extent = origin.dimensions[dimension];
            for (int i = 0; i < extent; i += origin.samplingFactor) {
                int [] childCoordinate = Arrays.copyOf(parentCoordinate, parentCoordinate.length);
                childCoordinate[dimension] = i;
                // System.out.println("Deploying Solver to " + Arrays.toString(childCoordinate) + " at depth " + (dimension + 1));
                Solver childSolver = new Solver (origin, childCoordinate, dimension + 1);
                ForkJoinPool.commonPool().invoke(childSolver);
            }
        }
        else {
            // String pCoordEncoded = Arrays.toString(parentCoordinate);
            // System.out.println("Deploying CellMeaner to " + pCoordEncoded + " at depth " + 0);
            float returned = ForkJoinPool.commonPool().invoke(new CellMeaner(origin, parentCoordinate, 0));
            int result = Math.round(returned);
            int [] scaledCoordinate = Arrays.copyOf(parentCoordinate, parentCoordinate.length);
            for (int i = 0; i < scaledCoordinate.length; ++i) {
                scaledCoordinate[i] /= origin.samplingFactor;
            }
            origin.ReferTo(downSampler.outPut, scaledCoordinate, result);            
            // System.out.println(pCoordEncoded + " is a corner and it's average value is " + result);
        }
    }
    
}
