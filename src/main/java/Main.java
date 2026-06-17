package main.java;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        // 1. Keep the prompt from the previous stage
        System.out.print("$ ");

        // 2. Read the user's input
        Scanner scanner = new Scanner(System.in);
        String input = scanner.nextLine();

        // 3. Print the formatted error message
        System.out.println(input + ": command not found");
    }
}