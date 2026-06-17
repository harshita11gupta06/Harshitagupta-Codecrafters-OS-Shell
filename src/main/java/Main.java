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
            
            // NEW: Search for standard output redirection (> or 1>)
            String redirectFile = null;
            for (int i = 0; i < parsedArgs.size(); i++) {
                String arg = parsedArgs.get(i);
                if (arg.equals(">") || arg.equals("1>")) {
                    if (i + 1 < parsedArgs.size()) {
                        redirectFile = parsedArgs.get(i + 1);
                        // Remove the operator and the filename from our args list
                        parsedArgs.subList(i, i + 2).clear();
                        break;
                    }
                }
            }
            
            if (parsedArgs.isEmpty()) continue;
            String cmd = parsedArgs.get(0);
            
            if (cmd.equals("exit")) {
                break;
            } else if (cmd.equals("echo")) {
                String out = String.join(" ", parsedArgs.subList(1, parsedArgs.size()));
                // Use our new helper method instead of System.out.println
                writeOutput(out, redirectFile);
            } else if (cmd.equals("pwd")) {
                writeOutput(System.getProperty("user.dir"), redirectFile);
            } else if (cmd.equals("cd")) {
                String dir = parsedArgs.size() > 1 ? parsedArgs.get(1) : "~";
                if (dir.equals("~")) {
                    String homeDir = System.getenv("HOME");
                    if (homeDir != null) System.setProperty("user.dir", homeDir);
                } else {
                    Path currentPath = Paths.get(System.getProperty("user.dir"));
                    Path newPath = currentPath.resolve(dir).normalize();
                    if (Files.isDirectory(newPath)) {
                        System.setProperty("user.dir", newPath.toString());
                    } else {
                        // Error messages ALWAYS stay on the terminal
                        System.out.println("cd: " + dir + ": No such file or directory"); 
                    }
                }
            } else if (cmd.equals("type")) {
                if (parsedArgs.size() < 2) continue;
                String target = parsedArgs.get(1);
                
                if (target.equals("echo") || target.equals("exit") || target.equals("type") || target.equals("pwd") || target.equals("cd")) {
                    writeOutput(target + " is a shell builtin", redirectFile);
                } else {
                    String path = getPath(target);
                    if (path != null) {
                        writeOutput(target + " is " + path, redirectFile);
                    } else {
                        System.out.println(target + ": not found"); // Error goes to terminal
                    }
                }
            } else {
                String path = getPath(cmd);
                if (path != null) {
                    try {
                        ProcessBuilder pb = new ProcessBuilder(parsedArgs);
                        pb.directory(new File(System.getProperty("user.dir")));
                        
                        // Handle external program redirection
                        if (redirectFile != null) {
                            File f = new File(redirectFile);
                            if (f.getParentFile() != null) f.getParentFile().mkdirs();
                            pb.redirectOutput(f); // Redirects stdout to the file
                        } else {
                            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT); // Stays on terminal
                        }
                        
                        // Errors always stay on the terminal
                        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                        
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

    // NEW HELPER: Handles printing to terminal OR saving to a file
    private static void writeOutput(String output, String redirectFile) {
        if (redirectFile != null) {
            try {
                File f = new File(redirectFile);
                if (f.getParentFile() != null) f.getParentFile().mkdirs();
                Files.writeString(f.toPath(), output + "\n");
            } catch (Exception e) {
                System.out.println("Error writing to file");
            }
        } else {
            System.out.println(output);
        }
    }

    // UNCHANGED PARSER
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
            } else if (c == '\\') {
                if (inSingleQuotes) {
                    currentArg.append(c);
                } else if (inDoubleQuotes) {
                    if (i + 1 < input.length()) {
                        char next = input.charAt(i + 1);
                        if (next == '"' || next == '\\') {
                            currentArg.append(next);
                            i++; 
                        } else {
                            currentArg.append(c); 
                        }
                    } else {
                        currentArg.append(c);
                    }
                } else {
                    if (i + 1 < input.length()) {
                        currentArg.append(input.charAt(i + 1));
                        i++; 
                    }
                }
                inArg = true;
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

    // UNCHANGED HELPER
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