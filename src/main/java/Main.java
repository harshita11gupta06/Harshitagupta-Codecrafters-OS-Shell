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
            
            // Ignore empty "Enter" presses
            if (input.isEmpty()) continue;
            
            // NEW: Parse the input using our custom method!
            List<String> parsedArgs = parseInput(input);
            if (parsedArgs.isEmpty()) continue;
            
            String cmd = parsedArgs.get(0);
            
            if (cmd.equals("exit")) {
                break;
            } else if (cmd.equals("echo")) {
                // Join all the arguments back together with a single space
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

    // NEW HELPER: Reads characters one by one to handle quotes properly!
    private static List<String> parseInput(String input) {
        List<String> args = new ArrayList<>();
        StringBuilder currentArg = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inArg = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '\'') {
                // Toggle whether we are inside single quotes
                inSingleQuotes = !inSingleQuotes;
                inArg = true; 
            } else if (c == ' ' && !inSingleQuotes) {
                // If we hit a space AND we aren't in quotes, finish the current argument
                if (inArg) {
                    args.add(currentArg.toString());
                    currentArg.setLength(0);
                    inArg = false;
                }
            } else {
                // Otherwise, just add the character to our current argument
                currentArg.append(c);
                inArg = true;
            }
        }
        
        // Add the very last argument if we were building one when the line ended
        if (inArg) {
            args.add(currentArg.toString());
        }
        
        return args;
    }

    // Unchanged
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