package com.example.sessionauth.config;

import com.example.sessionauth.filter.AuthFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<AuthFilter> authFilter() {
        var registration = new FilterRegistrationBean<>(new AuthFilter());
        registration.addUrlPatterns("/me");
        return registration;
    }
}
