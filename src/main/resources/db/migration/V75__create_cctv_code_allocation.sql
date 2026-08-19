CREATE SEQUENCE IF NOT EXISTS cctv_code_sequence
    START WITH 1
    INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS cctv_code_allocations (
    number BIGINT NOT NULL,
    CONSTRAINT pk_cctv_code_allocations PRIMARY KEY (number)
);
