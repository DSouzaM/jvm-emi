package example;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner s = new Scanner(System.in);
        while(s.hasNext()) {
            bar(s.next());
        }
    }

    static void bar(String str) {
        if (str.equals("foo")) {
            System.out.println("it's a foo");
        } else if (str.equals("bar")) {
            System.out.println("it's a bar");
        } else {
            System.out.println("it's something else");
        }
    }
}
