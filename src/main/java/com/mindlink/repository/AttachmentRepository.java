package com.mindlink.repository;

import com.mindlink.domain.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    List<Attachment> findByTargetTypeAndTargetId(Attachment.TargetType targetType, Long targetId);

    @Modifying
    @Query("DELETE FROM Attachment a WHERE a.targetType = :targetType AND a.targetId = :targetId")
    void deleteByTargetTypeAndTargetId(@Param("targetType") Attachment.TargetType targetType,
                                        @Param("targetId") Long targetId);
}
