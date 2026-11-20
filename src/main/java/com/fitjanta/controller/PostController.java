package com.fitjanta.controller;

import com.fitjanta.model.Comment;
import com.fitjanta.model.Post;
import com.fitjanta.model.User;
import com.fitjanta.repository.CommentRepository;
import com.fitjanta.repository.PostRepository;
import com.fitjanta.repository.UserRepository;
import com.fitjanta.service.ImageStorageService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Photo posts with captions and comments (no likes).
 * Images are saved to uploads/posts/; posts and comments are rows in MySQL.
 */
@RestController
@RequestMapping("/api/posts")
public class PostController {

    private static final int MAX_CAPTION = 2200;
    private static final int MAX_COMMENT = 500;

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;
    private final ImageStorageService images;

    public PostController(PostRepository postRepository, UserRepository userRepository,
                          CommentRepository commentRepository, ImageStorageService images) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.commentRepository = commentRepository;
        this.images = images;
    }

    public record CommentView(Long id, Long postId, Long userId, String userName, String userAvatar,
                              String text, LocalDateTime createdAt) {
    }

    public record PostView(Long id, Long userId, String userName, String userCity, String userAvatar, int userLevel,
                           String caption, String imageUrl, LocalDateTime createdAt,
                           long commentCount, List<CommentView> latestComments) {
    }

    public record CommentRequest(Long userId, String text) {
    }

    // ------------------------------------------------------------------
    // Posts
    // ------------------------------------------------------------------

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> create(@RequestParam Long userId,
                                    @RequestParam(required = false) String caption,
                                    @RequestParam("image") MultipartFile image) throws IOException {

        Optional<User> user = userRepository.findById(userId);
        if (user.isEmpty()) {
            return error("Please log in again.");
        }

        String imagePath;
        try {
            imagePath = images.save(image, "posts");
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }

        String cleanCaption = caption == null ? "" : caption.trim();
        if (cleanCaption.length() > MAX_CAPTION) cleanCaption = cleanCaption.substring(0, MAX_CAPTION);

        Post post = new Post();
        post.setUserId(userId);
        post.setCaption(cleanCaption);
        post.setImagePath(imagePath);
        post = postRepository.save(post);

        return ResponseEntity.ok(views(List.of(post)).get(0));
    }

    // Community feed, newest first
    @GetMapping
    public List<PostView> feed() {
        return views(postRepository.findAllByOrderByIdDesc());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> one(@PathVariable Long id) {
        return postRepository.findById(id)
                .<ResponseEntity<?>>map(p -> ResponseEntity.ok(views(List.of(p)).get(0)))
                .orElse(ResponseEntity.status(404).body(Map.of("message", "Post not found.")));
    }

    @GetMapping("/user/{userId}")
    public List<PostView> byUser(@PathVariable Long userId) {
        return views(postRepository.findByUserIdOrderByIdDesc(userId));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> delete(@PathVariable Long id, @RequestParam Long userId) {
        Optional<Post> post = postRepository.findById(id);
        if (post.isEmpty() || !Objects.equals(post.get().getUserId(), userId)) {
            return ResponseEntity.status(404).body(Map.of("message", "Post not found."));
        }
        images.delete(post.get().getImagePath());
        commentRepository.deleteByPostId(id);
        postRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", id));
    }

    // ------------------------------------------------------------------
    // Comments
    // ------------------------------------------------------------------

    @GetMapping("/{postId}/comments")
    public List<CommentView> comments(@PathVariable Long postId) {
        List<Comment> list = commentRepository.findByPostIdOrderByIdAsc(postId);
        Map<Long, User> users = usersById(list.stream().map(Comment::getUserId).collect(Collectors.toSet()));
        return list.stream().map(c -> toView(c, users.get(c.getUserId()))).toList();
    }

    @PostMapping("/{postId}/comments")
    public ResponseEntity<?> addComment(@PathVariable Long postId, @RequestBody CommentRequest request) {
        if (request.userId() == null) return error("Please log in again.");
        Optional<User> user = userRepository.findById(request.userId());
        if (user.isEmpty()) return error("Please log in again.");
        if (!postRepository.existsById(postId)) {
            return ResponseEntity.status(404).body(Map.of("message", "This post was deleted."));
        }
        String text = request.text() == null ? "" : request.text().trim();
        if (text.isEmpty()) return error("Write something first.");
        if (text.length() > MAX_COMMENT) return error("Comments can be up to " + MAX_COMMENT + " characters.");

        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setUserId(request.userId());
        comment.setText(text);
        comment = commentRepository.save(comment);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("comment", toView(comment, user.get()));
        result.put("commentCount", commentRepository.countByPostId(postId));
        return ResponseEntity.ok(result);
    }

    // A comment can be deleted by its author or by the owner of the post
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<?> deleteComment(@PathVariable Long commentId, @RequestParam Long userId) {
        Optional<Comment> comment = commentRepository.findById(commentId);
        if (comment.isEmpty()) return ResponseEntity.status(404).body(Map.of("message", "Comment not found."));

        Long postOwner = postRepository.findById(comment.get().getPostId()).map(Post::getUserId).orElse(null);
        if (!Objects.equals(comment.get().getUserId(), userId) && !Objects.equals(postOwner, userId)) {
            return ResponseEntity.status(403).body(Map.of("message", "You can only delete your own comments."));
        }
        Long postId = comment.get().getPostId();
        commentRepository.deleteById(commentId);
        return ResponseEntity.ok(Map.of("deleted", commentId, "commentCount", commentRepository.countByPostId(postId)));
    }

    // ------------------------------------------------------------------

    private List<PostView> views(List<Post> posts) {
        if (posts.isEmpty()) return List.of();
        List<Long> postIds = posts.stream().map(Post::getId).toList();

        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : commentRepository.countByPostIds(postIds)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        Map<Long, List<Comment>> commentsByPost = commentRepository.findByPostIdIn(postIds).stream()
                .collect(Collectors.groupingBy(Comment::getPostId));

        Set<Long> userIds = new HashSet<>(posts.stream().map(Post::getUserId).toList());
        commentsByPost.values().forEach(l -> l.forEach(c -> userIds.add(c.getUserId())));
        Map<Long, User> users = usersById(userIds);

        return posts.stream().map(p -> {
            User u = users.get(p.getUserId());
            List<Comment> all = commentsByPost.getOrDefault(p.getId(), List.of());
            List<CommentView> latest = all.stream()
                    .sorted(Comparator.comparing(Comment::getId))
                    .skip(Math.max(0, all.size() - 2))
                    .map(c -> toView(c, users.get(c.getUserId())))
                    .toList();
            return new PostView(p.getId(), p.getUserId(),
                    u == null ? "FitJanta member" : u.getName(),
                    u == null ? null : u.getCity(),
                    u == null ? null : u.getAvatarUrl(),
                    u == null ? 1 : Math.max(1, u.getLevel()),
                    p.getCaption(), p.getImagePath(), p.getCreatedAt(),
                    counts.getOrDefault(p.getId(), 0L), latest);
        }).toList();
    }

    private CommentView toView(Comment c, User u) {
        return new CommentView(c.getId(), c.getPostId(), c.getUserId(),
                u == null ? "FitJanta member" : u.getName(),
                u == null ? null : u.getAvatarUrl(),
                c.getText(), c.getCreatedAt());
    }

    private Map<Long, User> usersById(Collection<Long> ids) {
        return userRepository.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private static ResponseEntity<Map<String, String>> error(String message) {
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }
}
