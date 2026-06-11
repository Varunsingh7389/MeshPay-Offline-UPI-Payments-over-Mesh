package com.demo.upimesh.service;

import com.demo.upimesh.model.Account;
import com.demo.upimesh.model.PaymentInstruction;
import com.demo.upimesh.model.Transaction;
import com.demo.upimesh.repository.AccountRepository;
import com.demo.upimesh.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class SettlementService {

    private static final Logger log =
            LoggerFactory.getLogger(SettlementService.class);

    private final AccountRepository accounts;
    private final TransactionRepository transactions;

    public SettlementService(
            AccountRepository accounts,
            TransactionRepository transactions) {

        this.accounts = accounts;
        this.transactions = transactions;
    }

    @Transactional
    public Transaction settle(
            PaymentInstruction instruction,
            String packetHash,
            String bridgeNodeId,
            int hopCount) {

        Account sender = accounts.findByVpa(
                instruction.getSenderVpa()
        ).orElseThrow(() ->
                new IllegalArgumentException(
                        "Unknown sender VPA: "
                                + instruction.getSenderVpa()
                )
        );

        Account receiver = accounts.findByVpa(
                instruction.getReceiverVpa()
        ).orElseThrow(() ->
                new IllegalArgumentException(
                        "Unknown receiver VPA: "
                                + instruction.getReceiverVpa()
                )
        );

        if (!sender.getPinHash().equals(
                instruction.getPinHash())) {

            return recordRejected(
                    instruction,
                    packetHash,
                    bridgeNodeId,
                    hopCount
            );
        }

        if (instruction.getAmount().signum() <= 0) {
            throw new IllegalArgumentException(
                    "Amount must be positive"
            );
        }

        if (sender.getBalance()
                .compareTo(instruction.getAmount()) < 0) {

            log.warn(
                    "Insufficient balance: {} has {}, tried {}",
                    sender.getVpa(),
                    sender.getBalance(),
                    instruction.getAmount()
            );

            return recordRejected(
                    instruction,
                    packetHash,
                    bridgeNodeId,
                    hopCount
            );
        }

        sender.setBalance(
                sender.getBalance()
                        .subtract(instruction.getAmount())
        );

        receiver.setBalance(
                receiver.getBalance()
                        .add(instruction.getAmount())
        );

        accounts.save(sender);
        accounts.save(receiver);

        Transaction tx = new Transaction();

        tx.setPacketHash(packetHash);
        tx.setSenderVpa(instruction.getSenderVpa());
        tx.setReceiverVpa(instruction.getReceiverVpa());
        tx.setAmount(instruction.getAmount());
        tx.setSignedAt(
                Instant.ofEpochMilli(
                        instruction.getSignedAt()
                )
        );
        tx.setSettledAt(Instant.now());
        tx.setBridgeNodeId(bridgeNodeId);
        tx.setHopCount(hopCount);
        tx.setStatus(Transaction.Status.SETTLED);

        transactions.save(tx);

        log.info(
                "SETTLED {} -> {} amount={}",
                sender.getVpa(),
                receiver.getVpa(),
                instruction.getAmount()
        );

        return tx;
    }

    private Transaction recordRejected(
            PaymentInstruction instruction,
            String packetHash,
            String bridgeNodeId,
            int hopCount) {

        Transaction tx = new Transaction();

        tx.setPacketHash(packetHash);
        tx.setSenderVpa(instruction.getSenderVpa());
        tx.setReceiverVpa(instruction.getReceiverVpa());
        tx.setAmount(instruction.getAmount());
        tx.setSignedAt(
                Instant.ofEpochMilli(
                        instruction.getSignedAt()
                )
        );
        tx.setSettledAt(Instant.now());
        tx.setBridgeNodeId(bridgeNodeId);
        tx.setHopCount(hopCount);
        tx.setStatus(Transaction.Status.REJECTED);

        return transactions.save(tx);
    }
}