import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        
        while (true) {
            System.out.print("$ ");
            String input = scanner.nextLine();
            
            if (input.isEmpty()) continue;
            
            List<String> parsedArgs = parseInput(input);
            if (parsedArgs.isEmpty()) continue;
            
            String cmd = parsedArgs.get(0);
            
            if (cmd.equals("exit")) {
                break;
            } else if (cmd.equals("echo")) {
                System.out.println(String.join(" ", parsedArgs.subList(1, parsedArgs.size())));
            } else if (cmd.equals("pwd")) {
                System.out.println(System.getProperty("user.dir"));
            } else if (cmd.equals("cd")) {
                String dir = parsedArgs.size() > 1 ? parsedArgs.get(1) : "~";
                
                if (dir.equals("~")) {
                    String homeDir = System.getenv("HOME");
                    if (homeDir != null) {
                        System.setProperty("user.dir", homeDir);
                    }
                } else {
                    Path currentPath = Paths.get(System.getProperty("user.dir"));
                    Path newPath = currentPath.resolve(dir).normalize();
                    
                    if (Files.isDirectory(newPath)) {
                        System.setProperty("user.dir", newPath.toString());
                    } else {
                        System.out.println("cd: " + dir + ": No such file or directory");
                    }
                }
            } else if (cmd.equals("type")) {
                if (parsedArgs.size() < 2) continue;
                String target = parsedArgs.get(1);
                
                if (target.equals("echo") || target.equals("exit") || target.equals("type") || target.equals("pwd") || target.equals("cd")) {
                    System.out.println(target + " is a shell builtin");
                } else {
                    String path = getPath(target);
                    if (path != null) {
                        System.out.println(target + " is " + path);
                    } else {
                        System.out.println(target + ": not found");
                    }
                }
            } else {
                String path = getPath(cmd);
                
                if (path != null) {
                    try {
                        ProcessBuilder pb = new ProcessBuilder(parsedArgs);
                        pb.directory(new File(System.getProperty("user.dir")));
                        pb.inheritIO();
                        Process process = pb.start();
                        process.waitFor();
                    } catch (Exception e) {
                        System.out.println(cmd + ": command not found");
                    }
                } else {
                    System.out.println(cmd + ": command not found");
                }
            }
        }
    }

    // UPDATED HELPER: Now handles Backslashes outside quotes!
    private static List<String> parseInput(String input) {
        List<String> args = new ArrayList<>();
        StringBuilder currentArg = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean inArg = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
                inArg = true; 
            } else if (c == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
                inArg = true;
            } else if (c == '\\' && !inSingleQuotes && !inDoubleQuotes) {
                // NEW: Handle backslash escaping outside quotes
                if (i + 1 < input.length()) {
                    currentArg.append(input.charAt(i + 1)); // Append the escaped character
                    i++; // Skip the character we just escaped so the loop doesn't read it again
                    inArg = true;
                }
            } else if (c == ' ' && !inSingleQuotes && !inDoubleQuotes) {
                if (inArg) {
                    args.add(currentArg.toString());
                    currentArg.setLength(0);
                    inArg = false;
                }
            } else {
                currentArg.append(c);
                inArg = true;
            }
        }
        
        if (inArg) {
            args.add(currentArg.toString());
        }
        
        return args;
    }

    private static String getPath(String cmd) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            String[] paths = pathEnv.split(File.pathSeparator);
            for (String dir : paths) {
                Path filePath = Paths.get(dir, cmd);
                if (Files.isRegularFile(filePath) && Files.isExecutable(filePath)) {
                    return filePath.toString();
                }
            }
        }
        return null;
    }
}