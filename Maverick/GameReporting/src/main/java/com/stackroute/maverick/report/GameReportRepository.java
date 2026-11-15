package com.stackroute.maverick.report;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.stackroute.maverick.report.GameReport.GameMode;

public interface GameReportRepository extends MongoRepository<GameReport, String> {

	List<GameReport> findByUserIdOrderByCompletedAtDesc(int userId);

	List<GameReport> findByUserIdAndModeOrderByCompletedAtDesc(int userId, GameMode mode);
}
