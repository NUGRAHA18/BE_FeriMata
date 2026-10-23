package com.fertimata.backend.plant;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface HarvestRecordRepository
        extends JpaRepository<HarvestRecord, Long>, JpaSpecificationExecutor<HarvestRecord> {

    List<HarvestRecord> findByPlantIdOrderByHarvestedOnDescIdDesc(Long plantId);
}
