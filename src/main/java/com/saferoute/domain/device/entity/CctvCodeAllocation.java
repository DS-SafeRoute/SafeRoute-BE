package com.saferoute.domain.device.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "cctv_code_allocations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CctvCodeAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cctv_code_sequence_generator")
    @SequenceGenerator(
            name = "cctv_code_sequence_generator",
            sequenceName = "cctv_code_sequence",
            initialValue = 1,
            allocationSize = 1
    )
    private Long number;

    public static CctvCodeAllocation create() {
        return new CctvCodeAllocation();
    }
}
