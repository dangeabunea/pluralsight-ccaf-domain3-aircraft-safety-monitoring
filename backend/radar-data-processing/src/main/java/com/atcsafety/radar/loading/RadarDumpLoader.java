package com.atcsafety.radar.loading;

import com.atcsafety.contracts.RadarPosition;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Reads the radar dump JSON file and deserialises each top-level array element
 * into a {@link RadarPosition}.
 *
 * <p>Error handling strategy:
 * <ul>
 *   <li>File not found — throws {@link RadarDumpFileNotFoundException} at startup.
 *   <li>Malformed record (missing required field, wrong type) — logs ERROR and
 *       skips that record; the remaining records are still loaded.
 * </ul>
 *
 * <p>Scope boundary: this component returns ALL successfully deserialised
 * records, including those with a null {@code flightNb}. Filtering out
 * null-flightNb positions is the responsibility of {@code RadarPositionValidator}
 * (Task 01-B-3), not this loader.
 */
@Component
public class RadarDumpLoader {

    private static final Logger log = LoggerFactory.getLogger(RadarDumpLoader.class);

    private final ObjectMapper objectMapper;
    private final String filePath;

    public RadarDumpLoader(
            @Qualifier("radarObjectMapper") ObjectMapper objectMapper,
            @Value("${radar.dump.file-path}") String filePath) {
        this.objectMapper = objectMapper;
        this.filePath = filePath;
    }

    /**
     * Loads all radar positions from the configured JSON file.
     *
     * @return unmodifiable list of successfully deserialised positions
     * @throws RadarDumpFileNotFoundException if the file does not exist or cannot be read
     */
    public List<RadarPosition> load() {
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            throw new RadarDumpFileNotFoundException(
                    "Radar dump file not found: " + filePath);
        }

        try {
            // readTree(Path) is available in Jackson 3 and throws JacksonException.
            JsonNode root = objectMapper.readTree(path);
            List<RadarPosition> positions = new ArrayList<>();

            for (JsonNode element : root) {
                try {
                    positions.add(objectMapper.treeToValue(element, RadarPosition.class));
                } catch (JacksonException e) {
                    log.error("Skipping malformed radar record: {} — {}", element, e.getMessage());
                }
            }

            return List.copyOf(positions);

        } catch (JacksonException e) {
            throw new RadarDumpFileNotFoundException(
                    "Failed to read radar dump file: " + filePath, e);
        }
    }

    /**
     * Groups a flat list of radar positions into ordered cycle batches.
     *
     * <p>Returns a {@link TreeMap} whose keys are distinct {@code radarCycle} values in
     * ascending order and whose values are the lists of positions for that cycle.
     * The natural ordering of {@code TreeMap} guarantees ascending cycle sequence
     * regardless of input order.
     *
     * <p>Scope boundary: this method is not a validator. Records with
     * {@code flightNb = null} and records with {@code radarCycle = 0} pass through
     * unchanged. An empty input returns an empty map without error.
     *
     * @param positions flat list of radar positions; must not be null
     * @return ordered map of radarCycle → positions for that cycle
     */
    public TreeMap<Integer, List<RadarPosition>> groupByCycle(List<RadarPosition> positions) {
        return positions.stream()
                .collect(Collectors.groupingBy(
                        RadarPosition::radarCycle,
                        TreeMap::new,
                        Collectors.toUnmodifiableList()
                ));
    }
}
