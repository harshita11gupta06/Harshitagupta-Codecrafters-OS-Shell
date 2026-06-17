package main.java;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
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

            // --- REDIRECTION HANDLING ---
            boolean isAppendRedirect = false;
            String outputFile = null;
            String commandPart = input;

            // Check for >> or 1>>
            if (input.contains(" >> ")) {
                isAppendRedirect = true;
                int idx = input.indexOf(" >> ");
                commandPart = input.substring(0, idx).trim();
                outputFile = input.substring(idx + 4).trim();
            } else if (input.contains(" 1>> ")) {
                isAppendRedirect = true;
                int idx = input.indexOf(" 1>> ");
                commandPart = input.substring(0, idx).trim();
                outputFile = input.substring(idx + 5).trim();
            }
            // -----------------------------

            // 2. Check for echo
            if (commandPart.startsWith("echo ")) {
                String message = commandPart.substring(5);
                if (isAppendRedirect) {
                    try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile, true))) {
                        writer.println(message);
                    }
                } else {
                    System.out.println(message);
                }
            } 
            // 3. Check for pwd
            else if (commandPart.equals("pwd")) {
                String currentDir = System.getProperty("user.dir");
                if (isAppendRedirect) {
                    try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile, true))) {
                        writer.println(currentDir);
                    }
                } else {
                    System.out.println(currentDir);
                }
            }
            // 4. Check for cd
            else if (commandPart.startsWith("cd ")) {
                String targetDir = commandPart.substring(3).trim();
                File directory = new File(targetDir);
                if (directory.exists() && directory.isDirectory()) {
                    System.setProperty("user.dir", directory.getAbsolutePath());
                } else {
                    System.out.println("cd: " + targetDir + ": No such file or directory");
                }
            }
            // 5. Check for type
            else if (commandPart.startsWith("type ")) {
                String commandToCheck = commandPart.substring(5).trim();
                String result;
                
                if (commandToCheck.equals("echo") || commandToCheck.equals("exit") || 
                    commandToCheck.equals("type") || commandToCheck.equals("pwd") || 
                    commandToCheck.equals("cd")) {
                    result = commandToCheck + " is a shell builtin";
                } else {
                    String pathEnv = System.getenv("PATH");
                    String executablePath = findInPath(commandToCheck, pathEnv);
                    if (executablePath != null) {
                        result = commandToCheck + " is " + executablePath;
                    } else {
                        result = commandToCheck + ": not found";
                    }
                }

                if (isAppendRedirect) {
                    try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile, true))) {
                        writer.println(result);
                    }
                } else {
                    System.out.println(result);
                }
            } 
            // 6. Try running it as an external program
            else {
                String[] parts = commandPart.split(" ");
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
                        pb.directory(new File(System.getProperty("user.dir")));
                        
                        if (isAppendRedirect) {
                            // Redirect standard output to append mode for the file
                            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(new File(outputFile)));
                            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                            pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
                        } else {
                            pb.inheritIO();
                        }
                        
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