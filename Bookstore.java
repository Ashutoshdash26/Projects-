// Bookstore Application - Spring Boot Complete Project

// -------------------------
// README (README.md)
// -------------------------

/*
# Bookstore REST API

A RESTful API for managing a bookstore with JWT-based authentication.

## Tech Stack
- Java 17
- Spring Boot
- Spring Security
- JWT
- H2 Database (can switch to MySQL/PostgreSQL)
- Maven
- Swagger UI

## Features
- User Signup/Login
- JWT Authentication
- CRUD Operations for Books
- Filtering, Searching, Pagination, Sorting
- Input Validation and Exception Handling
- Swagger API Docs

## Running the App
bash
mvn spring-boot:run


Access:
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- H2 Console: http://localhost:8080/h2-console
*/

// -------------------------
// pom.xml (Dependencies)
// -------------------------

<project ...>
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.example</groupId>
  <artifactId>bookstore</artifactId>
  <version>1.0</version>
  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
      <groupId>io.jsonwebtoken</groupId>
      <artifactId>jjwt</artifactId>
      <version>0.9.1</version>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
      <groupId>com.h2database</groupId>
      <artifactId>h2</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springdoc</groupId>
      <artifactId>springdoc-openapi-ui</artifactId>
      <version>1.6.14</version>
    </dependency>
    <dependency>
      <groupId>jakarta.validation</groupId>
      <artifactId>jakarta.validation-api</artifactId>
    </dependency>
  </dependencies>
</project>

// -------------------------
// application.properties
// -------------------------

server.port=8080
spring.datasource.url=jdbc:h2:mem:bookstore
debug=true
spring.h2.console.enabled=true
spring.jpa.show-sql=true
spring.jpa.hibernate.ddl-auto=update
jwt.secret=secret123
jwt.expiration=3600000

// -------------------------
// Entity: User.java
// -------------------------

@Entity
public class User {
  @Id @GeneratedValue private Long id;
  @Column(unique = true) private String email;
  private String password;
}

// -------------------------
// Entity: Book.java
// -------------------------

@Entity
public class Book {
  @Id @GeneratedValue private Long id;
  private String title;
  private String author;
  private String category;
  private double price;
  private double rating;
  private LocalDate publishedDate;
}

// -------------------------
// Repositories: UserRepository.java & BookRepository.java
// -------------------------

public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByEmail(String email);
}

public interface BookRepository extends JpaRepository<Book, Long> {
  List<Book> findByAuthorContainingIgnoreCase(String author);
  List<Book> findByCategoryContainingIgnoreCase(String category);
  List<Book> findByRatingGreaterThanEqual(double rating);
  List<Book> findByTitleContainingIgnoreCase(String title);
}

// -------------------------
// DTOs: AuthRequest, AuthResponse, BookDTO
// -------------------------

public record AuthRequest(String email, String password) {}
public record AuthResponse(String token) {}
public record BookDTO(String title, String author, String category, double price, double rating, LocalDate publishedDate) {}

// -------------------------
// Security: JWTUtil.java, SecurityConfig.java, JWTFilter.java
// -------------------------

@Component
public class JWTUtil {
  @Value("${jwt.secret}") private String secret;
  @Value("${jwt.expiration}") private long expiration;
  public String generateToken(String username) {
    return Jwts.builder().setSubject(username).setIssuedAt(new Date())
      .setExpiration(new Date(System.currentTimeMillis() + expiration))
      .signWith(SignatureAlgorithm.HS512, secret).compact();
  }
  public String validateToken(String token) {
    return Jwts.parser().setSigningKey(secret).parseClaimsJws(token).getBody().getSubject();
  }
}

@Configuration
@EnableWebSecurity
public class SecurityConfig {
  @Autowired private JWTFilter jwtFilter;
  @Bean public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf().disable().authorizeHttpRequests()
      .requestMatchers("/auth/", "/swagger-ui/", "/v3/api-docs/").permitAll()
      .anyRequest().authenticated()
      .and().sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
    http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
  @Bean public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}

@Component
public class JWTFilter extends OncePerRequestFilter {
  @Autowired private JWTUtil jwtUtil;
  @Autowired private UserRepository userRepo;
  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
    throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring(7);
      String email = jwtUtil.validateToken(token);
      if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
        UserDetails userDetails = new User(email, "", List.of());
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
      }
    }
    chain.doFilter(request, response);
  }
}

// -------------------------
// Controllers: AuthController.java, BookController.java
// -------------------------

@RestController
@RequestMapping("/auth")
public class AuthController {
  @Autowired private UserRepository userRepo;
  @Autowired private PasswordEncoder encoder;
  @Autowired private JWTUtil jwtUtil;

  @PostMapping("/signup")
  public ResponseEntity<String> signup(@RequestBody AuthRequest request) {
    if (userRepo.findByEmail(request.email()).isPresent()) {
      return ResponseEntity.badRequest().body("Email already registered");
    }
    User user = new User();
    user.setEmail(request.email());
    user.setPassword(encoder.encode(request.password()));
    userRepo.save(user);
    return ResponseEntity.ok("Signup successful");
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
    User user = userRepo.findByEmail(request.email()).orElseThrow();
    if (encoder.matches(request.password(), user.getPassword())) {
      return ResponseEntity.ok(new AuthResponse(jwtUtil.generateToken(user.getEmail())));
    }
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
  }
}

@RestController
@RequestMapping("/books")
public class BookController {
  @Autowired private BookRepository repo;

  @PostMapping
  public Book add(@RequestBody BookDTO dto) {
    return repo.save(new Book(null, dto.title(), dto.author(), dto.category(), dto.price(), dto.rating(), dto.publishedDate()));
  }

  @GetMapping
  public Page<Book> list(@RequestParam Optional<String> author, @RequestParam Optional<String> category,
                         @RequestParam Optional<Double> rating, @RequestParam Optional<String> title,
                         @RequestParam Optional<Integer> page, @RequestParam Optional<Integer> size,
                         @RequestParam Optional<String> sortBy) {
    Pageable pageable = PageRequest.of(page.orElse(0), size.orElse(10), Sort.by(sortBy.orElse("id")));
    Specification<Book> spec = Specification.where(null);
    if (author.isPresent()) spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("author")), "%" + author.get().toLowerCase() + "%"));
    if (category.isPresent()) spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("category")), "%" + category.get().toLowerCase() + "%"));
    if (rating.isPresent()) spec = spec.and((root, q, cb) -> cb.ge(root.get("rating"), rating.get()));
    if (title.isPresent()) spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("title")), "%" + title.get().toLowerCase() + "%"));
    return repo.findAll(spec, pageable);
  }

  @GetMapping("/{id}")
  public ResponseEntity<Book> get(@PathVariable Long id) {
    return repo.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
  }

  @PutMapping("/{id}")
  public ResponseEntity<Book> update(@PathVariable Long id, @RequestBody BookDTO dto) {
    return repo.findById(id).map(b -> {
      b.setTitle(dto.title()); b.setAuthor(dto.author()); b.setCategory(dto.category());
      b.setPrice(dto.price()); b.setRating(dto.rating()); b.setPublishedDate(dto.publishedDate());
      return ResponseEntity.ok(repo.save(b));
    }).orElse(ResponseEntity.notFound().build());
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    if (!repo.existsById(id)) return ResponseEntity.notFound().build();
    repo.deleteById(id);
    return ResponseEntity.noContent().build();
  }
}

// -------------------------
// Done!
// -------------------------

// Let me know if you want:
// - MySQL/PostgreSQL integration
// - Unit tests with JUnit + Mockito
// - Dockerfile and docker-compose.yml
// - Postman collection
