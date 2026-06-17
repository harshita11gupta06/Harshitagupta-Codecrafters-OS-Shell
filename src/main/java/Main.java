

import java.io.File;
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
                
                // A. Check if it's a builtin
                if (commandToCheck.equals("echo") || commandToCheck.equals("exit") || commandToCheck.equals("type")) {
                    System.out.println(commandToCheck + " is a shell builtin");
                } else {
                    // B. Search for the command in the PATH directories
                    String pathEnv = System.getenv("PATH");
                    String executablePath = findInPath(commandToCheck, pathEnv);
                    
                    if (executablePath != null) {
                        System.out.println(commandToCheck + " is " + executablePath);
                    } else {
                        System.out.println(commandToCheck + ": not found");
                    }
                }
            } 
            // 4. Fallback for invalid commands
            else {
                System.out.println(input + ": command not found");
            }
        }
    }

    // Helper method to look up a command in the system PATH
    private static String findInPath(String command, String pathEnv) {
        if (pathEnv == null || pathEnv.isEmpty()) {
            return null;
        }

        // Split PATH using File.pathSeparator (handles ':' on Linux/macOS and ';' on Windows)
        String[] directories = pathEnv.split(File.pathSeparator);

        for (String directory : directories) {
            File file = new File(directory, command);
            // Check if the file exists and is executable
            if (file.exists() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }
}