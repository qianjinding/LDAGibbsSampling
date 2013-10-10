package ron;

import java.util.Arrays;
import java.util.logging.Logger;

public class ProgressTracker implements AutoCloseable {
    long st = System.currentTimeMillis();
    final long[] pro;
    final long[] recent;
    long recentts = st;
    long nxt = System.currentTimeMillis() + 1500;
    final String units[];
    final long max1;
    final String prefix;
    private final Logger logger;
    private final boolean debuglevel;

    public ProgressTracker(Logger logger, String prefix, long max1, String... unit) {
        this(false, logger, prefix, max1, unit);
    }
    public ProgressTracker(boolean debuglevel, Logger logger, String prefix, long max1, String... unit) {
        this.units = unit;
        this.debuglevel = debuglevel;
        pro = new long[units.length];
        this.logger = logger;
        this.prefix = prefix;
        this.max1 = max1;
        recent = new long[units.length];
        Arrays.fill(recent, 0);
    }

    private static String derp(long n) {
        if (n > 1000000) {
            return String.format("%,.2fM", Double.valueOf(n/1000000.));
        } else if (n > 1000) {
            return String.format("%,.2fK", Double.valueOf(n/1000.));
        } else {
            return String.format("%,d", Long.valueOf(n));
        }
    }
    private static String derp2(long n) {
        return String.format("%,d", Long.valueOf(n));
    }
    private static String derp(double n) {
        if (n > 1000000) {
            return String.format("%,.2fM", Double.valueOf(n/1000000.));
        } else if (n > 1000) {
            return String.format("%,.2fK", Double.valueOf(n/1000.));
        } else {
            return String.format("%,.2f", Double.valueOf(n));
        }
    }

    private String blerp(long now, String s, int i) {
        if (pro[i] == recent[i]) {
            return s;
        }
        return s + String.format("%s %s (%s/s); ", derp(pro[i]), units[i], derp((pro[i] - recent[i]) / (1e-3 * (now - recentts))));
    }
    private String blerp2(long now, String s, int i) {
        if (pro[i] == recent[i]) {
            return s;
        }
        return s + String.format("%s %s (%s/s); ", derp2(pro[i]), units[i], derp((pro[i] - recent[i]) / (1e-3 * (now - recentts))));
    }

    public synchronized void advise(String info, long... delta) {
        for (int i =0;i<Math.min(delta.length,  pro.length);i++) {
            pro[i] += delta[i];
        }
        if (max1 >0 && pro[0] > max1) {
            throw new IllegalStateException("went past the end: "+max1+" "+pro[0]);
        }
        emit(info);
    }

    private void emit(String info) {
        long now = System.currentTimeMillis();
        if (info== null && now < nxt && !(max1 >0 && max1==pro[0])) return;
        nxt = now + 2500;
        String s = prefix+": ";
        if (max1 > 0) {
            s += String.format("%.2f%%; ", Double.valueOf(pro[0] * 100. / max1));
        }
        for (int i=0;i<pro.length; i++) {
            if (info == null)
                s = blerp(now, s, i);
            else
                s = blerp2(now, s, i);
            recent[i] = pro[i];
        }
        recentts = now;
        String elapsed = String.format("%,d seconds", Long.valueOf((now - st) / 1000));
        String string = s + elapsed + " " + ( info==null?"":(" "+(info.length() > 150 ? info.substring(0,150) : info)));
        if (logger == null) {
            System.out.println(string);
        } else {
            if (debuglevel) {
                logger.fine(string);
            } else {
                logger.info(string);
            }
        }
    }
    public synchronized void advise(long... delta) {
        advise(null, delta);
    }
    @Override
    public synchronized void close() {
        emit("FINISHED");
        Arrays.fill(pro, 0);
        st = System.currentTimeMillis();
    }
}