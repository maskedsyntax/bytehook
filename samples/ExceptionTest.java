package samples;

public class ExceptionTest {
    public static void main(String[] args) {
        try {
            System.out.println("Beginning risky operation...");
            riskyTask();
            System.out.println("Risky operation succeeded!");
        } catch (RuntimeException e) {
            System.err.println("Caught an error: " + e.getMessage());
        } finally {
            System.out.println("Cleaning up resources...");
        }
    }

    private static void riskyTask() {
        if (Math.random() > 0.5) {
            throw new RuntimeException("Random failure occurred!");
        }
    }
}
