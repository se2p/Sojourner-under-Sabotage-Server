package de.tim_greller.susserver.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LoginRedirectSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        String redirect = session != null ? (String) session.getAttribute("LOGIN_REDIRECT") : null;
        if (session != null) {
            session.removeAttribute("LOGIN_REDIRECT");
        }

        if (isSafeLocalRedirect(redirect)) {
            getRedirectStrategy().sendRedirect(request, response, redirect);
            return;
        }

        super.onAuthenticationSuccess(request, response, authentication);
    }

    private boolean isSafeLocalRedirect(String redirect) {
        if (!StringUtils.hasText(redirect)) {
            return false;
        }
        if (!redirect.startsWith("/")) {
            return false;
        }
        return !redirect.startsWith("//") && !redirect.startsWith("/\\");
    }
}
