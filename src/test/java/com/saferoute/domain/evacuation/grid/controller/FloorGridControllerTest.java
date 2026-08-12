package com.saferoute.domain.evacuation.grid.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saferoute.domain.evacuation.grid.dto.response.FloorGridCellPageResponse;
import com.saferoute.domain.evacuation.grid.dto.response.FloorGridCellResponse;
import com.saferoute.domain.evacuation.grid.service.FloorGridService;
import com.saferoute.global.config.SecurityConfig;
import com.saferoute.global.security.JwtAuthenticationFilter;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = FloorGridController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, SecurityConfig.class}
        )
)
@AutoConfigureMockMvc(addFilters = false)
class FloorGridControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean FloorGridService floorGridService;

    @Test
    void getGridCells_usesDefaultPagination() throws Exception {
        UUID floorId = UUID.randomUUID();
        FloorGridCellResponse cell = new FloorGridCellResponse(
                UUID.randomUUID(), 0, 0, true, false, 0.1, 0.1);
        given(floorGridService.getGridCells(floorId, 0, 500))
                .willReturn(new FloorGridCellPageResponse(
                        List.of(cell), 0, 500, 1, 1, true, true));

        mockMvc.perform(get("/api/v1/floors/{floorId}/grid/cells", floorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content.length()").value(1))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(500));
        verify(floorGridService).getGridCells(floorId, 0, 500);
    }

    @Test
    void getGridCells_rejectsPageSizeOverLimit() throws Exception {
        UUID floorId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/floors/{floorId}/grid/cells", floorId)
                        .param("size", "2001"))
                .andExpect(status().isBadRequest());
        verify(floorGridService, never()).getGridCells(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt()
        );
    }
}
