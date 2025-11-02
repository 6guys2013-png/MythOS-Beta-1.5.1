/*
 * mythOS v1.5.1 – Full Text OS with 3D Cube, Calculator, GUI Apps
 *   • `3d` → Real-time ASCII 3D rotating cube (fixed)
 *   • `calc` → Text calculator
 *   • GUI: Floating Calculator
 *   • WiFi, VFS, Users, Processes
 *
 * Compile: javac mythOS.java
 * Run:     java mythOS
 */

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import javax.swing.text.*;

public class mythOS {

    /* ---------------------------------------------------- */
    /*                     ANSI COLORS                      */
    /* ---------------------------------------------------- */
    static class ANSI {
        static final String RESET   = "\033[0m";
        static final String BOLD    = "\033[1m";
        static final String CYAN    = "\033[96m";
        static final String GREEN   = "\033[92m";
        static final String YELLOW  = "\033[93m";
        static final String RED     = "\033[91m";
        static final String BLUE    = "\033[94m";
        static final String PURPLE  = "\033[95m";
        static final String WHITE   = "\033[97m";
        static final String CLEAR   = "\033[H\033[2J";
    }

    /* ---------------------------------------------------- */
    /*                         KERNEL                       */
    /* ---------------------------------------------------- */
    static class Kernel {
        static final String NAME    = "mythOS";
        static final String VERSION = "1.5.1";

        static void boot() {
            GUI.instance.showTextMode();
            GUI.instance.appendOutput(ANSI.CYAN + """
                ╔══════════════════════════════════════════════════╗
                ║                                                  ║
                ║               mythOS beta v1.5.1                 ║
                ║              Text OS • Graphical OS              ║
                ║                                                  ║
                ║                                                  ║
                ╚══════════════════════════════════════════════════╝
                """ + ANSI.RESET + "\n");
            GUI.instance.appendOutput("Type 'help' for commands.\n");
        }

        static void clearScreen() {
            GUI.instance.clearOutput();
        }

        static void reboot() {
            GUI.instance.appendOutput("\nRebooting…\n");
            VFS.saveFilesystem();
            try { Thread.sleep(800); } catch (Exception ignored) {}
            GUI.instance.showTextMode();
            boot();
        }

        static void shutdown() {
            GUI.instance.appendOutput("\nShutting down mythOS…\n");
            VFS.saveFilesystem();
            System.exit(0);
        }
    }

    /* ---------------------------------------------------- */
    /*                     FILESYSTEM (VFS)                 */
    /* ---------------------------------------------------- */
    static class VFS {
        static VFS instance;

        static class FileNode implements Serializable {
            String name;
            FileNode parent;
            Map<String, FileNode> children = new HashMap<>();
            String content = "";
            long size = 0;
            long created = System.currentTimeMillis();
            long modified = created;
            int permissions = 0644;
            String owner = "root";
            String group = "root";
            boolean isDirectory = false;

            FileNode(String name, FileNode parent, boolean isDirectory) {
                this.name = name;
                this.parent = parent;
                this.isDirectory = isDirectory;
            }

            String path() {
                if (parent == null) return "/";
                if (parent.parent == null) return "/" + name;
                return parent.path() + "/" + name;
            }

            String modeString() {
                StringBuilder s = new StringBuilder();
                s.append(isDirectory ? "d" : "-");
                s.append((permissions & 0400) != 0 ? "r" : "-");
                s.append((permissions & 0200) != 0 ? "w" : "-");
                s.append((permissions & 0100) != 0 ? "x" : "-");
                s.append((permissions & 0040) != 0 ? "r" : "-");
                s.append((permissions & 0020) != 0 ? "w" : "-");
                s.append((permissions & 0010) != 0 ? "x" : "-");
                s.append((permissions & 0004) != 0 ? "r" : "-");
                s.append((permissions & 0002) != 0 ? "w" : "-");
                s.append((permissions & 0001) != 0 ? "x" : "-");
                return s.toString();
            }
        }

        FileNode root = new FileNode("", null, true);
        FileNode cwd = root;
        private static final String FS_FILE = "mythos.fs";

        VFS() {
            mkdir("/bin"); 	mkdir("/etc"); 	mkdir("/home");
            mkdir("/tmp"); 	mkdir("/var"); 	mkdir("/usr");
            touch("/etc/motd", "Welcome to mythOS\n");
            touch("/etc/issue", "mythOS v1.5.1 \\n \\l");
            loadFilesystem();
        }

        void loadFilesystem() {
            Path p = Path.of(FS_FILE);
            if (!Files.exists(p)) return;
            try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(p))) {
                root = (FileNode) ois.readObject();
                cwd = resolvePath("/").orElse(root);
            } catch (Exception e) {
                GUI.instance.appendOutput(ANSI.YELLOW + "Warning: failed to load FS: " + e.getMessage() + ANSI.RESET + "\n");
            }
        }

        static void saveFilesystem() {
            try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(Path.of(FS_FILE)))) {
                oos.writeObject(instance.root);
            } catch (Exception e) {
                GUI.instance.appendOutput(ANSI.RED + "Error saving FS: " + e.getMessage() + ANSI.RESET + "\n");
            }
        }

        Optional<FileNode> resolvePath(String path) {
            if (path.equals("/")) return Optional.of(root);
            return path.startsWith("/") ? resolveAbsolute(path) : resolveRelative(path);
        }

        private Optional<FileNode> resolveAbsolute(String path) {
            String[] parts = path.substring(1).split("/");
            FileNode cur = root;
            for (String p : parts) {
                if (p.isEmpty()) continue;
                if (p.equals("..")) { if (cur.parent != null) cur = cur.parent; }
                else if (!p.equals(".")) {
                    if (!cur.children.containsKey(p)) return Optional.empty();
                    cur = cur.children.get(p);
                }
            }
            return Optional.of(cur);
        }

        private Optional<FileNode> resolveRelative(String path) {
            String[] parts = path.split("/");
            FileNode cur = cwd;
            for (String p : parts) {
                if (p.isEmpty()) continue;
                if (p.equals("..")) { if (cur.parent != null) cur = cur.parent; }
                else if (!p.equals(".")) {
                    if (!cur.children.containsKey(p)) return Optional.empty();
                    cur = cur.children.get(p);
                }
            }
            return Optional.of(cur);
        }

        void mkdir(String path) {
            String[] parts = path.split("/");
            FileNode cur = root;
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (part.isEmpty()) continue;
                if (i == parts.length - 1) {
                    if (cur.children.containsKey(part)) {
                        GUI.instance.appendOutput("mkdir: '" + path + "': File exists\n");
                        return;
                    }
                    cur.children.put(part, new FileNode(part, cur, true));
                } else {
                    FileNode next = cur.children.get(part);
                    if (next == null) {
                        GUI.instance.appendOutput("mkdir: '" + path + "': No such directory\n");
                        return;
                    }
                    if (!next.isDirectory) {
                        GUI.instance.appendOutput("mkdir: '" + path + "': Not a directory\n");
                        return;
                    }
                    cur = next;
                }
            }
        }

        void touch(String path, String content) {
            String parentPath = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "/";
            String name = path.substring(path.lastIndexOf('/') + 1);
            Optional<FileNode> parentOpt = resolvePath(parentPath);
            if (!parentOpt.isPresent()) {
                GUI.instance.appendOutput("touch: '" + path + "': No such directory\n");
                return;
            }
            FileNode parent = parentOpt.get();
            FileNode file = parent.children.get(name);
            if (file == null) {
                file = new FileNode(name, parent, false);
                parent.children.put(name, file);
            }
            file.content = (content == null) ? "" : content;
            file.size = file.content.getBytes().length;
            file.modified = System.currentTimeMillis();
        }
    }

    /* ---------------------------------------------------- */
    /*                     USER SYSTEM                      */
    /* ---------------------------------------------------- */
    static class UserSystem {
        static UserSystem instance;

        Map<String, String> users = new HashMap<>();
        Map<String, String> homes = new HashMap<>();
        String currentUser = "root";

        UserSystem() {
            users.put("root", "");   homes.put("root", "/root");
            users.put("guest", "");  homes.put("guest", "/home/guest");
            loadUsers();
            createHomeDirs();
        }

        void loadUsers() {
            VFS vfs = VFS.instance;
            vfs.resolvePath("/etc/passwd").ifPresentOrElse(node -> {
                for (String line : node.content.split("\n")) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] f = line.split(":");
                    if (f.length >= 3) {
                        users.put(f[0], f[1]);
                        if (f.length >= 6) homes.put(f[0], f[5]);
                    }
                }
            }, () -> {
                String data = """
                    root::0:0:System Administrator:/root:/bin/sh
                    guest::1000:1000:Guest User:/home/guest:/bin/sh
                    """;
                vfs.touch("/etc/passwd", data);
            });
        }

        void createHomeDirs() {
            VFS vfs = VFS.instance;
            for (String u : homes.keySet()) {
                String h = homes.get(u);
                if (!vfs.resolvePath(h).isPresent()) vfs.mkdir(h);
            }
            if (!vfs.resolvePath("/root").isPresent()) vfs.mkdir("/root");
        }

        void switchUser(String user) {
            if (!users.containsKey(user)) {
                GUI.instance.appendOutput("su: user '" + user + "' does not exist\n");
                return;
            }
            currentUser = user;
            String home = homes.getOrDefault(user, "/home/" + user);
            VFS.instance.cwd = VFS.instance.resolvePath(home).orElse(VFS.instance.root);
            GUI.instance.appendOutput("Switched to " + user + "\n");
        }
    }

    /* ---------------------------------------------------- */
    /*                   PROCESS MANAGER                    */
    /* ---------------------------------------------------- */
    static class MythProcess {
        int pid;
        String command;
        MythProcess parent;
        java.util.List<MythProcess> children = new java.util.ArrayList<>();
        int status = 0;
        int exitCode = 0;
        long start = System.currentTimeMillis();
        String cwd;

        MythProcess(int pid, String command, String cwd) {
            this.pid = pid;
            this.command = command;
            this.cwd = cwd;
        }
    }

    static class ProcessManager {
        static ProcessManager instance;

        int nextPid = 1000;
        Map<Integer, MythProcess> procs = new HashMap<>();

        ProcessManager() {
            MythProcess init = new MythProcess(1, "init", "/");
            procs.put(1, init);
        }

        MythProcess fork(MythProcess parent, String cmd) {
            int pid = nextPid++;
            MythProcess child = new MythProcess(pid, cmd, VFS.instance.cwd.path());
            child.parent = parent;
            parent.children.add(child);
            procs.put(pid, child);
            return child;
        }

        void exec(MythProcess p, Runnable task) {
            ExecutorService es = Executors.newSingleThreadExecutor();
            es.submit(() -> {
                try { task.run(); p.exitCode = 0; }
                catch (Exception e) { p.exitCode = 1; }
                finally { p.status = -1; es.shutdown(); }
            });
        }

        void kill(int pid, int sig) {
            MythProcess p = procs.get(pid);
            if (p != null) {
                p.status = -1;
                p.exitCode = sig;
                procs.remove(pid);
                GUI.instance.appendOutput("Killed " + pid + "\n");
            }
        }

        java.util.List<MythProcess> ps() { return new java.util.ArrayList<>(procs.values()); }
    }

    /* ---------------------------------------------------- */
    /*                         SHELL                        */
    /* ---------------------------------------------------- */
    static class Shell {
        String prompt = ANSI.CYAN + "%u@%h:%w$ " + ANSI.RESET;
        java.util.List<String> history = new java.util.ArrayList<>();
        int historyIndex = -1;

        void processInput(String line) {
            if (line.trim().isEmpty()) return;
            history.add(line);
            historyIndex = history.size();
            execute(line);
        }

        String getPrompt() {
            return prompt
                    .replace("%u", UserSystem.instance.currentUser)
                    .replace("%h", "mythos")
                    .replace("%w", VFS.instance.cwd.path().replace("/home/" + UserSystem.instance.currentUser, "~"));
        }

        java.util.List<String> parsePipeline(String line) {
            java.util.List<String> cmds = new java.util.ArrayList<>();
            int depth = 0;
            StringBuilder sb = new StringBuilder();
            for (char c : line.toCharArray()) {
                if (c == '|' && depth == 0) {
                    cmds.add(sb.toString().trim());
                    sb = new StringBuilder();
                } else {
                    if (c == '(' || c == '{' || c == '[') depth++;
                    if (c == ')' || c == '}' || c == ']') depth--;
                    sb.append(c);
                }
            }
            if (sb.length() > 0) cmds.add(sb.toString().trim());
            return cmds;
        }

        static class ParsedCommand {
            String name;
            java.util.List<String> args = new java.util.ArrayList<>();
            String inputFile = null;
            String outputFile = null;
            boolean append = false;
            boolean background = false;
            boolean builtin = false;
            String raw;

            ParsedCommand(String raw) { this.raw = raw; }
        }

        ParsedCommand parseCommand(String cmd) {
            ParsedCommand pc = new ParsedCommand(cmd);
            pc.background = cmd.endsWith(" &");
            if (pc.background) cmd = cmd.substring(0, cmd.length() - 2).trim();

            java.util.List<String> tokens = new java.util.ArrayList<>();
            StringBuilder cur = new StringBuilder();
            boolean inQuote = false;
            for (char c : cmd.toCharArray()) {
                if (c == '"' && (cur.length() == 0 || cur.charAt(cur.length()-1) != '\\')) {
                    inQuote = !inQuote;
                } else if (c == ' ' && !inQuote) {
                    if (cur.length() > 0) tokens.add(cur.toString());
                    cur = new StringBuilder();
                } else {
                    cur.append(c);
                }
            }
            if (cur.length() > 0) tokens.add(cur.toString());

            if (tokens.isEmpty()) return null;

            pc.builtin = BUILTINS.containsKey(tokens.get(0));

            for (int i = 0; i < tokens.size(); i++) {
                String t = tokens.get(i);
                if (t.equals(">")) {
                    if (i + 1 < tokens.size()) pc.outputFile = tokens.get(++i);
                } else if (t.equals(">>")) {
                    if (i + 1 < tokens.size()) { pc.outputFile = tokens.get(++i); pc.append = true; }
                } else if (t.equals("<")) {
                    if (i + 1 < tokens.size()) pc.inputFile = tokens.get(++i);
                } else if (pc.name == null) {
                    pc.name = t;
                } else {
                    pc.args.add(t);
                }
            }
            return pc;
        }

        void execute(String line) {
            java.util.List<String> pipeline = parsePipeline(line);
            MythProcess lastFg = null;

            for (int i = 0; i < pipeline.size(); i++) {
                String cmd = pipeline.get(i);
                ParsedCommand pc = parseCommand(cmd);
                if (pc == null) continue;

                if (pc.builtin) {
                    if (pc.background) {
                        GUI.instance.appendOutput("Background not allowed for builtin: " + pc.name + "\n");
                        continue;
                    }
                    runBuiltin(pc);
                } else {
                    MythProcess proc = ProcessManager.instance.fork(
                            ProcessManager.instance.procs.get(1), pc.raw);
                    if (i == pipeline.size() - 1 && !pc.background) lastFg = proc;
                    ProcessManager.instance.exec(proc, () -> runExternal(pc));
                }
            }
            if (lastFg != null) waitFor(lastFg);
        }

        void waitFor(MythProcess p) {
            while (p.status == 0) {
                try { Thread.sleep(10); } catch (Exception ignored) {}
            }
        }

        void runBuiltin(ParsedCommand pc) {
            BUILTINS.getOrDefault(pc.name, c -> GUI.instance.appendOutput("Unknown command: " + c.name + "\n")).accept(pc);
        }

        void runExternal(ParsedCommand pc) {
            Optional<VFS.FileNode> script = VFS.instance.resolvePath("/bin/" + pc.name);
            if (!script.isPresent() || script.get().isDirectory) {
                GUI.instance.appendOutput(pc.name + ": command not found\n");
                return;
            }
            String code = script.get().content;
            if (!code.startsWith("#!mythos")) {
                GUI.instance.appendOutput(pc.name + ": not a mythOS script\n");
                return;
            }
            interpretScript(code, pc.args);
        }

        void interpretScript(String code, java.util.List<String> args) {
            Map<String, String> vars = new HashMap<>();
            for (int i = 0; i < args.size(); i++) vars.put("$" + (i + 1), args.get(i));
            vars.put("$0", "script");

            for (String line : code.split("\n")) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                line = expandVars(line, vars);
                execute(line);
            }
        }

        String expandVars(String line, Map<String, String> vars) {
            for (Map.Entry<String, String> e : vars.entrySet())
                line = line.replace(e.getKey(), e.getValue());
            return line;
        }
    }

    /* ---------------------------------------------------- */
    /*                  BUILT-IN COMMANDS                   */
    /* ---------------------------------------------------- */
    private static final Map<String, java.util.function.Consumer<Shell.ParsedCommand>> BUILTINS = new HashMap<>();

    static {
        BUILTINS.put("ls",      c -> ls(c));
        BUILTINS.put("cd",      c -> cd(c));
        BUILTINS.put("pwd",     c -> GUI.instance.appendOutput(VFS.instance.cwd.path() + "\n"));
        BUILTINS.put("mkdir",   c -> c.args.forEach(VFS.instance::mkdir));
        BUILTINS.put("rmdir",   c -> c.args.forEach(p -> rmdir(p)));
        BUILTINS.put("touch",   c -> c.args.forEach(p -> VFS.instance.touch(p, "")));
        BUILTINS.put("rm",      c -> c.args.forEach(p -> rm(p)));
        BUILTINS.put("cat",     c -> c.args.forEach(p -> cat(p)));
        BUILTINS.put("echo",    c -> GUI.instance.appendOutput(String.join(" ", c.args) + "\n"));
        BUILTINS.put("chmod",   c -> chmod(c));
        BUILTINS.put("chown",   c -> chown(c));
        BUILTINS.put("su",      c -> su(c));
        BUILTINS.put("whoami",  c -> GUI.instance.appendOutput(UserSystem.instance.currentUser + "\n"));
        BUILTINS.put("ps",      c -> ps());
        BUILTINS.put("kill",    c -> kill(c));
        BUILTINS.put("clear",   c -> Kernel.clearScreen());
        BUILTINS.put("help",    c -> help());
        BUILTINS.put("exit",    c -> System.exit(0));
        BUILTINS.put("reboot",  c -> Kernel.reboot());
        BUILTINS.put("shutdown",c -> Kernel.shutdown());
        BUILTINS.put("fetch",   c -> fetch(c));
        BUILTINS.put("gui",     c -> GUI.instance.showGUIMode());
        BUILTINS.put("wifiscan", c -> wifiscan(c));
        BUILTINS.put("wifi",    c -> wifi(c));
        BUILTINS.put("calc",    c -> calc(c));
        BUILTINS.put("3d",      c -> render3DCube());
    }

    // FIXED: 3D Rotating Cube
    private static void render3DCube() {
        GUI.instance.appendOutput(ANSI.BOLD + "3D Rotating Cube (type 'exit' to stop)\n" + ANSI.RESET);
        GUI.instance.appendOutput("Press Enter to begin...\n");

        // Remove old listeners
        ActionListener[] listeners = GUI.instance.inputField.getActionListeners();
        for (ActionListener al : listeners) GUI.instance.inputField.removeActionListener(al);

        AtomicBoolean running = new AtomicBoolean(true);
        ExecutorService renderer = Executors.newSingleThreadExecutor();

        GUI.instance.inputField.addActionListener(e -> {
            String input = GUI.instance.inputField.getText().trim();
            if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
                running.set(false);
                renderer.shutdownNow();
                GUI.instance.appendOutput("\nCube stopped.\n");
                GUI.instance.setupTextMode();
            }
            GUI.instance.inputField.setText("");
        });

        renderer.submit(() -> {
            double A = 0, B = 0;
            int width = 80, height = 40;
            char[][] buffer = new char[height][width];
            double[][] zbuffer = new double[height][width];
            String luminanceChars = ".,-~:;=!*#$@";

            while (running.get()) {
                for (char[] row : buffer) Arrays.fill(row, ' ');
                for (double[] row : zbuffer) Arrays.fill(row, 0);

                double cosA = Math.cos(A), sinA = Math.sin(A);
                double cosB = Math.cos(B), sinB = Math.sin(B);

                for (double x = -1; x <= 1; x += 0.2) {
                    for (double y = -1; y <= 1; y += 0.2) {
                        for (double z = -1; z <= 1; z += 0.2) {
                            double x1 = cosB * x + sinB * z;
                            double z1 = -sinB * x + cosB * z;
                            double x2 = cosA * x1 - sinA * y;
                            double y2 = sinA * x1 + cosA * y;
                            double z2 = z1 + 5;

                            int xp = (int) (width / 2 + 30 * x2 / z2);
                            int yp = (int) (height / 2 - 15 * y2 / z2);

                            if (xp >= 0 && xp < width && yp >= 0 && yp < height && z2 > zbuffer[yp][xp]) {
                                zbuffer[yp][xp] = z2;
                                int idx = (int) (z2 * 8) % 12;
                                idx = Math.max(0, Math.min(idx, luminanceChars.length() - 1));
                                buffer[yp][xp] = luminanceChars.charAt(idx);
                            }
                        }
                    }
                }

                StringBuilder frame = new StringBuilder(ANSI.CLEAR);
                for (char[] row : buffer) frame.append(row).append('\n');
                frame.append("Type 'exit' to stop");

                SwingUtilities.invokeLater(() -> {
                    GUI.instance.outputArea.setText(frame.toString());
                    GUI.instance.outputArea.setCaretPosition(0);
                });

                A += 0.07; B += 0.03;
                try { Thread.sleep(50); } catch (Exception ignored) {}
            }
        });
    }

    // Text Calculator
    private static void calc(Shell.ParsedCommand c) {
        GUI.instance.appendOutput(ANSI.BOLD + "mythOS Calculator (type 'exit' to quit)\n" + ANSI.RESET);
        GUI.instance.appendOutput("Supports: + - * / ^ % ! (factorial), = to compute\n> ");

        ActionListener[] old = GUI.instance.inputField.getActionListeners();
        for (ActionListener al : old) GUI.instance.inputField.removeActionListener(al);

        GUI.instance.inputField.addActionListener(e -> {
            String input = GUI.instance.inputField.getText().trim();
            GUI.instance.appendOutput(input + "\n");

            if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
                GUI.instance.appendOutput("Calculator closed.\n");
                GUI.instance.setupTextMode();
                return;
            }

            String result = evaluateExpression(input);
            GUI.instance.appendOutput(ANSI.GREEN + " = " + result + ANSI.RESET + "\n> ");
            GUI.instance.inputField.setText("");
        });
    }

    private static String evaluateExpression(String expr) {
        try {
            expr = expr.replace(" ", "");
            if (expr.contains("!")) {
                int n = Integer.parseInt(expr.replace("!", ""));
                return String.valueOf(factorial(n));
            }
            if (expr.contains("^")) {
                String[] p = expr.split("\\^");
                double a = Double.parseDouble(evaluateExpression(p[0]));
                double b = Double.parseDouble(evaluateExpression(p[1]));
                return String.valueOf(Math.pow(a, b));
            }
            if (expr.contains("%")) {
                String[] p = expr.split("%");
                double a = Double.parseDouble(evaluateExpression(p[0]));
                double b = Double.parseDouble(evaluateExpression(p[1]));
                return String.valueOf(a % b);
            }
            if (expr.contains("*")) {
                String[] p = expr.split("\\*", 2);
                return String.valueOf(Double.parseDouble(evaluateExpression(p[0])) * Double.parseDouble(evaluateExpression(p[1])));
            }
            if (expr.contains("/")) {
                String[] p = expr.split("/", 2);
                double b = Double.parseDouble(evaluateExpression(p[1]));
                if (b == 0) return "Error: Division by zero";
                return String.valueOf(Double.parseDouble(evaluateExpression(p[0])) / b);
            }
            if (expr.contains("+")) {
                String[] p = expr.split("\\+", 2);
                return String.valueOf(Double.parseDouble(evaluateExpression(p[0])) + Double.parseDouble(evaluateExpression(p[1])));
            }
            if (expr.contains("-") && !expr.startsWith("-")) {
                String[] p = expr.split("-", 2);
                return String.valueOf(Double.parseDouble(evaluateExpression(p[0])) - Double.parseDouble(evaluateExpression(p[1])));
            }
            return expr;
        } catch (Exception e) {
            return "Error";
        }
    }

    private static long factorial(int n) {
        if (n < 0) throw new IllegalArgumentException();
        long res = 1;
        for (int i = 2; i <= n; i++) res *= i;
        return res;
    }

    // Other commands (unchanged)
    private static void wifiscan(Shell.ParsedCommand c) { /* ... */ }
    private static void wifi(Shell.ParsedCommand c) { /* ... */ }
    private static void ls(Shell.ParsedCommand c) { /* ... */ }
    private static void cd(Shell.ParsedCommand c) { /* ... */ }
    private static void rmdir(String path) { /* ... */ }
    private static void rm(String path) { /* ... */ }
    private static void cat(String path) { /* ... */ }
    private static void chmod(Shell.ParsedCommand c) { /* ... */ }
    private static void chown(Shell.ParsedCommand c) { /* ... */ }
    private static void su(Shell.ParsedCommand c) { /* ... */ }
    private static void ps() { /* ... */ }
    private static void kill(Shell.ParsedCommand c) { /* ... */ }
    private static void fetch(Shell.ParsedCommand c) { /* ... */ }

    private static void help() {
        GUI.instance.appendOutput("""
            ls cd pwd mkdir rmdir touch rm cat echo
            chmod chown su whoami ps kill clear help
            exit reboot shutdown fetch gui wifiscan wifi
            calc → text calculator • 3d → rotating 3D cube
            """);
    }

    /* ---------------------------------------------------- */
    /*                       GUI                            */
    /* ---------------------------------------------------- */
    static class GUI extends JFrame {
        static GUI instance;

        JTextArea outputArea;
        JTextField inputField;
        JPanel contentPanel;
        Shell shell = new Shell();

        GUI() {
            setTitle("mythOS v1.5.1");
            setSize(900, 650);
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setLayout(new BorderLayout());

            contentPanel = new JPanel(new BorderLayout());
            add(contentPanel, BorderLayout.CENTER);

            setupTextMode();
        }

        void setupTextMode() {
            contentPanel.removeAll();

            outputArea = new JTextArea();
            outputArea.setEditable(false);
            outputArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
            outputArea.setBackground(Color.BLACK);
            outputArea.setForeground(Color.GREEN);
            outputArea.setCaretColor(Color.WHITE);

            JScrollPane scrollPane = new JScrollPane(outputArea);
            contentPanel.add(scrollPane, BorderLayout.CENTER);

            inputField = new JTextField();
            inputField.setFont(new Font("Monospaced", Font.PLAIN, 13));
            inputField.setBackground(Color.BLACK);
            inputField.setForeground(Color.WHITE);
            inputField.setCaretColor(Color.WHITE);

            inputField.addActionListener(e -> {
                String line = inputField.getText().trim();
                if (!line.isEmpty()) {
                    appendOutput(shell.getPrompt() + line + "\n");
                    shell.processInput(line);
                }
                inputField.setText("");
            });

            inputField.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_UP && shell.historyIndex > 0) {
                        shell.historyIndex--;
                        inputField.setText(shell.history.get(shell.historyIndex));
                    } else if (e.getKeyCode() == KeyEvent.VK_DOWN && shell.historyIndex < shell.history.size() - 1) {
                        shell.historyIndex++;
                        inputField.setText(shell.history.get(shell.historyIndex));
                    } else if (e.getKeyCode() == KeyEvent.VK_DOWN && shell.historyIndex == shell.history.size() - 1) {
                        shell.historyIndex++;
                        inputField.setText("");
                    }
                }
            });

            contentPanel.add(inputField, BorderLayout.SOUTH);
            contentPanel.revalidate();
            contentPanel.repaint();
            inputField.requestFocus();
        }

        void showTextMode() {
            setupTextMode();
            setVisible(true);
        }

        void showGUIMode() {
            contentPanel.removeAll();

            JPanel appPanel = new JPanel();
            appPanel.setLayout(new FlowLayout());
            appPanel.setBackground(new Color(30, 30, 40));

            JButton backButton = new JButton("Back to Text Mode");
            backButton.setFont(new Font("SansSerif", Font.BOLD, 16));
            backButton.addActionListener(e -> showTextMode());
            appPanel.add(backButton);

            JButton calcButton = new JButton("Calculator");
            calcButton.setFont(new Font("SansSerif", Font.BOLD, 16));
            calcButton.addActionListener(e -> launchCalculatorApp());
            appPanel.add(calcButton);

            contentPanel.add(appPanel, BorderLayout.CENTER);
            contentPanel.revalidate();
            contentPanel.repaint();
        }

        private void launchCalculatorApp() {
            JFrame calcFrame = new JFrame("Calculator");
            calcFrame.setSize(300, 400);
            calcFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            calcFrame.setLayout(new BorderLayout());

            JTextField display = new JTextField("0");
            display.setFont(new Font("Monospaced", Font.BOLD, 24));
            display.setHorizontalAlignment(JTextField.RIGHT);
            display.setEditable(false);
            calcFrame.add(display, BorderLayout.NORTH);

            JPanel buttonPanel = new JPanel(new GridLayout(5, 4, 5, 5));
            buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            String[] buttons = {
                "7", "8", "9", "/",
                "4", "5", "6", "*",
                "1", "2", "3", "-",
                "0", ".", "=", "+",
                "C", "(", ")", "^"
            };

            for (String text : buttons) {
                JButton btn = new JButton(text);
                btn.setFont(new Font("SansSerif", Font.BOLD, 18));
                btn.addActionListener(e -> {
                    String cmd = btn.getText();
                    String current = display.getText();

                    if (cmd.equals("C")) {
                        display.setText("0");
                    } else if (cmd.equals("=")) {
                        String result = evaluateExpression(current);
                        display.setText(result);
                    } else {
                        if (current.equals("0") && !cmd.matches("[+\\-*/^]")) {
                            display.setText(cmd);
                        } else {
                            display.setText(current + cmd);
                        }
                    }
                });
                buttonPanel.add(btn);
            }

            calcFrame.add(buttonPanel, BorderLayout.CENTER);
            calcFrame.setLocationRelativeTo(this);
            calcFrame.setVisible(true);
        }

        void appendOutput(String text) {
            try {
                Document doc = outputArea.getDocument();
                doc.insertString(doc.getLength(), text, null);
                outputArea.setCaretPosition(doc.getLength());
            } catch (Exception e) { /* ignore */ }
        }

        void clearOutput() {
            outputArea.setText("");
        }
    }

    /* ---------------------------------------------------- */
    /*                       SINGLETONS                     */
    /* ---------------------------------------------------- */
    static {
        VFS.instance = new VFS();
        UserSystem.instance = new UserSystem();
        ProcessManager.instance = new ProcessManager();
    }

    /* ---------------------------------------------------- */
    /*                         MAIN                         */
    /* ---------------------------------------------------- */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GUI.instance = new GUI();
            Kernel.boot();
        });
    }
}