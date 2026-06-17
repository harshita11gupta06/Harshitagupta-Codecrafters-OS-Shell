
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        
        // Wrap the read-eval-print steps in an infinite loop
        while (true) {
            // 1. Read: Print the prompt and get input
            System.out.print("$ ");
            String input = scanner.nextLine();
            
            // 2. Eval & Print: For now, treat every command as invalid
            System.out.println(input + ": command not found");
        }
    }
}