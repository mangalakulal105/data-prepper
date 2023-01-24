package org.opensearch.dataprepper.logging;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public final class DataPrepperMarkers {
    public static final Marker EVENT_MARKER = MarkerFactory.getMarker("EVENT");
    public static final Marker SENSITIVE_MARKER = MarkerFactory.getMarker("SENSITIVE");

    static {
        SENSITIVE_MARKER.add(EVENT_MARKER);
    }

    private DataPrepperMarkers() {}
}
