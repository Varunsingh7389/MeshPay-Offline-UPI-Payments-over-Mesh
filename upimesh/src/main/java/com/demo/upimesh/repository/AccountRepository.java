package com.demo.upimesh.repository;

import com.demo.upimesh.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountRepository
        extends JpaRepository<Account, Long> {

    Optional<Account> findByVpa(String vpa);
}