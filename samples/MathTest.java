package samples;

public class MathTest {
    public static void main(String[] args) {
        int x = 10;
        int y = 5;
        System.out.println("Result of add(10, 5): " + add(x, y));
        System.out.println("Result of multiply(10, 5): " + multiply(x, y));
        System.out.println("Square of 10: " + square(10));
    }

    public static int add(int a, int b) {
        return a + b;
    }

    public static int multiply(int a, int b) {
        return a * b;
    }

    public static int square(int a) {
        return a * a;
    }
}
