package io.fundrequest.core.request.fund.domain;

import io.fundrequest.core.infrastructure.repository.AbstractEntity;
import lombok.Builder;
import lombok.Getter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table(name = "fund")
@Entity
@Getter
public class Fund extends AbstractEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "funder")
    private String funder;

    @Column(name = "amount_in_wei")
    private BigDecimal amountInWei;

    @Column(name = "token")
    private String token;

    @Column(name = "time_stamp")
    private LocalDateTime timestamp;

    @Column(name = "request_id")
    private Long requestId;

    protected Fund() {
    }

    @Builder
    Fund(String funder, BigDecimal amountInWei, String token, Long requestId, LocalDateTime timestamp) {
        this.funder = funder;
        this.amountInWei = amountInWei;
        this.token = token;
        this.requestId = requestId;
        this.timestamp = timestamp;
    }

    void setId(Long id) {
        this.id = id;
    }

}
