package com.eventledger.account.repository;

import com.eventledger.account.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByAccountIdOrderByEventTimestampAsc(String accountId);

    /**
     * Computes the running balance for an account on the fly.
     * CREDITs add to balance, DEBITs subtract from balance.
     * Returns null when the account has no transactions.
     */
    @Query("""
            SELECT COALESCE(
                SUM(CASE WHEN t.type = 'CREDIT' THEN t.amount ELSE -t.amount END),
                0
            )
            FROM Transaction t
            WHERE t.accountId = :accountId
            """)
    BigDecimal computeBalanceByAccountId(@Param("accountId") String accountId);

    /**
     * Simple existence check used by the health endpoint to verify DB connectivity.
     */
    @Query("SELECT COUNT(t) FROM Transaction t")
    long countAll();
}
