package com.demo.upimesh.service;

import com.demo.upimesh.model.MeshPacket;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A simulated phone in the mesh network.
 *
 * Each device stores packets it has already seen.
 * Duplicate packet IDs are ignored.
 */
public class VirtualDevice {

    private final String deviceId;
    private final boolean hasInternet;

    private final Map<String, MeshPacket> heldPackets =
            new ConcurrentHashMap<>();

    public VirtualDevice(
            String deviceId,
            boolean hasInternet) {

        this.deviceId = deviceId;
        this.hasInternet = hasInternet;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public boolean hasInternet() {
        return hasInternet;
    }

    /**
     * Store packet if not already present.
     */
    public void hold(MeshPacket packet) {
        heldPackets.putIfAbsent(
                packet.getPacketId(),
                packet
        );
    }

    /**
     * All packets currently stored by this device.
     */
    public Collection<MeshPacket> getHeldPackets() {
        return heldPackets.values();
    }

    /**
     * Has this device already seen this packet?
     */
    public boolean holds(String packetId) {
        return heldPackets.containsKey(packetId);
    }

    /**
     * Number of packets currently stored.
     */
    public int packetCount() {
        return heldPackets.size();
    }

    /**
     * Clear all packets.
     */
    public void clear() {
        heldPackets.clear();
    }
}