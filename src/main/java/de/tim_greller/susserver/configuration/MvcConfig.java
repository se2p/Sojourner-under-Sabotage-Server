package de.tim_greller.susserver.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Value("${paths.api}") String apiUrl;

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/hello").setViewName("hello");
        registry.addViewController("/editor").setViewName("editor");
        registry.addViewController("/error").setViewName("error");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping(apiUrl + "/**");
    }

}
