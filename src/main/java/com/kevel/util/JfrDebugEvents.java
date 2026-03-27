package com.kevel.util;

import java.lang.reflect.Field;
import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.Timespan;

public class JfrDebugEvents {

    public static final String DEBUGGING = "Application Debugging";

    @Name("pdg.TimeMarker")
    @Label("Time Entry")
    @Category(DEBUGGING)
    public static class TimeMarkerEvent extends Event {
        @Label("Message")
        public String message;

        public long nanos = System.nanoTime();

        @Timespan(Timespan.NANOSECONDS)
        public long userNanoDuration;

        public long setUserDuration(long newNanos) {
            this.userNanoDuration = newNanos - nanos;
            return this.userNanoDuration;
        }

        public Object getDuration() throws IllegalAccessException {
            Field[] fields = TimeMarkerEvent.class.getDeclaredFields();
            for (Field f : fields) {
                if (f.getName() == "duration") {
                    return f.get(this);
                }
            }
            return null;
        }
    }

    public static TimeMarkerEvent timeMarker() {
        return new TimeMarkerEvent();
    }

    public static TimeMarkerEvent startMarker() {
        TimeMarkerEvent e = new TimeMarkerEvent();
        e.begin();
        return e;
    }
}
