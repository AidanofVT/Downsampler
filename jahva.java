import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

class downSampler {

    static DataBank forTheThreads;
    static int [][] input = new int [100][100];
    static Object outPut;
    static int minValue = 0;
    static int maxValue = 2;

    public static void main (String[] args) {
        System.out.println("Start.");
        float startTime = System.currentTimeMillis();
        forTheThreads = new DataBank(input, outPut);
        int[] zeroPoint = new int[forTheThreads.depth];
        Arrays.fill(zeroPoint, 0);
        fillUp(zeroPoint, 0);
        System.out.println("Done populating array. " + (System.currentTimeMillis() - startTime) + " ellapsed.");
        ForkJoinPool.commonPool().invoke(new Solver(forTheThreads, zeroPoint, 0));
        // while (ForkJoinPool.commonPool().getPoolSize() > 1) {
        //     System.out.println(ForkJoinPool.commonPool().getPoolSize());
        // }
        System.out.println("End after " + (System.currentTimeMillis() - startTime) + " of downSampling.");
    }

    static void fillUp (int[] startCorner, int dimension) {
        int extent = forTheThreads.dimensions[dimension];
        if (dimension + 1 < forTheThreads.depth) {  
            for (int i = 0; i < extent; ++i) {
                int[] childCoordinate = Arrays.copyOf(startCorner, startCorner.length);
                childCoordinate[dimension] += i;
                fillUp(childCoordinate, dimension + 1);
            }
        }
        else {
            Random random = new Random();
            for (int i = 0; i < extent; ++i) {
                int [] finalCoordinate = Arrays.copyOf(startCorner, startCorner.length);
                finalCoordinate[dimension] += i;
                forTheThreads.ReferTo(input, finalCoordinate, random.nextInt(maxValue) + minValue);
            }
        }
    }

}