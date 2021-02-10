package example;

public class Main {
    String[] args;

    public static void main(String[] args) throws Exception {
        Main m = new Main(args);
        m.bar();
    }

    Main(String[] args) {
        this.args = args;
    }

    void bar() throws Exception {
        for (String arg: args) {
            if (arg.equals("foo")) {
                System.out.println("it's a foo");
            } else if (arg.equals("bar")) {
                System.out.println("it's a bar");
            } else {
                System.out.println("it's something else");
            }
        }
    }
}
