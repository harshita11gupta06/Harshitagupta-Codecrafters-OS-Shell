

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        
        while (true) {
            System.out.print("$ ");
            String input = scanner.nextLine().trim();
            
            if (input.isEmpty()) {
                continue;
            }

            // 1. Check for exit
            if (input.equals("exit")) {
                break;
            } 
            // 2. Check for echo
            else if (input.startsWith("echo ")) {
                String message = input.substring(5);
                System.out.println(message);
            } 
            // 3. Check for pwd
            else if (input.equals("pwd")) {
                System.out.println(System.getProperty("user.dir"));
            }
            // 4. Check for cd
            else if (input.startsWith("cd ")) {
                String targetDir = input.substring(3).trim();
                File directory = new File(targetDir);
                
                // For this stage, we assume absolute paths (starting with '/')
                if (directory.exists() && directory.isDirectory()) {
                    // Update Java's user.dir property so 'pwd' reflects the change
                    System.setProperty("user.dir", directory.getAbsolutePath());
                } else {
                    System.out.println("cd: " + targetDir + ": No such file or directory");
                }
            }
            // 5. Check for type (Make sure to update type to recognize "cd" too!)
            else if (input.startsWith("type ")) {
                String commandToCheck = input.substring(5).trim();
                
                if (commandToCheck.equals("echo") || commandToCheck.equals("exit") || 
                    commandToCheck.equals("type") || commandToCheck.equals("pwd") || 
                    commandToCheck.equals("cd")) {
                    System.out.println(commandToCheck + " is a shell builtin");
                } else {
                    String pathEnv = System.getenv("PATH");
                    String executablePath = findInPath(commandToCheck, pathEnv);
                    
                    if (executablePath != null) {
                        System.out.println(commandToCheck + " is " + executablePath);
                    } else {
                        System.out.println(commandToCheck + ": not found");
                    }
                }
            } 
            // 6. Try running it as an external program
            else {
                String[] parts = input.split(" ");
                String command = parts[0];
                
                String pathEnv = System.getenv("PATH");
                String executablePath = findInPath(command, pathEnv);
                
                if (executablePath != null) {
                    try {
                        List<String> commandWithArgs = new ArrayList<>();
                        commandWithArgs.add(command); 
                        for (int i = 1; i < parts.length; i++) {
                            commandWithArgs.add(parts[i]);
                        }
                        
                        ProcessBuilder pb = new ProcessBuilder(commandWithArgs);
                        
                        // Crucial: Set the working directory for external commands 
                        // to match the updated simulated shell directory!
                        pb.directory(new File(System.getProperty("user.dir")));
                        
                        pb.inheritIO();
                        Process process = pb.start();
                        process.waitFor();
                    } catch (Exception e) {
                        System.out.println(command + ": command not found");
                    }
                } else {
                    System.out.println(command + ": command not found");
                }
            }
        }
    }

    private static String findInPath(String command, String pathEnv) {
        if (pathEnv == null || pathEnv.isEmpty()) {
            return null;
        }

        String[] directories = pathEnv.split(File.pathSeparator);
        for (String directory : directories) {
            File file = new File(directory, command);
            if (file.exists() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }
}