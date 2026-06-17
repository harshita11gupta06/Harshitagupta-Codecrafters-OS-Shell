

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

            if (input.equals("exit")) {
                break;
            } 

            // --- REDIRECTION PARSING ---
            boolean isRedirect = false;
            boolean isStderr = false;
            boolean shouldAppend = false;
            String outputFile = null;
            String commandPart = input;

            // Define the operators to check, ordered by length to prevent partial matches
            String[] operators = {" 2>> ", " 1>> ", " >> ", " 2> ", " 1> ", " > "};
            for (String op : operators) {
                if (input.contains(op)) {
                    isRedirect = true;
                    int idx = input.indexOf(op);
                    commandPart = input.substring(0, idx).trim();
                    outputFile = input.substring(idx + op.length()).trim();
                    
                    if (op.contains("2")) {
                        isStderr = true;
                    }
                    if (op.contains(">>")) {
                        shouldAppend = true;
                    }
                    break; // Match found, stop checking
                }
            }
            // -----------------------------

            // Helper to handle builtin output redirection cleanly
            final boolean finalIsRedirect = isRedirect;
            final boolean finalIsStderr = isStderr;
            final boolean finalShouldAppend = shouldAppend;
            final String finalOutputFile = outputFile;

            java.util.function.Consumer<String> shellOut = (text) -> {
                if (finalIsRedirect && !finalIsStderr) {
                    try (PrintWriter writer = new PrintWriter(new FileWriter(finalOutputFile, finalShouldAppend))) {
                        writer.println(text);
                    } catch (Exception e) {
                        System.out.println(text);
                    }
                } else {
                    System.out.println(text);
                }
            };

            java.util.function.Consumer<String> shellErr = (text) -> {
                if (finalIsRedirect && finalIsStderr) {
                    try (PrintWriter writer = new PrintWriter(new FileWriter(finalOutputFile, finalShouldAppend))) {
                        writer.println(text);
                    } catch (Exception e) {
                        System.err.println(text);
                    }
                } else {
                    System.err.println(text);
                }
            };

            // 2. Check Builtins
            if (commandPart.startsWith("echo ")) {
                shellOut.accept(commandPart.substring(5));
            } 
            else if (commandPart.equals("pwd")) {
                shellOut.accept(System.getProperty("user.dir"));
            }
            else if (commandPart.startsWith("cd ")) {
                String targetDir = commandPart.substring(3).trim();
                File directory = new File(targetDir);
                if (directory.exists() && directory.isDirectory()) {
                    System.setProperty("user.dir", directory.getAbsolutePath());
                } else {
                    shellOut.accept("cd: " + targetDir + ": No such file or directory");
                }
            }
            else if (commandPart.startsWith("type ")) {
                String commandToCheck = commandPart.substring(5).trim();
                if (commandToCheck.equals("echo") || commandToCheck.equals("exit") || 
                    commandToCheck.equals("type") || commandToCheck.equals("pwd") || 
                    commandToCheck.equals("cd")) {
                    shellOut.accept(commandToCheck + " is a shell builtin");
                } else {
                    String pathEnv = System.getenv("PATH");
                    String executablePath = findInPath(commandToCheck, pathEnv);
                    if (executablePath != null) {
                        shellOut.accept(commandToCheck + " is " + executablePath);
                    } else {
                        shellOut.accept(commandToCheck + ": not found");
                    }
                }
            } 
            // 3. External Programs
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
                        
                        if (isRedirect) {
                            File targetFile = new File(outputFile);
                            ProcessBuilder.Redirect fileRedirect = shouldAppend ? 
                                    ProcessBuilder.Redirect.appendTo(targetFile) : 
                                    ProcessBuilder.Redirect.to(targetFile);
                            
                            if (isStderr) {
                                pb.redirectError(fileRedirect);
                                pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                            } else {
                                pb.redirectOutput(fileRedirect);
                                pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                            }
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