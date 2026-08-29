package com.saferoute.domain.training.repository;

import com.saferoute.domain.training.entity.TrainingScenario;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TrainingScenarioRepository extends JpaRepository<TrainingScenario, UUID> {
    List<TrainingScenario> findAllByBuilding_SchoolNameOrderByCreatedAtDesc(String schoolName);

    Optional<TrainingScenario> findByIdAndBuilding_SchoolName(UUID id, String schoolName);
}
