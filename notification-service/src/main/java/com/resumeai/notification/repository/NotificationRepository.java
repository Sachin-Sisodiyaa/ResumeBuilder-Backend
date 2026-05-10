package com.resumeai.notification.repository;

import com.resumeai.notification.model.Notification;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {

    List<Notification> findByRecipientIdOrderBySentAtDesc(Long recipientId);

    List<Notification> findByRecipientIdAndReadStatusFalseOrderBySentAtDesc(Long recipientId);

    long countByRecipientIdAndReadStatusFalse(Long recipientId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE Notification n SET n.readStatus = true WHERE n.recipientId = :recipientId AND n.readStatus = false")
    int markAllAsRead(@Param("recipientId") Long recipientId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    int deleteBySentAtBefore(Instant cutoff);
}
