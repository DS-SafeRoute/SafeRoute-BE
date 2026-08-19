package com.saferoute.infrastructure.database;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class CctvCodeMigrationTest {

    @Test
    void migration이_CCTV_채번_테이블과_시퀀스를_생성한다() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:cctv-migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE TABLE_NAME = 'CCTV_CODE_ALLOCATIONS'",
                Integer.class
        );
        Integer sequenceCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SEQUENCES "
                        + "WHERE SEQUENCE_NAME = 'CCTV_CODE_SEQUENCE'",
                Integer.class
        );

        assertThat(tableCount).isEqualTo(1);
        assertThat(sequenceCount).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT NEXT VALUE FOR cctv_code_sequence",
                Long.class
        )).isEqualTo(1L);
    }
}
