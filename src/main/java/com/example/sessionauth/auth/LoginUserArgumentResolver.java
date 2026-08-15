package com.example.sessionauth.auth;

import com.example.sessionauth.domain.SessionUser;
import com.example.sessionauth.session.LoginSessionStore;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final LoginSessionStore loginSessionStore;

    LoginUserArgumentResolver(LoginSessionStore loginSessionStore) {
        this.loginSessionStore = loginSessionStore;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginUser.class) &&
                parameter.getParameterType().equals(SessionUser.class);
    }

    @Override
    public @Nullable Object resolveArgument(
            MethodParameter parameter,
            @Nullable ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            @Nullable WebDataBinderFactory binderFactory) throws Exception {
        return loginSessionStore.current().orElseThrow(IllegalStateException::new);
    }
}
