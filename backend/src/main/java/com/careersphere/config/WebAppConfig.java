package com.careersphere.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serves the built React app when it is bundled into the jar (the Docker image does this),
 * so the whole project runs from one address. Any path that is not a file and not /api
 * returns index.html, so links like /student/jobs/3 work after a page refresh.
 *
 * In local development the React app runs separately on port 5173 and this does nothing.
 */
@Configuration
public class WebAppConfig implements WebMvcConfigurer {

    private static final ClassPathResource INDEX = new ClassPathResource("/static/index.html");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String path, Resource location) throws IOException {
                        if (path.startsWith("api/") || !INDEX.exists()) return null;
                        if (!path.isEmpty() && !path.endsWith("/")) {
                            Resource file = location.createRelative(path);
                            if (file.exists() && file.isReadable()) return file;
                        }
                        return INDEX;
                    }
                });
    }
}
