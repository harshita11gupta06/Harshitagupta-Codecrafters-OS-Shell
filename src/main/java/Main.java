

import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        
        while (true) {
            System.out.print("$ ");
            String input = scanner.nextLine();
            
            // 1. Check for exit
            if (input.equals("exit")) {
                break;
            } 
            // 2. Check for echo
            else if (input.startsWith("echo ")) {
                String message = input.substring(5);
                System.out.println(message);
            } 
            // 3. Check for type
            else if (input.startsWith("type ")) {
                String commandToCheck = input.substring(5).trim();
                
                // Verify if the command is a known shell builtin
                if (commandToCheck.equals("echo") || commandToCheck.equals("exit") || commandToCheck.equals("type")) {
                    System.out.println(commandToCheck + " is a shell builtin");
                } else {
                    System.out.println(commandToCheck + ": not found");
                }
            } 
            // 4. Fallback for invalid commands
            else {
                System.out.println(input + ": command not found");
            }
        }
    }
}