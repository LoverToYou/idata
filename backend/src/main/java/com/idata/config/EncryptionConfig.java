package com.idata.config;

import com.idata.utils.PasswordEncryptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EncryptionConfig {

    @Bean
    public PasswordEncryptor passwordEncryptor(@Value("${idata.encryption.key}") String key) {
        return new PasswordEncryptor(key);
    }
}
