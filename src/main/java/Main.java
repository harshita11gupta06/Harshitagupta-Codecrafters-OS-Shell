
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
                    break;
                }
            }

            if (isRedirect && outputFile != null) {
                File file = new File(outputFile);
                if (file.getParentFile() != null) {
                    file.getParentFile().mkdirs();
                }
                if (!shouldAppend) {
                    try (FileWriter fw = new FileWriter(file, false)) { }
                } else if (!file.exists()) {
                    try (FileWriter fw = new FileWriter(file, true)) { }
                }
            }
            // -----------------------------

            // --- QUOTE-AWARE COMMAND PARSING ---
            List<String> tokens = parseArguments(commandPart);
            if (tokens.isEmpty()) {
                continue;
            }
            String command = tokens.get(0);
            // ------------------------------------

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

            // 2. Check Builtins
            if (command.equals("echo")) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < tokens.size(); i++) {
                    sb.append(tokens.get(i));
                    if (i < tokens.size() - 1) sb.append(" ");
                }
                shellOut.accept(sb.toString());
            } 
            else if (command.equals("pwd")) {
                shellOut.accept(System.getProperty("user.dir"));
            }
            else if (command.equals("cd")) {
                String targetDir = tokens.size() > 1 ? tokens.get(1) : System.getenv("HOME");
                
                // EXPAND HOME DIRECTORY (~ tilde expansion)
                if (targetDir.equals("~")) {
                    targetDir = System.getenv("HOME");
                }
                
                File directory = new File(targetDir);
                if (directory.exists() && directory.isDirectory()) {
                    System.setProperty("user.dir", directory.getAbsolutePath());
                } else {
                    shellOut.accept("cd: " + targetDir + ": No such file or directory");
                }
            }
            else if (command.equals("type")) {
                if (tokens.size() > 1) {
                    String commandToCheck = tokens.get(1);
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
            } 
            // 3. External Programs
            else {
                String pathEnv = System.getenv("PATH");
                String executablePath = findInPath(command, pathEnv);
                
                if (executablePath != null) {
                    try {
                        ProcessBuilder pb = new ProcessBuilder(tokens);
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

    private static List<String> parseArguments(String commandPart) {
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean escaped = false;

        for (int i = 0; i < commandPart.length(); i++) {
            char c = commandPart.charAt(i);

            if (escaped) {
                currentToken.append(c);
                escaped = false;
            } else if (c == '\\' && !inSingleQuotes) {
                if (inDoubleQuotes) {
                    if (i + 1 < commandPart.length()) {
                        char next = commandPart.charAt(i + 1);
                        if (next == '$' || next == '`' || next == '"' || next == '\\' || next == '\n') {
                            escaped = true;
                        } else {
                            currentToken.append(c);
                        }
                    } else {
                        currentToken.append(c);
                    }
                } else {
                    escaped = true;
                }
            } else if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
            } else if (c == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
            } else if (Character.isWhitespace(c) && !inSingleQuotes && !inDoubleQuotes) {
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString());
                    currentToken.setLength(0);
                }
            } else {
                currentToken.append(c);
            }
        }
        if (currentToken.length() > 0) {
            tokens.add(currentToken.toString());
        }
        return tokens;
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