package top.ilovemyhome.zora.common.system;

import java.io.IOException;


public class SystemCommandChecker {

    public static boolean isCommandAvailable(String command) {
        String[] checkCommands;
        switch (OSUtil.OS_TYPE){
            case LINUX, MAC -> checkCommands = new String[]{"which", command};
            case WINDOWS -> checkCommands = new String[]{"where", command};
            default -> throw new IllegalStateException("Unexpected value: " + OSUtil.OS_TYPE);
        }
        try {
            Process process = new ProcessBuilder(checkCommands).start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private SystemCommandChecker() {
    }
}
