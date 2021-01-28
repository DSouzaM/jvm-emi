package example;

public class Main {
    String name;

    public static void main(String[] args) throws Exception {
        Main m = new Main("foo");
        m.bar("a");
    }

    Main(String name) {
        this.name = name;
    }

    String bar(String x) throws Exception {
        if (x == null) {
            throw new RuntimeException("oof");
        } else {
            return null;
        }
    }
}
