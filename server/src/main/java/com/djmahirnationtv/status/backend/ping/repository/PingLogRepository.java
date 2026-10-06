package com.djmahirnationtv.status.backend.ping.repository;
import com.djmahirnationtv.status.backend.ping.model.PingLog;
import org.springframework.stereotype.Repository;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Aggregation;
import com.djmahirnationtv.status.backend.ping.PingHistoryService.HistoryAggregate;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.time.Instant;

@Repository
public interface PingLogRepository extends MongoRepository<PingLog, String> {
    @Aggregation(pipeline = {
        "{ $match: { monitorId: ?0, timestamp: { $gte: ?1, $lte: ?2 } } }",
        """
        { $facet: {
            daily: [
                { $group: { _id: { $dateToString: { date: "$timestamp", format: "%Y-%m-%d", timezone: "UTC" } },
                    checks: { $sum: 1 }, successful: { $sum: { $cond: ["$successful", 1, 0] } } } },
                { $project: { _id: 0, date: "$_id", checks: 1, successful: 1 } },
                { $sort: { date: 1 } }
            ],
            response: [
                { $match: { timestamp: { $gte: ?3 } } },
                { $group: { _id: { $dateTrunc: { date: "$timestamp", unit: "minute", binSize: ?4, timezone: "UTC" } },
                    responseTimeMs: { $avg: { $cond: ["$successful", "$responseTimeMs", null] } },
                    checks: { $sum: 1 }, successful: { $sum: { $cond: ["$successful", 1, 0] } } } },
                { $project: { _id: 0, timestamp: "$_id", responseTimeMs: 1, checks: 1, successful: 1 } },
                { $sort: { timestamp: 1 } }
            ],
            day: [ { $match: { timestamp: { $gte: ?5 } } },
                { $group: { _id: null, checks: { $sum: 1 }, successful: { $sum: { $cond: ["$successful", 1, 0] } } } } ],
            week: [ { $match: { timestamp: { $gte: ?6 } } },
                { $group: { _id: null, checks: { $sum: 1 }, successful: { $sum: { $cond: ["$successful", 1, 0] } } } } ],
            month: [ { $match: { timestamp: { $gte: ?7 } } },
                { $group: { _id: null, checks: { $sum: 1 }, successful: { $sum: { $cond: ["$successful", 1, 0] } } } } ],
            quarter: [ { $group: { _id: null, checks: { $sum: 1 }, successful: { $sum: { $cond: ["$successful", 1, 0] } } } } ]
        } }
        """
    })
    HistoryAggregate aggregateHistory(
            Long monitorId, Instant from, Instant until, Instant chartFrom, int bucketMinutes,
            Instant dayFrom, Instant weekFrom, Instant monthFrom);

    List<PingLog> findByMonitorIdOrderByTimestampDesc(Long monitorId, Pageable pageable);

    List<PingLog> findByMonitorIdAndTimestampAfterOrderByTimestampAsc(Long monitorId, Instant after);
    
    void deleteByMonitorId(Long monitorId);
}
