package com.example.chat;

import com.example.chat.common.AppExceptionMapper;
import jakarta.ws.rs.ApplicationPath;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;

@ApplicationPath("/api")
public class ChatApplication extends ResourceConfig {
    public ChatApplication() {
        packages("com.example.chat");
        register(AppExceptionMapper.class);
        register(MultiPartFeature.class);
    }
}
