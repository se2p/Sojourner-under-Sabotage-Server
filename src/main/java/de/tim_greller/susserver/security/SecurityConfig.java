package de.tim_greller.susserver.security;

import java.util.stream.Stream;

import static org.springframework.security.config.Customizer.withDefaults;

import de.tim_greller.susserver.service.auth.SusUserDetailsService;
import de.tim_greller.susserver.service.auth.UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

@Configuration
public class SecurityConfig {

    /**
     * Apply the API filter chain first. Uses JWT tokens for authentication & authorization.
     * Creation of a new token is done by the {@link de.tim_greller.susserver.controller.api.ApiAuthenticationController}
     * via username and password. The {@link JwtRequestFilter} handles the authorization via tokens.
     */
    @Configuration
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    public static class ApiSecurityConfig {

        private final String apiUrl;
        private final JwtRequestFilter jwtRequestFilter;
        private final MvcRequestMatcher.Builder mvc;

        @Autowired
        public ApiSecurityConfig(JwtRequestFilter jwtRequestFilter, MvcRequestMatcher.Builder mvc,
                                 @Value("${paths.api}") String apiUrl) {
            this.jwtRequestFilter = jwtRequestFilter;
            this.mvc = mvc;
            this.apiUrl = apiUrl;
        }

        @Bean
        public SecurityFilterChain securityFilterChainAPI(HttpSecurity http) throws Exception {
            return http
                    .securityMatcher(mvc.pattern(apiUrl + "/**"))
                    .cors(withDefaults())
                    // TODO: enable CSRF protection
                    // .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(requests -> requests
                            .requestMatchers(
                                    mvc.pattern(apiUrl + "/hello"),
                                    mvc.pattern(apiUrl + "/auth")).permitAll()
                            .requestMatchers(mvc.pattern(apiUrl + "/admin/**")).hasAuthority("ADMIN")
                            .requestMatchers(mvc.pattern(apiUrl + "/**")).authenticated()
                    )
                    // disable redirect to log in form and send 401 instead
                    .exceptionHandling(ehc -> ehc
                            .defaultAuthenticationEntryPointFor(
                                    (request, response, authException) -> {
                                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                    },
                                    (request) -> request.getRequestURI().startsWith(apiUrl)
                            )
                    )
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class)
                    .build();
        }

        @Bean
        CorsConfigurationSource corsConfigurationSource() {
            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            CorsConfiguration config = new CorsConfiguration().applyPermitDefaultValues();
            config.addAllowedMethod("PUT");
            source.registerCorsConfiguration(apiUrl + "/**", config);
            return source;
        }

        @Bean
        public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
                throws Exception {
            return authenticationConfiguration.getAuthenticationManager();
        }
    }

    /**
     * Filter applied to all URLs (except the API-URLs already handled by the higher precedence filter) and require
     * default Spring Security Authentication.
     */
    @Configuration
    @Order(Ordered.HIGHEST_PRECEDENCE + 2)
    @RequiredArgsConstructor
    public static class WebSecurityConfig {

        private final MvcRequestMatcher.Builder mvc;

        private MvcRequestMatcher[] mvcPattern(String... strings) {
            return Stream.of(strings).map(mvc::pattern).toArray(MvcRequestMatcher[]::new);
        }

        @Bean
        public SecurityFilterChain securityFilterChainWeb(HttpSecurity http) throws Exception {
            return http
                    .authorizeHttpRequests((requests) -> requests
                            .requestMatchers(new AntPathRequestMatcher("/websocket/**")).permitAll()
                            .requestMatchers(mvcPattern(
                                    "/", "/home", "/register", "/error", "/login",
                                    "/css/**", "/js/**", "/images/**", "/fonts/**",
                                    "/webpack/**", "/unity/**",
                                    "/favicon.ico", "/robots.txt", "/websocket/**"
                            )).permitAll()
                            .requestMatchers(mvcPattern("/websocket/**")).permitAll()
                            .requestMatchers(mvcPattern(
                                    "/monitoring", "/monitoring/**", "admin", "/admin/**"
                            )).hasAuthority("ADMIN")
                            .anyRequest().authenticated()
                    )
                    .formLogin((form) -> form
                            .loginPage("/login")
                            .defaultSuccessUrl("/")
                            .permitAll()
                    )
                    .logout(logoutConfig -> logoutConfig
                            .logoutRequestMatcher(mvc.pattern(HttpMethod.GET, "/logout"))
                            .logoutSuccessUrl("/?logout")
                            .deleteCookies("JSESSIONID")
                            .invalidateHttpSession(true)
                            .permitAll()
                    )
                    .build();
        }
    }


    // Services used by API and Web

    @Bean
    public UserDetailsService userDetailsService(UserService userService) {
        return new SusUserDetailsService(userService);
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    MvcRequestMatcher.Builder mvc(HandlerMappingIntrospector introspector) {
        return new MvcRequestMatcher.Builder(introspector);
    }
}
