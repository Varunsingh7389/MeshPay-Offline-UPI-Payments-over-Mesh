package com.demo.upimesh.controller;

import com.demo.upimesh.crypto.ServerKeyHolder;
import com.demo.upimesh.model.Account;
import com.demo.upimesh.model.MeshPacket;
import com.demo.upimesh.model.Transaction;
import com.demo.upimesh.repository.AccountRepository;
import com.demo.upimesh.repository.TransactionRepository;
import com.demo.upimesh.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    private ServerKeyHolder serverKey;

    @Autowired
    private DemoService demo;

    @Autowired
    private MeshSimulatorService mesh;

    @Autowired
    private BridgeIngestionService bridge;

    @Autowired
    private AccountRepository accountRepo;

    @Autowired
    private TransactionRepository txRepo;

    @Autowired
    private IdempotencyService idempotency;

    // ------------------------------------------------ SERVER KEY

    @GetMapping("/server-key")
    public Map<String, String> getServerPublicKey() {

        return Map.of(
                "publicKey", serverKey.getPublicKeyBase64(),
                "algorithm", "RSA-2048 / OAEP-SHA256",
                "hybridScheme", "RSA-OAEP + AES-256-GCM"
        );
    }

    // ------------------------------------------------ DEMO SEND

    @PostMapping("/demo/send")
    public ResponseEntity<?> demoSend(
            @RequestBody DemoSendRequest req) throws Exception {

        MeshPacket packet =
                demo.createPacket(
                        req.senderVpa,
                        req.receiverVpa,
                        req.amount,
                        req.pin,
                        req.ttl == null ? 5 : req.ttl
                );

        String startDevice =
                req.startDevice == null
                        ? "phone-alice"
                        : req.startDevice;

        mesh.inject(startDevice, packet);

        return ResponseEntity.ok(
                Map.of(
                        "packetId", packet.getPacketId(),
                        "ttl", packet.getTtl(),
                        "injectedAt", startDevice,
                        "ciphertextPreview",
                        packet.getCiphertext()
                                .substring(0, 64) + "..."
                )
        );
    }

    public static class DemoSendRequest {

        public String senderVpa;
        public String receiverVpa;
        public BigDecimal amount;
        public String pin;
        public Integer ttl;
        public String startDevice;
    }

    // ------------------------------------------------ MESH STATE

    @GetMapping("/mesh/state")
    public Map<String, Object> meshState() {

        List<Map<String, Object>> deviceData =
                new ArrayList<>();

        for (VirtualDevice d : mesh.getDevices()) {

            deviceData.add(
                    Map.of(
                            "deviceId", d.getDeviceId(),
                            "hasInternet", d.hasInternet(),
                            "packetCount", d.packetCount(),
                            "packetIds",
                            d.getHeldPackets()
                                    .stream()
                                    .map(p ->
                                            p.getPacketId()
                                                    .substring(0, 8))
                                    .toList()
                    )
            );
        }

        return Map.of(
                "devices", deviceData,
                "idempotencyCacheSize",
                idempotency.size()
        );
    }

    // ------------------------------------------------ GOSSIP

    @PostMapping("/mesh/gossip")
    public Map<String, Object> meshGossip() {

        MeshSimulatorService.GossipResult r =
                mesh.gossipOnce();

        return Map.of(
                "transfers", r.transfers(),
                "deviceCounts", r.deviceCounts()
        );
    }

    // ------------------------------------------------ FLUSH

    @PostMapping("/mesh/flush")
    public Map<String, Object> meshFlush() {

        List<MeshSimulatorService.BridgeUpload>
                uploads =
                mesh.collectBridgeUploads();

        List<Map<String, Object>> results =
                new ArrayList<>();

        uploads.parallelStream().forEach(up -> {

            BridgeIngestionService.IngestResult r =
                    bridge.ingest(
                            up.packet(),
                            up.bridgeNodeId(),
                            5 - up.packet().getTtl()
                    );

            synchronized (results) {

                results.add(
                        Map.of(
                                "bridgeNode",
                                up.bridgeNodeId(),

                                "packetId",
                                up.packet()
                                        .getPacketId()
                                        .substring(0, 8),

                                "outcome",
                                r.outcome(),

                                "reason",
                                r.reason() == null
                                        ? ""
                                        : r.reason(),

                                "transactionId",
                                r.transactionId() == null
                                        ? -1
                                        : r.transactionId()
                        )
                );
            }
        });

        return Map.of(
                "uploadsAttempted",
                uploads.size(),

                "results",
                results
        );
    }

    // ------------------------------------------------ RESET

    @PostMapping("/mesh/reset")
    public Map<String, Object> meshReset() {

        mesh.resetMesh();
        idempotency.clear();

        return Map.of(
                "status",
                "mesh and idempotency cache cleared"
        );
    }

    // ------------------------------------------------ BRIDGE

    @PostMapping("/bridge/ingest")
    public ResponseEntity<?> ingest(

            @RequestBody MeshPacket packet,

            @RequestHeader(
                    value = "X-Bridge-Node-Id",
                    defaultValue = "unknown")
            String bridgeNodeId,

            @RequestHeader(
                    value = "X-Hop-Count",
                    defaultValue = "0")
            int hopCount) {

        BridgeIngestionService.IngestResult r =
                bridge.ingest(
                        packet,
                        bridgeNodeId,
                        hopCount
                );

        return ResponseEntity.ok(r);
    }

    // ------------------------------------------------ ACCOUNTS

    @GetMapping("/accounts")
    public List<Account> listAccounts() {
        return accountRepo.findAll();
    }

    // ------------------------------------------------ TRANSACTIONS

    @GetMapping("/transactions")
    public List<Transaction> listTransactions() {
        return txRepo.findTop20ByOrderByIdDesc();
    }
}