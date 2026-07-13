package com.meridian.platform.customer.infrastructure.adapter.out.persistence;

import com.meridian.platform.customer.application.port.out.CustomerNumberSequenceRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCustomerNumberSequenceRepository implements CustomerNumberSequenceRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcCustomerNumberSequenceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long nextCustomerNumberSequence() {
        Long sequenceValue = jdbcTemplate.queryForObject("SELECT nextval('customer_number_seq')", Long.class);
        if (sequenceValue == null) {
            throw new IllegalStateException("customer_number_seq did not return a value");
        }
        return sequenceValue;
    }
}