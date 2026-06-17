import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        
        while (true) {
            System.out.print("$ ");
            String input = scanner.nextLine();
            
            if (input.equals("exit")) {
                break;
            } else if (input.startsWith("echo ")) {
                System.out.println(input.substring(5));
            } else if (input.equals("pwd")) {
                System.out.println(System.getProperty("user.dir"));
            } else if (input.startsWith("cd ")) {
                String dir = input.substring(3);
                
                // NEW: Calculate the relative or absolute path based on where we currently are
                Path currentPath = Paths.get(System.getProperty("user.dir"));
                Path newPath = currentPath.resolve(dir).normalize();
                
                if (Files.isDirectory(newPath)) {
                    System.setProperty("user.dir", newPath.toString());
                } else {
                    System.out.println("cd: " + dir + ": No such file or directory");
                }
            } else if (input.startsWith("type ")) {
                String cmd = input.substring(5);
                
                if (cmd.equals("echo") || cmd.equals("exit") || cmd.equals("type") || cmd.equals("pwd") || cmd.equals("cd")) {
                    System.out.println(cmd + " is a shell builtin");
                } else {
                    String path = getPath(cmd);
                    if (path != null) {
                        System.out.println(cmd + " is " + path);
                    } else {
                        System.out.println(cmd + ": not found");
                    }
                }
            } else {
                String[] parts = input.split(" ");
                String cmd = parts[0];
                String path = getPath(cmd);
                
                if (path != null) {
                    try {
                        ProcessBuilder pb = new ProcessBuilder(parts);
                        pb.directory(new File(System.getProperty("user.dir")));
                        pb.inheritIO();
                        Process process = pb.start();
                        process.waitFor();
                    } catch (Exception e) {
                        System.out.println(input + ": command not found");
                    }
                } else {
                    System.out.println(input + ": command not found");
                }
            }
        }
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