package com.mindlink.recommendation.repository;

import com.mindlink.recommendation.EmotionCategory;
import com.mindlink.recommendation.domain.RecommendationBook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecommendationBookRepository extends JpaRepository<RecommendationBook, Long> {
    List<RecommendationBook> findByEmotion(EmotionCategory emotion);
    Optional<RecommendationBook> findByIsbn(String isbn);
}
