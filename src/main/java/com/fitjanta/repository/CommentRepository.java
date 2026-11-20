package com.fitjanta.repository;

import com.fitjanta.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByPostIdOrderByIdAsc(Long postId);

    List<Comment> findByPostIdIn(Collection<Long> postIds);

    long countByPostId(Long postId);

    @Query("select c.postId, count(c) from Comment c where c.postId in ?1 group by c.postId")
    List<Object[]> countByPostIds(Collection<Long> postIds);

    @Transactional
    void deleteByPostId(Long postId);
}
