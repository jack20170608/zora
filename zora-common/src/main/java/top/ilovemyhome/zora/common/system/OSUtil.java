package top.ilovemyhome.zora.common.system;

public final class OSUtil {

    private static final String OS = System.getProperty("os.name").toLowerCase();
    public static final OSType OS_TYPE = getOSType();

    public enum OSType {
        WINDOWS, MAC, UNIX, SOLARIS, LINUX, UNKNOWN
    }

    public static OSType getOSType() {
        if (isLinux()){
            return OSType.LINUX;
        } else if (isWindows()) {
            return OSType.WINDOWS;
        } else if (isMac()) {
            return OSType.MAC;
        } else if (isUnix()) {
            return OSType.UNIX;
        } else if (isSolaris()) {
            return OSType.SOLARIS;
        } else {
            return OSType.UNKNOWN;
        }
    }

    public static boolean isWindows() {
        return (OS.indexOf("win") >= 0);
    }

    public static boolean isMac() {
        return (OS.indexOf("mac") >= 0);
    }

    public static boolean isUnix() {
        return (OS.indexOf("nix") >= 0
                || OS.indexOf("nux") >= 0
                || OS.indexOf("aix") > 0);
    }

    public static boolean isSolaris() {
        return (OS.indexOf("sunos") >= 0);
    }

    public static boolean isLinux() {
        return OS.indexOf("linux") >= 0;
    }
}
