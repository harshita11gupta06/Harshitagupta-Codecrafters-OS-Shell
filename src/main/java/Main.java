
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
            // 2. Check for echo (note the trailing space so we handle arguments)
            else if (input.startsWith("echo ")) {
                String message = input.substring(5);
                System.out.println(message);
            } 
            // 3. Fallback for invalid commands
            else {
                System.out.println(input + ": command not found");
            }
        }
    }
}