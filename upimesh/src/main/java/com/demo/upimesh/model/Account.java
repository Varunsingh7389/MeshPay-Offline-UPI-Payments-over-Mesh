package com.demo.upimesh.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String vpa;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal balance;

    @Column(nullable = false)
    private String pinHash;

    @Version
    private Long version;

    public Account() {
    }

    public Account(String vpa, String name,
                   BigDecimal balance,
                   String pinHash) {
        this.vpa = vpa;
        this.name = name;
        this.balance = balance;
        this.pinHash = pinHash;
    }

    public Long getId() {
        return id;
    }

    public String getVpa() {
        return vpa;
    }

    public void setVpa(String vpa) {
        this.vpa = vpa;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public String getPinHash() {
        return pinHash;
    }

    public void setPinHash(String pinHash) {
        this.pinHash = pinHash;
    }

    public Long getVersion() {
        return version;
    }
}