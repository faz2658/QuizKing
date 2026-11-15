package com.fazeel.maverick.account;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Integer> {

	Optional<Account> findByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCase(String email);
}
