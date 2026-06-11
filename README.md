# 📡 MeshPay — Offline UPI Payments over Mesh

> Simulate how UPI payments travel without internet — phone to phone to bank.

---

## What is this?

MeshPay is a backend simulation of an offline-first UPI payment system. It demonstrates how a payment instruction can be created on a sender's phone, encrypted, passed through a chain of nearby devices via Bluetooth-style gossip, and finally settled by a bridge node when it regains internet connectivity — without the sender ever needing to be online.

This is a **proof-of-concept simulation** running on a Spring Boot backend. The mesh gossip is simulated in-memory to demonstrate the architecture and security model. There is no actual Bluetooth or WiFi Direct involved.

---

## The Problem It Solves

India has ~800 million UPI users, but internet connectivity is unreliable in rural areas, metro stations, markets, and during network congestion. A payment that fails mid-way is not just inconvenient — it breaks trust in digital payments entirely.

**MeshPay explores the question:** what if your phone could hand a payment to a nearby phone, which hands it to another, until someone with internet connectivity submits it to the bank?

---

## How It Works

```
[Sender's Phone]
      │
      │  Creates PaymentInstruction (VPA, amount, PIN hash, nonce, timestamp)
      │  Encrypts it with RSA-OAEP + AES-256-GCM → MeshPacket
      │
      ▼
[Mesh Gossip Layer]
      │
      │  Packet hops device-to-device (TTL decrements each hop)
      │  Strangers carry the packet — they cannot read it (ciphertext only)
      │
      ▼
[Bridge Node — has internet]
      │
      │  Uploads packet to Spring Boot backend
      │  Multiple bridges may upload the same packet simultaneously
      │
      ▼
[Spring Boot Backend]
      │
      │  Verifies packet hash (SHA-256)
      │  Checks idempotency — rejects duplicates atomically
      │  Checks freshness — rejects packets older than 24 hours
      │  Decrypts PaymentInstruction (RSA private key)
      │  Verifies PIN hash
      │  Settles payment (@Transactional debit + credit)
      │
      ▼
[H2 Database]
      Transaction recorded, balances updated
```

---

## Key Technical Decisions

### Hybrid Encryption (RSA-OAEP + AES-256-GCM)
RSA alone cannot encrypt large payloads efficiently. So a random AES-256 key encrypts the payment instruction, and that AES key is then wrapped with the server's RSA-2048 public key. Only the server's private key can unwrap the AES key and decrypt the payload. Strangers relaying the packet see only opaque ciphertext.

### Idempotency via ConcurrentHashMap
When a bridge node uploads a packet, three bridges might upload the same packet simultaneously (all walked outside at the same time). A `ConcurrentHashMap` with atomic `putIfAbsent` ensures only one thread wins and settles the payment — the others are silently dropped. The database `UNIQUE` constraint on `packetHash` is a second safety net.

### TTL (Time To Live)
Each packet carries a TTL counter that decrements at every hop. When it hits zero the packet is dropped. This prevents infinite looping in the mesh and limits how stale a packet can get before it reaches a bridge.

### Freshness Window
The `PaymentInstruction` contains a `signedAt` timestamp. The backend rejects any instruction older than 24 hours. This prevents replay attacks where an attacker captures a packet and tries to submit it later.

### Optimistic Locking on Accounts
The `Account` entity has a `@Version` field. If two transactions try to debit the same account simultaneously, only one succeeds — the other gets an `OptimisticLockException`. This protects against double-spending at the database level.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend framework | Spring Boot 3.3 |
| Language | Java 17 |
| Database | H2 (in-memory) |
| ORM | Spring Data JPA / Hibernate |
| Encryption | Java JCA — RSA-OAEP, AES-256-GCM, SHA-256 |
| UI | Thymeleaf + vanilla JavaScript |
| Build tool | Maven |

---

## How to Run

**Prerequisites:** Java 17+, Maven (or use the included wrapper)

```bash
# Clone the repository
git clone https://github.com/your-username/upimesh.git
cd upimesh

# Run the app
./mvnw spring-boot:run          # Mac / Linux
mvnw.cmd spring-boot:run        # Windows
```

Open **http://localhost:8080** in your browser.

To inspect the database directly, open **http://localhost:8080/h2-console**
- JDBC URL: `jdbc:h2:mem:upimesh`
- Username: `sa`
- Password: *(leave blank)*

---

## Demo Walkthrough

1. **Compose a payment** — select sender (e.g. alice@demo), receiver (e.g. bob@demo), enter amount and PIN, click **Inject into Mesh**. The payment is encrypted and injected into the mesh at alice's device.

2. **Run Gossip Round** — packets hop between offline devices, simulating Bluetooth relay. Watch the device packet counts change.

3. **Bridges Upload to Backend** — the bridge node (which has 4G) uploads all packets it holds to the server simultaneously. The idempotency layer ensures each payment settles exactly once even if multiple bridges upload the same packet.

4. **Watch the ledger** — the Transaction Ledger updates with the settled payment, showing hop count, which bridge submitted it, and the settled timestamp.

5. **Reset** — clears the mesh and idempotency cache to run a fresh demo.

---

## Project Structure

```
src/main/java/com/demo/upimesh/
├── UpiMeshApplication.java          # Entry point
├── model/
│   ├── Account.java                 # JPA entity — user bank account
│   ├── AccountRepository.java       # Spring Data repository
│   ├── Transaction.java             # JPA entity — settled payment record
│   ├── TransactionRepository.java   # Spring Data repository
│   ├── MeshPacket.java              # Wire format — encrypted packet
│   └── PaymentInstruction.java      # Decrypted payment payload
├── crypto/
│   ├── ServerKeyHolder.java         # RSA-2048 keypair, generated on startup
│   └── HybridCryptoService.java     # Encrypt / decrypt / hash
├── service/
│   ├── IdempotencyService.java      # Atomic duplicate detection
│   ├── SettlementService.java       # Transactional debit + credit
│   ├── BridgeIngestionService.java  # Full pipeline: verify → decrypt → settle
│   ├── MeshSimulatorService.java    # Gossip simulation, device management
│   ├── DemoService.java             # Account seeding, packet creation
│   └── VirtualDevice.java          # Represents one phone in the mesh
└── web/
    ├── ApiController.java           # REST endpoints
    └── DashboardController.java     # Serves dashboard.html
```

---

## Limitations & Future Work

- **No real Bluetooth/WiFi Direct** — gossip is simulated in-memory. A real implementation would use Android's Nearby Connections API or WiFi Direct.
- **H2 in-memory DB** — data is lost on restart. Production would use PostgreSQL.
- **PIN verification is simplified** — a production system would use a proper bank-side PIN verification flow with HSM (Hardware Security Module).
- **No digital signatures** — a production system would have the sender sign the payment instruction with their private key so the bank can verify authenticity.

## Screenshots

### Dashboard
<img width="1282" height="428" alt="image" src="https://github.com/user-attachments/assets/88e9d7bf-1635-4a6b-9494-4dd0dd437f77" />

### Mesh State
<img width="593" height="520" alt="image" src="https://github.com/user-attachments/assets/dcad267e-c1cd-4a86-a43d-ae811013c953" />

### Account Balances
<img width="594" height="315" alt="image" src="https://github.com/user-attachments/assets/4354efd2-191b-46ca-8e12-6c25e40f4227" />

### Transaction Ledger and Activity Log
<img width="1204" height="566" alt="image" src="https://github.com/user-attachments/assets/0cd1c9a0-9c06-4526-86c9-c0f06bddca0e" />


---

## Author

**Varun Singh**

Built as a personal project to explore distributed systems, applied cryptography, and offline-first architecture in the context of India's UPI payment ecosystem.
