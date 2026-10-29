package com.fertimata.backend.plant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fertimata.backend.common.exception.BusinessRuleException;
import com.fertimata.backend.plant.dto.PlantRequest;
import com.fertimata.backend.plant.dto.PlantResponse;
import com.fertimata.backend.support.TestFixtures;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlantServiceTest {

    @Mock
    private PlantRepository plantRepository;

    @Mock
    private com.fertimata.backend.cultivation.CultivationService cultivationService;

    private PlantService service;

    @BeforeEach
    void setUp() {
        service = new PlantService(plantRepository, cultivationService);
    }

    @Test
    @DisplayName("a farmer can name a plant freely; a unique code is made from the name")
    void codeIsDerivedFromFreeTextName() {
        when(plantRepository.existsByCode("Melon-06")).thenReturn(true);
        when(plantRepository.existsByCode("Melon-06-2")).thenReturn(false);
        when(plantRepository.save(any(Plant.class))).thenAnswer(inv -> {
            Plant p = inv.getArgument(0);
            TestFixtures.setId(p, 10L);
            return p;
        });

        PlantResponse created = service.create(new PlantRequest("  Melon 06  ", null, "dekat pintu"));

        assertThat(created.code()).isEqualTo("Melon-06-2");
        assertThat(created.name()).isEqualTo("Melon 06");
        assertThat(created.description()).isEqualTo("dekat pintu");
        assertThat(service.uniqueCodeFrom("Melon #7 (pojok)")).isEqualTo("Melon-7-pojok");
        assertThat(service.uniqueCodeFrom("***")).isEqualTo("Tanaman");
    }

    @Test
    @DisplayName("a plant without name or code is refused")
    void nameOrCodeRequired() {
        assertThatThrownBy(() -> service.create(new PlantRequest(" ", null, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("deleting a plant keeps its AI detections, detached")
    void deleteDetachesDetections() {
        Plant plant = new Plant("Melon-01", "Melon 01");
        TestFixtures.setId(plant, 5L);
        when(plantRepository.findById(5L)).thenReturn(Optional.of(plant));

        service.delete(5L);

        verify(plantRepository).detachDetections(5L);
        verify(plantRepository).delete(plant);
    }

    @Test
    @DisplayName("deleting the last plant clears the transplant date so the next season starts fresh")
    void deletingLastPlantResetsTransplantDate() {
        Plant plant = new Plant("Melon-01", "Melon 01");
        TestFixtures.setId(plant, 5L);
        when(plantRepository.findById(5L)).thenReturn(Optional.of(plant));
        when(plantRepository.count()).thenReturn(0L);

        service.delete(5L);

        verify(cultivationService).resetTransplantDate();
    }

    @Test
    @DisplayName("deleting one of several plants keeps the transplant date")
    void deletingOnePlantKeepsTransplantDate() {
        Plant plant = new Plant("Melon-01", "Melon 01");
        TestFixtures.setId(plant, 5L);
        when(plantRepository.findById(5L)).thenReturn(Optional.of(plant));
        when(plantRepository.count()).thenReturn(4L);

        service.delete(5L);

        org.mockito.Mockito.verify(cultivationService, org.mockito.Mockito.never()).resetTransplantDate();
    }
}
