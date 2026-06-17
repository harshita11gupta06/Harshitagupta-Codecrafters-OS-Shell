
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        
        while (true) {
            System.out.print("$ ");
            String input = scanner.nextLine();
            
            // Check if the command is "exit"
            if (input.equals("exit")) {
                break; // This breaks the while loop and exits the program safely
            }
            
            // Otherwise, treat it as an invalid command
            System.out.println(input + ": command not found");
        }
    }
}