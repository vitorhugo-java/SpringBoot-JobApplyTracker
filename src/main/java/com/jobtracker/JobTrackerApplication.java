package com.jobtracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.freemarker.FreeMarkerAutoConfiguration;
import org.springframework.boot.autoconfigure.groovy.template.GroovyTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.mustache.MustacheAutoConfiguration;
import org.springframework.boot.autoconfigure.websocket.servlet.WebSocketServletAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.jmx.JmxEndpointAutoConfiguration;

@SpringBootApplication(exclude = {
        JmxAutoConfiguration.class,
        JmxEndpointAutoConfiguration.class,
        FreeMarkerAutoConfiguration.class,
        GroovyTemplateAutoConfiguration.class,
        MustacheAutoConfiguration.class,
        WebSocketServletAutoConfiguration.class
})
public class JobTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobTrackerApplication.class, args);
    }
}
