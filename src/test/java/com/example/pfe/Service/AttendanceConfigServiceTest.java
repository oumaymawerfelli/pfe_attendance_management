package com.example.pfe.Service;

import com.example.pfe.Repository.AttendanceConfigRepository;
import com.example.pfe.dto.AttendanceConfigDTO;
import com.example.pfe.entities.AttendanceConfig;
import com.example.pfe.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AttendanceConfigService - Unit Tests")
class AttendanceConfigServiceTest {

    @Mock
    private AttendanceConfigRepository configRepository;

    @InjectMocks
    private AttendanceConfigService service;

    private AttendanceConfig config;

    @BeforeEach
    void setUp() {
        config = AttendanceConfig.builder()
                .id(1L)
                .configKey("LATE_HOUR")
                .configValue(9.0)
                .description("Hour threshold for late arrival")
                .lastModifiedBy("system")
                .build();
    }

    // ─────────────────────────────────────────────
    // 1. GET ALL CONFIGS
    // ─────────────────────────────────────────────
    @Test
    @DisplayName("getAllConfigs - should return list of configs")
    void getAllConfigs_shouldReturnList() {

        given(configRepository.findAll()).willReturn(List.of(config));

        List<?> result = service.getAllConfigs();

        assertThat(result).hasSize(1);
        verify(configRepository).findAll();
    }

    // ─────────────────────────────────────────────
    // 2. GET VALUE SUCCESS
    // ─────────────────────────────────────────────
    @Test
    @DisplayName("getValue - should return config value")
    void getValue_shouldReturnValue() {

        given(configRepository.findByConfigKey("LATE_HOUR"))
                .willReturn(Optional.of(config));

        double result = service.getValue("LATE_HOUR");

        assertThat(result).isEqualTo(9.0);
        verify(configRepository).findByConfigKey("LATE_HOUR");
    }

    // ─────────────────────────────────────────────
    // 3. GET VALUE NOT FOUND
    // ─────────────────────────────────────────────
    @Test
    @DisplayName("getValue - should throw exception when key not found")
    void getValue_shouldThrowException() {

        given(configRepository.findByConfigKey("UNKNOWN"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getValue("UNKNOWN"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("UNKNOWN");
    }

    // ─────────────────────────────────────────────
    // 4. UPDATE CONFIG SUCCESS
    // ─────────────────────────────────────────────
    @Test
    @DisplayName("updateConfig - should update value successfully")
    void updateConfig_shouldUpdateValue() {

        AttendanceConfigDTO.UpdateRequest request =
                new AttendanceConfigDTO.UpdateRequest();
        request.setConfigValue(10.0);
        request.setLastModifiedBy("admin");

        given(configRepository.findByConfigKey("LATE_HOUR"))
                .willReturn(Optional.of(config));

        given(configRepository.save(any(AttendanceConfig.class)))
                .willReturn(config);

        AttendanceConfigDTO.Response response =
                service.updateConfig("LATE_HOUR", request);

        assertThat(response).isNotNull();
        verify(configRepository).save(config);
        assertThat(config.getConfigValue()).isEqualTo(10.0);
    }

    // ─────────────────────────────────────────────
    // 5. UPDATE CONFIG NOT FOUND
    // ─────────────────────────────────────────────
    @Test
    @DisplayName("updateConfig - should throw exception if key not found")
    void updateConfig_shouldThrowException() {

        AttendanceConfigDTO.UpdateRequest request =
                new AttendanceConfigDTO.UpdateRequest();
        request.setConfigValue(10.0);

        given(configRepository.findByConfigKey("UNKNOWN"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.updateConfig("UNKNOWN", request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─────────────────────────────────────────────
    // 6. INT CONVERSION
    // ─────────────────────────────────────────────
    @Test
    @DisplayName("getInt - should convert value to int")
    void getInt_shouldConvertValue() {

        given(configRepository.findByConfigKey("LATE_HOUR"))
                .willReturn(Optional.of(config));

        int result = service.getInt("LATE_HOUR");

        assertThat(result).isEqualTo(9);
    }

    // ─────────────────────────────────────────────
    // 7. DOUBLE CONVERSION
    // ─────────────────────────────────────────────
    @Test
    @DisplayName("getDouble - should return double value")
    void getDouble_shouldReturnValue() {

        given(configRepository.findByConfigKey("LATE_HOUR"))
                .willReturn(Optional.of(config));

        double result = service.getDouble("LATE_HOUR");

        assertThat(result).isEqualTo(9.0);
    }
}