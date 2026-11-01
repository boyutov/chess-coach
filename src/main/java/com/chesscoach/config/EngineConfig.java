package com.chesscoach.config;

import com.chesscoach.engine.Engine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Движок не зависит от Spring, поэтому регистрируется здесь. */
@Configuration
public class EngineConfig {

    @Bean
    public Engine engine() {
        return new Engine();
    }
}
