package jobt;

@SuppressWarnings("checkstyle:regexpmultiline")
public final class Progress {

    private static long lastTimestamp;

    private Progress() {
    }

    public static void newStatus(final String message) {
        lastTimestamp = System.nanoTime();
        System.out.print(message);
    }

    public static void complete() {
        complete("OK");
    }

    public static void complete(final String status) {
        long duration = System.nanoTime() - lastTimestamp;
        System.out.printf(" [%s] [+ %.3fs]%n", status, duration / 1_000_000_000D);
    }

    public static void log(final String message) {
        System.out.println(message);
    }

}
