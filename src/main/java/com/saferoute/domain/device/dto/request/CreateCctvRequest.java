package com.saferoute.domain.device.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateCctvRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull UUID floorId,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double x,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double y,
        @NotEmpty List<@NotNull UUID> gridCellIds
) {
}
