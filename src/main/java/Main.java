import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

public class Main {
    private static class BackgroundJob {
        int id;
        long pid;
        String commandString;
        Process process;

        BackgroundJob(int id, long pid, String commandString, Process process) {
            this.id = id;
            this.pid = pid;
            this.commandString = commandString;
            this.process = process;
        }
    }

    private static final List<BackgroundJob> activeJobs = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        
        while (true) {
            // --- AUTOMATIC REAPING ---
            int totalJobsBeforeReap = activeJobs.size();
            Iterator<BackgroundJob> reapIterator = activeJobs.iterator();
            int reapIndex = 0;
            
            while (reapIterator.hasNext()) {
                BackgroundJob job = reapIterator.next();
                
                if (!job.process.isAlive()) {
                    String marker = " ";
                    if (reapIndex == totalJobsBeforeReap - 1) {
                        marker = "+";
                    } else if (reapIndex == totalJobsBeforeReap - 2) {
                        marker = "-";
                    }
                    
                    String baseCmd = job.commandString;
                    if (baseCmd.endsWith(" &")) {
                        baseCmd = baseCmd.substring(0, baseCmd.length() - 2).trim();
                    } else if (baseCmd.endsWith("&")) {
                        baseCmd = baseCmd.substring(0, baseCmd.length() - 1).trim();
                    }
                    
                    System.out.println("[" + job.id + "]" + marker + "  Done                 " + baseCmd);
                    reapIterator.remove();
                    totalJobsBeforeReap--;
                } else {
                    reapIndex++;
                }
            }

            System.out.print("$ ");
            if (!scanner.hasNextLine()) {
                break;
            }
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

            String fullCommandString = input;

            boolean isBackground = false;
            String remainingCommand = commandPart;
            if (remainingCommand.endsWith("&")) {
                isBackground = true;
                remainingCommand = remainingCommand.substring(0, remainingCommand.length() - 1).trim();
            }

            // --- PIPELINE HANDLING ---
            if (remainingCommand.contains("|")) {
                String[] pipeParts = remainingCommand.split("\\|");
                List<ProcessBuilder> builders = new ArrayList<>();
                boolean pipelineFailed = false;

                for (int i = 0; i < pipeParts.length; i++) {
                    String stageStr = pipeParts[i].trim();
                    List<String> cmdTokens = parseArguments(stageStr);
                    if (cmdTokens.isEmpty()) continue;
                    
                    String cmd = cmdTokens.get(0);
                    boolean isLastStage = (i == pipeParts.length - 1);

                    ProcessBuilder pb;
                    // If it's a built-in, run it via sh -c so ProcessBuilder can pipeline it natively
                    if (cmd.equals("echo") || cmd.equals("type") || cmd.equals("pwd") || cmd.equals("jobs")) {
                        pb = new ProcessBuilder("sh", "-c", stageStr);
                    } else {
                        String exePath = findInPath(cmd, System.getenv("PATH"));
                        if (exePath == null) {
                            System.out.println(cmd + ": command not found");
                            pipelineFailed = true;
                            break;
                        }
                        pb = new ProcessBuilder(cmdTokens);
                    }

                    pb.directory(new File(System.getProperty("user.dir")));

                    // Apply standard file redirections to the absolute final stage of the pipe
                    if (isLastStage && isRedirect) {
                        File targetFile = new File(outputFile);
                        ProcessBuilder.Redirect fileRedirect = shouldAppend ? 
                                ProcessBuilder.Redirect.appendTo(targetFile) : 
                                ProcessBuilder.Redirect.to(targetFile);
                        if (isStderr) {
                            pb.redirectError(fileRedirect);
                        } else {
                            pb.redirectOutput(fileRedirect);
                        }
                    } else if (isLastStage) {
                        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                    }

                    builders.add(pb);
                }

                if (!pipelineFailed && !builders.isEmpty()) {
                    try {
                        // Launch native OS pipeline chain seamlessly
                        List<Process> pipeline = ProcessBuilder.startPipeline(builders);
                        Process lastProcess = pipeline.get(pipeline.size() - 1);

                        if (isBackground) {
                            int assignedJobId = 1;
                            while (true) {
                                boolean idTaken = false;
                                for (BackgroundJob j : activeJobs) {
                                    if (j.id == assignedJobId) { idTaken = true; break; }
                                }
                                if (!idTaken) break;
                                assignedJobId++;
                            }
                            System.out.println("[" + assignedJobId + "] " + lastProcess.pid());
                            activeJobs.add(new BackgroundJob(assignedJobId, lastProcess.pid(), fullCommandString, lastProcess));
                        } else {
                            lastProcess.waitFor();
                            // Terminate any stuck long-running stages (like tail -f) once the end process terminates
                            for (Process p : pipeline) {
                                if (p.isAlive()) {
                                    p.destroy();
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("Pipeline execution failed");
                    }
                }
                continue;
            }

            // --- SINGLE STANDALONE COMMANDS ---
            List<String> tokens = parseArguments(remainingCommand);
            if (tokens.isEmpty()) {
                continue;
            }
            String command = tokens.get(0);

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
                if (targetDir.equals("~")) {
                    targetDir = System.getenv("HOME");
                }
                File directory = targetDir.startsWith("/") ? new File(targetDir) : new File(System.getProperty("user.dir"), targetDir);
                if (directory.exists() && directory.isDirectory()) {
                    System.setProperty("user.dir", directory.getCanonicalPath());
                } else {
                    shellOut.accept("cd: " + targetDir + ": No such file or directory");
                }
            }
            else if (command.equals("jobs")) {
                int totalJobs = activeJobs.size();
                Iterator<BackgroundJob> it = activeJobs.iterator();
                int currentIndex = 0;
                
                while (it.hasNext()) {
                    BackgroundJob job = it.next();
                    String marker = " ";
                    if (currentIndex == totalJobs - 1) {
                        marker = "+";
                    } else if (currentIndex == totalJobs - 2) {
                        marker = "-";
                    }
                    
                    String baseCmd = job.commandString;
                    if (baseCmd.endsWith(" &")) {
                        baseCmd = baseCmd.substring(0, baseCmd.length() - 2).trim();
                    } else if (baseCmd.endsWith("&")) {
                        baseCmd = baseCmd.substring(0, baseCmd.length() - 1).trim();
                    }

                    if (job.process.isAlive()) {
                        shellOut.accept("[" + job.id + "]" + marker + "   Running                 " + baseCmd + " &");
                        currentIndex++;
                    } else {
                        shellOut.accept("[" + job.id + "]" + marker + "  Done                 " + baseCmd);
                        it.remove(); 
                        totalJobs--;
                    }
                }
            }
            else if (command.equals("type")) {
                if (tokens.size() > 1) {
                    String commandToCheck = tokens.get(1);
                    if (commandToCheck.equals("echo") || commandToCheck.equals("exit") || 
                        commandToCheck.equals("type") || commandToCheck.equals("pwd") || 
                        commandToCheck.equals("cd") || commandToCheck.equals("jobs")) {
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
                        
                        if (isBackground) {
                            int assignedJobId = 1;
                            while (true) {
                                boolean idTaken = false;
                                for (BackgroundJob j : activeJobs) {
                                    if (j.id == assignedJobId) { idTaken = true; break; }
                                }
                                if (!idTaken) break;
                                assignedJobId++;
                            }
                            System.out.println("[" + assignedJobId + "] " + process.pid());
                            activeJobs.add(new BackgroundJob(assignedJobId, process.pid(), fullCommandString, process));
                        } else {
                            process.waitFor();
                        }
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