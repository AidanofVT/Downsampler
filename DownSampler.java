import java.util.Arrays;
import java.lang.reflect.Array;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;

class DownSampler {

    // This is just here because I found it useful as a pattern to test whether the program was actually working. Remember to turn off PopulateInputRandomly()!
    // static int [][] input = {
    //     {0,0,1,1,0,0,1,1},
    //     {0,0,1,1,1,1,0,0},
    //     {0,0,1,1,0,0,1,1},
    //     {0,0,1,1,1,1,0,0}
    // };
    static Object [] tests = {
        new int [1000000],
        new int [1][1][1][1][1][1][1][1][1][1][1][1][1000000],
        new int [1000000][1][1][1][1][1][1][1][1][1][1][1][1],
        new int [1024][1024],
        new int [4][4][4][4][4][4][4][4][4][4]
    };
    static Object input;
    static Object output;
// These numbers specify the range of numbers that will fill the array if PopulateInputRandomly() is used.
    static int minValue = 0;
    static int maxValue = 1;
// The total count of dimensions.
    public static int depth;
// The extents of the dimensions.
    public static int [] dimensions;
// For each dimension, the amount of corners in that dimension and all higher dimensions. So, if input has three dimensions X,Y,Z, the first entry in this list would be all the
// corners in the entire array, the second entry would be the number of corners with a single given X value, and the third entry would be the number of corners with a given X
// and a given Y. Useful when iterating through a dimension, needing an estimate of how much work is contained in higher dimensions.
    public static int [] cornersByTheSlice;
// Similar to the above, but this contains a count of all points, rather than corners, that share {index} lower coordinates.
    public static int [] locationsByTheSlice;
// The factor by which input is to be downsampled. Should be a power of 2!
    public static int samplingFactor = 2;
    public static boolean staySynchronous = false;
    public static int ModerThreadsStarted;
    public static int SeekerThreadsStarted;

    public static void main (String[] args) {
        System.out.println("Start.");
        for (int i = 0; i < tests.length; ++i) {
            System.out.println("Test #" + (i + 1));            
            input = tests[i];
            depth = FindDepth(input.getClass());
            FindDimensions();
            SafetyCheck();
            CreateReceptacle();
            int[] originPoint = new int[depth];
            PopulateInputRandomly(originPoint, 0);
            ModerThreadsStarted = 0;
            SeekerThreadsStarted = 1;
            staySynchronous = false;
            long startTime = System.currentTimeMillis();
            ForkJoinPool.commonPool().execute(new CornerSeeker(originPoint, 0, false));
            while (ForkJoinPool.commonPool().isQuiescent() == false) {
                try {
                    Thread.sleep(15);
                }
                catch (InterruptedException e) {}
            }
            System.out.println("     Multithreaded attempt finished after " + (System.currentTimeMillis() - startTime) + "ms of downSampling, involving " + ModerThreadsStarted + " moder threads and " + SeekerThreadsStarted + " cornering threads.");
            ModerThreadsStarted = 0;
            SeekerThreadsStarted = 1;
            staySynchronous = true;
            CreateReceptacle();
            startTime = System.currentTimeMillis();
            ForkJoinPool.commonPool().execute(new CornerSeeker(originPoint, 0, false));
            while (ForkJoinPool.commonPool().isQuiescent() == false) {
                try {
                    Thread.sleep(15);
                }
                catch (InterruptedException e) {}
            }
            System.out.println("     Single-threaded attempt finished after " + (System.currentTimeMillis() - startTime) + "ms of downSampling.");
        }        
    }

    static void CreateReceptacle () {
        int [] newDimensions = Arrays.copyOf(dimensions, dimensions.length);
        for (int i = 0; i < depth; ++i) {
            int reducedValue = newDimensions[i] / samplingFactor;
            if (reducedValue < 1) {
                reducedValue = 1;
            }
            newDimensions[i] = reducedValue;
        }
// Thank goodness for this built-in function!      
        output = Array.newInstance(int.class, newDimensions);
    }

    static int FindDepth(Class<?> type) {
        if (type.getComponentType() == null) {
            return 0;
        } else {
            return FindDepth(type.getComponentType()) + 1;
        }
    }

//Also populates cornersByTheSlice and locationsByTheSlice
    static void FindDimensions () {
        dimensions = new int[depth];               
        Class<?> type;
        Object thisLevel = input;
        for (int i = 0; i < depth; ++i) {
            type = thisLevel.getClass().getComponentType();
            if (type != null){
                dimensions[i] = Array.getLength(thisLevel);
                if (type != int.class) {
                    thisLevel = ((Object[]) thisLevel)[0];
                }
            }
        }
        cornersByTheSlice = new int[depth];
        cornersByTheSlice[depth - 1] = (int) Math.ceil((float) dimensions[depth - 1] / (float) samplingFactor);
        for (int i = depth - 2; i >= 0; --i) {
            int cornerVolume = cornersByTheSlice[i + 1];
            int cornersThisDimension = (int) Math.ceil((float) dimensions[i] / (float) samplingFactor);
            cornerVolume *= cornersThisDimension;
            cornersByTheSlice[i] = cornerVolume;
        }
        locationsByTheSlice = new int [depth];
        int sumOfNextLevel = 1;
        for (int i = depth - 1; i >= 0; --i) {
// This logic allows the program to downsample any array, even if some dimensions start smaller than the samplingFactor:
            if (dimensions[i] < samplingFactor) {
                locationsByTheSlice[i] = dimensions[i] * sumOfNextLevel;
            }
            else {
                locationsByTheSlice[i] = samplingFactor * sumOfNextLevel;
            }
            sumOfNextLevel = locationsByTheSlice[i];         
        }
    }

    // This was lifted from the web:
    static boolean IsPowerOfTwo(int n) { 
        return (int)(Math.ceil((Math.log(n) / Math.log(2))))  
            == (int)(Math.floor(((Math.log(n) / Math.log(2))))); 
    } 

    static void PopulateInputRandomly (int[] startCorner, int dimension) {
        int extent = dimensions[dimension];
        if (dimension + 1 < depth) {  
            for (int i = 0; i < extent; ++i) {
                int[] childCoordinate = Arrays.copyOf(startCorner, startCorner.length);
                childCoordinate[dimension] += i;
                PopulateInputRandomly(childCoordinate, dimension + 1);
            }
        }
        else {
            Random random = new Random();
            for (int i = 0; i < extent; ++i) {
                int [] finalCoordinate = Arrays.copyOf(startCorner, startCorner.length);
                finalCoordinate[dimension] += i;
                ReferTo(input, finalCoordinate, random.nextInt(maxValue + 1) + minValue);
            }
        }
    }

// Necessary to get values from arbitrary-length arrays:
    public static int ReferTo (Object theArray, int[] coordinate) {
        int level = 0;
        int[] arrayOfInts;
        if (depth != 1){
            Object [] listLevelX = (Object[]) theArray;
            for (; level < depth - 2; ++level) {
                listLevelX = (Object[]) listLevelX[coordinate[level]];
            }
            arrayOfInts = (int[]) listLevelX[coordinate[level]];
        }
        else {
            arrayOfInts = (int[]) theArray;
            level = -1;
        }
        return arrayOfInts[coordinate[level + 1]];
    }

// An overloaded version which acts as a setter:
    public static void ReferTo (Object theArray, int[] coordinate, int setTo) {
        int level = 0;
        int[] arrayOfInts;
        if (depth != 1){
            Object [] listLevelX = (Object[]) theArray;
            for (; level < depth - 2; ++level) {
                listLevelX = (Object[]) listLevelX[coordinate[level]];
            }
            arrayOfInts = (int[]) listLevelX[coordinate[level]];
        }
        else {
            arrayOfInts = (int[]) theArray;
            level = -1;
        }
        arrayOfInts[coordinate[level + 1]] = setTo;
    }

    static void SafetyCheck () {
        // System.out.println("This is an array with " + depth + " dimensions.");
        String dimensionsList = "     The dimensions are ";
        for (int i = 0; i < depth; ++i) {
            int inQuestion = dimensions[i];
            if (inQuestion % 2 != 0 && inQuestion != 1) {
                throw new IllegalArgumentException("Abort! All dimensions of the starting array must either be one or a multiple of 2.");
            }
            dimensionsList += inQuestion + ",";
        }
        System.out.println(dimensionsList.substring(0, dimensionsList.length() - 1));
        if (IsPowerOfTwo(samplingFactor) == false) {
            throw new IllegalArgumentException("Abort! The sampling factor must be a power of 2.");
        }
    }

}