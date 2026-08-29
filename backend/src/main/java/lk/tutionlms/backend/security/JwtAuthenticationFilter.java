package lk.tutionlms.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // HTTP Request එකේ "Authorization" header එක ගන්නවා
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;

        // Header එක නැත්නම්, හෝ "Bearer " කියන වචනෙන් පටන් ගන්නේ නැත්නම්, මේකෙ token
        // එකක් නෑ.
        // ඒ නිසා token check කරන්නේ නැතුව ඊළඟට යවනවා (public API එකක් වෙන්න පුළුවන්)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // "Bearer " කියන අකුරු 7 අයින් කරලා token එක විතරක් ගන්නවා
        jwt = authHeader.substring(7);
        // Token එකෙන් email (username) එක extract කරනවා
        try {
            userEmail = jwtService.extractUsername(jwt);
        } catch (RuntimeException ex) {
            filterChain.doFilter(request, response);
            return;
        }

        // Email එකක් තියෙනවා නම් සහ දැනටමත් authenticate වෙලා නැත්නම්
        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // Database එකෙන් User details ගන්නවා
            UserDetails userDetails;
            try {
                userDetails = this.userDetailsService.loadUserByUsername(userEmail);
            } catch (RuntimeException ex) {
                filterChain.doFilter(request, response);
                return;
            }

            // Token එක valid ද කියලා check කරනවා (expire වෙලාද, user ගේමද කියලා)
            if (jwtService.isTokenValid(jwt, userDetails)) {

                // Valid නම් Spring Security වලට user ව login කරවනවා (Context එක update කරනවා)
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities());
                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        // Filter chain එකේ ඊළඟ පියවරට යවනවා
        filterChain.doFilter(request, response);
    }
}
