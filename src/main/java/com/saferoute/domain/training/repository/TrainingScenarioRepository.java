package com.saferoute.domain.training.repository;

import com.saferoute.domain.training.entity.TrainingScenario;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TrainingScenarioRepository extends JpaRepository<TrainingScenario, UUID> {

}
