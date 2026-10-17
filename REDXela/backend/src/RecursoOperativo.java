import java.util.concurrent.Semaphore;
final class RecursoOperativo {
        final String key, name;
        final Semaphore permits;
        final long[] owners, since;
        long busyNanos;
        RecursoOperativo(String key, String name, int capacity) {
            this.key=key; this.name=name; permits=new Semaphore(capacity, true);
            owners=new long[capacity]; since=new long[capacity];
        }
    }
