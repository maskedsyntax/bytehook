package samples;

public class LoopsTest {
    public static void main(String[] args) {
        for (int i = 0; i < 5; i++) {
            System.out.println("Loop iteration: " + i);
        }
        
        int count = 0;
        while (count < 3) {
            doWork(count);
            count++;
        }
    }

    private static void doWork(int id) {
        System.out.println("Doing work for id: " + id);
    }
}
