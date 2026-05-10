package com.resumeai.payment.repository;

import com.resumeai.payment.model.PaymentOrder;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, String> {
    List<PaymentOrder> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<PaymentOrder> findAllByOrderByCreatedAtDesc();
}
