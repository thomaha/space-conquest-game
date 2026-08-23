package com.spaceconquest.engine.galaxy;

import com.spaceconquest.engine.SolarSystem;

import java.util.*;

/**
 * Generates and manages the galactic warp lane / hyperlane network graph.
 */
public class WarpNetwork {

    /**
     * Generates a connected network of warp lanes between solar systems based on spatial proximity.
     *
     * @param systems       list of solar systems
     * @param maxLinkRangeLy maximum distance in light-years to establish a direct warp corridor
     * @return list of generated WarpLane corridors
     */
    public List<WarpLane> generateNetwork(List<SolarSystem> systems, double maxLinkRangeLy) {
        if (systems == null || systems.size() < 2) {
            return List.of();
        }

        List<WarpLane> lanes = new ArrayList<>();
        Set<String> existingPairs = new HashSet<>();
        double maxRange = Math.max(10.0, maxLinkRangeLy);

        // Ensure each system connects to at least its 2 closest neighbors, plus any within maxRange
        for (int i = 0; i < systems.size(); i++) {
            SolarSystem s1 = systems.get(i);

            // Sort other systems by distance
            List<SolarSystem> others = new ArrayList<>(systems);
            others.remove(s1);
            others.sort(Comparator.comparingDouble(s2 -> calculateDistance(s1, s2)));

            int minLinks = Math.min(2, others.size());
            for (int j = 0; j < others.size(); j++) {
                SolarSystem s2 = others.get(j);
                double dist = calculateDistance(s1, s2);

                if (j < minLinks || dist <= maxRange) {
                    String pairKey = s1.id().compareTo(s2.id()) < 0
                            ? s1.id() + "<->" + s2.id()
                            : s2.id() + "<->" + s1.id();

                    if (!existingPairs.contains(pairKey)) {
                        existingPairs.add(pairKey);
                        String laneId = "warp_" + s1.id() + "_" + s2.id();
                        lanes.add(new WarpLane(laneId, s1.id(), s2.id(), dist, true));
                    }
                }
            }
        }

        return lanes;
    }

    /**
     * Finds the shortest path of star system IDs between origin and destination systems.
     */
    public List<String> findShortestPath(String startSystemId, String targetSystemId, List<WarpLane> lanes) {
        if (startSystemId == null || targetSystemId == null || lanes == null || lanes.isEmpty()) {
            return List.of();
        }
        if (startSystemId.equals(targetSystemId)) {
            return List.of(startSystemId);
        }

        // Build adjacency map
        Map<String, List<WarpLane>> adj = new HashMap<>();
        for (WarpLane lane : lanes) {
            adj.computeIfAbsent(lane.systemIdA(), k -> new ArrayList<>()).add(lane);
            adj.computeIfAbsent(lane.systemIdB(), k -> new ArrayList<>()).add(lane);
        }

        // Dijkstra shortest path
        Map<String, Double> distances = new HashMap<>();
        Map<String, String> previous = new HashMap<>();
        PriorityQueue<Node> queue = new PriorityQueue<>(Comparator.comparingDouble(n -> n.distance));

        distances.put(startSystemId, 0.0);
        queue.add(new Node(startSystemId, 0.0));

        while (!queue.isEmpty()) {
            Node current = queue.poll();
            if (current.id.equals(targetSystemId)) break;
            if (current.distance > distances.getOrDefault(current.id, Double.MAX_VALUE)) continue;

            List<WarpLane> neighbors = adj.getOrDefault(current.id, List.of());
            for (WarpLane lane : neighbors) {
                String nextId = lane.getOppositeSystemId(current.id);
                double newDist = current.distance + lane.distanceLightYears();
                if (newDist < distances.getOrDefault(nextId, Double.MAX_VALUE)) {
                    distances.put(nextId, newDist);
                    previous.put(nextId, current.id);
                    queue.add(new Node(nextId, newDist));
                }
            }
        }

        if (!previous.containsKey(targetSystemId)) {
            return List.of(); // Unreachable
        }

        List<String> path = new ArrayList<>();
        String curr = targetSystemId;
        while (curr != null) {
            path.add(0, curr);
            curr = previous.get(curr);
        }
        return path;
    }

    public double calculateDistance(SolarSystem a, SolarSystem b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static class Node {
        final String id;
        final double distance;

        Node(String id, double distance) {
            this.id = id;
            this.distance = distance;
        }
    }
}
