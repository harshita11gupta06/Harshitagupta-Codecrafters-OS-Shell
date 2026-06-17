import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
    private static class Job {
        int jobId;
        long pid;
        String commandString;
        Process process;
        String status; 

        Job(int jobId, long pid, String commandString, Process process) {
            this.jobId = jobId;
            this.pid = pid;
            this.commandString = commandString;
            this.process = process;
            this.status = "Running";
        }
    }

    private static final List<Job> activeJobs = new ArrayList<>();
    private static int nextJobId = 1;

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        
        while (true) {
            cleanUpFinishedJobs(); 
            System.out.print("$ ");
            if (!scanner.hasNextLine()) break;
            String input = scanner.nextLine();
            
            if (input.isEmpty()) continue;

            if (hasUnquotedPipe(input)) {
                handleMultiPipeline(input);
                continue;
            }
            
            executeSingleCommand(input);
        }
    }

    private static void cleanUpFinishedJobs() {
        for (Job job : activeJobs) {
            if (job.status.equals("Running") && !job.process.isAlive()) {
                job.status = "Done";
            }
        }
    }

    private static boolean hasUnquotedPipe(String input) {
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\'' && !inDoubleQuotes) inSingleQuotes = !inSingleQuotes;
            else if (c == '"' && !inSingleQuotes) inDoubleQuotes = !inDoubleQuotes;
            else if (c == '\\' && !inSingleQuotes && !inDoubleQuotes) i++; 
            else if (c == '|' && !inSingleQuotes && !inDoubleQuotes) return true;
        }
        return false;
    }

    private static void handleMultiPipeline(String input) throws Exception {
        List<String> rawCommands = splitByUnquotedPipe(input);
        String currentInputData = ""; 

        for (int i = 0; i < rawCommands.size(); i++) {
            String rawCmd = rawCommands.get(i).trim();
            List<String> parsedArgs = parseInput(rawCmd);
            if (parsedArgs.isEmpty()) continue;

            String cmd = parsedArgs.get(0);
            boolean isLast = (i == rawCommands.size() - 1);

            if (isBuiltin(cmd)) {
                currentInputData = executeBuiltinToBuffer(parsedArgs, currentInputData);
                if (isLast) System.out.print(currentInputData);
            } else {
                List<ProcessBuilder> builders = new ArrayList<>();
                int j = i;
                
                while (j < rawCommands.size()) {
                    String extRaw = rawCommands.get(j).trim();
                    List<String> extArgs = parseInput(extRaw);
                    if (extArgs.isEmpty()) break;
                    if (isBuiltin(extArgs.get(0))) break;
                    
                    String path = getPath(extArgs.get(0));
                    if (path == null) {
                        System.err.println(extArgs.get(0) + ": command not found");
                        return;
                    }
                    
                    ProcessBuilder pb = new ProcessBuilder(extArgs);
                    pb.directory(new File(System.getProperty("user.dir")));
                    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                    builders.add(pb);
                    j++;
                }

                if (i == 0 && currentInputData.isEmpty()) {
                    builders.get(0).redirectInput(ProcessBuilder.Redirect.INHERIT);
                } else {
                    builders.get(0).redirectInput(ProcessBuilder.Redirect.PIPE);
                }

                boolean chainEndsPipeline = (j == rawCommands.size());
                if (chainEndsPipeline) {
                    builders.get(builders.size() - 1).redirectOutput(ProcessBuilder.Redirect.INHERIT);
                } else {
                    builders.get(builders.size() - 1).redirectOutput(ProcessBuilder.Redirect.PIPE);
                }

                List<Process> processes = ProcessBuilder.startPipeline(builders);

                final String finalInputData = currentInputData;
                Process firstProcess = processes.get(0);
                Thread inputThread = new Thread(() -> {
                    if (!finalInputData.isEmpty()) {
                        try (OutputStream os = firstProcess.getOutputStream()) {
                            os.write(finalInputData.getBytes());
                            os.flush();
                        } catch (Exception e) {}
                    }
                });
                inputThread.start();

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                Process lastProcess = processes.get(processes.size() - 1);
                Thread outputThread = new Thread(() -> {
                    if (!chainEndsPipeline) {
                        try (InputStream is = lastProcess.getInputStream()) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = is.read(buffer)) != -1) {
                                baos.write(buffer, 0, bytesRead);
                            }
                        } catch (Exception e) {}
                    }
                });
                outputThread.start();

                lastProcess.waitFor();
                inputThread.join();
                outputThread.join();

                if (!chainEndsPipeline) currentInputData = baos.toString();
                i = j - 1;
            }
        }
    }

    private static List<String> splitByUnquotedPipe(String input) {
        List<String> parts = new ArrayList<>();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        int start = 0;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\'' && !inDoubleQuotes) inSingleQuotes = !inSingleQuotes;
            else if (c == '"' && !inSingleQuotes) inDoubleQuotes = !inDoubleQuotes;
            else if (c == '\\' && !inSingleQuotes && !inDoubleQuotes) i++;
            else if (c == '|' && !inSingleQuotes && !inDoubleQuotes) {
                parts.add(input.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(input.substring(start));
        return parts;
    }

    private static boolean isBuiltin(String cmd) {
        return cmd.equals("echo") || cmd.equals("exit") || cmd.equals("type") || 
               cmd.equals("pwd") || cmd.equals("cd") || cmd.equals("jobs");
    }

    private static String executeBuiltinToBuffer(List<String> args, String stdinContext) throws Exception {
        String cmd = args.get(0);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);

        if (cmd.equals("echo")) {
            String out = String.join(" ", args.subList(1, args.size()));
            ps.println(out);
        } else if (cmd.equals("pwd")) {
            ps.println(System.getProperty("user.dir"));
        } else if (cmd.equals("exit")) {
            System.exit(0);
        } else if (cmd.equals("jobs")) {
            // Re-evaluate if jobs are still running right before printing them
            cleanUpFinishedJobs();

            List<Job> runningJobs = new ArrayList<>();
            for (Job job : activeJobs) {
                if (job.status.equals("Running")) {
                    runningJobs.add(job);
                }
            }

            int runningCount = runningJobs.size();
            List<Job> retaining = new ArrayList<>();

            for (Job job : activeJobs) {
                char marker = ' ';
                if (job.status.equals("Running")) {
                    int runningIdx = runningJobs.indexOf(job);
                    if (runningIdx == runningCount - 1) {
                        marker = '+';
                    } else if (runningIdx == runningCount - 2) {
                        marker = '-';
                    }
                } else {
                    // For completed jobs, the most recent dead one traditionally gets '+' marker context
                    marker = '+'; 
                }

                // Format constraint handling: Do not print trailing & if the job status is Done
                if (job.status.equals("Done")) {
                    ps.printf("[%d]%c Done\t\t%s\n", job.jobId, marker, job.commandString);
                } else {
                    ps.printf("[%d]%c %s\t%s &\n", job.jobId, marker, job.status, job.commandString);
                }
                
                // Keep only still running jobs in table for subsequent 'jobs' execution calls
                if (job.status.equals("Running")) {
                    retaining.add(job);
                }
            }
            activeJobs.clear();
            activeJobs.addAll(retaining);
        } else if (cmd.equals("type")) {
            if (args.size() >= 2) {
                String target = args.get(1);
                if (isBuiltin(target)) {
                    ps.println(target + " is a shell builtin");
                } else {
                    String path = getPath(target);
                    if (path != null) ps.println(target + " is " + path);
                    else ps.println(target + ": not found");
                }
            }
        } else if (cmd.equals("cd")) {
            String dir = args.size() > 1 ? args.get(1) : "~";
            if (dir.equals("~")) {
                String homeDir = System.getenv("HOME");
                if (homeDir != null) System.setProperty("user.dir", homeDir);
            } else {
                Path currentPath = Paths.get(System.getProperty("user.dir"));
                Path newPath = currentPath.resolve(dir).normalize();
                if (Files.isDirectory(newPath)) {
                    System.setProperty("user.dir", newPath.toString());
                } else {
                    ps.println("cd: " + dir + ": No such file or directory");
                }
            }
        }
        
        return baos.toString();
    }

    private static void executeSingleCommand(String input) throws Exception {
        String commandStringToRun = input.trim();
        boolean isBackground = false;

        if (commandStringToRun.endsWith("&") && !isEndingAmperSandQuoted(commandStringToRun)) {
            isBackground = true;
            commandStringToRun = commandStringToRun.substring(0, commandStringToRun.length() - 1).trim();
        }

        List<String> parsedArgs = parseInput(commandStringToRun);
        if (parsedArgs.isEmpty()) return;

        String cleanCommandString = String.join(" ", parsedArgs);
        
        String stdoutFile = null;
        String stderrFile = null;
        boolean isAppendStdout = false; 
        boolean isAppendStderr = false; 
        
        for (int i = 0; i < parsedArgs.size(); i++) {
            String arg = parsedArgs.get(i);
            if (arg.equals(">>") || arg.equals("1>>")) {
                if (i + 1 < parsedArgs.size()) {
                    stdoutFile = parsedArgs.get(i + 1);
                    isAppendStdout = true;
                    parsedArgs.remove(i + 1); parsedArgs.remove(i); i--; 
                }
            } else if (arg.equals(">") || arg.equals("1>")) {
                if (i + 1 < parsedArgs.size()) {
                    stdoutFile = parsedArgs.get(i + 1);
                    isAppendStdout = false;
                    parsedArgs.remove(i + 1); parsedArgs.remove(i); i--; 
                }
            } else if (arg.equals("2>>")) {
                if (i + 1 < parsedArgs.size()) {
                    stderrFile = parsedArgs.get(i + 1);
                    isAppendStderr = true;
                    parsedArgs.remove(i + 1); parsedArgs.remove(i); i--; 
                }
            } else if (arg.equals("2>")) {
                if (i + 1 < parsedArgs.size()) {
                    stderrFile = parsedArgs.get(i + 1);
                    isAppendStderr = false;
                    parsedArgs.remove(i + 1); parsedArgs.remove(i); i--; 
                }
            }
        }
        
        if (parsedArgs.isEmpty()) return;

        if (stdoutFile != null) {
            File f = new File(stdoutFile);
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            if (!isAppendStdout) Files.writeString(f.toPath(), "");
            else if (!f.exists()) f.createNewFile();
        }
        if (stderrFile != null) {
            File f = new File(stderrFile);
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            if (!isAppendStderr) Files.writeString(f.toPath(), "");
            else if (!f.exists()) f.createNewFile();
        }

        String cmd = parsedArgs.get(0);
        
        if (isBuiltin(cmd)) {
            String result = executeBuiltinToBuffer(parsedArgs, "");
            if (stdoutFile != null) {
                writeOutput(result.trim(), stdoutFile, isAppendStdout);
            } else {
                System.out.print(result);
            }
        } else {
            String path = getPath(cmd);
            if (path != null) {
                try {
                    ProcessBuilder pb = new ProcessBuilder(parsedArgs);
                    pb.directory(new File(System.getProperty("user.dir")));
                    
                    if (stdoutFile != null) {
                        File f = new File(stdoutFile);
                        if (isAppendStdout) pb.redirectOutput(ProcessBuilder.Redirect.appendTo(f));
                        else pb.redirectOutput(f);
                    } else {
                        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                    }
                    
                    if (stderrFile != null) {
                        File f = new File(stderrFile);
                        if (isAppendStderr) pb.redirectError(ProcessBuilder.Redirect.appendTo(f));
                        else pb.redirectError(f);
                    } else {
                        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                    }
                    
                    Process process = pb.start();
                    
                    if (isBackground) {
                        long pid = process.pid();
                        int jobId = nextJobId++;
                        System.out.printf("[%d] %d\n", jobId, pid);
                        activeJobs.add(new Job(jobId, pid, cleanCommandString, process));
                    } else {
                        process.waitFor();
                    }
                } catch (Exception e) {
                    writeError(cmd + ": command not found", stderrFile, isAppendStderr);
                }
            } else {
                writeError(cmd + ": command not found", stderrFile, isAppendStderr);
            }
        }
    }

    private static boolean isEndingAmperSandQuoted(String input) {
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        int ampIndex = input.lastIndexOf('&');
        if (ampIndex == -1) return false;

        for (int i = 0; i < ampIndex; i++) {
            char c = input.charAt(i);
            if (c == '\'' && !inDoubleQuotes) inSingleQuotes = !inSingleQuotes;
            else if (c == '"' && !inSingleQuotes) inDoubleQuotes = !inDoubleQuotes;
            else if (c == '\\' && !inSingleQuotes && !inDoubleQuotes) i++;
        }
        return inSingleQuotes || inDoubleQuotes;
    }

    private static void writeOutput(String output, String redirectFile, boolean append) {
        if (redirectFile != null) {
            try {
                File f = new File(redirectFile);
                if (append) Files.writeString(f.toPath(), output + "\n", StandardOpenOption.APPEND);
                else Files.writeString(f.toPath(), output + "\n");
            } catch (Exception e) {
                System.out.println("Error writing to file");
            }
        } else {
            System.out.println(output);
        }
    }

    private static void writeError(String errorMsg, String redirectFile, boolean append) {
        if (redirectFile != null) {
            try {
                File f = new File(redirectFile);
                if (append) Files.writeString(f.toPath(), errorMsg + "\n", StandardOpenOption.APPEND);
                else Files.writeString(f.toPath(), errorMsg + "\n");
            } catch (Exception e) {
                System.out.println("Error writing to file");
            }
        } else {
            System.err.println(errorMsg);
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