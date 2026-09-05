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
@Table(name = "iot_light_code_allocations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IoTLightCodeAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "iot_light_code_sequence_generator")
    @SequenceGenerator(
            name = "iot_light_code_sequence_generator",
            sequenceName = "iot_light_code_sequence",
            initialValue = 1,
            allocationSize = 1
    )
    private Long number;

    public static IoTLightCodeAllocation create() {
        return new IoTLightCodeAllocation();
    }
}
