package org.ecom.largecom.common.config;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class SchemaSafetyGuard implements BeanFactoryPostProcessor, EnvironmentAware {
    private Environment env;

    @Override
    public void setEnvironment(@NonNull Environment environment) {
        this.env = environment;
    }

    @Override
    public void postProcessBeanFactory(@NonNull ConfigurableListableBeanFactory bf) {
        var ddl = env.getProperty("spring.jpa.hibernate.ddl-auto", "validate");
        if(!ddl.equals("validate") && !ddl.equals("none")) {
            throw new IllegalStateException("Refusing to start: ddl-auto=" + ddl + " can destory data");
        }
        if (!env.getProperty("spring.flywat.clean-disabled", Boolean.class,true)) {
            throw new IllegalStateException("Refusing to start: spring.flywat.clean-disabled=true");
        }
    }

}
