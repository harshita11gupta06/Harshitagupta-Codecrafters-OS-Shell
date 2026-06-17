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
            
            String stdoutFile = null;
            String stderrFile = null;
            
            for (int i = 0; i < parsedArgs.size(); i++) {
                String arg = parsedArgs.get(i);
                if (arg.equals(">") || arg.equals("1>")) {
                    if (i + 1 < parsedArgs.size()) {
                        stdoutFile = parsedArgs.get(i + 1);
                        parsedArgs.remove(i + 1);
                        parsedArgs.remove(i);
                        i--; 
                    }
                } else if (arg.equals("2>")) {
                    if (i + 1 < parsedArgs.size()) {
                        stderrFile = parsedArgs.get(i + 1);
                        parsedArgs.remove(i + 1);
                        parsedArgs.remove(i);
                        i--; 
                    }
                }
            }
            
            if (parsedArgs.isEmpty()) continue;

            // NEW FIX: Create the files immediately so they exist even if no output/error is written!
            if (stdoutFile != null) {
                File f = new File(stdoutFile);
                if (f.getParentFile() != null) f.getParentFile().mkdirs();
                Files.writeString(f.toPath(), "");
            }
            if (stderrFile != null) {
                File f = new File(stderrFile);
                if (f.getParentFile() != null) f.getParentFile().mkdirs();
                Files.writeString(f.toPath(), "");
            }

            String cmd = parsedArgs.get(0);
            
            if (cmd.equals("exit")) {
                break;
            } else if (cmd.equals("echo")) {
                String out = String.join(" ", parsedArgs.subList(1, parsedArgs.size()));
                writeOutput(out, stdoutFile);
            } else if (cmd.equals("pwd")) {
                writeOutput(System.getProperty("user.dir"), stdoutFile);
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
                        writeError("cd: " + dir + ": No such file or directory", stderrFile);
                    }
                }
            } else if (cmd.equals("type")) {
                if (parsedArgs.size() < 2) continue;
                String target = parsedArgs.get(1);
                
                if (target.equals("echo") || target.equals("exit") || target.equals("type") || target.equals("pwd") || target.equals("cd")) {
                    writeOutput(target + " is a shell builtin", stdoutFile);
                } else {
                    String path = getPath(target);
                    if (path != null) {
                        writeOutput(target + " is " + path, stdoutFile);
                    } else {
                        writeError(target + ": not found", stderrFile);
                    }
                }
            } else {
                String path = getPath(cmd);
                if (path != null) {
                    try {
                        ProcessBuilder pb = new ProcessBuilder(parsedArgs);
                        pb.directory(new File(System.getProperty("user.dir")));
                        
                        if (stdoutFile != null) {
                            File f = new File(stdoutFile);
                            pb.redirectOutput(f);
                        } else {
                            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                        }
                        
                        if (stderrFile != null) {
                            File f = new File(stderrFile);
                            pb.redirectError(f);
                        } else {
                            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                        }
                        
                        Process process = pb.start();
                        process.waitFor();
                    } catch (Exception e) {
                        writeError(cmd + ": command not found", stderrFile);
                    }
                } else {
                    writeError(cmd + ": command not found", stderrFile);
                }
            }
        }
    }

    private static void writeOutput(String output, String redirectFile) {
        if (redirectFile != null) {
            try {
                File f = new File(redirectFile);
                Files.writeString(f.toPath(), output + "\n");
            } catch (Exception e) {
                System.out.println("Error writing to file");
            }
        } else {
            System.out.println(output);
        }
    }

    private static void writeError(String errorMsg, String redirectFile) {
        if (redirectFile != null) {
            try {
                File f = new File(redirectFile);
                Files.writeString(f.toPath(), errorMsg + "\n");
            } catch (Exception e) {
                System.out.println("Error writing to file");
            }
        } else {
            System.out.println(errorMsg);
        }
    }

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