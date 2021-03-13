import java.util.Arrays;
import java.lang.reflect.Array;

public class DataBank {

    public static Object biggest;
    public static Object smallest;
    public static int depth;
    public static int [] dimensions;
    public static int samplingFactor = 2;

    public DataBank (Object masterThing, Object outPut) {
        biggest = masterThing;
        smallest = outPut;
        depth = FindDepth(biggest.getClass());
        FindDimensions();
        SafetyCheck();
        CreateReceptical();
    }
   
    static void CreateReceptical () {
        int [] newDimensions = Arrays.copyOf(dimensions, dimensions.length);
        for (int i = 0; i < depth; ++i) {
            int reducedValue = newDimensions[i] / samplingFactor;
            if (reducedValue < 1) {
                reducedValue = 1;
            }
            newDimensions[i] = reducedValue;
        }        
        downSampler.outPut = Array.newInstance(int.class, newDimensions);
    }

    // After a frustrating afternoon trying my own methods, I found and lifted this from StackOverflow:
    static int FindDepth(Class<?> type) {
        if (type.getComponentType() == null) {
            return 0;
        } else {
            return FindDepth(type.getComponentType()) + 1;
        }
    }

    static void FindDimensions () {
        dimensions = new int[depth];
        Object thisLevel = biggest;
        int i = 0;        
        Class<?> type;
        while (i < depth) {
            type = thisLevel.getClass().getComponentType();
            if (type != null){
                dimensions[i] = Array.getLength(thisLevel);
                if (type != int.class) {
                    thisLevel = ((Object[]) thisLevel)[0];
                    ++i;
                }
                else {
                    break;
                }
            }
        }        
    }

// This was lifted from the web:
    static boolean IsPowerOfTwo(int n) { 
        return (int)(Math.ceil((Math.log(n) / Math.log(2))))  
            == (int)(Math.floor(((Math.log(n) / Math.log(2))))); 
    } 

    public static int ReferTo (Object theArray, int[] coordinate, int setTo) {
        if (depth == 1) {
            return ((int[])theArray)[coordinate[0]];
        }
        else  {
            int level = 0;
            Object [] listLevelX = (Object[]) theArray;
            for (; level < depth - 2; ++level) {
                listLevelX = (Object[]) listLevelX[coordinate[level]];
            }
            int[] arrayOfInts = (int[]) listLevelX[coordinate[level]];
            if (setTo != -1) {
                arrayOfInts[coordinate[level + 1]] = setTo;

            }
            return arrayOfInts[coordinate[level + 1]];
        }
    }

    static void SafetyCheck () {
        System.out.println("This is an array with " + depth + " dimensions.");
        String dimensionsList = "The dimensions are ";
        for (int i = 0; i < depth; ++i) {
            int inQuestion = dimensions[i];
            if (inQuestion % 2 != 0 && inQuestion != 1) {
                throw new IllegalArgumentException("Abort! All dimensions of the starting array must either be one or a multiple of 2.");
            }
            dimensionsList += inQuestion + ", ";
        }
        System.out.println(dimensionsList);
        if (IsPowerOfTwo(samplingFactor) == false) {
            throw new IllegalArgumentException("Abort! The sampling factor must be a power of 2.");
        }
    }

}
